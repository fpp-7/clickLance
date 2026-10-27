"""Durable local outbox: accepted events retain identity, window and progress."""
from contextlib import closing, contextmanager
import json
from pathlib import Path
import sqlite3
import time
from uuid import uuid4

class EventStore:
    def __init__(self, path, max_pending=2000):
        self.path, self.max_pending = str(path), max_pending
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        with self._connect() as db:
            db.execute("PRAGMA journal_mode=WAL")
            db.execute("CREATE TABLE IF NOT EXISTS events (evento_id TEXT PRIMARY KEY,timestamp REAL NOT NULL,payload TEXT NOT NULL,state TEXT NOT NULL DEFAULT 'CAPTURED',attempts INTEGER NOT NULL DEFAULT 0,next_attempt REAL NOT NULL DEFAULT 0,clip_path TEXT,s3_key TEXT,error TEXT,confirmed_at REAL)")
            db.execute("CREATE INDEX IF NOT EXISTS due_events ON events(state,next_attempt,timestamp)")

    @contextmanager
    def _connect(self):
        db = sqlite3.connect(self.path, timeout=30)
        db.row_factory = sqlite3.Row
        try:
            yield db
            db.commit()
        except Exception:
            db.rollback()
            raise
        finally:
            db.close()

    def enqueue(self, timestamp, *, dispositivo_id, quadra_id, before, after=0.2, evento_id=None):
        event_id = str(evento_id or uuid4())
        payload = dict(dispositivo_id=dispositivo_id, quadra_id=quadra_id, before=before, after=after)
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            if db.execute("SELECT COUNT(*) FROM events WHERE state!='CONFIRMED'").fetchone()[0] >= self.max_pending:
                raise RuntimeError("Fila local cheia: clique nao aceito.")
            db.execute("INSERT INTO events(evento_id,timestamp,payload) VALUES(?,?,?)", (event_id,timestamp,json.dumps(payload)))
        return event_id

    @staticmethod
    def _decode(row):
        if row is None:
            return None
        event = dict(row)
        event.update(json.loads(event.pop("payload")))
        return event

    def get(self, event_id):
        with self._connect() as db:
            return self._decode(db.execute("SELECT * FROM events WHERE evento_id=?", (event_id,)).fetchone())

    def due(self, states, now=None):
        with self._connect() as db:
            marks = ",".join("?" for _ in states)
            row = db.execute(f"SELECT * FROM events WHERE state IN ({marks}) AND next_attempt<=? ORDER BY timestamp LIMIT 1", (*states,time.time() if now is None else now)).fetchone()
            return self._decode(row)

    def transition(self, event_id, state, *, clip_path=None, s3_key=None):
        with self._connect() as db:
            db.execute("UPDATE events SET state=?,clip_path=COALESCE(?,clip_path),s3_key=COALESCE(?,s3_key),attempts=0,next_attempt=0,error=NULL,confirmed_at=? WHERE evento_id=?", (state,clip_path,s3_key,time.time() if state=="CONFIRMED" else None,event_id))

    def retry(self, event_id, error, *, delay=None):
        with self._connect() as db:
            row = db.execute("SELECT attempts FROM events WHERE evento_id=?", (event_id,)).fetchone()
            wait = min(300,2**min(row[0]+1,8)) if delay is None else delay
            db.execute("UPDATE events SET attempts=attempts+1,next_attempt=?,error=? WHERE evento_id=?", (time.time()+wait,str(error)[:1000],event_id))

    def capture_windows(self):
        with self._connect() as db:
            return [(e["timestamp"]-e["before"],e["timestamp"]+e["after"]) for e in map(self._decode,db.execute("SELECT * FROM events WHERE state='CAPTURED'"))]

    def confirmed_files(self):
        with self._connect() as db:
            return [r[0] for r in db.execute("SELECT clip_path FROM events WHERE state='CONFIRMED' AND clip_path IS NOT NULL")]

    def count_pending(self):
        with self._connect() as db:
            return db.execute("SELECT COUNT(*) FROM events WHERE state!='CONFIRMED'").fetchone()[0]

    def block(self, event, error):
        with self._connect() as db:
            db.execute("UPDATE events SET state=?,error=? WHERE evento_id=?",
                       ("BLOCKED_" + event["state"], str(error)[:1000], event["evento_id"]))

    def requeue(self, event_id):
        with self._connect() as db:
            row = db.execute("SELECT state FROM events WHERE evento_id=?", (event_id,)).fetchone()
            if not row or not row[0].startswith("BLOCKED_"):
                raise ValueError("Evento inexistente ou nao bloqueado.")
            db.execute("UPDATE events SET state=?,attempts=0,next_attempt=0,error=NULL WHERE evento_id=?",
                       (row[0][len("BLOCKED_"):], event_id))

    def pending(self):
        with self._connect() as db:
            return [self._decode(row) for row in db.execute("SELECT * FROM events WHERE state!='CONFIRMED' ORDER BY timestamp")]

    def backup(self, destination):
        """SQLite online backup includes a consistent WAL snapshot; never overwrite an existing file."""
        target = Path(destination).resolve()
        if target == Path(self.path).resolve():
            raise ValueError("Destino do backup precisa ser diferente da fila ativa.")
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("xb"):
            pass
        try:
            with self._connect() as source, closing(sqlite3.connect(target)) as snapshot:
                source.backup(snapshot)
        except Exception:
            target.unlink(missing_ok=True)
            raise
        return target
