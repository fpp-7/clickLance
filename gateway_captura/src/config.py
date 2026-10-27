"""Configuration and authenticated bootstrap with a last-known-good cache."""
import json
import os
from pathlib import Path
from dotenv import load_dotenv

if os.getenv("CLICKLANCE_LOAD_ENV", "1") == "1":
    load_dotenv()

class Config:
    SERIAL_PORT = os.getenv("SERIAL_PORT", "COM6")
    BAUD_RATE = int(os.getenv("BAUD_RATE", "9600"))
    CAMERA_DEVICE_NAME = os.getenv("CAMERA_DEVICE_NAME", "USB Video Device")
    SEGMENT_DURATION_SECONDS = int(os.getenv("SEGMENT_DURATION_SECONDS", "60"))
    SEGMENTS_TO_CONCAT = int(os.getenv("SEGMENTS_TO_CONCAT", "2"))
    SEGMENTS_DIR = os.getenv("SEGMENTS_DIR", "segments")
    OUTPUT_DIR = os.getenv("OUTPUT_DIR", "clips")
    RETENTION_HOURS = float(os.getenv("RETENTION_HOURS", "6"))
    API_BASE_URL = os.getenv("API_BASE_URL", "http://localhost:8080/api/v1").rstrip("/")
    API_URL = os.getenv("API_URL", f"{API_BASE_URL}/replays")
    DISPOSITIVO_ID = os.getenv("DISPOSITIVO_ID", "disp_001")
    DEVICE_TOKEN = os.getenv("DEVICE_TOKEN", "")
    QUADRA_ID = os.getenv("QUADRA_ID", "")
    STATE_DB = os.getenv("STATE_DB", "state/events.sqlite3")
    CONFIG_CACHE = os.getenv("CONFIG_CACHE", "state/config.json")
    COOLDOWN_SECONDS = float(os.getenv("COOLDOWN_SECONDS", "5"))
    CLIP_AFTER_SECONDS = float(os.getenv("CLIP_AFTER_SECONDS", "0.2"))
    MAX_PENDING_EVENTS = int(os.getenv("MAX_PENDING_EVENTS", "2000"))
    MIN_FREE_DISK_MB = int(os.getenv("MIN_FREE_DISK_MB", "512"))
    AWS_ACCESS_KEY_ID = os.getenv("AWS_ACCESS_KEY_ID")
    AWS_SECRET_ACCESS_KEY = os.getenv("AWS_SECRET_ACCESS_KEY")
    AWS_SESSION_TOKEN = os.getenv("AWS_SESSION_TOKEN")
    AWS_REGION = os.getenv("AWS_REGION", "us-east-1")
    S3_BUCKET_NAME = os.getenv("S3_BUCKET_NAME")
    S3_ENDPOINT_URL = os.getenv("S3_ENDPOINT_URL") or None

    @classmethod
    def validate(cls):
        if not cls.DEVICE_TOKEN or not cls.DISPOSITIVO_ID or not cls.S3_BUCKET_NAME:
            raise ValueError("Configure DEVICE_TOKEN, DISPOSITIVO_ID e S3_BUCKET_NAME.")
        if not 1 <= cls.SEGMENT_DURATION_SECONDS <= 60 or not 1 <= cls.SEGMENTS_TO_CONCAT <= 300 or cls.SEGMENT_DURATION_SECONDS * cls.SEGMENTS_TO_CONCAT > 600:
            raise ValueError("Configuracao de captura fora dos limites.")
        if cls.RETENTION_HOURS * 3600 < cls.SEGMENT_DURATION_SECONDS * (cls.SEGMENTS_TO_CONCAT + 2):
            raise ValueError("Retencao insuficiente para janela e dois segmentos adicionais.")
        if cls.MAX_PENDING_EVENTS < 1 or cls.MIN_FREE_DISK_MB < 1 or cls.COOLDOWN_SECONDS < 0:
            raise ValueError("Limites operacionais invalidos.")
        if not 0 <= cls.CLIP_AFTER_SECONDS <= 30:
            raise ValueError("CLIP_AFTER_SECONDS fora dos limites.")
        from urllib.parse import urlparse
        for url in (cls.API_BASE_URL, cls.API_URL):
            p = urlparse(url)
            if p.scheme != "https" and not (p.scheme == "http" and p.hostname in {"localhost", "127.0.0.1", "::1"}):
                raise ValueError("API exige HTTPS, exceto localhost.")

    @classmethod
    def _apply_remote(cls, data):
        if data.get("dispositivo_id") != cls.DISPOSITIVO_ID:
            raise ValueError("Configuracao pertence a outro dispositivo.")
        from uuid import UUID
        quadra = str(UUID(data["quadra_id"]))
        duration, count = int(data["duracao_segmento_segundos"]), int(data["segmentos_concatenar"])
        if not 1 <= duration <= 60 or not 1 <= count <= 300 or duration * count > 600:
            raise ValueError("Configuracao remota fora dos limites.")
        if cls.RETENTION_HOURS * 3600 < duration * (count + 2):
            raise ValueError("Retencao local insuficiente para configuracao remota.")
        cls.QUADRA_ID, cls.SEGMENT_DURATION_SECONDS, cls.SEGMENTS_TO_CONCAT = quadra, duration, count

    @classmethod
    def bootstrap(cls, session=None):
        import requests
        session = session or requests
        cache = Path(cls.CONFIG_CACHE)
        try:
            response = session.get(f"{cls.API_BASE_URL}/dispositivos/{cls.DISPOSITIVO_ID}/config",
                                   headers={"X-Device-Token": cls.DEVICE_TOKEN}, timeout=15)
            response.raise_for_status()
            data = response.json()
        except requests.RequestException as exc:
            if getattr(getattr(exc, "response", None), "status_code", 0) in {400, 401, 403, 404}:
                raise RuntimeError("Credencial/configuracao recusada; captura nao iniciada.") from exc
            if not cache.exists():
                raise RuntimeError("Sem configuracao remota nem cache valido.") from exc
            cls._apply_remote(json.loads(cache.read_text(encoding="utf-8")))
            return "cache"
        cls._apply_remote(data)
        cache.parent.mkdir(parents=True, exist_ok=True)
        temporary = cache.with_suffix(".tmp")
        temporary.write_text(json.dumps(data), encoding="utf-8")
        temporary.replace(cache)
        return "remote"
