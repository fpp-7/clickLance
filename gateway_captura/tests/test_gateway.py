"""GW-03: regressões de produção sem câmera, Arduino ou serviços cloud."""
import os
import json
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
            REPLAY_WAIT_SECONDS=0, REPLAY_BEFORE_SECONDS=2,
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
        def render(request, records, output):
            selected.extend(r['path'] for r in records)
            Path(output).write_bytes(b'clip')
        self.worker._render_replay = render
        return selected

    def catalog_segments(self, count=3):
        if self.dvr.session_id is None:
            self.dvr.session_id = self.dvr.catalog.create_session(100, 'synthetic')
        directory = self.dvr.catalog.session_directory(self.dvr.session_id)
        for i in range(count):
            (directory / f'seg_{i:04d}.mp4').write_bytes(b'fixture')
        (directory / 'segments.csv').write_text(''.join(
            f'seg_{i:04d}.mp4,{2*i},{2*i+2}\n' for i in range(count)), encoding='utf-8')
        probe = SimpleNamespace(stdout=json.dumps({
            'format': {'duration': '2'}, 'streams': [{'codec_type': 'video'}],
        }))
        with patch('src.segment_catalog.subprocess.run', return_value=probe):
            self.dvr.catalog.reconcile(self.dvr.session_id)
        return directory

    def request(self, click=5):
        return self.dvr.catalog.reserve(self.dvr.session_id, click, 2, 0)


class GatewayTests(GatewayFixture, unittest.TestCase):
    def test_pipeline_calls_storage_and_api_without_network(self):
        self.catalog_segments()
        request = self.request()
        selected = self.record_selection()
        with patch('src.video_processor.requests.post') as post:
            post.return_value.status_code = 201
            self.worker._process_event(100, 'success', request)
        self.assertEqual(len(selected), 2)
        self.storage.upload_file.assert_called_once()
        self.assertEqual(post.call_args.kwargs['json']['timestamp'],
                         datetime.fromtimestamp(100).isoformat())
        post.return_value.raise_for_status.assert_called_once()

    def test_gw05_click_in_current_segment_must_be_preserved(self):
        directory = self.catalog_segments(2)
        current = str(directory / 'seg_0002.mp4')
        Path(current).write_bytes(b'writing')
        request = self.request()
        self.dvr.catalog.reconcile(self.dvr.session_id)
        self.assertEqual(next(r['status'] for r in self.dvr.catalog.records(self.dvr.session_id)
                              if r['path'] == current), 'WRITING')
        self.catalog_segments(3)  # O fechamento acontece depois do clique.
        selected = self.record_selection()
        self.worker._upload_to_s3 = Mock(return_value='fake://clip')
        self.worker._notify_backend = Mock()
        self.worker._process_event(103, 'click', request)
        self.assertIn(current, selected, 'A jogada está no segmento ainda aberto')

    def test_gw05_queue_delay_must_not_change_sources(self):
        self.catalog_segments(3)
        request = self.request()
        _, baseline = self.dvr.catalog.select_reserved(request['id'])
        selected = self.record_selection()
        self.worker._upload_to_s3 = Mock(return_value='fake://clip')
        self.worker._notify_backend = Mock()
        self.catalog_segments(6)
        self.dvr.catalog.prune(10000)  # Limpeza concorrente não apaga a reserva.
        self.worker._process_event(102, 'delayed', request)
        self.assertEqual(selected, [r['path'] for r in baseline])

    def test_gw04_restarted_session_must_not_mix_old_sources(self):
        old = self.segment(10, 100, b'old session')
        self.segment(0, 200, b'new session')
        self.segment(1, 201, b'new current')
        restarted = DVRManager()
        self.addCleanup(restarted.catalog.close)
        restarted.session_id = restarted.catalog.create_session(200, 'rtsp')
        self.assertNotIn(old, restarted.get_recent_segments(2))

    def test_gw07_failed_upload_must_preserve_clip(self):
        self.catalog_segments()
        request = self.request()
        self.record_selection()
        self.storage.upload_file.side_effect = RuntimeError('simulated offline storage')
        with self.assertRaises(RuntimeError):
            self.worker._process_event(100, 'offline', request)
        self.assertTrue((Path(self.worker.output_dir) / f"lance_{request['id']}.mp4").exists())

    def test_without_original_context_never_uses_recent_segments(self):
        with self.assertRaisesRegex(ValueError, 'REPLAY_CONTEXT_REQUIRED'):
            self.worker._process_event(100, 'legacy')
        self.storage.upload_file.assert_not_called()

    def test_failed_generation_preserves_reservation_and_does_not_upload(self):
        self.catalog_segments()
        request = self.request()
        self.worker._render_replay = Mock(side_effect=RuntimeError('encoder failure'))
        with self.assertRaises(RuntimeError):
            self.worker._process_event(100, 'failure', request)
        self.assertEqual(self.dvr.catalog.reservation(request['id']), request)
        self.storage.upload_file.assert_not_called()

    def test_missing_coverage_times_out_without_upload(self):
        self.catalog_segments(2)
        request = self.request()
        with self.assertRaisesRegex(ValueError, 'VIDEO_COVERAGE_TIMEOUT'):
            self.worker._process_event(100, 'gap', request)
        self.storage.upload_file.assert_not_called()

    def test_backend_failure_preserves_generated_clip(self):
        self.catalog_segments()
        request = self.request()
        self.record_selection()
        self.worker._upload_to_s3 = Mock(return_value='fake://uploaded')
        self.worker._notify_backend = Mock(side_effect=RuntimeError('API offline'))
        with self.assertRaises(RuntimeError):
            self.worker._process_event(100, 'api_failure', request)
        self.assertTrue((Path(self.worker.output_dir) / f"lance_{request['id']}.mp4").exists())

    def test_invalid_clip_duration_keeps_sources_and_never_uploads(self):
        self.catalog_segments()
        request = self.request()
        self.worker._concat = Mock()
        with patch('src.video_processor.validate_video', return_value=0.5):
            with self.assertRaisesRegex(ValueError, 'INVALID_REPLAY_DURATION'):
                self.worker._process_event(100, 'short', request)
        self.assertEqual(self.dvr.catalog.reservation(request['id']), request)
        self.storage.upload_file.assert_not_called()

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
        dvr = Mock()
        dvr.snapshot_replay.return_value = {'id': 'simulated-reservation'}
        listener = SerialListener(queue, dvr)
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
        event = queue.get_nowait()
        self.assertEqual(event['timestamp'], 1000)
        self.assertEqual(event['replay'], {'id': 'simulated-reservation'})
        dvr.snapshot_replay.assert_called_once()
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
