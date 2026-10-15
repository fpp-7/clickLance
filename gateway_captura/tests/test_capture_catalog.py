"""GW-04: catálogo, entrada IP e isolamento sem câmera física."""
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, patch

from src.config import Config
from src.dvr_manager import DVRManager
from src.diagnostics import capture_sample
from src.segment_catalog import SegmentCatalog


class CatalogTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.catalog = SegmentCatalog(self.temp.name)
        self.addCleanup(self.catalog.close)

    def test_restart_keeps_sessions_separate_and_orphan_incomplete(self):
        first = self.catalog.create_session(100, 'rtsp')
        old = self.catalog.session_directory(first) / 'seg_0000.mp4'
        old.write_bytes(b'incomplete video')
        self.catalog.recover()
        second = self.catalog.create_session(200, 'rtsp')
        self.assertNotEqual(first, second)
        self.assertEqual(self.catalog.records(first)[0]['status'], 'INCOMPLETE')
        self.assertEqual(self.catalog.records(second), [])
        self.assertEqual(old.read_bytes(), b'incomplete video')

    def test_csv_and_probe_are_required_and_missing_file_is_detected(self):
        session = self.catalog.create_session(100, 'rtsp')
        folder = self.catalog.session_directory(session)
        clip = folder / 'seg_0000.mp4'
        clip.write_bytes(b'fixture')
        self.catalog.reconcile(session)
        self.assertEqual(self.catalog.records(session)[0]['status'], 'WRITING')
        (folder / 'segments.csv').write_text('seg_0000.mp4,0,2\npartial', encoding='utf-8')
        probe = SimpleNamespace(stdout=json.dumps({
            'format': {'duration': '2'}, 'streams': [{'codec_type': 'video'}],
            'packets': [{'pts_time': str(i/30), 'duration_time': str(1/30)} for i in range(60)],
        }))
        with patch('src.segment_catalog.subprocess.run', return_value=probe):
            self.catalog.reconcile(session)
        record = self.catalog.records(session)[0]
        self.assertEqual((record['start'], record['end'], record['status']), (0, 2, 'CLOSED'))
        clip.unlink()
        with patch('src.segment_catalog.subprocess.run', side_effect=OSError):
            self.catalog.reconcile(session, finished=True)
        self.assertEqual(self.catalog.records(session)[0]['status'], 'MISSING')

    def test_catalog_persists_across_connection_restart(self):
        session = self.catalog.create_session(100, 'rtsp')
        (self.catalog.session_directory(session) / 'seg_0000.mp4').write_bytes(b'orphan')
        self.catalog.reconcile(session, finished=True)
        reopened = SegmentCatalog(self.temp.name)
        try:
            self.assertEqual(reopened.records(session)[0]['status'], 'INCOMPLETE')
        finally:
            reopened.close()

    def test_retention_keeps_writing_file_and_removes_only_closed(self):
        session = self.catalog.create_session(100, 'rtsp')
        folder = self.catalog.session_directory(session)
        closed = folder / 'seg_0000.mp4'
        writing = folder / 'seg_0001.mp4'
        closed.write_bytes(b'closed')
        writing.write_bytes(b'writing')
        (folder / 'segments.csv').write_text('seg_0000.mp4,0,2\n', encoding='utf-8')
        result = SimpleNamespace(stdout=json.dumps({
            'format': {'duration': '2'}, 'streams': [{'codec_type': 'video'}],
            'packets': [{'pts_time': str(i/30), 'duration_time': str(1/30)} for i in range(60)],
        }))
        with patch('src.segment_catalog.subprocess.run', return_value=result):
            self.catalog.reconcile(session)
        self.catalog.prune(200)
        self.assertFalse(closed.exists())
        self.assertTrue(writing.exists())
        self.catalog.reconcile(session)
        statuses = {Path(r['path']).name: r['status'] for r in self.catalog.records(session)}
        self.assertEqual(statuses, {'seg_0000.mp4': 'REMOVED', 'seg_0001.mp4': 'WRITING'})

    def test_coverage_reports_gap_instead_of_using_mtime(self):
        session = self.catalog.create_session(100, 'rtsp')
        folder = self.catalog.session_directory(session)
        for i in (0, 2):
            (folder / f'seg_{i:04d}.mp4').write_bytes(b'fixture')
        (folder / 'segments.csv').write_text(
            'seg_0000.mp4,0,2\nseg_0002.mp4,4,6\n', encoding='utf-8')
        probe = SimpleNamespace(stdout=json.dumps({
            'format': {'duration': '2'}, 'streams': [{'codec_type': 'video'}],
            'packets': [{'pts_time': str(i/30), 'duration_time': str(1/30)} for i in range(60)],
        }))
        with patch('src.segment_catalog.subprocess.run', return_value=probe):
            self.catalog.reconcile(session, finished=True)
        self.assertEqual(self.catalog.coverage_gaps(session, 0, 6), [(2, 4)])
        self.assertEqual(self.catalog.coverage_gaps(session, 4, 6), [])

    def test_internal_packet_gap_is_not_hidden_by_segment_duration(self):
        session = self.catalog.create_session(100, 'rtsp')
        folder = self.catalog.session_directory(session)
        (folder / 'seg_0000.mp4').write_bytes(b'fixture')
        (folder / 'segments.csv').write_text('seg_0000.mp4,0,2\n', encoding='utf-8')
        probe = SimpleNamespace(stdout=json.dumps({
            'format': {'duration': '2'}, 'streams': [{'codec_type': 'video'}],
            'packets': [{'pts_time': '0'}, {'pts_time': '1.5'}],
        }))
        with patch('src.segment_catalog.subprocess.run', return_value=probe):
            self.catalog.reconcile(session, finished=True)
        self.assertEqual(self.catalog.records(session)[0]['status'], 'INVALID')
        self.assertEqual(self.catalog.coverage_gaps(session, 0, 2), [(0, 2)])


class CameraInputTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        config = patch.object(Config, 'SEGMENTS_DIR', self.temp.name)
        config.start()
        self.addCleanup(config.stop)
        self.dvr = DVRManager()
        self.addCleanup(self.dvr.catalog.close)

    def test_readers_and_stop_before_process_exists(self):
        self.dvr._read_progress()
        self.dvr._read_stderr()
        self.dvr.stop_recording()
        self.assertFalse(self.dvr._running.is_set())

    def test_process_without_pipes_is_terminated(self):
        process = Mock(pid=123, stdin=None, stdout=None, stderr=None)
        process.poll.return_value = None
        self.dvr._ffmpeg_process = process
        self.dvr._read_progress()
        self.dvr._read_stderr()
        self.dvr.stop_recording()
        process.terminate.assert_called_once_with()
        process.wait.assert_called_once_with(timeout=10)

    def test_stop_kills_process_when_graceful_shutdown_times_out(self):
        process = Mock(pid=123)
        process.poll.return_value = None
        process.stdin.closed = False
        process.wait.side_effect = [subprocess.TimeoutExpired('ffmpeg', 10), 0]
        self.dvr._ffmpeg_process = process
        self.dvr.stop_recording()
        process.stdin.write.assert_called_once_with(b'q\n')
        process.kill.assert_called_once_with()
        process.stdin.close.assert_called_once_with()

    def test_rtsp_transport_and_timeout_before_input(self):
        with patch.multiple(Config, CAMERA_SOURCE='rtsp',
                            CAMERA_RTSP_URL='rtsp://user:secret@127.0.0.1:8554/stream',
                            CAMERA_RTSP_TRANSPORT='tcp', CAMERA_TIMEOUT_SECONDS=10):
            args = self.dvr.build_recording_command('test.mp4')
        self.assertLess(args.index('-rtsp_transport'), args.index('-i'))
        self.assertIn('10000000', args)
        self.assertNotIn('dshow', args)
        self.assertIn('-an', args)

    def test_invalid_rtsp_does_not_echo_secret(self):
        with patch.multiple(Config, CAMERA_SOURCE='rtsp', CAMERA_RTSP_URL='https://user:secret@host'):
            with self.assertRaises(ValueError) as failure:
                self.dvr.build_input_args()
        self.assertNotIn('secret', str(failure.exception))

    def test_dshow_remains_explicit_test_option(self):
        with patch.object(Config, 'CAMERA_SOURCE', 'dshow'):
            self.assertIn('dshow', self.dvr.build_input_args())

    def test_rtsp_failure_diagnostic_redacts_credentials(self):
        url = 'rtsp://operator:secret_value@127.0.0.1:8554/stream'
        result = SimpleNamespace(returncode=1, stderr=f'Input failed {url} password secret_value')
        directory = Path(self.temp.name) / 'diagnostic'
        with patch.object(Config, 'CAMERA_RTSP_URL', url), \
                patch('src.diagnostics.subprocess.run', return_value=result):
            with self.assertRaises(RuntimeError) as failure:
                capture_sample(self.dvr, directory, synthetic=True)
        self.assertNotIn('secret_value', str(failure.exception))
        self.assertNotIn('secret_value', (directory / 'ffmpeg.log').read_text(encoding='utf-8'))

    def test_capture_timeout_does_not_echo_url(self):
        error = subprocess.TimeoutExpired(['ffmpeg', '-i', 'rtsp://user:secret@host'], 30)
        with patch('src.diagnostics.subprocess.run', side_effect=error):
            with self.assertRaises(RuntimeError) as failure:
                capture_sample(self.dvr, Path(self.temp.name) / 'timeout', synthetic=True)
        self.assertNotIn('secret', str(failure.exception))

    def test_active_session_never_selects_legacy_flat_files(self):
        (Path(self.temp.name) / 'seg_9999.mp4').write_bytes(b'legacy')
        self.dvr.session_id = self.dvr.catalog.create_session(100, 'rtsp')
        self.assertEqual(self.dvr.get_recent_segments(2), [])

    @unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'), 'Requer FFmpeg e ffprobe')
    def test_real_ffmpeg_manifest_catalogues_all_closed_segments(self):
        catalog = self.dvr.catalog
        session = catalog.create_session(100, 'synthetic')
        directory = catalog.session_directory(session)
        self.dvr.segment_duration = 2
        command = self.dvr.build_recording_command(
            str(directory / 'seg_%04d.mp4'),
            input_args=['-f', 'lavfi', '-i', 'testsrc2=size=160x120:rate=30'],
            duration=6, manifest_path=str(directory / 'segments.csv'),
        )
        subprocess.run(command, capture_output=True, timeout=30, check=True)
        catalog.reconcile(session, finished=True)
        records = catalog.records(session)
        self.assertEqual(len(records), 3)
        self.assertEqual([r['status'] for r in records], ['CLOSED'] * 3)
        self.assertAlmostEqual(records[0]['start'], 0, delta=0.04)
        self.assertAlmostEqual(records[-1]['end'], 6, delta=0.04)
        self.dvr.session_id = session
        self.assertEqual(len(self.dvr.get_recent_segments(3)), 3)

    @unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'), 'Requer FFmpeg e ffprobe')
    def test_recording_lifecycle_creates_session_and_final_catalog(self):
        original = self.dvr.build_recording_command
        self.dvr.segment_duration = 2
        def finite(*args, **kwargs):
            return original(*args, **kwargs, duration=6)
        source = ['-f', 'lavfi', '-i', 'testsrc2=size=160x120:rate=30']
        with patch.object(self.dvr, 'build_input_args', return_value=source), \
                patch.object(self.dvr, 'build_recording_command', side_effect=finite):
            self.dvr.start_recording()
        records = self.dvr.catalog.records(self.dvr.session_id)
        self.assertEqual(len(records), 3)
        self.assertTrue(all(r['status'] == 'CLOSED' for r in records))
        self.assertFalse(self.dvr._running.is_set())
        self.assertGreaterEqual(self.dvr._last_content_pts, 5)


if __name__ == '__main__':
    unittest.main()
