"""Diagnóstico com comandos de captura e concatenação de produção."""
import subprocess
from pathlib import Path
from urllib.parse import urlsplit
from .config import Config
from .dvr_manager import DVRManager
from .media import validate_video


def capture_sample(dvr: DVRManager, directory: Path, *, synthetic: bool) -> list[Path]:
    directory.mkdir(parents=True, exist_ok=False)
    input_args = None
    if synthetic:
        source = (
            'testsrc2=size=320x240:rate=30,'
            "drawtext=text='t=%{pts} frame=%{n}':x=10:y=10:fontsize=20:"
            'fontcolor=white:box=1:boxcolor=black,'
            "drawbox=x=10:y=50:w=60:h=60:color=red:t=fill:enable='lt(mod(t,2),0.2)'"
        )
        input_args = ['-f', 'lavfi', '-i', source]
    command = dvr.build_recording_command(
        str(directory / 'seg_%04d.mp4'), input_args=input_args, duration=6,
        manifest_path=str(directory / 'segments.csv'),
    )
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.SubprocessError) as exc:
        # TimeoutExpired/CalledProcessError podem carregar a URL na propriedade cmd.
        raise RuntimeError(f'Falha ao executar captura ({type(exc).__name__})') from None
    stderr = result.stderr
    # A saída do FFmpeg pode repetir o endereço autenticado da entrada.
    if Config.CAMERA_RTSP_URL:
        stderr = stderr.replace(Config.CAMERA_RTSP_URL, '[RTSP_URL]')
        url = urlsplit(Config.CAMERA_RTSP_URL)
        for secret in (url.password, url.username):
            if secret:
                stderr = stderr.replace(secret, '[REDACTED]')
    (directory / 'ffmpeg.log').write_text(stderr, encoding='utf-8')
    if result.returncode != 0:
        raise RuntimeError(f'Captura FFmpeg falhou ({result.returncode}): {stderr[-1000:]}')
    segments = sorted(directory.glob('seg_*.mp4'))
    if len(segments) != 3:
        raise RuntimeError(f'Esperados 3 segmentos, encontrados {len(segments)}')
    for segment in segments:
        duration = validate_video(segment)
        if abs(duration - 2) > 0.15:
            raise ValueError(f'Duração inesperada em {segment}: {duration}')
    return segments
