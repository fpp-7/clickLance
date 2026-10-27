"""Supervised DVR; unique sessions and finalized CSV intervals anchor event windows."""
import csv
from dataclasses import dataclass
import json
from pathlib import Path
import shutil
import subprocess
import threading
import time
from uuid import uuid4
from .config import Config
from .logger import logger

@dataclass(frozen=True)
class Segment:
    path: str
    start: float
    end: float
    session: str

class WindowNotReady(RuntimeError):
    pass

class DVRManager:
    def __init__(self, store=None):
        self.segments_dir = Config.SEGMENTS_DIR
        self.segment_duration = Config.SEGMENT_DURATION_SECONDS
        self.camera_name = Config.CAMERA_DEVICE_NAME
        self.retention_hours = Config.RETENTION_HOURS
        self.store = store
        self._stop = threading.Event()
        self._ffmpeg_process = None
        self._active_session = None
        Path(self.segments_dir).mkdir(parents=True, exist_ok=True)
        self._filesystem_lock = threading.RLock()

    def _command(self, folder):
        return ["ffmpeg","-hide_banner","-loglevel","warning","-n","-f","dshow","-i",f"video={self.camera_name}","-an","-c:v","libx264","-preset","ultrafast","-tune","zerolatency","-pix_fmt","yuv420p","-g","30","-sc_threshold","0","-force_key_frames",f"expr:gte(t,n_forced*{self.segment_duration})","-f","segment","-segment_time",str(self.segment_duration),"-segment_list",str(folder/"segments.csv"),"-segment_list_type","csv","-reset_timestamps","1",str(folder/"seg_%08d.mp4")]

    def start_recording(self):
        while not self._stop.is_set():
            folder = Path(self.segments_dir).resolve()/uuid4().hex
            folder.mkdir()
            self._active_session = str(folder)
            (folder/"session.json").write_text(json.dumps({"started_at":time.time()}),encoding="utf-8")
            try:
                recording_started = time.monotonic()
                with (folder/"ffmpeg.log").open("a",encoding="utf-8") as error_log:
                    self._ffmpeg_process = subprocess.Popen(self._command(folder),stdin=subprocess.PIPE,stdout=subprocess.DEVNULL,stderr=error_log)
                    while self._ffmpeg_process.poll() is None and not self._stop.wait(1):
                        if shutil.disk_usage(folder).free < Config.MIN_FREE_DISK_MB*1024*1024:
                            logger.error("DVR: reserva de disco atingida; captura interrompida.")
                            self._finish_process()
                            break
                        manifest = folder / "segments.csv"
                        progress_age = time.time() - manifest.stat().st_mtime if manifest.exists() else time.monotonic() - recording_started
                        if progress_age > self.segment_duration * 2 + 15:
                            logger.error("DVR: manifesto sem progresso; reiniciando captura.")
                            self._finish_process()
                            break
                if not self._stop.is_set():
                    logger.error("DVR: gravacao interrompida; nova tentativa em cinco segundos. Log: %s",folder/"ffmpeg.log")
            except (OSError,subprocess.SubprocessError):
                logger.exception("DVR: falha ao iniciar gravacao")
            finally:
                self._finish_process()
                self._active_session = None
            self._stop.wait(5)

    def _finish_process(self):
        process = self._ffmpeg_process
        if process and process.poll() is None:
            try:
                process.stdin.write(b"q\n")
                process.stdin.flush()
                process.wait(timeout=10)
            except (OSError,ValueError,subprocess.TimeoutExpired):
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)

    def stop_recording(self):
        self._stop.set()
        self._finish_process()

    def segments(self):
        result = []
        for manifest in Path(self.segments_dir).glob("*/segments.csv"):
            folder = manifest.parent
            first, anchor = folder/"seg_00000000.mp4",folder/"origin.json"
            try:
                if anchor.exists():
                    origin = json.loads(anchor.read_text(encoding="utf-8"))["epoch"]
                elif first.exists():
                    stat = first.stat()
                    origin = getattr(stat,"st_birthtime",stat.st_ctime)
                    anchor.write_text(json.dumps({"epoch":origin}),encoding="utf-8")
                else:
                    continue
                with manifest.open(newline="",encoding="utf-8") as stream:
                    for row in csv.reader(stream):
                        if len(row)!=3:
                            continue
                        name,begin,end = row
                        path = folder/Path(name).name
                        begin,end = float(begin),float(end)
                        if path.is_file() and end>begin:
                            result.append(Segment(str(path.resolve()),origin+begin,origin+end,str(folder.resolve())))
            except (OSError,ValueError,json.JSONDecodeError):
                logger.warning("DVR: manifesto temporariamente indisponivel: %s",manifest)
        return sorted(result,key=lambda s:s.start)

    def window(self,timestamp,before,after):
        start,end = timestamp-before,timestamp+after
        segments = [s for s in self.segments() if s.end>start and s.start<end]
        if not segments or segments[0].start>start+0.1 or segments[-1].end<end-0.001:
            raise WindowNotReady("Janela ainda nao finalizada ou buffer insuficiente; evento preservado.")
        cursor,session = start,segments[0].session
        for segment in segments:
            if segment.session!=session or segment.start>cursor+0.1:
                raise WindowNotReady("Janela atravessa interrupcao de gravacao; necessita revisao.")
            cursor = max(cursor,segment.end)
        return segments,max(0,start-segments[0].start),end-start

    def ready_for_click(self,timestamp,before):
        if not self._ffmpeg_process or self._ffmpeg_process.poll() is not None:
            return False
        segments = self.segments()
        if not segments or segments[-1].end<timestamp-self.segment_duration-5:
            return False
        return any(s.session==self._active_session and s.start<=timestamp-before+0.1 for s in segments)

    def start_cleanup_loop(self):
        while not self._stop.is_set():
            self._cleanup_old_segments()
            self._stop.wait(60)

    def _cleanup_old_segments(self):
        with self._filesystem_lock:
            cutoff = time.time()-self.retention_hours*3600
            windows = self.store.capture_windows() if self.store else []
            for segment in self.segments():
                protected = any(segment.end>start and segment.start<end for start,end in windows)
                if segment.end<cutoff and not protected:
                    try:
                        Path(segment.path).unlink(missing_ok=True)
                    except OSError:
                        logger.warning("DVR: limpeza adiada para %s",segment.path)
            # Retire abandoned session logs/partial files only after retention, never pending windows.
            root = Path(self.segments_dir).resolve()
            for folder in root.iterdir():
                if not folder.is_dir() or str(folder) == self._active_session or folder.parent != root:
                    continue
                metadata = folder / "session.json"
                try:
                    if not metadata.exists():
                        continue
                    files = list(folder.iterdir())
                    if any(not item.is_file() for item in files):
                        continue
                    started = json.loads(metadata.read_text(encoding="utf-8"))["started_at"]
                    ended = max(item.stat().st_mtime for item in files)
                    if ended >= cutoff or any(ended > start and started < end for start, end in windows):
                        continue
                    for item in files:
                        item.unlink()
                    folder.rmdir()
                except (OSError, ValueError, KeyError):
                    logger.warning("DVR: sessao antiga preservada para proxima limpeza: %s", folder)
