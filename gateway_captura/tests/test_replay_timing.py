"""GW-05: janelas, relógios, reservas e conteúdo decodificado, sem câmera."""
import shutil
import subprocess
import threading
import time
import unittest
from pathlib import Path
from unittest.mock import patch
from unittest.mock import Mock

from src.config import Config
from src.diagnostics import validate_video
from src.event_store import EventStore
from src.persistent_pipeline import PersistentPipeline
from test_gateway import GatewayFixture


class ReplayClockTests(GatewayFixture, unittest.TestCase):
    def setUp(self):
        super().setUp()
        self.catalog_segments()
        self.dvr._running.set()
        self.dvr.observe_content(5, 100)

    def test_utc_adjustment_does_not_change_content_time(self):
        with patch.multiple(Config, CAMERA_LATENCY_SECONDS=0.2), \
                patch('time.time', return_value=-99999):
            request = self.dvr.snapshot_replay(100.5)
        self.assertAlmostEqual(request['click'], 5.7)
        self.assertAlmostEqual(request['start'], 3.6)

    def test_rtsp_without_calibration_is_rejected(self):
        with patch.multiple(Config, CAMERA_SOURCE='rtsp', CAMERA_LATENCY_SECONDS=None):
            with self.assertRaisesRegex(ValueError, 'CAMERA_TIME_NOT_CALIBRATED'):
                self.dvr.snapshot_replay(101)

    def test_stalled_capture_is_rejected(self):
        with patch.object(Config, 'CAMERA_LATENCY_SECONDS', 0):
            with self.assertRaisesRegex(ValueError, 'CAMERA_UNAVAILABLE'):
                self.dvr.snapshot_replay(106)

    def test_reservation_survives_catalog_restart_and_protects_sources(self):
        request = self.request()
        from src.segment_catalog import SegmentCatalog
        reopened = SegmentCatalog(self.dvr.segments_dir)
        try:
            reopened.prune(10000)
            restored, records = reopened.select_reserved(request['id'])
            self.assertEqual(restored, request)
            self.assertEqual(len(records), 2)
            reopened.release(request['id'])
            reopened.prune(10000)
            self.assertFalse(any(Path(r['path']).exists() for r in records))
        finally:
            reopened.close()

    def test_wait_includes_segment_closed_after_processing_starts(self):
        request = self.dvr.catalog.reserve(self.dvr.session_id, 7, 2, 0)
        def finish():
            time.sleep(0.1)
            self.catalog_segments(4)
        closer = threading.Thread(target=finish)
        closer.start()
        try:
            with patch.object(Config, 'REPLAY_WAIT_SECONDS', 2):
                _, records = self.dvr.wait_replay(request['id'])
            self.assertEqual(Path(records[-1]['path']).name, 'seg_0003.mp4')
        finally:
            closer.join(timeout=3)

    def test_encoder_timeout_cleans_exclusive_concat_file(self):
        request = self.request()
        _, records = self.dvr.catalog.select_reserved(request['id'])
        with patch.object(self.worker, '_execute', side_effect=subprocess.TimeoutExpired('ffmpeg', 1)):
            with self.assertRaises(subprocess.TimeoutExpired):
                self.worker._render_replay(request, records, str(self.root / 'timeout.mp4'))
        self.assertEqual(list(Path(self.worker.output_dir).glob('*.ffconcat')), [])
        self.assertEqual(self.dvr.catalog.reservation(request['id']), request)


@unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'), 'Requer FFmpeg e ffprobe')
class ReplayContentTests(GatewayFixture, unittest.TestCase):
    def test_full_120_second_window_through_durable_pipeline(self):
        catalog = self.dvr.catalog
        session = catalog.create_session(time.time(), 'synthetic')
        self.dvr.session_id = session
        directory = catalog.session_directory(session)
        self.dvr.segment_duration = 2
        command = self.dvr.build_recording_command(
            str(directory / 'seg_%04d.mp4'),
            input_args=['-f', 'lavfi', '-i', 'testsrc2=size=160x120:rate=30'],
            duration=124, manifest_path=str(directory / 'segments.csv'),
        )
        subprocess.run(command, capture_output=True, timeout=60, check=True)
        catalog.reconcile(session, finished=True)
        self.dvr._running.set()
        self.dvr.observe_content(123, time.monotonic())
        with patch.multiple(Config, REPLAY_BEFORE_SECONDS=120, REPLAY_AFTER_SECONDS=0,
                            CAMERA_LATENCY_SECONDS=0, MIN_FREE_BYTES=0, S3_BUCKET_NAME='simulated', DEVICE_TOKEN='simulated'):
            store = EventStore(self.dvr)
            accepted = store.accept(Config.DEVICE_ID, 'full_window_test', 1, '123000',
                                    time.monotonic(), time.time())
            self.assertTrue(accepted['accepted'], accepted)
            cloud = Mock()
            cloud.upload.side_effect = lambda event: self.assertAlmostEqual(
                validate_video(Path(event['clip'])), 120.2, delta=0.05)
            pipeline = PersistentPipeline(store, self.worker, cloud)
            for _ in range(3):
                pipeline.step(accepted['event_id'])
            self.assertEqual(store.get(accepted['event_id'])['state'], 'REGISTRO_CONFIRMADO')
            cloud.upload.assert_called_once()
            cloud.register.assert_called_once()

    def test_clips_at_segment_start_middle_end_preserve_marker(self):
        catalog = self.dvr.catalog
        session = catalog.create_session(100, 'synthetic')
        self.dvr.session_id = session
        directory = catalog.session_directory(session)
        self.dvr.segment_duration = 2
        # Marcadores grandes permitem verificar o conteúdo por pixels, sem OCR.
        source = (
            'color=red:size=160x120:rate=30,'
            "drawbox=x=0:y=0:w=iw:h=ih:color=green:t=fill:enable='gte(t,2)',"
            "drawbox=x=0:y=0:w=iw:h=ih:color=blue:t=fill:enable='gte(t,4)'"
        )
        command = self.dvr.build_recording_command(
            str(directory / 'seg_%04d.mp4'), input_args=['-f', 'lavfi', '-i', source],
            duration=6, manifest_path=str(directory / 'segments.csv'),
        )
        subprocess.run(command, capture_output=True, timeout=30, check=True)
        catalog.reconcile(session, finished=True)
        for click in (2.2, 3.0, 3.8):
            with self.subTest(click=click):
                request = catalog.reserve(session, click, 1, 0.4)
                _, records = self.dvr.wait_replay(request['id'])
                output = self.root / f'click_{click}.mp4'
                self.worker._render_replay(request, records, str(output))
                self.assertAlmostEqual(validate_video(output), 1.6, delta=0.05)
                at_click = click - request['start']
                pixel = subprocess.run(
                    ['ffmpeg', '-v', 'error', '-i', str(output), '-ss', str(at_click),
                     '-frames:v', '1', '-vf', 'scale=1:1', '-pix_fmt', 'rgb24', '-f', 'rawvideo', '-'],
                    capture_output=True, timeout=15, check=True,
                ).stdout
                self.assertEqual(len(pixel), 3)
                self.assertGreater(pixel[1], pixel[0] + 50)
                self.assertGreater(pixel[1], pixel[2] + 50)
                # Confere também as bordas para não passar com um clipe verde deslocado.
                for timestamp, channel in ((0, 0 if click < 3.1 else 1),
                                           (1.5, 2 if click > 3.5 else 1)):
                    edge = subprocess.run(
                        ['ffmpeg', '-v', 'error', '-i', str(output), '-ss', str(timestamp),
                         '-frames:v', '1', '-vf', 'scale=1:1', '-pix_fmt', 'rgb24', '-f', 'rawvideo', '-'],
                        capture_output=True, timeout=15, check=True,
                    ).stdout
                    self.assertEqual(len(edge), 3)
                    self.assertGreater(edge[channel], max(edge[i] for i in range(3) if i != channel) + 50)
                catalog.release(request['id'])


if __name__ == '__main__':
    unittest.main()
