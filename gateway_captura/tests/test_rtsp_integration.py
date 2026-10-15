"""Teste opt-in de RTSP local. MEDIAMTX_PATH aponta para o executável oficial."""
import os
import shutil
import socket
import subprocess
import threading
import time
import unittest
from unittest.mock import patch

from src.config import Config
from src.event_store import EventStore
from src.media import validate_video
from src.operations import supervise_capture
from test_gateway import GatewayFixture


@unittest.skipUnless(os.getenv('MEDIAMTX_PATH') and shutil.which('ffmpeg'), 'Requer MEDIAMTX_PATH e FFmpeg')
class RTSPIntegration(GatewayFixture, unittest.TestCase):
    def test_network_loss_restarts_capture_in_new_session(self):
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', 0))
            port = sock.getsockname()[1]
        url = f'rtsp://127.0.0.1:{port}/test'
        configuration = self.root / 'mediamtx.yml'
        configuration.write_text(
            f'logLevel: error\nrtspAddress: 127.0.0.1:{port}\nrtspTransports: [tcp]\n'
            'rtmp: false\nhls: false\nwebrtc: false\nsrt: false\nmoq: false\n'
            'paths:\n  all_others:\n', encoding='utf-8')
        processes = []
        logs = self.root / 'network.log'
        with logs.open('wb') as output, patch.multiple(
            Config, CAMERA_SOURCE='rtsp', CAMERA_RTSP_URL=url, CAMERA_TIMEOUT_SECONDS=3,
            SEGMENT_DURATION_SECONDS=1, KEYFRAME_INTERVAL=15, CAMERA_LATENCY_SECONDS=0,
        ):
            self.dvr.segment_duration = 1
            stop = threading.Event()
            supervisor = threading.Thread(target=supervise_capture,
                                          args=(EventStore(self.dvr), stop), daemon=True)

            def launch_network():
                server = subprocess.Popen([os.environ['MEDIAMTX_PATH'], str(configuration)],
                                          stdout=output, stderr=output,
                                          creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
                processes.append(server)
                deadline = time.monotonic() + 5
                while time.monotonic() < deadline:
                    try:
                        with socket.create_connection(('127.0.0.1', port), timeout=0.1):
                            break
                    except OSError:
                        time.sleep(0.05)
                else:
                    self.fail('Servidor RTSP não iniciou: ' + logs.read_text(errors='replace')[-1000:])
                publisher = subprocess.Popen(
                    ['ffmpeg', '-v', 'error', '-re', '-f', 'lavfi', '-i',
                     'testsrc2=size=160x120:rate=15', '-c:v', 'libx264', '-preset', 'ultrafast',
                     '-tune', 'zerolatency', '-g', '15', '-an', '-f', 'rtsp', '-rtsp_transport', 'tcp', url],
                    stdout=output, stderr=output, creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
                processes.append(publisher)

            def wait_closed(previous=None):
                deadline = time.monotonic() + 30
                while time.monotonic() < deadline:
                    session = self.dvr.session_id
                    if session and session != previous:
                        records = [r for r in self.dvr.catalog.records(session) if r['status'] == 'CLOSED']
                        if len(records) >= 2:
                            return session, records
                    time.sleep(0.1)
                self.fail('Captura RTSP sem progresso: ' + str(self.dvr.stderr_tail))

            try:
                launch_network()
                supervisor.start()
                first, records = wait_closed()
                validate_video(__import__('pathlib').Path(records[0]['path']))
                for process in processes:
                    process.kill()
                    process.wait(timeout=5)
                processes.clear()
                # Dá tempo para o cliente perceber o encerramento da conexão.
                deadline = time.monotonic() + 10
                while self.dvr._running.is_set() and time.monotonic() < deadline:
                    time.sleep(0.1)
                launch_network()
                second, records = wait_closed(first)
                self.assertNotEqual(first, second)
                self.assertTrue(all(r['session_id'] == second for r in records))
                self.assertTrue(all(second in r['path'] for r in records))
            finally:
                stop.set()
                self.dvr.stop_recording()
                if supervisor.ident:
                    supervisor.join(timeout=20)
                for process in processes:
                    if process.poll() is None:
                        process.kill()
                    process.wait(timeout=5)
                self.assertFalse(supervisor.is_alive())


if __name__ == '__main__':
    unittest.main()
