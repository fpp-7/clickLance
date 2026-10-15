"""
video_processor.py — Worker Thread que processa lances.

Pipeline por evento:
  1. Recuperar janela reservada no instante do clique.
  2. Aguardar cobertura, cortar e validar o clipe.
  3. Upload do lance para o AWS S3.
  4. Notificar a API Spring Boot (POST).
  5. Limpar o arquivo local do lance.
"""

import os
import subprocess
import threading
import tempfile
from datetime import datetime
from pathlib import Path
from queue import Queue

import boto3
import requests
from botocore.exceptions import ClientError, NoCredentialsError

from .config import Config
from .dvr_manager import DVRManager
from .logger import logger
from .media import validate_video


class VideoProcessor(threading.Thread):
    """Consumidor: lê eventos da fila e executa o pipeline completo de lance."""

    def __init__(self, event_queue: Queue, dvr: DVRManager, *, s3_client=None) -> None:
        super().__init__(name="WorkerThread", daemon=True)
        self.queue = event_queue
        self.dvr = dvr
        self._active_ffmpeg = None

        self.output_dir: str = Config.OUTPUT_DIR
        self.quadra_id: str = Config.QUADRA_ID
        self.api_url: str = Config.API_URL

        # Inicializa o cliente S3
        self.s3_client = s3_client if s3_client is not None else boto3.client(
            "s3",
            aws_access_key_id=Config.AWS_ACCESS_KEY_ID,
            aws_secret_access_key=Config.AWS_SECRET_ACCESS_KEY,
            region_name=Config.AWS_REGION,
        )
        self.bucket_name: str | None = Config.S3_BUCKET_NAME

        os.makedirs(self.output_dir, exist_ok=True)

    # ------------------------------------------------------------------
    # LOOP PRINCIPAL
    # ------------------------------------------------------------------
    def run(self) -> None:
        """Loop infinito consumindo eventos da fila."""
        logger.info("Worker: Thread de processamento iniciada. Aguardando eventos...")

        while True:
            try:
                event = self.queue.get()  # Bloqueante até chegar um evento
                timestamp = event["timestamp"]
                dt_str = datetime.fromtimestamp(timestamp).strftime("%Y%m%d_%H%M%S")

                logger.info("=" * 60)
                logger.info("Worker: NOVO LANCE — timestamp %s", dt_str)
                logger.info("=" * 60)

                self._process_event(timestamp, dt_str, event.get('replay'))

            except Exception as exc:
                logger.error("Worker: Erro crítico no pipeline → %s", exc, exc_info=True)
            finally:
                self.queue.task_done()

    def _process_event(self, timestamp: float, dt_str: str, replay: dict | None = None) -> str:
        """Executa o pipeline completo para um único evento."""
        if replay is None:
            raise ValueError('REPLAY_CONTEXT_REQUIRED')
        request, records = self.dvr.wait_replay(replay['id'])
        clip_path = os.path.join(self.output_dir, f"lance_{request['id']}.mp4")
        self._render_replay(request, records, clip_path)
        # A fonte só é liberada depois de clipe validado; falha na geração conserva reserva.
        self.dvr.catalog.release(request['id'])
        video_url = self._upload_to_s3(clip_path)
        self._notify_backend(timestamp, video_url)
        self._cleanup(clip_path)
        logger.info('Worker: Replay %s enviado e registrado.', request['id'])
        return clip_path

    def _render_replay(self, request: dict, records: list[dict], output_path: str):
        offset = request['start'] - records[0]['start']
        duration = request['end'] - request['start']
        if offset < -0.001 or duration <= 0:
            raise ValueError('INVALID_REPLAY_INTERVAL')
        # Recodificação é deliberada: cópia não garante bordas arbitrárias.
        options = ['-ss', f'{max(0, offset):.6f}', '-t', f'{duration:.6f}',
                   '-map', '0:v:0', '-an', '-c:v', 'libx264', '-preset', 'veryfast',
                   '-crf', '18', '-pix_fmt', 'yuv420p', '-movflags', '+faststart']
        options.extend(['-maxrate', str(Config.CAPACITY_BITRATE), '-bufsize', str(Config.CAPACITY_BITRATE * 2)])
        self._concat([r['path'] for r in records], output_path, options)
        actual_duration = validate_video(Path(output_path))
        if abs(actual_duration - duration) > 0.05:
            raise ValueError('INVALID_REPLAY_DURATION')

    # ------------------------------------------------------------------
    # ETAPA 1: CONCATENAÇÃO FFMPEG
    # ------------------------------------------------------------------
    def _concatenate_segments(self, segments: list[str], output_path: str) -> None:
        """
        Concatena uma lista de segmentos .mp4 num único arquivo usando
        o demuxer 'concat' do FFmpeg (sem re-encode — ultra rápido).
        """
        self._concat(segments, output_path, ['-c', 'copy'])

    def _concat(self, segments: list[str], output_path: str, output_options: list[str]):
        if not segments:
            raise ValueError('EMPTY_REPLAY_SOURCES')
        lines = []
        for seg_path in segments:
            abs_path = os.path.abspath(seg_path).replace('\\', '/')
            if '\n' in abs_path or '\r' in abs_path:
                raise ValueError('INVALID_SOURCE_PATH')
            escaped = abs_path.replace("'", "'\\''")
            lines.append(f"file '{escaped}'\n")
        with tempfile.NamedTemporaryFile(mode='w', suffix='.ffconcat',
                                         dir=self.output_dir, encoding='utf-8', delete=False) as f:
            concat_list_path = f.name
            f.writelines(lines)

        command = [
            "ffmpeg",
            "-y",
            "-f", "concat",
            "-safe", "0",
            "-i", concat_list_path,
            *output_options,
            output_path,
        ]

        logger.info("Worker: Concatenando %d segmentos → %s", len(segments), os.path.basename(output_path))
        logger.debug("Worker: Segmentos: %s", [os.path.basename(s) for s in segments])
        logger.debug("Worker: Comando → %s", " ".join(command))

        try:
            result = self._execute(command)
        finally:
            os.remove(concat_list_path)

        if result.returncode != 0:
            logger.error("Worker: FFmpeg concat falhou → %s", result.stderr[-1000:])
            raise RuntimeError("Falha na concatenação FFmpeg.")

        file_size_mb = os.path.getsize(output_path) / (1024 * 1024)
        logger.info("Worker: Clipe gerado (%.1f MB): %s", file_size_mb, output_path)

    def _execute(self, command):
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        self._active_ffmpeg = process
        try:
            try:
                stdout, stderr = process.communicate(timeout=Config.REPLAY_FFMPEG_TIMEOUT_SECONDS)
            except subprocess.TimeoutExpired:
                process.kill()
                process.communicate()
                raise
            return subprocess.CompletedProcess(command, process.returncode, stdout, stderr)
        finally:
            self._active_ffmpeg = None

    def cancel(self):
        process = self._active_ffmpeg
        if process is not None and process.poll() is None:
            process.kill()

    # ------------------------------------------------------------------
    # ETAPA 2: UPLOAD S3
    # ------------------------------------------------------------------
    def _upload_to_s3(self, file_path: str) -> str:
        """Faz upload do clipe para o S3 e retorna a URL pública."""
        file_name = os.path.basename(file_path)
        object_key = f"replays/{self.quadra_id}/{file_name}"

        logger.info("Worker: Enviando %s para S3 (bucket: %s)...", file_name, self.bucket_name)

        try:
            self.s3_client.upload_file(file_path, self.bucket_name, object_key)

            url = f"https://{self.bucket_name}.s3.{Config.AWS_REGION}.amazonaws.com/{object_key}"
            logger.info("Worker: Upload S3 concluído → %s", url)
            return url

        except NoCredentialsError:
            logger.error("Worker: Credenciais AWS ausentes! Verifique o .env.")
            raise
        except ClientError as exc:
            logger.error("Worker: Erro AWS ClientError → %s", exc)
            raise

    # ------------------------------------------------------------------
    # ETAPA 3: NOTIFICAÇÃO API
    # ------------------------------------------------------------------
    def _notify_backend(self, timestamp: float, video_url: str) -> None:
        """Envia POST para a API Spring Boot notificando o novo lance."""
        payload = {
            "quadra_id": self.quadra_id,
            "timestamp": datetime.fromtimestamp(timestamp).isoformat(),
            "video_url": video_url,
        }

        logger.info("Worker: Notificando backend → POST %s", self.api_url)
        logger.debug("Worker: Payload → %s", payload)

        try:
            response = requests.post(self.api_url, json=payload, timeout=15)
            response.raise_for_status()
            logger.info("Worker: Backend respondeu HTTP %d — OK!", response.status_code)
        except requests.exceptions.Timeout:
            logger.error("Worker: Timeout ao notificar o backend (15s).")
            raise
        except requests.exceptions.RequestException as exc:
            logger.error("Worker: Falha na requisição ao backend → %s", exc)
            raise

    # ------------------------------------------------------------------
    # ETAPA 4: LIMPEZA LOCAL
    # ------------------------------------------------------------------
    @staticmethod
    def _cleanup(file_path: str) -> None:
        """Remove o clipe local após upload e registro confirmados."""
        try:
            if os.path.exists(file_path):
                os.remove(file_path)
                logger.info("Worker: Clipe local removido → %s", os.path.basename(file_path))
        except OSError as exc:
            logger.warning("Worker: Não foi possível remover %s → %s", file_path, exc)
