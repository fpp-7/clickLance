import os
import math
import re
from pathlib import Path
from dotenv import load_dotenv

load_dotenv()


class Config:
    """Centraliza toda a configuração do Equipamento Local, lida do .env."""

    # --- Serial ---
    SERIAL_PORT: str = os.getenv("SERIAL_PORT", "COM6")
    BAUD_RATE: int = int(os.getenv("BAUD_RATE", "9600"))

    # --- DVR ---
    CAMERA_SOURCE: str = os.getenv("CAMERA_SOURCE", "rtsp")
    CAMERA_RTSP_URL: str = os.getenv("CAMERA_RTSP_URL", "")
    CAMERA_RTSP_TRANSPORT: str = os.getenv("CAMERA_RTSP_TRANSPORT", "tcp")
    CAMERA_TIMEOUT_SECONDS: float = float(os.getenv("CAMERA_TIMEOUT_SECONDS", "10"))
    KEYFRAME_INTERVAL: int = int(os.getenv("KEYFRAME_INTERVAL", "30"))
    MAX_PACKET_GAP_SECONDS: float = float(os.getenv('MAX_PACKET_GAP_SECONDS', '0.25'))
    # RTSP exige compensação medida antes de enfileirar cliques reais.
    CAMERA_LATENCY_SECONDS: float | None = (
        float(os.environ["CAMERA_LATENCY_SECONDS"])
        if os.getenv("CAMERA_LATENCY_SECONDS", "").strip() else None
    )
    CAMERA_DEVICE_NAME: str = os.getenv("CAMERA_DEVICE_NAME", "USB Video Device")
    SEGMENT_DURATION_SECONDS: int = int(os.getenv("SEGMENT_DURATION_SECONDS", "60"))
    SEGMENTS_DIR: str = os.getenv("SEGMENTS_DIR", "segments")
    RETENTION_HOURS: float = float(os.getenv("RETENTION_HOURS", "6"))

    # --- Processamento de Vídeo ---
    SEGMENTS_TO_CONCAT: int = int(os.getenv("SEGMENTS_TO_CONCAT", "2"))
    REPLAY_BEFORE_SECONDS: float = float(os.getenv("REPLAY_BEFORE_SECONDS", "120"))
    REPLAY_AFTER_SECONDS: float = float(os.getenv("REPLAY_AFTER_SECONDS", "0"))
    REPLAY_WAIT_SECONDS: float = float(os.getenv("REPLAY_WAIT_SECONDS", "65"))
    REPLAY_FFMPEG_TIMEOUT_SECONDS: float = float(os.getenv("REPLAY_FFMPEG_TIMEOUT_SECONDS", "180"))
    OUTPUT_DIR: str = os.getenv("OUTPUT_DIR", "clips")
    DEVICE_ID: str = os.getenv('DEVICE_ID', 'mega01')
    DEVICE_TOKEN: str = os.getenv('DEVICE_TOKEN', '')
    MAX_PENDING_EVENTS: int = int(os.getenv('MAX_PENDING_EVENTS', '100'))
    MIN_FREE_BYTES: int = int(os.getenv('MIN_FREE_BYTES', str(2 * 1024**3)))
    BUFFER_QUOTA_BYTES: int = int(os.getenv('BUFFER_QUOTA_BYTES', str(24 * 1024**3)))
    CLIPS_QUOTA_BYTES: int = int(os.getenv('CLIPS_QUOTA_BYTES', str(20 * 1024**3)))
    CAPACITY_BITRATE: int = int(os.getenv('CAPACITY_BITRATE', '8000000'))
    MAX_RETRIES: int = int(os.getenv('MAX_RETRIES', '10'))
    CONFIG_URL: str = os.getenv('CONFIG_URL', '')
    CONFIG_REFRESH_SECONDS: int = int(os.getenv('CONFIG_REFRESH_SECONDS', '300'))
    CONFIG_VERSION: str = 'local'

    # --- AWS S3 ---
    AWS_ACCESS_KEY_ID: str | None = os.getenv("AWS_ACCESS_KEY_ID") or None
    AWS_SECRET_ACCESS_KEY: str | None = os.getenv("AWS_SECRET_ACCESS_KEY") or None
    AWS_SESSION_TOKEN: str | None = os.getenv('AWS_SESSION_TOKEN') or None
    AWS_REGION: str = os.getenv("AWS_REGION", "us-east-1")
    S3_BUCKET_NAME: str | None = os.getenv("S3_BUCKET_NAME") or None

    # --- API Backend ---
    API_URL: str = os.getenv("API_URL", "http://localhost:8080/api/v1/replays")
    QUADRA_ID: str = os.getenv("QUADRA_ID", "quadra_001")

    @classmethod
    def validate_local(cls):
        if not re.fullmatch(r'[A-Za-z0-9_-]{1,32}', cls.DEVICE_ID):
            raise ValueError('DEVICE_ID inválido')
        for field in ('REPLAY_BEFORE_SECONDS', 'SEGMENT_DURATION_SECONDS', 'KEYFRAME_INTERVAL',
                      'MAX_PENDING_EVENTS', 'BUFFER_QUOTA_BYTES', 'CLIPS_QUOTA_BYTES',
                      'CAPACITY_BITRATE', 'MAX_RETRIES', 'CONFIG_REFRESH_SECONDS',
                      'REPLAY_FFMPEG_TIMEOUT_SECONDS', 'MAX_PACKET_GAP_SECONDS'):
            value = getattr(cls, field)
            if not math.isfinite(value) or value <= 0:
                raise ValueError('Configuração local inválida: ' + field)
        for field in ('REPLAY_AFTER_SECONDS', 'REPLAY_WAIT_SECONDS', 'MIN_FREE_BYTES'):
            if not math.isfinite(getattr(cls, field)) or getattr(cls, field) < 0:
                raise ValueError('Configuração local inválida: ' + field)
        segments, clips = Path(cls.SEGMENTS_DIR).resolve(), Path(cls.OUTPUT_DIR).resolve()
        if segments.is_relative_to(clips) or clips.is_relative_to(segments):
            raise ValueError('SEGMENTS_DIR e OUTPUT_DIR devem ser diretórios separados')
