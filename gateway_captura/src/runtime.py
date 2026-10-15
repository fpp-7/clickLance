"""Orquestração do gateway Windows; banco e recursos têm uma única instância."""
import signal
import threading
import time
from contextlib import ExitStack
from pathlib import Path
from queue import Queue

from .config import Config
from .dvr_manager import DVRManager
from .event_store import EventStore
from .logger import logger
from .operations import InstanceLock, maintenance, supervise_capture
from .persistent_pipeline import PersistentPipeline
from .remote_config import RemoteConfig
from .serial_listener import SerialListener
from .video_processor import VideoProcessor


def run():
    stop = threading.Event()
    with ExitStack() as locks:
        locks.enter_context(InstanceLock('base:' + str(Path(Config.SEGMENTS_DIR).resolve())))
        locks.enter_context(InstanceLock('serial:' + Config.SERIAL_PORT.upper()))
        dvr = DVRManager()
        store = EventStore(dvr)
        remote = RemoteConfig(store)
        remote.initialize()
        renderer = VideoProcessor(Queue(), dvr, s3_client=object())
        pipeline = PersistentPipeline(store, renderer)
        serial = SerialListener(Queue(), dvr, store)

        def refresh():
            while not stop.wait(Config.CONFIG_REFRESH_SECONDS):
                try:
                    changed = remote.refresh()
                    if changed or not store.enabled:
                        dvr.stop_recording()
                except Exception as exc:
                    logger.warning('Configuração mantida; atualização falhou (%s).', type(exc).__name__)

        targets = [
            ('Supervisor', supervise_capture, (store, stop)),
            ('Pipeline', pipeline.run, (stop,)),
            ('Manutencao', maintenance, (store, stop)),
            ('Serial', serial.start_listening, ()),
            ('Configuracao', refresh, ()),
        ]
        threads = [threading.Thread(name=name, target=target, args=args, daemon=True)
                   for name, target, args in targets]
        def shutdown(signum, frame):
            stop.set()
        signal.signal(signal.SIGINT, shutdown)
        signal.signal(signal.SIGTERM, shutdown)
        try:
            for thread in threads:
                thread.start()
            while not stop.wait(0.5):
                if any(not thread.is_alive() for thread in threads):
                    logger.error('Rotina essencial encerrou; encerrando gateway com trabalho preservado.')
                    stop.set()
        finally:
            stop.set()
            serial.stop()
            dvr.stop_recording()
            renderer.cancel()
            deadline = time.monotonic() + 20
            for thread in threads:
                if thread.ident:
                    thread.join(timeout=max(0, deadline - time.monotonic()))
            if all(not thread.is_alive() for thread in threads):
                dvr.catalog.close()
            logger.info('Gateway encerrado; eventos e etapas permanecem no SQLite.')
