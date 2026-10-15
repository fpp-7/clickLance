"""Catálogo local de sessões e segmentos fechados; tempos relativos ao conteúdo."""
import csv
import json
import math
import sqlite3
import subprocess
import threading
from pathlib import Path
from uuid import uuid4
from .config import Config


class SegmentCatalog:
    def __init__(self, directory: str):
        self.root = Path(directory).resolve()
        self.root.mkdir(parents=True, exist_ok=True)
        self.lock = threading.RLock()
        self.db = sqlite3.connect(self.root / 'catalog.sqlite3', check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        self.db.execute('PRAGMA synchronous=FULL')
        with self.db:
            self.db.executescript('''
                CREATE TABLE IF NOT EXISTS sessions (
                    id TEXT PRIMARY KEY, started_utc REAL NOT NULL,
                    source TEXT NOT NULL, status TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS segments (
                    session_id TEXT NOT NULL, path TEXT PRIMARY KEY,
                    start REAL, end REAL, duration REAL,
                    status TEXT NOT NULL,
                    FOREIGN KEY(session_id) REFERENCES sessions(id)
                );
                CREATE TABLE IF NOT EXISTS replay_reservations (
                    id TEXT PRIMARY KEY, session_id TEXT NOT NULL,
                    start REAL NOT NULL, end REAL NOT NULL, click REAL NOT NULL,
                    FOREIGN KEY(session_id) REFERENCES sessions(id)
                );
            ''')

    def close(self):
        self.db.close()

    def create_session(self, started_utc: float, source: str) -> str:
        session_id = uuid4().hex
        (self.root / session_id).mkdir(exist_ok=False)
        with self.lock, self.db:
            self.db.execute('INSERT INTO sessions VALUES (?, ?, ?, ?)',
                            (session_id, started_utc, source, 'RECORDING'))
        return session_id

    def session_directory(self, session_id: str) -> Path:
        if len(session_id) != 32 or any(c not in '0123456789abcdef' for c in session_id):
            raise ValueError('Sessão inválida')
        return self.root / session_id

    def reconcile(self, session_id: str, *, finished: bool = False):
        """Só o CSV de fechamento do FFmpeg pode promover um arquivo a CLOSED.

        Arquivos órfãos são INCOMPLETE após encerramento. Nunca inferir horários
        pelo nome ou mtime. Repetir a reconciliação é idempotente.
        """
        directory = self.session_directory(session_id)
        manifest = directory / 'segments.csv'
        rows = []
        if manifest.exists():
            with manifest.open(encoding='utf-8', newline='') as stream:
                rows = list(csv.reader(stream))
        with self.lock:
            known = {r['path']: r['status'] for r in self.db.execute(
                'SELECT path, status FROM segments WHERE session_id=?', (session_id,))}
            for row in rows:
                if len(row) != 3:
                    continue  # Linha parcial: será relida no próximo ciclo.
                path = (directory / Path(row[0]).name).resolve()
                if path.parent != directory or path.suffix != '.mp4':
                    continue
                if known.get(str(path)) == 'REMOVED' or not path.is_file():
                    continue
                if known.get(str(path)) == 'CLOSED' and path.exists():
                    continue
                try:
                    start, end = float(row[1]), float(row[2])
                    if not all(math.isfinite(n) for n in (start, end)) or start < 0 or end <= start:
                        continue
                    probe = subprocess.run(
                        ['ffprobe', '-v', 'error', '-show_streams', '-show_format',
                         '-show_packets', '-select_streams', 'v:0', '-show_entries',
                         'format=duration:stream=codec_type:packet=pts_time,duration_time',
                         '-of', 'json', str(path)],
                        capture_output=True, text=True, timeout=15, check=True,
                    )
                    data = json.loads(probe.stdout)
                    duration = float(data['format']['duration'])
                    if not math.isfinite(duration) or duration <= 0:
                        continue
                    if not any(s['codec_type'] == 'video' for s in data['streams']):
                        continue
                    if abs(duration - (end - start)) > 0.15:
                        continue
                    packets = data.get('packets', [])
                    pts = sorted(float(packet['pts_time']) for packet in packets)
                    if not pts or not all(math.isfinite(n) for n in pts):
                        raise ValueError('INVALID_FRAME_TIMESTAMPS')
                    if any(b - a > Config.MAX_PACKET_GAP_SECONDS for a, b in zip(pts, pts[1:])):
                        raise ValueError('FRAME_GAP')
                    if any(float(packet.get('duration_time', '0')) > Config.MAX_PACKET_GAP_SECONDS
                           for packet in packets):
                        raise ValueError('FRAME_GAP')
                except (OSError, ValueError, KeyError, subprocess.SubprocessError):
                    with self.db:
                        self.db.execute('INSERT OR REPLACE INTO segments VALUES (?, ?, NULL, NULL, NULL, ?)',
                                        (session_id, str(path), 'INVALID'))
                    continue
                with self.db:
                    self.db.execute('INSERT OR REPLACE INTO segments VALUES (?, ?, ?, ?, ?, ?)',
                                    (session_id, str(path), start, end, duration, 'CLOSED'))
            with self.db:
                for path in directory.glob('seg_*.mp4'):
                    self.db.execute('INSERT OR IGNORE INTO segments VALUES (?, ?, NULL, NULL, NULL, ?)',
                                    (session_id, str(path.resolve()), 'INCOMPLETE' if finished else 'WRITING'))
                # CLOSED que perdeu o arquivo não continua utilizável.
                for record in self.db.execute('SELECT path, status FROM segments WHERE session_id=?', (session_id,)).fetchall():
                    if record['status'] != 'REMOVED' and not Path(record['path']).exists():
                        self.db.execute("UPDATE segments SET status='MISSING' WHERE path=?", (record['path'],))
                if finished:
                    self.db.execute("UPDATE segments SET status='INCOMPLETE' WHERE session_id=? AND status='WRITING'", (session_id,))
                    self.db.execute("UPDATE sessions SET status='STOPPED' WHERE id=?", (session_id,))

    def records(self, session_id: str) -> list[dict]:
        with self.lock:
            return [dict(row) for row in self.db.execute(
                'SELECT * FROM segments WHERE session_id=? ORDER BY start, path', (session_id,))]

    def coverage_gaps(self, session_id: str, start: float, end: float) -> list[tuple[float, float]]:
        """Lacunas na linha de conteúdo; não usa relógio civil nem mtime."""
        if not all(math.isfinite(n) for n in (start, end)) or start < 0 or end <= start:
            raise ValueError('Janela inválida')
        cursor = start
        gaps = []
        for record in self.records(session_id):
            if record['status'] != 'CLOSED' or record['end'] <= cursor:
                continue
            if record['start'] >= end:
                break
            if record['start'] > cursor + 0.001:
                gaps.append((cursor, min(record['start'], end)))
            cursor = max(cursor, record['end'])
            if cursor >= end:
                break
        if cursor < end - 0.001:
            gaps.append((cursor, end))
        return gaps

    def recover(self):
        """Uso na inicialização, antes de iniciar a própria captura."""
        with self.lock:
            sessions = [row[0] for row in self.db.execute('SELECT id FROM sessions')]
        for session in sessions:
            self.reconcile(session, finished=True)

    def reserve(self, session_id: str, click: float, before: float, after: float, *, commit=True) -> dict:
        """Congela a janela original e protege também segmentos ainda em escrita.

        Margem de 100 ms permite incluir o frame do clique no MP4 recodificado.
        Isto é reserva de fonte; ainda não é aceitação durável de evento (GW-06).
        """
        if not all(math.isfinite(n) for n in (click, before, after)):
            raise ValueError('INVALID_WINDOW')
        if before <= 0 or after < 0 or click - before < 0:
            raise ValueError('WARMING_UP')
        start = max(0, click - before - 0.1)
        end = click + after + 0.1
        request = dict(id=uuid4().hex, session_id=session_id, start=start, end=end, click=click)
        with self.lock:
            if not self.db.execute('SELECT 1 FROM sessions WHERE id=?', (session_id,)).fetchone():
                raise ValueError('UNKNOWN_SESSION')
            self.db.execute('INSERT INTO replay_reservations VALUES (?, ?, ?, ?, ?)',
                            (request['id'], session_id, start, end, click))
            if commit:
                self.db.commit()
        return request

    def reservation(self, request_id: str) -> dict:
        with self.lock:
            row = self.db.execute('SELECT * FROM replay_reservations WHERE id=?', (request_id,)).fetchone()
            if row is None:
                raise ValueError('UNKNOWN_RESERVATION')
            return dict(row)

    def release(self, request_id: str):
        with self.lock, self.db:
            self.db.execute('DELETE FROM replay_reservations WHERE id=?', (request_id,))

    def select_reserved(self, request_id: str) -> tuple[dict, list[dict]]:
        with self.lock:
            request = self.reservation(request_id)
            if self.coverage_gaps(request['session_id'], request['start'], request['end']):
                raise ValueError('VIDEO_GAP')
            records = [r for r in self.records(request['session_id'])
                       if r['status'] == 'CLOSED' and r['end'] > request['start']
                       and r['start'] < request['end']]
            if not records or not all(Path(r['path']).is_file() for r in records):
                raise ValueError('VIDEO_MISSING')
            if any(abs(a['end'] - b['start']) > 0.001 for a, b in zip(records, records[1:])):
                raise ValueError('VIDEO_GAP')
            return request, records

    def prune(self, cutoff_utc: float):
        """Retenção de arquivos fechados; segmentos em escrita ficam preservados.

        Reservas protegem a janela inteira, incluindo arquivos fechados depois do clique.
        """
        with self.lock:
            candidates = self.db.execute('''
                SELECT segments.path FROM segments JOIN sessions
                ON sessions.id=segments.session_id
                WHERE segments.status='CLOSED' AND sessions.started_utc + segments.end < ?
                AND NOT EXISTS (
                    SELECT 1 FROM replay_reservations r
                    WHERE r.session_id=segments.session_id
                    AND r.start < segments.end AND r.end > segments.start
                )
            ''', (cutoff_utc,)).fetchall()
            for row in candidates:
                path = Path(row['path'])
                if not path.is_relative_to(self.root):
                    continue
                try:
                    path.unlink(missing_ok=True)
                except OSError:
                    continue
                with self.db:
                    self.db.execute("UPDATE segments SET status='REMOVED' WHERE path=?", (str(path),))

    def reclaim(self, bytes_needed, active_session, keep_seconds):
        """Libera só fontes antigas sem reserva, preservando o buffer ativo recente."""
        reclaimed = 0
        with self.lock:
            latest = self.db.execute("SELECT MAX(end) FROM segments WHERE session_id=? AND status='CLOSED'",
                                     (active_session,)).fetchone()[0] or 0
            candidates = self.db.execute('''SELECT s.path FROM segments s
                WHERE s.status='CLOSED' AND (s.session_id!=? OR s.end < ?)
                AND NOT EXISTS (SELECT 1 FROM replay_reservations r WHERE r.session_id=s.session_id
                    AND r.start<s.end AND r.end>s.start)
                ORDER BY s.rowid''', (active_session or '', latest - keep_seconds)).fetchall()
            for row in candidates:
                if reclaimed >= bytes_needed:
                    break
                path = Path(row['path'])
                if not path.is_relative_to(self.root):
                    continue
                try:
                    size = path.stat().st_size
                    path.unlink()
                except OSError:
                    continue
                with self.db:
                    self.db.execute("UPDATE segments SET status='REMOVED' WHERE path=?", (str(path),))
                reclaimed += size
        return reclaimed
