"""Evento, identidade serial e reserva compartilham o mesmo commit SQLite."""
import json
import random
import shutil
import time
from datetime import datetime, timezone
from pathlib import Path

from .config import Config


def tree_bytes(directory):
    total = 0
    for path in Path(directory).rglob('*'):
        try:
            if path.is_file():
                total += path.stat().st_size
        except OSError:
            continue
    return total


class EventStore:
    def __init__(self, dvr):
        self.dvr, self.catalog = dvr, dvr.catalog
        self.db, self.lock = self.catalog.db, self.catalog.lock
        self.last_accept_mono = None
        self.enabled = True
        with self.lock, self.db:
            version = self.db.execute('PRAGMA user_version').fetchone()[0]
            if version > 1:
                raise RuntimeError('DATABASE_SCHEMA_TOO_NEW')
            self.db.executescript('''
                CREATE TABLE IF NOT EXISTS events (
                    id TEXT PRIMARY KEY, device TEXT NOT NULL, serial_session TEXT NOT NULL,
                    sequence INTEGER NOT NULL, timestamp TEXT NOT NULL, created REAL NOT NULL,
                    request TEXT NOT NULL, config TEXT NOT NULL, state TEXT NOT NULL,
                    clip TEXT, sha256 TEXT, object_key TEXT, attempts INTEGER NOT NULL DEFAULT 0,
                    next_attempt REAL NOT NULL DEFAULT 0, error TEXT,
                    UNIQUE(device, serial_session, sequence)
                );
                CREATE TABLE IF NOT EXISTS serial_results (
                    device TEXT NOT NULL, session TEXT NOT NULL, sequence INTEGER NOT NULL,
                    payload TEXT NOT NULL, reply TEXT NOT NULL,
                    PRIMARY KEY(device, session, sequence)
                );
                CREATE TABLE IF NOT EXISTS attempts (
                    event_id TEXT NOT NULL, time REAL NOT NULL, stage TEXT NOT NULL,
                    error TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS configuration (
                    singleton INTEGER PRIMARY KEY CHECK(singleton=1), payload TEXT NOT NULL
                );
            ''')
            columns = {row['name'] for row in self.db.execute('PRAGMA table_info(events)')}
            if 'completed' not in columns:
                self.db.execute('ALTER TABLE events ADD COLUMN completed REAL')
            self.db.execute('PRAGMA user_version=1')

    def existing(self, device, session, sequence, payload):
        with self.lock:
            row = self.db.execute('SELECT * FROM serial_results WHERE device=? AND session=? AND sequence=?',
                                  (device, session, sequence)).fetchone()
        if row is None:
            return None
        if row['payload'] != payload:
            return {'accepted': False, 'reason': 'IDENTITY_CONFLICT'}
        return json.loads(row['reply'])

    def capacity(self):
        if not self.enabled:
            raise ValueError('DEVICE_INACTIVE')
        with self.lock:
            pending = self.db.execute("SELECT COUNT(*) FROM events WHERE state!='REGISTRO_CONFIRMADO'").fetchone()[0]
        if pending >= Config.MAX_PENDING_EVENTS:
            raise ValueError('CAPACITY')
        estimated = int((Config.REPLAY_BEFORE_SECONDS + Config.REPLAY_AFTER_SECONDS + 1)
                        * Config.CAPACITY_BITRATE / 8 * 3)
        reserved = pending * estimated
        if shutil.disk_usage(self.catalog.root).free < Config.MIN_FREE_BYTES + reserved + estimated:
            raise ValueError('CAPACITY')
        if tree_bytes(self.catalog.root) + estimated > Config.BUFFER_QUOTA_BYTES:
            raise ValueError('CAPACITY')
        if tree_bytes(Config.OUTPUT_DIR) + reserved + estimated > Config.CLIPS_QUOTA_BYTES:
            raise ValueError('CAPACITY')

    def reject(self, device, session, sequence, payload, reason):
        with self.lock, self.db:
            duplicate = self.existing(device, session, sequence, payload)
            if duplicate is not None:
                return duplicate
            reply = {'accepted': False, 'reason': reason}
            self.db.execute('INSERT INTO serial_results VALUES (?,?,?,?,?)',
                            (device, session, sequence, payload, json.dumps(reply)))
            return reply

    def health(self):
        try:
            self.capacity()
            self.dvr.snapshot_replay(time.monotonic(), reserve=False)
            return 'READY'
        except ValueError as exc:
            return 'WARMING_UP' if str(exc) == 'WARMING_UP' else 'UNAVAILABLE'

    def accept(self, device, session, sequence, payload, click_mono, click_utc, *, valid_session=True):
        with self.lock:
            duplicate = self.existing(device, session, sequence, payload)
            if duplicate is not None:
                return duplicate
            self.db.execute('BEGIN IMMEDIATE')
            try:
                self.db.execute('SAVEPOINT acceptance')
                try:
                    if not valid_session:
                        raise ValueError('NOT_SYNCED')
                    self.capacity()
                    if self.last_accept_mono is not None and time.monotonic() - self.last_accept_mono < 5:
                        raise ValueError('COOLDOWN')
                    request = self.dvr.snapshot_replay(click_mono, commit=False)
                    snapshot = json.dumps({
                        'device': device, 'quadra_id': Config.QUADRA_ID, 'bucket': Config.S3_BUCKET_NAME,
                        'version': getattr(Config, 'CONFIG_VERSION', 'local'),
                    }, sort_keys=True)
                    timestamp = datetime.fromtimestamp(click_utc, timezone.utc).isoformat()
                    self.db.execute('''INSERT INTO events
                        (id,device,serial_session,sequence,timestamp,created,request,config,state)
                        VALUES (?,?,?,?,?,?,?,?,?)''',
                        (request['id'], device, session, sequence, timestamp, time.time(),
                         json.dumps(request), snapshot, 'ACEITO'))
                    reply = {'accepted': True, 'event_id': request['id']}
                    self.db.execute('RELEASE acceptance')
                except ValueError as exc:
                    self.db.execute('ROLLBACK TO acceptance')
                    self.db.execute('RELEASE acceptance')
                    reply = {'accepted': False, 'reason': str(exc)}
                self.db.execute('INSERT INTO serial_results VALUES (?,?,?,?,?)',
                                (device, session, sequence, payload, json.dumps(reply)))
                self.db.commit()
                if reply['accepted']:
                    self.last_accept_mono = time.monotonic()
                return reply
            except BaseException:
                self.db.rollback()
                raise

    def get(self, event_id):
        with self.lock:
            row = self.db.execute('SELECT * FROM events WHERE id=?', (event_id,)).fetchone()
            if row is None:
                raise ValueError('UNKNOWN_EVENT')
            return dict(row)

    def due(self):
        with self.lock:
            row = self.db.execute('''SELECT id FROM events WHERE state NOT IN
                ('REGISTRO_CONFIRMADO','INTERVENCAO') AND next_attempt<=? ORDER BY created LIMIT 1''',
                                  (time.time(),)).fetchone()
            return self.get(row['id']) if row else None

    def advance(self, event_id, state, *, clip=None, sha256=None, object_key=None, release=False):
        with self.lock, self.db:
            self.db.execute('''UPDATE events SET state=?, clip=COALESCE(?,clip),
                sha256=COALESCE(?,sha256),object_key=COALESCE(?,object_key),
                attempts=0,next_attempt=0,error=NULL WHERE id=?''',
                            (state, clip, sha256, object_key, event_id))
            if release:
                self.db.execute('DELETE FROM replay_reservations WHERE id=?', (event_id,))
            if state == 'REGISTRO_CONFIRMADO':
                self.db.execute('UPDATE events SET completed=? WHERE id=?', (time.time(), event_id))

    def fail(self, event_id, error, *, permanent=False):
        with self.lock, self.db:
            event = self.get(event_id)
            attempt = event['attempts'] + 1
            # Apenas classificação, nunca resposta cloud, URL ou token bruto.
            code = type(error).__name__
            if isinstance(error, ValueError):
                code = str(error) if str(error).isupper() and len(str(error)) < 64 else code
            stop = permanent or attempt >= Config.MAX_RETRIES
            delay = min(900, 5 * 2**min(attempt - 1, 12)) * random.uniform(1, 1.2)
            self.db.execute('INSERT INTO attempts VALUES (?,?,?,?)',
                            (event_id, time.time(), event['state'], code))
            self.db.execute('UPDATE events SET attempts=?,next_attempt=?,error=?,state=? WHERE id=?',
                            (attempt, time.time() + delay, code,
                             'INTERVENCAO' if stop else event['state'], event_id))

    def retry(self, event_id):
        with self.lock, self.db:
            event = self.get(event_id)
            if event['state'] != 'INTERVENCAO':
                raise ValueError('EVENT_NOT_IN_INTERVENTION')
            stage = self.db.execute('SELECT stage FROM attempts WHERE event_id=? ORDER BY rowid DESC LIMIT 1',
                                    (event_id,)).fetchone()
            if stage is None:
                raise ValueError('MISSING_STAGE_HISTORY')
            self.db.execute('UPDATE events SET state=?,attempts=0,next_attempt=0,error=NULL WHERE id=?',
                            (stage['stage'], event_id))
