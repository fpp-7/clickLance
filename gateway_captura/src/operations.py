"""Instância única, supervisão, diagnóstico local e retenção."""
import ctypes
import hashlib
import json
import os
import shutil
import threading
import time
from pathlib import Path
from urllib.parse import urlsplit

from .config import Config
from .logger import logger
from .event_store import tree_bytes


class InstanceLock:
    def __init__(self, name):
        self.name, self.handle = name, None

    def __enter__(self):
        if os.name != 'nt':
            raise RuntimeError('Instalação suportada: Windows')
        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel.CreateMutexW.restype = ctypes.c_void_p
        kernel.CreateMutexW.argtypes = [ctypes.c_void_p, ctypes.c_bool, ctypes.c_wchar_p]
        self.kernel = kernel
        self.handle = kernel.CreateMutexW(None, False, 'Global\\ClickLance_' + hashlib.sha256(self.name.casefold().encode()).hexdigest())
        if not self.handle:
            raise OSError('Não foi possível criar mutex')
        if ctypes.get_last_error() == 183:
            self.__exit__(None, None, None)
            raise RuntimeError('Outra instância utiliza este recurso')
        return self

    def __exit__(self, *args):
        if self.handle:
            self.kernel.CloseHandle.argtypes = [ctypes.c_void_p]
            self.kernel.CloseHandle(self.handle)
            self.handle = None


def health_report(store):
    reason = store.health_reason()
    with store.lock:
        pending = store.db.execute("SELECT COUNT(*),MIN(created) FROM events WHERE state!='REGISTRO_CONFIRMADO'").fetchone()
        last_cloud = store.db.execute("SELECT MAX(completed) FROM events WHERE state='REGISTRO_CONFIRMADO'").fetchone()[0]
        last_segment = store.db.execute('''SELECT s.session_id,s.end FROM segments s JOIN sessions c
            ON c.id=s.session_id WHERE s.status='CLOSED'
            ORDER BY (s.session_id=?) DESC,c.started_utc DESC,s.end DESC LIMIT 1''',
                                        (store.dvr.session_id or '',)).fetchone()
    return {
        'version': '0.3.0', 'generated_utc': time.time(),
        'state': 'READY' if reason is None else ('WARMING_UP' if reason == 'WARMING_UP' else 'UNAVAILABLE'),
        'reason': reason,
        'pending': pending[0], 'oldest_pending_age_seconds': time.time() - pending[1] if pending[1] else 0,
        'free_bytes': shutil.disk_usage(store.catalog.root).free,
        'last_segment': dict(last_segment) if last_segment else None,
        'last_cloud_confirmation_utc': last_cloud,
        'capture_session': store.dvr.session_id,
        'last_capture_errors': list(store.dvr.stderr_tail),
        'last_capture_start_error': store.dvr.last_start_error,
        'capture_progress_age_seconds': (time.monotonic() - store.dvr._last_progress_mono
                                         if store.dvr._content_anchor is not None else None),
    }


def supervise_capture(store, stop: threading.Event):
    dvr = store.dvr
    retry_delay = 2
    while not stop.is_set():
        if not store.enabled or dvr.capacity_paused:
            stop.wait(1)
            continue
        source = urlsplit(Config.CAMERA_RTSP_URL)
        camera = (f'{source.hostname}:{source.port}{source.path}' if Config.CAMERA_SOURCE == 'rtsp'
                  else Config.CAMERA_DEVICE_NAME)
        try:
            with InstanceLock('camera:' + camera):
                capture = threading.Thread(target=dvr.start_recording, name='Capture', daemon=True)
                capture.start()
                launch = time.monotonic()
                while capture.is_alive() and not stop.wait(0.5):
                    with dvr._clock_lock:
                        last = dvr._last_progress_mono
                        anchor = dvr._content_anchor
                    stalled = (anchor is None and time.monotonic() - launch > Config.CAMERA_TIMEOUT_SECONDS + 10)
                    stalled |= anchor is not None and time.monotonic() - last > 5
                    if stalled or not dvr._running.is_set() or not store.enabled:
                        dvr.stop_recording()
                        break
                dvr.stop_recording()
                capture.join(timeout=15)
                if capture.is_alive():
                    logger.error('Captura não encerrou no prazo; supervisão interrompida para evitar duplicidade.')
                    stop.set()
                    return
                retry_delay = 2 if time.monotonic() - launch > 60 else min(30, retry_delay * 2)
        except RuntimeError:
            dvr._running.clear()
            logger.warning('Câmera ocupada por outra instância; aguardando.')
        stop.wait(retry_delay)


def maintenance(store, stop):
    while not stop.is_set():
        try:
            store.dvr._cleanup_old_segments()
            enforce_capture_capacity(store)
            report = health_report(store)
            destination = store.catalog.root / 'health.json'
            temporary = destination.with_suffix('.tmp')
            temporary.write_text(json.dumps(report, indent=2), encoding='utf-8')
            os.replace(temporary, destination)
            # Reinício após commit remoto, antes de remover o arquivo: limpeza idempotente.
            with store.lock:
                finished = store.db.execute("SELECT clip FROM events WHERE state='REGISTRO_CONFIRMADO' AND clip IS NOT NULL").fetchall()
            for row in finished:
                path = Path(row['clip'])
                if path.resolve().is_relative_to(Path(Config.OUTPUT_DIR).resolve()):
                    path.unlink(missing_ok=True)
        except (OSError, ValueError):
            logger.warning('Falha no diagnóstico/manutenção; será repetido.')
        stop.wait(5)


def enforce_capture_capacity(store):
    # Margem adicional para terminar um segmento e finalização do encoder.
    segment_margin = int(Config.SEGMENT_DURATION_SECONDS * Config.CAPACITY_BITRATE / 8 * 2)
    need = max(0, tree_bytes(store.catalog.root) + segment_margin - Config.BUFFER_QUOTA_BYTES,
               Config.MIN_FREE_BYTES + segment_margin - shutil.disk_usage(store.catalog.root).free)
    if need:
        reclaimed = store.catalog.reclaim(
            need, store.dvr.session_id,
            Config.REPLAY_BEFORE_SECONDS + Config.REPLAY_AFTER_SECONDS + 2*Config.SEGMENT_DURATION_SECONDS)
        store.dvr.capacity_paused = reclaimed < need
        if store.dvr.capacity_paused:
            store.dvr.stop_recording()
    else:
        store.dvr.capacity_paused = False
