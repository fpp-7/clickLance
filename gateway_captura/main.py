"""Single-camera gateway with durable capture and delivery stages."""
import signal
import threading
from src.config import Config
from src.dvr_manager import DVRManager
from src.event_store import EventStore
from src.logger import logger
from src.serial_listener import SerialListener
from src.video_processor import VideoProcessor
from src.instance_lock import InstanceLock

def main():
    Config.validate()
    source = Config.bootstrap()
    logger.info("Configuracao %s: dispositivo %s, quadra %s",source,Config.DISPOSITIVO_ID,Config.QUADRA_ID)
    store = EventStore(Config.STATE_DB,Config.MAX_PENDING_EVENTS)
    dvr = DVRManager(store)
    clip,delivery = VideoProcessor(store,dvr),VideoProcessor(store,dvr,delivery=True)
    listener = SerialListener(store,dvr)
    recording = threading.Thread(target=dvr.start_recording,name="DVR",daemon=False)
    cleanup = threading.Thread(target=dvr.start_cleanup_loop,name="Cleanup",daemon=False)
    def shutdown(signum=None,frame=None):
        listener.stop()
    signal.signal(signal.SIGINT,shutdown)
    signal.signal(signal.SIGTERM,shutdown)
    for worker in (recording,cleanup,clip,delivery):
        worker.start()
    try:
        listener.start_listening()
    finally:
        clip.stop()
        delivery.stop()
        dvr.stop_recording()
        for worker in (recording,cleanup,clip,delivery):
            worker.join()
        logger.info("Gateway encerrado; %d eventos preservados.",store.count_pending())

if __name__=="__main__":
    with InstanceLock(Config.STATE_DB + ".lock"):
        main()
