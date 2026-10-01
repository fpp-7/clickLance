"""
video_processor.py — Worker Thread que processa lances.

Pipeline por evento:
  1. Selecionar segmentos recentes do DVR.
  2. Concatenar via FFmpeg num único arquivo de lance.
  3. Upload do lance para o AWS S3.
  4. Notificar a API Spring Boot (POST).
  5. Limpar o arquivo local do lance.
"""

import os
import subprocess
import threading
import time
from datetime import datetime
from queue import Queue

import boto3
import requests
from botocore.exceptions import ClientError, NoCredentialsError

from .config import Config
from .dvr_manager import DVRManager
from .logger import logger


class VideoProcessor(threading.Thread):
    """Consumidor: lê eventos da fila e executa o pipeline completo de lance."""

    def __init__(self, event_queue: Queue, dvr: DVRManager) -> None:
        super().__init__(name="WorkerThread", daemon=True)
        self.queue = event_queue
        self.dvr = dvr

        self.segments_to_concat: int = Config.SEGMENTS_TO_CONCAT
        self.output_dir: str = Config.OUTPUT_DIR
        self.quadra_id: str = Config.QUADRA_ID
        self.api_url: str = Config.API_URL

        # Inicializa o cliente S3
        self.s3_client = boto3.client(
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

                self._process_event(timestamp, dt_str)

            except Exception as exc:
                logger.error("Worker: Erro crítico no pipeline → %s", exc, exc_info=True)
            finally:
                self.queue.task_done()

    def _process_event(self, timestamp: float, dt_str: str) -> None:
        """Executa o pipeline completo para um único evento."""
        clip_path: str | None = None

        try:
            # 1. Selecionar segmentos
            segments = self.dvr.get_recent_segments(self.segments_to_concat)
            if not segments:
                logger.warning("Worker: Nenhum segmento completo disponível ainda. Lance descartado.")
                return

            # 2. Concatenar
            clip_filename = f"lance_{dt_str}.mp4"
            clip_path = os.path.join(self.output_dir, clip_filename)
            self._concatenate_segments(segments, clip_path)

            # 3. Upload S3
            video_url = self._upload_to_s3(clip_path)

            # 4. Notificar API
            self._notify_backend(timestamp, video_url)

            logger.info("Worker: ✅ Lance %s processado com sucesso!", clip_filename)

        except Exception as exc:
            logger.error("Worker: ❌ Falha no pipeline do lance → %s", exc)

        finally:
            # 5. Limpeza local (mesmo se algum passo falhou parcialmente)
            if clip_path:
                self._cleanup(clip_path)

    # ------------------------------------------------------------------
    # ETAPA 1: CONCATENAÇÃO FFMPEG
    # ------------------------------------------------------------------
    def _concatenate_segments(self, segments: list[str], output_path: str) -> None:
        """
        Concatena uma lista de segmentos .mp4 num único arquivo usando
        o demuxer 'concat' do FFmpeg (sem re-encode — ultra rápido).
        """
        # Cria arquivo de lista temporário exigido pelo FFmpeg concat
        concat_list_path = os.path.join(self.output_dir, "concat_list.txt")

        with open(concat_list_path, "w", encoding="utf-8") as f:
            for seg_path in segments:
                # FFmpeg exige caminhos absolutos ou relativos com '/'
                abs_path = os.path.abspath(seg_path).replace("\\", "/")
                f.write(f"file '{abs_path}'\n")

        command = [
            "ffmpeg",
            "-y",
            "-f", "concat",
            "-safe", "0",
            "-i", concat_list_path,
            "-c", "copy",
            output_path,
        ]

        logger.info("Worker: Concatenando %d segmentos → %s", len(segments), os.path.basename(output_path))
        logger.debug("Worker: Segmentos: %s", [os.path.basename(s) for s in segments])
        logger.debug("Worker: Comando → %s", " ".join(command))

        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

        # Limpa o arquivo temporário de concat
        try:
            os.remove(concat_list_path)
        except OSError:
            pass

        if result.returncode != 0:
            logger.error("Worker: FFmpeg concat falhou → %s", result.stderr[-1000:])
            raise RuntimeError("Falha na concatenação FFmpeg.")

        file_size_mb = os.path.getsize(output_path) / (1024 * 1024)
        logger.info("Worker: Clipe gerado (%.1f MB): %s", file_size_mb, output_path)

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
        """Remove o clipe local após o processamento (sucesso ou falha)."""
        try:
            if os.path.exists(file_path):
                os.remove(file_path)
                logger.info("Worker: Clipe local removido → %s", os.path.basename(file_path))
        except OSError as exc:
            logger.warning("Worker: Não foi possível remover %s → %s", file_path, exc)
