"""Independently retried clip generation and cloud delivery."""
from datetime import datetime,timezone
from pathlib import Path
import json
import subprocess
import threading
import boto3
from botocore.config import Config as AWSClientConfig
import requests
from .config import Config
from .dvr_manager import WindowNotReady
from .logger import logger

class VideoProcessor(threading.Thread):
    def __init__(self,store,dvr,*,delivery=False,s3_client=None,http=None):
        super().__init__(name="Delivery" if delivery else "ClipProcessor",daemon=False)
        self.store,self.dvr,self.delivery = store,dvr,delivery
        self._stop_event = threading.Event()
        self.output_dir = Path(Config.OUTPUT_DIR).resolve()
        self.output_dir.mkdir(parents=True,exist_ok=True)
        self.http,self.s3 = http or requests,s3_client

    def stop(self):
        self._stop_event.set()

    def run(self):
        self.cleanup_confirmed()
        while not self._stop_event.is_set():
            event = self.store.due(("CLIP_READY","UPLOADED") if self.delivery else ("CAPTURED",))
            if event is None:
                self._stop_event.wait(0.5)
                continue
            try:
                self.process(event)
            except Exception as exc:
                status = getattr(getattr(exc, "response", None), "status_code", None)
                if status in {400, 401, 403, 404, 409, 422}:
                    # A failed notification may already have persisted UPLOADED; preserve that checkpoint.
                    current = self.store.get(event["evento_id"])
                    self.store.block(current, f"HTTP {status}: requer correcao e reenvio administrativo")
                else:
                    self.store.retry(event["evento_id"],exc,delay=2 if isinstance(exc,WindowNotReady) else None)
                logger.warning("Evento %s preservado para nova tentativa: %s",event["evento_id"],exc)

    def process(self,event):
        event_id = event["evento_id"]
        if event["state"]=="CAPTURED":
            clip = self.output_dir/f"{event_id}.mp4"
            if clip.is_file() and not self._valid_clip(clip, event["before"] + event["after"]):
                clip.unlink()
            if not clip.is_file():
                with self.dvr._filesystem_lock:
                    segments,offset,duration = self.dvr.window(event["timestamp"],event["before"],event["after"])
                # CAPTURED remains durable, so cleanup reserves these inputs without blocking serial I/O.
                self._make_clip(segments,clip,offset,duration)
            self.store.transition(event_id,"CLIP_READY",clip_path=str(clip))
            return
        key = event.get("s3_key") or f"replays/{event['quadra_id']}/{event_id}.mp4"
        if event["state"]=="CLIP_READY":
            self._upload(event["clip_path"],key)
            self.store.transition(event_id,"UPLOADED",s3_key=key)
        self._notify_backend(event,key)
        self.store.transition(event_id,"CONFIRMED")
        Path(event["clip_path"]).unlink(missing_ok=True)
        logger.info("Evento %s confirmado; clipe local removido.",event_id)

    def _make_clip(self,segments,output_path,offset,duration):
        manifest,temporary = output_path.with_suffix(".concat.txt"),output_path.with_suffix(".partial.mp4")
        try:
            with manifest.open("w",encoding="utf-8") as stream:
                for segment in segments:
                    escaped = str(Path(segment.path).resolve()).replace(chr(92),"/").replace("'","'"+chr(92)+"''")
                    stream.write(f"file '{escaped}'\n")
            command = ["ffmpeg","-hide_banner","-loglevel","error","-y","-f","concat","-safe","0","-i",str(manifest),"-ss",str(offset),"-t",str(duration),"-an","-c:v","libx264","-preset","veryfast","-crf","23","-pix_fmt","yuv420p","-movflags","+faststart",str(temporary)]
            result = subprocess.run(command,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,text=True,encoding="utf-8",errors="replace",timeout=max(120,duration*4))
            if result.returncode or not temporary.exists() or not temporary.stat().st_size:
                raise RuntimeError(f"FFmpeg falhou: {result.stderr[-1000:]}")
            if not self._valid_clip(temporary, duration):
                raise RuntimeError("Clipe gerado nao possui a duracao contratada; evento preservado.")
            temporary.replace(output_path)
        finally:
            manifest.unlink(missing_ok=True)
            temporary.unlink(missing_ok=True)

    def _upload(self,path,key):
        if self.s3 is None:
            self.s3 = boto3.client("s3",aws_access_key_id=Config.AWS_ACCESS_KEY_ID,aws_secret_access_key=Config.AWS_SECRET_ACCESS_KEY,aws_session_token=Config.AWS_SESSION_TOKEN,region_name=Config.AWS_REGION,endpoint_url=Config.S3_ENDPOINT_URL,
                                   config=AWSClientConfig(connect_timeout=5, read_timeout=30, retries={"mode": "standard", "max_attempts": 3}))
        self.s3.upload_file(path,Config.S3_BUCKET_NAME,key,ExtraArgs={"ContentType":"video/mp4"})

    @staticmethod
    def _valid_clip(path, expected_duration):
        try:
            result = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                                     "-of", "json", str(path)], capture_output=True, text=True, timeout=15)
            duration = float(json.loads(result.stdout)["format"]["duration"])
            return result.returncode == 0 and abs(duration - expected_duration) <= 0.25
        except (OSError, ValueError, KeyError, subprocess.TimeoutExpired):
            return False

    def _notify_backend(self,event,key):
        payload = {"dispositivo_id":event["dispositivo_id"],"evento_id":event["evento_id"],"timestamp":datetime.fromtimestamp(event["timestamp"],timezone.utc).isoformat(),"s3_key":key}
        response = self.http.post(Config.API_URL,json=payload,headers={"X-Device-Token":Config.DEVICE_TOKEN},timeout=(5,20))
        response.raise_for_status()
        if response.status_code not in (200,201) or not response.json().get("id"):
            raise RuntimeError("API nao confirmou um lance; arquivo local preservado.")

    def cleanup_confirmed(self):
        for path in self.store.confirmed_files():
            candidate = Path(path).resolve()
            if candidate.parent==self.output_dir:
                candidate.unlink(missing_ok=True)
