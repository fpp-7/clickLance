"""Validação de mídia compartilhada entre produção e diagnóstico."""
import json
import math
import subprocess
from pathlib import Path
from .config import Config


def validate_video(path: Path) -> float:
    result = subprocess.run(
        ['ffprobe', '-v', 'error', '-show_streams', '-show_format', '-of', 'json', str(path)],
        capture_output=True, text=True, timeout=20, check=True,
    )
    data = json.loads(result.stdout)
    duration = float(data['format']['duration'])
    if not math.isfinite(duration) or duration <= 0 or not any(
            s['codec_type'] == 'video' for s in data['streams']):
        raise ValueError(f'Vídeo sem conteúdo: {path}')
    subprocess.run(
        ['ffmpeg', '-v', 'error', '-xerror', '-i', str(path), '-f', 'null', '-'],
        capture_output=True, timeout=Config.REPLAY_FFMPEG_TIMEOUT_SECONDS, check=True,
    )
    return duration
