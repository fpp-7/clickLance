import logging
import sys
from logging.handlers import RotatingFileHandler

LOG_FILE = "app.log"
LOG_MAX_BYTES = 5 * 1024 * 1024  # 5 MB por arquivo de log
LOG_BACKUP_COUNT = 3             # Mantém até 3 arquivos rotacionados


def setup_logger() -> logging.Logger:
    """
    Configura e retorna o logger principal da aplicação.
    - Arquivo rotativo (app.log, max 5 MB, 3 backups).
    - Saída no console (stdout).
    """
    logger = logging.getLogger("ClickLanceEdge")
    logger.setLevel(logging.DEBUG)

    # Evita adicionar handlers duplicados se chamado mais de uma vez
    if logger.handlers:
        return logger

    formatter = logging.Formatter(
        "%(asctime)s | %(levelname)-7s | %(threadName)-18s | %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )

    # Handler: Arquivo rotativo
    file_handler = RotatingFileHandler(
        LOG_FILE, maxBytes=LOG_MAX_BYTES, backupCount=LOG_BACKUP_COUNT, encoding="utf-8"
    )
    file_handler.setLevel(logging.INFO)
    file_handler.setFormatter(formatter)

    # Handler: Console (stdout)
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(logging.DEBUG)
    console_handler.setFormatter(formatter)

    logger.addHandler(file_handler)
    logger.addHandler(console_handler)

    return logger


# Instância global — importar como: from src.logger import logger
logger = setup_logger()
