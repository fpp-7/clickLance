"""Crash/reenvio, transições persistentes e protocolo CL1 com fronteiras simuladas."""
import json
import sqlite3
import time
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

import requests
from botocore.exceptions import ClientError

from src.config import Config
from src.dvr_manager import DVRManager
from src.event_store import EventStore
from src.persistent_pipeline import CloudGateway, PermanentFailure, PersistentPipeline, file_hash
from src.serial_protocol import LineFramer, SerialProtocol
from test_gateway import GatewayFixture


class DurableFixture(GatewayFixture):
    def setUp(self):
        super().setUp()
        config = patch.multiple(Config, DEVICE_ID='mega01', DEVICE_TOKEN='test-token',
                                CAMERA_LATENCY_SECONDS=0, MIN_FREE_BYTES=0,
                                API_URL='http://simulator/api/v1/replays', S3_BUCKET_NAME='test-bucket')
        config.start()
        self.addCleanup(config.stop)
        self.catalog_segments()
        self.dvr._running.set()
        self.dvr.observe_content(5, 100)
        self.store = EventStore(self.dvr)

    def accepted(self):
        reply = self.store.accept('mega01', 'serial_session', 1, '1000', 100, time.time())
        self.assertTrue(reply['accepted'], reply)
        return reply['event_id']


class AcceptanceTests(DurableFixture, unittest.TestCase):
    def test_acceptance_and_reservation_commit_together_and_resend_after_restart(self):
        event_id = self.accepted()
        self.assertEqual(self.store.get(event_id)['state'], 'ACEITO')
        self.assertEqual(self.dvr.catalog.reservation(event_id)['click'], 5)
        restarted_dvr = DVRManager()
        self.addCleanup(restarted_dvr.catalog.close)
        restarted_store = EventStore(restarted_dvr)
        protocol = SerialProtocol(restarted_store)  # Sem sessão nem sincronização após reboot.
        reply = protocol.handle('CL1|TRIGGER|mega01|serial_session|1|1000')
        self.assertEqual(reply, f'CL1|ACCEPTED|mega01|serial_session|1|{event_id}')
        self.assertEqual(restarted_store.db.execute('SELECT COUNT(*) FROM events').fetchone()[0], 1)

    def test_crash_before_event_insert_rolls_back_reservation_and_no_ack(self):
        with self.store.lock, self.store.db:
            self.store.db.execute("""CREATE TRIGGER injected_failure BEFORE INSERT ON events
                BEGIN SELECT RAISE(ABORT,'injected'); END""")
        with self.assertRaises(sqlite3.IntegrityError):
            self.accepted()
        self.assertEqual(self.store.db.execute('SELECT COUNT(*) FROM replay_reservations').fetchone()[0], 0)
        self.assertEqual(self.store.db.execute('SELECT COUNT(*) FROM serial_results').fetchone()[0], 0)

    def test_same_identity_different_payload_conflicts(self):
        self.accepted()
        result = self.store.accept('mega01', 'serial_session', 1, '1001', 101, time.time())
        self.assertEqual(result['reason'], 'IDENTITY_CONFLICT')

    def test_capacity_rejection_is_stable_and_preserves_existing_sources(self):
        event_id = self.accepted()
        with patch.object(Config, 'MAX_PENDING_EVENTS', 1):
            result = self.store.accept('mega01', 'serial_session', 2, '2000', 100, time.time())
        self.assertEqual(result['reason'], 'CAPACITY')
        self.dvr.catalog.prune(100000)
        self.assertTrue(self.dvr.catalog.select_reserved(event_id)[1])
        self.assertEqual(self.store.existing('mega01', 'serial_session', 2, '2000'), result)

    def test_no_disk_capacity_never_confirms_success(self):
        with patch('src.event_store.shutil.disk_usage', return_value=Mock(free=0)):
            reply = self.store.accept('mega01', 's', 1, '1', 100, time.time())
        self.assertFalse(reply['accepted'])
        self.assertEqual(reply['reason'], 'CAPACITY')

    def test_protocol_preserves_original_click_and_wraps_millis(self):
        protocol = SerialProtocol(self.store)
        welcome = protocol.handle('CL1|HELLO|mega01', mono=99, utc=1000)
        session = welcome.split('|')[-1]
        protocol.handle(f'CL1|SYNC|mega01|{session}|4294967246', mono=99, utc=1000)
        reply = protocol.handle(f'CL1|TRIGGER|mega01|{session}|1|950', mono=100, utc=99999)
        event_id = reply.split('|')[-1]
        event = self.store.get(event_id)
        self.assertEqual(event['timestamp'], '1970-01-01T00:16:41+00:00')
        duplicate = protocol.handle(f'CL1|TRIGGER|mega01|{session}|1|950', mono=1000, utc=0)
        self.assertEqual(reply, duplicate)

    def test_malformed_protocol_never_creates_event(self):
        protocol = SerialProtocol(self.store)
        for line in ('CL2|HELLO|mega01', 'CL1|TRIGGER|mega01|s|-1|2',
                     'CL1|TRIGGER|mega01|s|1|4294967296', 'CL1|HELLO|wrong'):
            self.assertIsNone(protocol.handle(line))
        self.assertEqual(self.store.db.execute('SELECT COUNT(*) FROM events').fetchone()[0], 0)


class PipelineTests(DurableFixture, unittest.TestCase):
    def pipeline(self):
        cloud = Mock()
        self.record_selection()  # renderer real na suíte de conteúdo; aqui isola transições de rede.
        return PersistentPipeline(self.store, self.worker, cloud), cloud

    def test_restart_at_each_stage_resumes_without_regenerating_or_uploading_twice(self):
        event_id = self.accepted()
        pipeline, cloud = self.pipeline()
        pipeline.step(event_id)
        event = self.store.get(event_id)
        self.assertEqual(event['state'], 'CLIPE_PRONTO')
        self.assertTrue(Path(event['clip']).exists())
        with self.assertRaises(ValueError):
            self.dvr.catalog.reservation(event_id)
        pipeline = PersistentPipeline(EventStore(self.dvr), self.worker, cloud)
        pipeline.step(event_id)
        self.assertEqual(self.store.get(event_id)['state'], 'UPLOAD_CONFIRMADO')
        pipeline = PersistentPipeline(EventStore(self.dvr), self.worker, cloud)
        pipeline.step(event_id)
        self.assertEqual(self.store.get(event_id)['state'], 'REGISTRO_CONFIRMADO')
        self.assertFalse(Path(event['clip']).exists())
        cloud.upload.assert_called_once()
        cloud.register.assert_called_once()

    def test_network_failure_keeps_stage_clip_and_backoff(self):
        event_id = self.accepted()
        pipeline, cloud = self.pipeline()
        pipeline.step(event_id)
        cloud.upload.side_effect = requests.Timeout()
        pipeline.run_once()
        event = self.store.get(event_id)
        self.assertEqual(event['state'], 'CLIPE_PRONTO')
        self.assertEqual(event['attempts'], 1)
        self.assertGreater(event['next_attempt'], time.time())
        self.assertTrue(Path(event['clip']).exists())

    def test_permanent_auth_error_enters_intervention(self):
        event_id = self.accepted()
        pipeline, cloud = self.pipeline()
        pipeline.step(event_id)
        pipeline.step(event_id)
        cloud.register.side_effect = PermanentFailure('HTTP_401')
        pipeline.run_once()
        event = self.store.get(event_id)
        self.assertEqual(event['state'], 'INTERVENCAO')
        self.assertEqual(event['error'], 'HTTP_401')
        self.assertTrue(Path(event['clip']).exists())

    def test_http_contract_private_object_and_stable_id(self):
        event_id = self.accepted()
        pipeline, _ = self.pipeline()
        pipeline.step(event_id)
        event = self.store.get(event_id)
        http = Mock()
        http.post.return_value.status_code = 201
        http.post.return_value.json.return_value = {'evento_id': event_id, 'sha256': event['sha256']}
        CloudGateway(http=http).register(event)
        arguments = http.post.call_args.kwargs
        self.assertEqual(arguments['headers']['X-Device-Token'], 'test-token')
        self.assertEqual(arguments['json']['evento_id'], event_id)
        self.assertNotIn('video_url', arguments['json'])

    def test_uncertain_s3_upload_is_reconciled_with_checksum(self):
        event_id = self.accepted()
        pipeline, _ = self.pipeline()
        pipeline.step(event_id)
        event = self.store.get(event_id)
        s3 = Mock()
        s3.put_object.side_effect = ClientError({'Error': {'Code': 'PreconditionFailed'},
                                                'ResponseMetadata': {'HTTPStatusCode': 412}}, 'PutObject')
        s3.head_object.return_value = {'Metadata': {'sha256': event['sha256']}, 'ContentLength': 4}
        cloud = CloudGateway(s3=s3)
        cloud.upload(event)
        self.assertEqual(s3.put_object.call_args.kwargs['IfNoneMatch'], '*')
        s3.head_object.return_value['Metadata']['sha256'] = 'divergent'
        with self.assertRaisesRegex(PermanentFailure, 'REMOTE_CONTENT_CONFLICT'):
            cloud.upload(event)


class FramerTests(unittest.TestCase):
    def test_partial_oversized_invalid_ascii_and_timeout_recover_at_lf(self):
        framer = LineFramer()
        self.assertEqual(framer.feed(b'CL1|HEL', now=0), [])
        self.assertEqual(framer.feed(b'LO|mega01\r\n', now=0.2), ['CL1|HELLO|mega01'])
        self.assertEqual(framer.feed(b'x'*192 + b'\nvalid\n', now=0.3), ['valid'])
        self.assertEqual(framer.feed(b'partial', now=1), [])
        self.assertEqual(framer.feed(b'late\nvalid\n', now=3), ['valid'])
        self.assertEqual(framer.feed(b'\xff\n', now=4), [])


if __name__ == '__main__':
    unittest.main()
