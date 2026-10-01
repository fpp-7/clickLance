import os
from dotenv import load_dotenv

load_dotenv()


class Config:
    """Centraliza toda a configuração do Equipamento Local, lida do .env."""

    # --- Serial ---
    SERIAL_PORT: str = os.getenv("SERIAL_PORT", "COM6")
    BAUD_RATE: int = int(os.getenv("BAUD_RATE", "9600"))

    # --- DVR ---
    CAMERA_DEVICE_NAME: str = os.getenv("CAMERA_DEVICE_NAME", "USB Video Device")
    SEGMENT_DURATION_SECONDS: int = int(os.getenv("SEGMENT_DURATION_SECONDS", "60"))
    SEGMENTS_DIR: str = os.getenv("SEGMENTS_DIR", "segments")
    RETENTION_HOURS: float = float(os.getenv("RETENTION_HOURS", "6"))

    # --- Processamento de Vídeo ---
    SEGMENTS_TO_CONCAT: int = int(os.getenv("SEGMENTS_TO_CONCAT", "2"))
    OUTPUT_DIR: str = os.getenv("OUTPUT_DIR", "clips")

    # --- AWS S3 ---
    AWS_ACCESS_KEY_ID: str | None = os.getenv("AWS_ACCESS_KEY_ID")
    AWS_SECRET_ACCESS_KEY: str | None = os.getenv("AWS_SECRET_ACCESS_KEY")
    AWS_REGION: str = os.getenv("AWS_REGION", "us-east-1")
    S3_BUCKET_NAME: str | None = os.getenv("S3_BUCKET_NAME")

    # --- API Backend ---
    API_URL: str = os.getenv("API_URL", "http://localhost:8080/api/replays")
    QUADRA_ID: str = os.getenv("QUADRA_ID", "quadra_001")
