import json
import logging
import os
import sqlite3
import subprocess
import sys
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

import requests

from src.config import Config
from src.logger import SanitizingFormatter
from src.operations import InstanceLock, health_report
from src.remote_config import RemoteConfig
from test_durable_pipeline import DurableFixture


class ConfigTests(DurableFixture, unittest.TestCase):
    def setUp(self):
        super().setUp()
        snapshot = patch.multiple(Config, **{name: getattr(Config, name) for name in dir(Config) if name.isupper()})
        snapshot.start()
        self.addCleanup(snapshot.stop)
        config = patch.multiple(Config, CAMERA_RTSP_URL='rtsp://127.0.0.1/test',
                                CONFIG_URL='http://simulator/config', CONFIG_VERSION='test',
                                MIN_FREE_BYTES=1024**2)
        config.start()
        self.addCleanup(config.stop)
        self.remote = RemoteConfig(self.store, http=Mock())

    def test_invalid_response_does_not_replace_valid_configuration(self):
        self.remote.apply({'version': '1', 'active': True, 'settings': {}})
        before = self.remote.cached()
        with self.assertRaises(ValueError):
            self.remote.apply({'version': '2', 'active': True, 'settings': {'REPLAY_BEFORE_SECONDS': -1}})
        self.assertEqual(self.remote.cached(), before)

    def test_offline_restart_uses_cache_and_first_boot_without_cache_fails(self):
        self.remote.http.get.side_effect = requests.ConnectionError()
        with self.assertRaisesRegex(RuntimeError, 'sem configuração válida'):
            self.remote.initialize()
        self.remote.apply({'version': '1', 'active': True, 'settings': {'REPLAY_BEFORE_SECONDS': 3}})
        with patch.object(Config, 'REPLAY_BEFORE_SECONDS', 120):
            self.remote.initialize()
            self.assertEqual(Config.REPLAY_BEFORE_SECONDS, 3)

    def test_capture_change_invalidates_readiness_but_not_pending_request(self):
        event_id = self.accepted()
        original = self.store.get(event_id)['request']
        self.remote.apply({'version': '2', 'active': True, 'settings': {'SEGMENT_DURATION_SECONDS': 5}})
        self.assertFalse(self.dvr._running.is_set())
        self.assertEqual(self.dvr.segment_duration, 5)
        self.assertEqual(self.store.get(event_id)['request'], original)
        self.assertEqual(self.dvr.catalog.reservation(event_id)['click'], 5)

    def test_revocation_is_persisted_and_survives_offline_restart(self):
        self.remote.http.get.return_value.status_code = 403
        self.remote.refresh()
        self.assertFalse(self.store.enabled)
        self.assertFalse(self.remote.cached()['active'])
        self.remote.http.get.side_effect = requests.ConnectionError()
        self.remote.initialize()
        self.assertFalse(self.store.enabled)


class OperationsTests(DurableFixture, unittest.TestCase):
    @unittest.skipUnless(os.name == 'nt', 'Mutex Windows')
    def test_second_instance_is_rejected_and_lock_is_reusable(self):
        name = 'test:' + str(self.root)
        with InstanceLock(name):
            with self.assertRaises(RuntimeError):
                with InstanceLock(name):
                    self.fail('Segunda instância permitida')
        with InstanceLock(name):
            pass

    def test_health_has_backlog_and_no_tokens(self):
        self.accepted()
        report = health_report(self.store)
        self.assertEqual(report['pending'], 1)
        self.assertGreaterEqual(report['free_bytes'], 0)
        self.assertNotIn(Config.DEVICE_TOKEN, json.dumps(report))

    def test_retry_returns_to_last_failed_stage(self):
        event_id = self.accepted()
        self.store.fail(event_id, ValueError('TEST_FAILURE'), permanent=True)
        self.assertEqual(self.store.get(event_id)['state'], 'INTERVENCAO')
        self.store.retry(event_id)
        self.assertEqual(self.store.get(event_id)['state'], 'ACEITO')
        self.assertEqual(self.store.get(event_id)['attempts'], 0)

    def test_log_formatter_redacts_traceback_url_and_token(self):
        try:
            raise ValueError('rtsp://user:password@host/path ' + Config.DEVICE_TOKEN)
        except ValueError:
            record = logging.LogRecord('test', logging.ERROR, __file__, 1, 'error', (), sys.exc_info())
        message = SanitizingFormatter('%(message)s').format(record)
        self.assertNotIn('password', message)
        self.assertNotIn(Config.DEVICE_TOKEN, message)

    def test_real_process_crash_before_and_after_commit(self):
        # SQLite é encerrado abruptamente sem close(), incluindo no meio da transação.
        session = self.dvr.session_id
        child = r'''
import os, sys, time
from src.config import Config
Config.SEGMENTS_DIR, Config.OUTPUT_DIR = sys.argv[1:3]
Config.MIN_FREE_BYTES = 0
Config.REPLAY_BEFORE_SECONDS = 2
from src.dvr_manager import DVRManager
from src.event_store import EventStore
dvr = DVRManager()
store = EventStore(dvr)
dvr.snapshot_replay = lambda click, commit=True: dvr.catalog.reserve(sys.argv[3], 5, 2, 0, commit=commit)
if sys.argv[4] == 'before':
    store.db.create_function('crash_now', 0, lambda: os._exit(0))
    store.db.executescript("CREATE TRIGGER crash BEFORE INSERT ON events BEGIN SELECT crash_now(); END;")
store.accept('mega01', 'crash_session', 1, '1000', 100, time.time())
os._exit(0)
'''
        command = [sys.executable, '-c', child, self.dvr.segments_dir,
                   self.worker.output_dir, session]
        subprocess.run(command + ['before'], timeout=15, check=True, capture_output=True)
        self.assertEqual(self.store.db.execute('SELECT COUNT(*) FROM events').fetchone()[0], 0)
        self.assertEqual(self.store.db.execute('SELECT COUNT(*) FROM replay_reservations').fetchone()[0], 0)
        with self.store.lock, self.store.db:
            self.store.db.execute('DROP TRIGGER crash')
        subprocess.run(command + ['after'], timeout=15, check=True, capture_output=True)
        event = self.store.db.execute('SELECT * FROM events').fetchone()
        self.assertEqual(event['state'], 'ACEITO')
        self.assertEqual(self.dvr.catalog.reservation(event['id'])['click'], 5)


if __name__ == '__main__':
    unittest.main()
