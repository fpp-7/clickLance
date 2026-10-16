"""BE-012: clientes originais contra API real. Recebe fixture sintetica via stdin.

Executado por GatewayIntegrationIT, que provisiona/limpa somente seus registros.
Nao altera o gateway nem acessa camera/Arduino/AWS.
"""
import json
import sqlite3
import sys
import threading
import uuid
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'gateway_captura'))
import requests
from src.config import Config
from src.remote_config import RemoteConfig
from src.persistent_pipeline import CloudGateway, PermanentFailure


def run(p):
    db = sqlite3.connect(':memory:')
    db.row_factory = sqlite3.Row
    db.execute('CREATE TABLE configuration (singleton INTEGER PRIMARY KEY, payload TEXT)')
    running = threading.Event()
    running.set()
    store = SimpleNamespace(db=db, lock=threading.RLock(), enabled=True,
                            dvr=SimpleNamespace(_running=running))
    url = p['base_url']
    with patch.object(Config, 'CONFIG_URL', f"{url}/api/v1/dispositivos/{p['device_id']}/config"), \
            patch.object(Config, 'DEVICE_TOKEN', p['device_token']), \
            patch.object(Config, 'API_URL', f'{url}/api/v1/replays'):
        remote = RemoteConfig(store)
        remote.refresh()
        assert remote.cached()['active'] is True
        assert Config.CAMERA_SOURCE == 'rtsp' and Config.CAMERA_RTSP_URL == 'rtsp://192.0.2.20/stream'
        cached_settings = remote.cached()['settings'].copy()
        remote.apply({'version': 'cache-merge-check', 'active': True, 'settings': {'MAX_PENDING_EVENTS': 10}})
        assert remote.cached()['settings']['CAMERA_RTSP_URL'] == cached_settings['CAMERA_RTSP_URL']
        remote.refresh()
        event = {'id': p['event_id'], 'device': p['device_id'], 'timestamp': p['timestamp'],
                 'sha256': p['sha256'], 'object_key': f"replays/{p['device_id']}/{p['event_id']}.mp4",
                 'config': json.dumps({'quadra_id': p['court_id'], 'bucket': p['bucket']})}

        class LostAck(requests.Session):
            lost = False
            def post(self, *args, **kwargs):
                response = super().post(*args, **kwargs)
                if not self.lost:
                    self.lost = True
                    assert response.status_code == 201
                    raise requests.ConnectionError('synthetic response loss after commit')
                assert response.status_code == 200
                return response

        cloud = CloudGateway(s3=object(), http=LostAck())
        try:
            cloud.register(event)
            raise AssertionError('lost response must remain retryable')
        except requests.ConnectionError:
            pass
        cloud.register(event)
        conflict = dict(event, sha256='0' * 64)
        try:
            CloudGateway(s3=object()).register(conflict)
            raise AssertionError('conflict expected')
        except PermanentFailure as error:
            assert str(error) == 'HTTP_409'
        missing_id = uuid.uuid4().hex
        missing = dict(event, id=missing_id, object_key=f"replays/{p['device_id']}/{missing_id}.mp4")
        try:
            CloudGateway(s3=object()).register(missing)
            raise AssertionError('missing object must remain retryable')
        except requests.HTTPError as error:
            assert error.response.status_code == 503
        # Rejeicao autenticada persiste inativacao sem alterar o snapshot do evento.
        before = event.copy()
        with patch.object(Config, 'DEVICE_TOKEN', 'invalid-fixture-token'):
            remote.refresh()
            assert remote.cached()['active'] is False and store.enabled is False
        assert event == before
        remote.refresh()
        assert store.enabled is True
    db.close()
    print('GATEWAY_INTEGRATION_OK config/cache/inactive/lost_ack/retry/conflict/storage_503')


if __name__ == '__main__':
    run(json.load(sys.stdin))
