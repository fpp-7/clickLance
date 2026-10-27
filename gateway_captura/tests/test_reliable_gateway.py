"""Isolated tests: no camera, serial port, credentials or cloud access."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import Mock, patch
from uuid import uuid4

os.environ["CLICKLANCE_LOAD_ENV"] = "0"
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import requests
from src.config import Config
from src.dvr_manager import DVRManager, Segment, WindowNotReady
from src.event_store import EventStore
from src.instance_lock import InstanceLock
from src.serial_listener import SerialListener
from src.video_processor import VideoProcessor


class GatewayTests(unittest.TestCase):
    def setUp(self):
        self.test_root = Path(__file__).resolve().parents[1] / "test_work"
        self.test_root.mkdir(exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(dir=self.test_root)
        self.root = Path(self.temporary.name)
        self.config = patch.multiple(Config, SEGMENTS_DIR=str(self.root / "segments"), OUTPUT_DIR=str(self.root / "clips"),
                                     CONFIG_CACHE=str(self.root / "config.json"), STATE_DB=str(self.root / "events.sqlite3"),
                                     DISPOSITIVO_ID="disp_001", DEVICE_TOKEN="test-device-token", QUADRA_ID=str(uuid4()),
                                     S3_BUCKET_NAME="test-private-bucket", SEGMENT_DURATION_SECONDS=10,
                                     SEGMENTS_TO_CONCAT=2, RETENTION_HOURS=6, MIN_FREE_DISK_MB=1)
        self.config.start()
        self.store = EventStore(Config.STATE_DB)
        self.dvr = DVRManager(self.store)

    def tearDown(self):
        self.config.stop()
        self.temporary.cleanup()

    def session(self, name="session-a", origin=1000, intervals=((0, 10), (10, 20), (20, 30))):
        folder = Path(Config.SEGMENTS_DIR) / name
        folder.mkdir(parents=True)
        (folder / "origin.json").write_text(json.dumps({"epoch": origin}))
        rows = []
        for index, (start, end) in enumerate(intervals):
            filename = f"seg_{index:08d}.mp4"
            (folder / filename).write_bytes(b"fixture")
            rows.append(f"{filename},{start},{end}\n")
        (folder / "segments.csv").write_text("".join(rows))
        return folder

    def event(self, timestamp=1025, before=15, after=0.2):
        return self.store.enqueue(timestamp, dispositivo_id=Config.DISPOSITIVO_ID, quadra_id=Config.QUADRA_ID,
                                  before=before, after=after)

    def ready_clip(self):
        event_id = self.event()
        clip = Path(Config.OUTPUT_DIR) / f"{event_id}.mp4"
        clip.parent.mkdir(parents=True, exist_ok=True)
        clip.write_bytes(b"clip fixture")
        self.store.transition(event_id, "CLIP_READY", clip_path=str(clip))
        return event_id, clip

    def test_window_contains_event_and_remains_stable_after_queue_delay(self):
        self.session()
        first, offset, duration = self.dvr.window(1025, 15, 0.2)
        self.session("later-session", 2000)
        later, _, _ = self.dvr.window(1025, 15, 0.2)
        self.assertEqual(first, later)
        self.assertEqual([Path(s.path).name for s in first], ["seg_00000001.mp4", "seg_00000002.mp4"])
        self.assertAlmostEqual(offset, 0)
        self.assertAlmostEqual(duration, 15.2)
        self.assertTrue(first[-1].start <= 1025 < first[-1].end)

    def test_waits_for_active_segment_to_close_instead_of_skipping_goal(self):
        folder = self.session(intervals=((0, 10), (10, 20)))
        with self.assertRaises(WindowNotReady):
            self.dvr.window(1025, 15, 0.2)
        (folder / "seg_00000002.mp4").write_bytes(b"finalized")
        with (folder / "segments.csv").open("a") as stream:
            stream.write("seg_00000002.mp4,20,30\n")
        self.assertEqual(len(self.dvr.window(1025, 15, 0.2)[0]), 2)

    def test_restart_never_mixes_sessions(self):
        self.session(origin=1000, intervals=((0, 10), (10, 20)))
        self.session("session-b", 1020, intervals=((0, 10), (10, 20)))
        with self.assertRaises(WindowNotReady):
            self.dvr.window(1030, 20, 0)

    def test_gap_is_not_silently_ignored(self):
        self.session(intervals=((0, 10), (11, 20), (20, 30)))
        with self.assertRaises(WindowNotReady):
            self.dvr.window(1025, 20, 0)

    def test_relative_recording_directory_matches_absolute_active_session(self):
        folder = self.session()
        self.dvr.segments_dir = os.path.relpath(Config.SEGMENTS_DIR)
        self.dvr._active_session = str(folder.resolve())
        self.dvr._ffmpeg_process = Mock(poll=Mock(return_value=None))
        self.assertTrue(self.dvr.ready_for_click(1025, 15))

    def test_event_persists_identity_window_and_checkpoint_after_restart(self):
        event_id = self.event()
        self.store.transition(event_id, "UPLOADED", clip_path="fixture.mp4", s3_key="fixture/key")
        reopened = EventStore(Config.STATE_DB).get(event_id)
        self.assertEqual(reopened["state"], "UPLOADED")
        self.assertEqual(reopened["before"], 15)
        self.assertEqual(reopened["timestamp"], 1025)
        self.assertEqual(reopened["quadra_id"], Config.QUADRA_ID)

    def test_queue_limit_rejects_without_silent_loss(self):
        store = EventStore(self.root / "limited.sqlite3", max_pending=1)
        arguments = dict(dispositivo_id="d", quadra_id="q", before=10)
        event_id = store.enqueue(1, **arguments)
        with self.assertRaises(RuntimeError):
            store.enqueue(2, **arguments)
        self.assertIsNotNone(store.get(event_id))

    def test_online_backup_keeps_checkpoint_without_overwriting_existing_file(self):
        event_id = self.event()
        self.store.transition(event_id, "UPLOADED", clip_path="original.mp4", s3_key="object-key")
        destination = self.store.backup(self.root / "snapshot.sqlite3")
        snapshot = EventStore(destination).get(event_id)
        self.assertEqual(snapshot["state"], "UPLOADED")
        self.assertEqual(snapshot["s3_key"], "object-key")
        with self.assertRaises(FileExistsError):
            self.store.backup(destination)
        with self.assertRaises(ValueError):
            self.store.backup(Config.STATE_DB)

    def test_upload_failure_retains_local_file_and_stage(self):
        event_id, clip = self.ready_clip()
        s3 = Mock()
        s3.upload_file.side_effect = RuntimeError("offline")
        worker = VideoProcessor(self.store, self.dvr, delivery=True, s3_client=s3, http=Mock())
        with self.assertRaises(RuntimeError):
            worker.process(self.store.get(event_id))
        self.assertTrue(clip.exists())
        self.assertEqual(self.store.get(event_id)["state"], "CLIP_READY")

    def test_api_failure_resumes_without_reupload_and_removes_only_after_confirmation(self):
        event_id, clip = self.ready_clip()
        s3, http = Mock(), Mock()
        http.post.side_effect = requests.ConnectionError("offline")
        worker = VideoProcessor(self.store, self.dvr, delivery=True, s3_client=s3, http=http)
        with self.assertRaises(requests.ConnectionError):
            worker.process(self.store.get(event_id))
        self.assertTrue(clip.exists())
        self.assertEqual(self.store.get(event_id)["state"], "UPLOADED")
        response = Mock(status_code=200)
        response.json.return_value = {"id": str(uuid4())}
        http.post.side_effect, http.post.return_value = None, response
        worker.process(self.store.get(event_id))
        self.assertFalse(clip.exists())
        self.assertEqual(s3.upload_file.call_count, 1)
        self.assertEqual(self.store.get(event_id)["state"], "CONFIRMED")
        uploaded = s3.upload_file.call_args
        self.assertEqual(uploaded.kwargs["ExtraArgs"], {"ContentType": "video/mp4"})
        payload = http.post.call_args.kwargs["json"]
        self.assertEqual(payload["evento_id"], event_id)
        self.assertEqual(payload["s3_key"], f"replays/{Config.QUADRA_ID}/{event_id}.mp4")
        self.assertTrue(payload["timestamp"].endswith("+00:00"))
        self.assertEqual(http.post.call_args.kwargs["headers"], {"X-Device-Token": "test-device-token"})

    def test_nonconfirmation_does_not_delete_clip(self):
        event_id, clip = self.ready_clip()
        response = Mock(status_code=202)
        worker = VideoProcessor(self.store, self.dvr, delivery=True, s3_client=Mock(), http=Mock(post=Mock(return_value=response)))
        with self.assertRaises(RuntimeError):
            worker.process(self.store.get(event_id))
        self.assertTrue(clip.exists())

    def test_blocked_event_is_inspectable_and_manually_requeued(self):
        event_id, clip = self.ready_clip()
        self.store.block(self.store.get(event_id), "HTTP422")
        self.assertIsNone(self.store.due(("CLIP_READY", "UPLOADED")))
        self.assertTrue(clip.exists())
        self.assertEqual(self.store.pending()[0]["state"], "BLOCKED_CLIP_READY")
        self.store.requeue(event_id)
        self.assertEqual(self.store.get(event_id)["state"], "CLIP_READY")

    def test_retry_backoff_prevents_hot_loop(self):
        event_id = self.event()
        self.store.retry(event_id, "offline", delay=60)
        self.assertIsNone(self.store.due(("CAPTURED",)))
        self.assertEqual(self.store.get(event_id)["attempts"], 1)

    def test_cleanup_reserves_segments_for_pending_events(self):
        self.session()
        event_id = self.event()
        with patch("src.dvr_manager.time.time", return_value=100000):
            self.dvr._cleanup_old_segments()
        remaining = self.dvr.segments()
        self.assertEqual([Path(s.path).name for s in remaining], ["seg_00000001.mp4", "seg_00000002.mp4"])
        self.store.transition(event_id, "CLIP_READY")
        with patch("src.dvr_manager.time.time", return_value=100000):
            self.dvr._cleanup_old_segments()
        self.assertEqual(self.dvr.segments(), [])

    def test_remote_config_is_cached_and_transport_outage_uses_it(self):
        response, session = Mock(), Mock()
        response.json.return_value = dict(dispositivo_id=Config.DISPOSITIVO_ID, quadra_id=Config.QUADRA_ID,
                                          duracao_segmento_segundos=10, segmentos_concatenar=12)
        session.get.return_value = response
        self.assertEqual(Config.bootstrap(session), "remote")
        session.get.side_effect = requests.ConnectionError("offline")
        self.assertEqual(Config.bootstrap(session), "cache")
        self.assertEqual(Config.SEGMENTS_TO_CONCAT, 12)

    def test_revoked_device_does_not_fall_back_to_cached_config(self):
        Path(Config.CONFIG_CACHE).write_text("{}")
        response = Mock(status_code=401)
        response.raise_for_status.side_effect = requests.HTTPError(response=response)
        with self.assertRaises(RuntimeError):
            Config.bootstrap(Mock(get=Mock(return_value=response)))

    def test_other_device_cache_is_rejected(self):
        Path(Config.CONFIG_CACHE).write_text(json.dumps({"dispositivo_id": "another"}))
        with self.assertRaises(ValueError):
            Config.bootstrap(Mock(get=Mock(side_effect=requests.ConnectionError("offline"))))

    def test_remote_window_limits_match_backend_contract(self):
        data = dict(dispositivo_id=Config.DISPOSITIVO_ID, quadra_id=Config.QUADRA_ID,
                    duracao_segmento_segundos=1, segmentos_concatenar=120)
        Config._apply_remote(data)
        Config.validate()
        self.assertEqual(Config.SEGMENTS_TO_CONCAT, 120)
        for seconds, count in ((61, 1), (1, 301), (60, 11)):
            with self.subTest(seconds=seconds, count=count), self.assertRaises(ValueError):
                Config._apply_remote(dict(data, duracao_segmento_segundos=seconds, segmentos_concatenar=count))

    def test_click_cooldown_uses_monotonic_and_persists_first(self):
        dvr = Mock()
        dvr._filesystem_lock = threading.RLock()
        dvr.ready_for_click.return_value = True
        listener = SerialListener(self.store, dvr)
        first = listener.accept_click(timestamp=1000, monotonic=10)
        self.assertIsNotNone(self.store.get(first))
        self.assertIsNone(listener.accept_click(timestamp=5000, monotonic=11))
        second = listener.accept_click(timestamp=900, monotonic=16)
        self.assertNotEqual(first, second)

    def test_only_one_process_can_own_outbox(self):
        with InstanceLock(self.root / "instance.lock"):
            with self.assertRaises(RuntimeError):
                with InstanceLock(self.root / "instance.lock"):
                    pass

    @unittest.skipUnless(shutil.which("ffmpeg") and shutil.which("ffprobe"), "FFmpeg/ffprobe unavailable")
    def test_real_synthetic_video_trim_keeps_event_frame(self):
        # This fixture contains testsrc only; no camera or existing video is read.
        folder = self.root / "synthetic"
        folder.mkdir()
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i",
                        "testsrc=size=160x120:rate=10", "-t", "6", "-an", "-c:v", "libx264", "-pix_fmt", "yuv420p",
                        "-preset", "ultrafast", "-g", "10", "-sc_threshold", "0", "-f", "segment", "-segment_time", "2",
                        "-reset_timestamps", "1", str(folder / "seg_%02d.mp4")], check=True, capture_output=True, timeout=30)
        segments = [Segment(str(path), i*2, (i+1)*2, "synthetic") for i, path in enumerate(sorted(folder.glob("*.mp4")))]
        output = folder / "clip.mp4"
        VideoProcessor(self.store, self.dvr)._make_clip(segments, output, 2.5, 2)
        probe = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "json", str(output)],
                               check=True, capture_output=True, text=True, timeout=10)
        self.assertAlmostEqual(float(json.loads(probe.stdout)["format"]["duration"]), 2, delta=0.12)
        # Decode the frame at source t=4s and corresponding output t=1.5s and compare after encoding.
        def frame(path, timestamp):
            return subprocess.run(["ffmpeg", "-v", "error", "-ss", str(timestamp), "-i", str(path), "-frames:v", "1",
                                   "-vf", "scale=16:12,format=gray", "-f", "rawvideo", "-"], capture_output=True, check=True, timeout=10).stdout
        source_frame, clip_frame = frame(folder / "seg_02.mp4", 0), frame(output, 1.5)
        self.assertEqual(len(source_frame), 192)
        self.assertLess(sum(abs(a-b) for a,b in zip(source_frame,clip_frame))/192, 8)


if __name__ == "__main__":
    unittest.main()
