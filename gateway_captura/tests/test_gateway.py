"""GW-03: regressões de produção sem câmera, Arduino ou serviços cloud."""
import os
import shutil
import subprocess
import tempfile
import unittest
from datetime import datetime
from pathlib import Path
from queue import Queue
from types import SimpleNamespace
from unittest.mock import Mock, patch

from src.config import Config
from src.diagnostics import capture_sample, validate_video
from src.dvr_manager import DVRManager
from src.serial_listener import SerialListener, TRIGGER_MESSAGE
from src.video_processor import VideoProcessor


class GatewayFixture:
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.config = patch.multiple(
            Config, SEGMENTS_DIR=str(self.root / 'segments'), OUTPUT_DIR=str(self.root / 'clips'),
        )
        self.config.start()
        self.addCleanup(self.config.stop)
        self.dvr = DVRManager()
        self.addCleanup(self.dvr.catalog.close)
        self.storage = Mock()
        self.worker = VideoProcessor(Queue(), self.dvr, s3_client=self.storage)

    def segment(self, index, timestamp, content=b'video'):
        path = Path(self.dvr.segments_dir, f'seg_{index:04d}.mp4')
        path.write_bytes(content)
        os.utime(path, (timestamp, timestamp))
        return str(path)

    def record_selection(self):
        selected = []
        def concatenate(paths, output):
            selected.extend(paths)
            Path(output).write_bytes(b'clip')
        self.worker._concatenate_segments = concatenate
        return selected


class GatewayTests(GatewayFixture, unittest.TestCase):
    def test_pipeline_calls_storage_and_api_without_network(self):
        self.segment(0, 100)
        self.segment(1, 101)
        selected = self.record_selection()
        with patch('src.video_processor.requests.post') as post:
            self.worker._process_event(100, 'success')
        self.assertEqual(len(selected), 1)
        self.storage.upload_file.assert_called_once()
        self.assertEqual(post.call_args.kwargs['json']['timestamp'],
                         datetime.fromtimestamp(100).isoformat())
        post.return_value.raise_for_status.assert_called_once()

    @unittest.expectedFailure
    def test_gw05_click_in_current_segment_must_be_preserved(self):
        self.segment(0, 100)
        current = self.segment(1, 102)
        selected = self.record_selection()
        self.worker._upload_to_s3 = Mock(return_value='fake://clip')
        self.worker._notify_backend = Mock()
        self.worker._process_event(103, 'click')
        self.assertIn(current, selected, 'A jogada está no segmento ainda aberto')

    @unittest.expectedFailure
    def test_gw05_queue_delay_must_not_change_sources(self):
        for i in range(3):
            self.segment(i, 100 + i)
        selected = self.record_selection()
        self.worker._upload_to_s3 = Mock(return_value='fake://clip')
        self.worker._notify_backend = Mock()
        self.worker._process_event(102, 'before')
        original = selected.copy()
        selected.clear()
        for i in range(3, 6):
            self.segment(i, 100 + i)
        self.worker._process_event(102, 'delayed')
        self.assertEqual(selected, original, 'Mesmo evento deve usar a janela original')

    @unittest.expectedFailure
    def test_gw04_restarted_session_must_not_mix_old_sources(self):
        old = self.segment(10, 100, b'old session')
        self.segment(0, 200, b'new session')
        self.segment(1, 201, b'new current')
        restarted = DVRManager()
        self.addCleanup(restarted.catalog.close)
        self.assertNotIn(old, restarted.get_recent_segments(2))

    @unittest.expectedFailure
    def test_gw07_failed_upload_must_preserve_clip(self):
        self.segment(0, 100)
        self.segment(1, 101)
        self.record_selection()
        self.storage.upload_file.side_effect = RuntimeError('simulated offline storage')
        self.worker._process_event(100, 'offline')
        self.assertTrue((Path(self.worker.output_dir) / 'lance_offline.mp4').exists())

    def test_capture_failure_with_frame_output_is_rejected(self):
        result = SimpleNamespace(returncode=1, stderr='frame=10 fatal capture failure')
        with patch('src.diagnostics.subprocess.run', return_value=result):
            with self.assertRaisesRegex(RuntimeError, 'Captura FFmpeg falhou'):
                capture_sample(self.dvr, self.root / 'failed', synthetic=True)

    def test_old_directory_is_refused(self):
        with self.assertRaises(FileExistsError):
            capture_sample(self.dvr, Path(self.dvr.segments_dir), synthetic=True)

    def test_serial_simulated_press_and_cooldown(self):
        queue = Queue()
        listener = SerialListener(queue)
        messages = iter([b'unknown\n', (TRIGGER_MESSAGE + '\n').encode(),
                         (TRIGGER_MESSAGE + '\n').encode()])
        class FakeSerial:
            is_open = True
            in_waiting = 1
            def readline(self):
                message = next(messages)
                if message == (TRIGGER_MESSAGE + '\n').encode() and queue.qsize():
                    listener.stop()
                return message
            def close(self):
                self.is_open = False
        connection = FakeSerial()
        with patch('src.serial_listener.serial.Serial', return_value=connection), \
                patch('src.serial_listener.time.sleep'), \
                patch('src.serial_listener.time.time', return_value=1000):
            listener.start_listening()
        self.assertEqual(queue.qsize(), 1)
        self.assertEqual(queue.get_nowait()['timestamp'], 1000)
        self.assertFalse(connection.is_open)


@unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'), 'Requer FFmpeg e ffprobe no PATH')
class SyntheticVideoTests(GatewayFixture, unittest.TestCase):
    def test_synthetic_capture_concat_and_decode(self):
        self.dvr.segment_duration = 2
        segments = capture_sample(self.dvr, self.root / 'synthetic', synthetic=True)
        output = self.root / 'replay.mp4'
        self.worker._concatenate_segments([str(p) for p in segments[-2:]], str(output))
        self.assertAlmostEqual(validate_video(output), 4, delta=0.15)

    def test_corrupted_video_is_rejected(self):
        path = self.root / 'invalid.mp4'
        path.write_bytes(b'not a video')
        with self.assertRaises(subprocess.CalledProcessError):
            validate_video(path)


if __name__ == '__main__':
    unittest.main()
