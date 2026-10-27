"""Serial producer persists a click before considering it accepted."""
import shutil
import threading
import time
import serial
from .config import Config
from .logger import logger
TRIGGER_MESSAGE = "Botao foi Pressionado!"

class SerialListener:
    def __init__(self,store,dvr):
        self.store,self.dvr = store,dvr
        self._stop_event = threading.Event()
        self._last_trigger = float("-inf")

    def accept_click(self,timestamp=None,monotonic=None):
        now = time.time() if timestamp is None else timestamp
        tick = time.monotonic() if monotonic is None else monotonic
        if tick-self._last_trigger<Config.COOLDOWN_SECONDS:
            return None
        before = Config.SEGMENT_DURATION_SECONDS*Config.SEGMENTS_TO_CONCAT
        with self.dvr._filesystem_lock:
            if not self.dvr.ready_for_click(now,before):
                logger.warning("Clique nao aceito: camera indisponivel ou buffer aquecendo.")
                return None
            if shutil.disk_usage(Config.SEGMENTS_DIR).free<Config.MIN_FREE_DISK_MB*1024*1024:
                logger.error("Clique nao aceito: reserva de disco atingida.")
                return None
            event_id = self.store.enqueue(now,dispositivo_id=Config.DISPOSITIVO_ID,quadra_id=Config.QUADRA_ID,before=before,after=Config.CLIP_AFTER_SECONDS)
        self._last_trigger = tick
        logger.info("Clique aceito e persistido: %s; pendentes=%d",event_id,self.store.count_pending())
        return event_id

    def start_listening(self):
        while not self._stop_event.is_set():
            try:
                with serial.Serial(Config.SERIAL_PORT,Config.BAUD_RATE,timeout=1) as connection:
                    if self._stop_event.wait(2):
                        break
                    while not self._stop_event.is_set():
                        line = connection.readline().decode("utf-8",errors="replace").strip()
                        if line==TRIGGER_MESSAGE:
                            try:
                                self.accept_click()
                            except Exception:
                                logger.exception("Clique nao aceito; falha na persistencia.")
            except serial.SerialException:
                logger.exception("Serial indisponivel; reconexao em cinco segundos.")
                self._stop_event.wait(5)

    def stop(self):
        self._stop_event.set()
