"""
dvr_manager.py — Gravador Contínuo (DVR) com Segmentação FFmpeg.

Responsabilidades:
  1. Lançar o FFmpeg capturando a webcam via DirectShow (Windows).
  2. Fatiar a gravação em segmentos de N segundos usando `-f segment`.
  3. Rodar uma rotina de limpeza periódica que apaga segmentos mais velhos que X horas.
"""

import glob
import os
import subprocess
import threading
import time

from .config import Config
from .logger import logger


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

    # ------------------------------------------------------------------
    # GRAVAÇÃO
    # ------------------------------------------------------------------
    def start_recording(self) -> None:
        """
        Inicia o FFmpeg em modo segmentado (roda indefinidamente).
        Deve ser chamado dentro de uma Thread dedicada.
        """
        self._running.set()

        # Padrão de nomes: seg_0001.mp4, seg_0002.mp4, ...
        segment_pattern = os.path.join(self.segments_dir, "seg_%04d.mp4")

        command = [
            "ffmpeg",
            "-y",
            # Entrada: câmera via DirectShow (Windows)
            "-f", "dshow",
            "-i", f"video={self.camera_name}",
            # Codec de saída
            "-c:v", "libx264",
            "-preset", "ultrafast",   # Mínimo uso de CPU
            "-tune", "zerolatency",   # Sem buffer de latência
            # Segmentação
            "-f", "segment",
            "-segment_time", str(self.segment_duration),
            "-reset_timestamps", "1",
            # Saída
            segment_pattern,
        ]

        logger.info(f"DVR: Iniciando gravação segmentada ({self.segment_duration}s por segmento)")
        logger.debug(f"DVR: Comando FFmpeg → {' '.join(command)}")

        try:
            self._ffmpeg_process = subprocess.Popen(
                command,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
            logger.info("DVR: FFmpeg iniciado com sucesso (PID %d)", self._ffmpeg_process.pid)

            # Fica bloqueado aqui enquanto o FFmpeg estiver rodando
            _, stderr = self._ffmpeg_process.communicate()

            # Se o processo terminou sozinho (crash ou erro), loga o stderr
            if self._running.is_set():
                logger.error("DVR: FFmpeg encerrou inesperadamente!")
                if stderr:
                    logger.error("DVR: FFmpeg stderr → %s", stderr.decode("utf-8", errors="replace")[-2000:])

        except FileNotFoundError:
            logger.error("DVR: FFmpeg não encontrado no PATH do sistema. Instale o FFmpeg.")
        except Exception as exc:
            logger.error("DVR: Erro fatal na gravação → %s", exc)

    def stop_recording(self) -> None:
        """Encerra graciosamente o FFmpeg."""
        self._running.clear()
        if self._ffmpeg_process and self._ffmpeg_process.poll() is None:
            logger.info("DVR: Encerrando FFmpeg (PID %d)...", self._ffmpeg_process.pid)
            self._ffmpeg_process.terminate()
            try:
                self._ffmpeg_process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self._ffmpeg_process.kill()
            logger.info("DVR: FFmpeg encerrado.")

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
