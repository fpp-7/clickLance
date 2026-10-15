import logging
import sys
import re
import time
from logging.handlers import RotatingFileHandler
from .config import Config

LOG_FILE = "app.log"
LOG_MAX_BYTES = 5 * 1024 * 1024  # 5 MB por arquivo de log
LOG_BACKUP_COUNT = 3             # Mantém até 3 arquivos rotacionados


def redact(text):
    text = re.sub(r'(?:rtsps?|https?)://[^\s\'\"]+', '[URL]', text)
    for name in ('DEVICE_TOKEN', 'AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'AWS_SESSION_TOKEN'):
        secret = getattr(Config, name, None)
        if secret:
            text = text.replace(secret, '[REDACTED]')
    return text


class SecretFilter(logging.Filter):
    def filter(self, record):
        record.msg = redact(record.getMessage())
        record.args = ()
        return True


class SanitizingFormatter(logging.Formatter):
    def format(self, record):
        return redact(super().format(record))


class ConsoleFormatter(SanitizingFormatter):
    def format(self, record):
        text = super().format(record)
        return text.encode(getattr(sys.stdout, 'encoding', None) or 'utf-8', errors='backslashreplace').decode(
            getattr(sys.stdout, 'encoding', None) or 'utf-8')


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

    formatter = SanitizingFormatter(
        "%(asctime)s+00:00 | v0.3.0 | %(levelname)-7s | %(threadName)-18s | %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )
    formatter.converter = time.gmtime

    # Handler: Arquivo rotativo
    file_handler = RotatingFileHandler(
        LOG_FILE, maxBytes=LOG_MAX_BYTES, backupCount=LOG_BACKUP_COUNT, encoding="utf-8"
    )
    file_handler.setLevel(logging.INFO)
    file_handler.setFormatter(formatter)
    file_handler.addFilter(SecretFilter())

    # Handler: Console (stdout)
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(logging.DEBUG)
    console_formatter = ConsoleFormatter(formatter._fmt, datefmt='%Y-%m-%d %H:%M:%S')
    console_formatter.converter = time.gmtime
    console_handler.setFormatter(console_formatter)
    console_handler.addFilter(SecretFilter())

    logger.addHandler(file_handler)
    logger.addHandler(console_handler)

    return logger


# Instância global — importar como: from src.logger import logger
logger = setup_logger()
