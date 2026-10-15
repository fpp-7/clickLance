"""
dvr_manager.py — Gravador Contínuo (DVR) com Segmentação FFmpeg.

Responsabilidades:
  1. Capturar câmera IP via RTSP ou webcam de teste via DirectShow.
  2. Fatiar a gravação em segmentos de N segundos usando `-f segment`.
  3. Rodar uma rotina de limpeza periódica que apaga segmentos mais velhos que X horas.
"""

import glob
import os
import subprocess
import threading
import time
from urllib.parse import urlsplit

from .config import Config
from .logger import logger
from .segment_catalog import SegmentCatalog


class DVRManager:
    """Gerencia a gravação contínua da câmera e a limpeza de disco."""

    def __init__(self) -> None:
        self.segments_dir: str = Config.SEGMENTS_DIR
        self.segment_duration: int = Config.SEGMENT_DURATION_SECONDS
        self.camera_name: str = Config.CAMERA_DEVICE_NAME
        self.retention_hours: float = Config.RETENTION_HOURS

        self._ffmpeg_process: subprocess.Popen | None = None
        self._running = threading.Event()

        # Garante que a pasta de segmentos existe
        os.makedirs(self.segments_dir, exist_ok=True)
        self.catalog = SegmentCatalog(self.segments_dir)
        self.session_id: str | None = None

    # ------------------------------------------------------------------
    # GRAVAÇÃO
    # ------------------------------------------------------------------
    def start_recording(self) -> None:
        """
        Inicia o FFmpeg em modo segmentado (roda indefinidamente).
        Deve ser chamado dentro de uma Thread dedicada.
        """
        self._running.set()

        try:
            # Validar sem gravar URL ou credenciais no catálogo/log.
            self.build_input_args()
            self.catalog.recover()
            self.session_id = self.catalog.create_session(time.time(), Config.CAMERA_SOURCE)
            directory = self.catalog.session_directory(self.session_id)
            command = self.build_recording_command(
                str(directory / 'seg_%04d.mp4'), manifest_path=str(directory / 'segments.csv'),
            )
            logger.info('DVR: Iniciando sessão %s, entrada %s', self.session_id, Config.CAMERA_SOURCE)
            self._ffmpeg_process = subprocess.Popen(
                command, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            )
            logger.info("DVR: FFmpeg iniciado (PID %d)", self._ffmpeg_process.pid)
            while self._ffmpeg_process.poll() is None:
                self.catalog.reconcile(self.session_id)
                time.sleep(0.5)
            self.catalog.reconcile(self.session_id, finished=True)
            if self._running.is_set():
                logger.error("DVR: FFmpeg encerrou inesperadamente!")
        except FileNotFoundError:
            logger.error("DVR: FFmpeg não encontrado no PATH do sistema.")
        except Exception as exc:
            logger.error("DVR: Falha na captura (%s); confira configuração e fonte.", type(exc).__name__)
        finally:
            self.stop_recording()
            if self.session_id:
                self.catalog.reconcile(self.session_id, finished=True)
            self._running.clear()

    def build_input_args(self) -> list[str]:
        if Config.CAMERA_SOURCE == 'dshow':
            return ['-f', 'dshow', '-i', f'video={self.camera_name}']
        if Config.CAMERA_SOURCE != 'rtsp':
            raise ValueError('CAMERA_SOURCE deve ser rtsp ou dshow')
        url = urlsplit(Config.CAMERA_RTSP_URL)
        if url.scheme not in ('rtsp', 'rtsps') or not url.hostname:
            raise ValueError('CAMERA_RTSP_URL deve conter uma URL RTSP válida')
        if Config.CAMERA_RTSP_TRANSPORT not in ('tcp', 'udp'):
            raise ValueError('Transporte RTSP inválido')
        if not 0 < Config.CAMERA_TIMEOUT_SECONDS <= 300:
            raise ValueError('Timeout de câmera inválido')
        return ['-rtsp_transport', Config.CAMERA_RTSP_TRANSPORT,
                '-timeout', str(int(Config.CAMERA_TIMEOUT_SECONDS * 1_000_000)),
                '-i', Config.CAMERA_RTSP_URL]

    def build_recording_command(
        self, segment_pattern: str, *, input_args: list[str] | None = None,
        duration: float | None = None,
        manifest_path: str | None = None,
    ) -> list[str]:
        """Comando compartilhado pela captura real e pelo diagnóstico finito."""
        command = [
            "ffmpeg",
            "-y",
            # Entrada: câmera via DirectShow (Windows)
            *(input_args if input_args is not None else self.build_input_args()),
            '-map', '0:v:0', '-an',
            # Codec de saída
            "-c:v", "libx264",
            "-preset", "ultrafast",   # Mínimo uso de CPU
            "-tune", "zerolatency",   # Sem buffer de latência
            "-g", str(Config.KEYFRAME_INTERVAL),
            "-sc_threshold", "0",     # Desabilita detecção de cena que atrapalha o -g
            # Segmentação
            "-f", "segment",
            "-segment_time", str(self.segment_duration),
            "-reset_timestamps", "1",
        ]
        if self.segment_duration <= 0 or Config.KEYFRAME_INTERVAL <= 0:
            raise ValueError('Duração e intervalo de keyframes devem ser positivos')
        if manifest_path:
            command.extend(['-segment_list', manifest_path, '-segment_list_type', 'csv',
                            '-segment_list_size', '0'])
        if duration is not None:
            command.extend(["-t", str(duration)])
        return command + [segment_pattern]

    def stop_recording(self) -> None:
        """Encerra graciosamente o FFmpeg."""
        self._running.clear()
        if self._ffmpeg_process and self._ffmpeg_process.poll() is None:
            logger.info("DVR: Encerrando FFmpeg (PID %d)...", self._ffmpeg_process.pid)
            try:
                self._ffmpeg_process.stdin.write(b'q\n')
                self._ffmpeg_process.stdin.flush()
                self._ffmpeg_process.wait(timeout=10)
            except (OSError, ValueError, subprocess.TimeoutExpired):
                self._ffmpeg_process.kill()
                self._ffmpeg_process.wait(timeout=5)
            logger.info("DVR: FFmpeg encerrado.")
        if self._ffmpeg_process and self._ffmpeg_process.stdin:
            self._ffmpeg_process.stdin.close()

    # ------------------------------------------------------------------
    # LIMPEZA DE DISCO
    # ------------------------------------------------------------------
    def start_cleanup_loop(self) -> None:
        """
        Loop infinito que apaga segmentos mais antigos que RETENTION_HOURS.
        Deve ser chamado dentro de uma Thread dedicada (daemon).
        """
        logger.info("DVR Cleanup: Rotina de limpeza iniciada (retenção: %.1f horas)", self.retention_hours)

        while self._running.is_set():
            self._cleanup_old_segments()
            # Verifica a cada 5 minutos
            for _ in range(300):
                if not self._running.is_set():
                    break
                time.sleep(1)

        logger.info("DVR Cleanup: Rotina de limpeza finalizada.")

    def _cleanup_old_segments(self) -> None:
        """Apaga segmentos .mp4 mais velhos que o limite de retenção."""
        cutoff_time = time.time() - (self.retention_hours * 3600)
        self.catalog.prune(cutoff_time)
        pattern = os.path.join(self.segments_dir, "seg_*.mp4")
        removed = 0

        for filepath in glob.glob(pattern):
            try:
                if os.path.getmtime(filepath) < cutoff_time:
                    os.remove(filepath)
                    removed += 1
            except OSError as exc:
                logger.warning("DVR Cleanup: Não conseguiu apagar %s → %s", filepath, exc)

        if removed > 0:
            logger.info("DVR Cleanup: %d segmento(s) antigo(s) removido(s).", removed)

    # ------------------------------------------------------------------
    # UTILITÁRIO: listar segmentos ordenados
    # ------------------------------------------------------------------
    def get_recent_segments(self, count: int) -> list[str]:
        """
        Retorna os `count` segmentos MAIS RECENTES (excluindo o que está sendo escrito agora).
        Ordenados do mais antigo para o mais recente (ordem correta para concat).
        """
        if self.session_id:
            records = self.catalog.records(self.session_id)
            return [r['path'] for r in records if r['status'] == 'CLOSED'][-count:]
        # Compatibilidade com os diagnósticos legados. Worker será migrado para
        # seleção temporal em GW-05; arquivos planos nunca entram em uma sessão nova.
        pattern = os.path.join(self.segments_dir, "seg_*.mp4")
        all_segments = sorted(glob.glob(pattern), key=os.path.getmtime)

        if not all_segments:
            return []

        # O último arquivo da lista é o que o FFmpeg está escrevendo AGORA (incompleto).
        # Removemos ele da seleção para evitar corrupção.
        completed_segments = all_segments[:-1]

        # Pega os N mais recentes
        selected = completed_segments[-count:]

        logger.debug(
            "DVR: %d segmentos completos disponíveis. Selecionados %d: %s",
            len(completed_segments),
            len(selected),
            [os.path.basename(s) for s in selected],
        )
        return selected
