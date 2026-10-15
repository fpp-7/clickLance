import json
import os
import shutil
import socket
import subprocess
import sys
import threading
import time
import unittest
from collections import deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from queue import Queue
from unittest.mock import Mock, patch

from src.dvr_manager import DVRManager
from src.event_store import EventStore
from src.persistent_pipeline import CloudGateway, PersistentPipeline
from src.serial_listener import SerialListener
from test_durable_pipeline import DurableFixture


class RecoveryIntegration(DurableFixture, unittest.TestCase):
    def test_http_timeout_after_remote_commit_retries_same_identity_once(self):
        ledger, calls = {}, []
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass
            def do_POST(self):
                payload = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
                calls.append((self.path, dict(self.headers), payload))
                ledger.setdefault(payload['evento_id'], payload)
                if len(calls) == 1:
                    self.connection.shutdown(socket.SHUT_RDWR)
                    self.close_connection = True  # Commit remoto concluído, confirmação perdida.
                    return
                response = json.dumps({'evento_id': payload['evento_id'],
                                       'sha256': payload['objeto']['sha256']}).encode()
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(response)))
                self.end_headers()
                self.wfile.write(response)
        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            from src.config import Config
            with patch.object(Config, 'API_URL', f'http://127.0.0.1:{server.server_port}/api/v1/replays'):
                event_id = self.accepted()
                self.record_selection()
                cloud = CloudGateway()
                cloud.upload = Mock()
                pipeline = PersistentPipeline(self.store, self.worker, cloud)
                pipeline.step(event_id)
                pipeline.step(event_id)
                pipeline.run_once()
                event = self.store.get(event_id)
                self.assertEqual(event['state'], 'UPLOAD_CONFIRMADO')
                self.assertTrue(Path(event['clip']).exists())
                pipeline.step(event_id)
                self.assertEqual(self.store.get(event_id)['state'], 'REGISTRO_CONFIRMADO')
                self.assertEqual(len(ledger), 1)
                self.assertEqual(len(calls), 2)
                self.assertEqual(calls[0][2], calls[1][2])
                cloud.upload.assert_called_once()
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=3)

    def test_serial_ack_observes_committed_event_and_reserved_sources(self):
        self.dvr._content_anchor = None
        self.dvr._last_content_pts = -1
        self.dvr.observe_content(5, time.monotonic())
        listener = SerialListener(Queue(), self.dvr, self.store)
        incoming = deque([b'CL1|HELLO|mega01\n'])
        replies = []
        case = self
        class Link:
            @property
            def in_waiting(self):
                return len(incoming[0]) if incoming else 0
            def __enter__(self):
                return self
            def __exit__(self, *args):
                pass
            def read(self, count):
                return incoming.popleft() if incoming else b''
            def write(self, message):
                reply = message.decode().strip().split('|')
                replies.append(reply)
                if reply[1] == 'WELCOME':
                    incoming.append(f'CL1|SYNC|mega01|{reply[3]}|1000\n'.encode())
                elif reply[1] == 'SYNCED':
                    incoming.append(f'CL1|TRIGGER|mega01|{reply[3]}|1|1000\n'.encode())
                elif reply[1] == 'ACCEPTED':
                    case.assertFalse(case.store.db.in_transaction)
                    case.assertEqual(case.store.get(reply[5])['state'], 'ACEITO')
                    case.assertEqual(case.dvr.catalog.reservation(reply[5])['id'], reply[5])
                    listener.stop()
        with patch('src.serial_listener.serial.Serial', return_value=Link()):
            listener.start_listening()
        self.assertTrue(any(reply[1] == 'ACCEPTED' for reply in replies))
        self.assertEqual(self.store.db.execute('SELECT COUNT(*) FROM events').fetchone()[0], 1)

    @unittest.skipUnless(os.name == 'nt', 'Backup operacional com mutex Windows')
    def test_cli_backup_and_restore_same_paths_preserves_accepted_event(self):
        event_id = self.accepted()
        destination = self.root / 'backup'
        environment = dict(os.environ, SEGMENTS_DIR=self.dvr.segments_dir,
                           OUTPUT_DIR=self.worker.output_dir)
        subprocess.run([sys.executable, 'gatewayctl.py', 'backup', str(destination)],
                       env=environment, capture_output=True, timeout=15, check=True)
        self.dvr.catalog.close()
        original = Path(self.dvr.segments_dir).resolve()
        archive = self.root / 'original-archive'
        self.assertTrue(original.is_relative_to(self.root.resolve()))
        self.assertTrue(archive.resolve().is_relative_to(self.root.resolve()))
        original.rename(archive)
        shutil.copytree(destination / 'segments', original)
        restored_dvr = DVRManager()
        try:
            restored = EventStore(restored_dvr)
            self.assertEqual(restored.get(event_id)['state'], 'ACEITO')
            self.assertEqual(restored.dvr.catalog.reservation(event_id)['id'], event_id)
        finally:
            restored_dvr.catalog.close()


if __name__ == '__main__':
    unittest.main()
