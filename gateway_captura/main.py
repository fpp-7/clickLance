"""
main.py — Ponto de entrada do Equipamento Local (Edge PC).

Inicializa e gerencia três rotinas em paralelo:
  Thread 1 (DVR):       Gravação contínua segmentada da câmera.
  Thread 2 (Cleanup):   Limpeza periódica de segmentos antigos.
  Thread 3 (Worker):    Processamento de lances (concat → S3 → API).
  Main Thread (Serial): Escuta a porta serial do Arduino.
"""

import signal
import sys
import threading
from queue import Queue

from src.config import Config
from src.dvr_manager import DVRManager
from src.logger import logger
from src.serial_listener import SerialListener
from src.video_processor import VideoProcessor


def main() -> None:
    logger.info("=" * 60)
    logger.info("  ClickLance — Sistema Edge de Replay de Futsal")
    logger.info("  Quadra: %s | Porta: %s", Config.QUADRA_ID, Config.SERIAL_PORT)
    logger.info("=" * 60)

    # Fila thread-safe (Produtor: Serial → Consumidor: Worker)
    event_queue: Queue = Queue()

    # ---- Instanciar componentes ----
    dvr = DVRManager()
    worker = VideoProcessor(event_queue, dvr)
    serial_listener = SerialListener(event_queue)

    # ---- Thread 1: Gravação Contínua (DVR) ----
    dvr_thread = threading.Thread(
        target=dvr.start_recording,
        name="DVR-RecordThread",
        daemon=True,
    )
    dvr_thread.start()

    # ---- Thread 2: Limpeza de Disco ----
    cleanup_thread = threading.Thread(
        target=dvr.start_cleanup_loop,
        name="DVR-CleanupThread",
        daemon=True,
    )
    cleanup_thread.start()

    # ---- Thread 3: Worker de Processamento ----
    worker.start()  # Já é uma Thread (herda de threading.Thread)

    # ---- Graceful Shutdown via Ctrl+C ----
    def shutdown(signum, frame):
        logger.info("Sinal de encerramento recebido (Ctrl+C).")
        serial_listener.stop()
        dvr.stop_recording()

    signal.signal(signal.SIGINT, shutdown)
    signal.signal(signal.SIGTERM, shutdown)

    # ---- Main Thread: Escuta Serial (bloqueante) ----
    try:
        serial_listener.start_listening()
    except Exception as exc:
        logger.error("Erro fatal na Main Thread → %s", exc)
    finally:
        dvr.stop_recording()
        logger.info("Sistema Edge encerrado.")
        sys.exit(0)


if __name__ == "__main__":
    main()
