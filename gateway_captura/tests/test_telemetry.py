import threading
import unittest
from unittest.mock import Mock, patch
from src.config import Config
from src.telemetry import payload, send_once, run


class TelemetryTests(unittest.TestCase):
    def report(self):
        return dict(generated_utc=1793376000.123456, version='0.3.0', state='UNAVAILABLE', pending=3,
                    oldest_pending_age_seconds=10.1, capture_progress_age_seconds=None,
                    last_capture_errors=['rtsp://secret:password@camera'], reason='private details')

    @patch('src.telemetry.health_report')
    def test_whitelist_and_failed_network_preserve_pipeline(self, report):
        report.return_value = self.report()
        store = Mock()
        data = payload(store)
        self.assertEqual(set(data), {'schema_version', 'observed_at', 'gateway_version', 'capture_state',
                                    'pending_events', 'oldest_pending_age_seconds', 'capture_progress_age_seconds'})
        self.assertNotIn('secret', str(data))
        self.assertEqual(data['oldest_pending_age_seconds'], 11)
        http = Mock()
        http.post.side_effect = ConnectionError('fixture offline')
        with patch.object(Config, 'HEALTH_URL', 'http://127.0.0.1/health'):
            with self.assertRaises(ConnectionError):
                send_once(store, http)
        store.accept.assert_not_called()
        store.db.execute.assert_not_called()
        self.assertEqual(http.post.call_args.kwargs['timeout'], (3, 5))
        self.assertFalse(http.post.call_args.kwargs['allow_redirects'])

    def test_disabled_health_has_no_network_or_snapshot(self):
        with patch.object(Config, 'HEALTH_URL', ''), patch('src.telemetry.health_report') as report:
            http = Mock()
            send_once(Mock(), http)
            http.post.assert_not_called()
            report.assert_not_called()

    def test_background_failure_is_contained_and_shutdown_is_interruptible(self):
        stop = threading.Event()
        def failure(*args):
            stop.set()
            raise TimeoutError('fixture')
        with patch('src.telemetry.send_once', side_effect=failure) as send:
            run(Mock(), stop)
            send.assert_called_once()
