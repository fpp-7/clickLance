"""BE-026: cliente gateway original, API real e cache SQLite em disco.

Fixtures sinteticas por stdin; nao acessa camera/AWS nem altera o gateway.
"""
import json
import sqlite3
import sys
import tempfile
import threading
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'gateway_captura'))
import requests
from src.config import Config
from src.remote_config import RemoteConfig
from src.persistent_pipeline import CloudGateway, PermanentFailure


def store(path):
    db = sqlite3.connect(path)
    db.row_factory = sqlite3.Row
    db.execute('CREATE TABLE IF NOT EXISTS configuration (singleton INTEGER PRIMARY KEY, payload TEXT)')
    running = threading.Event()
    running.set()
    return SimpleNamespace(db=db, lock=threading.RLock(), enabled=True,
                           dvr=SimpleNamespace(_running=running))


def run(p):
    base = p['base_url']
    admin_headers = {'Authorization': f"Bearer {p['admin_token']}"}
    status_path = f"{base}/api/v1/admin/empresas/{p['company_id']}/quadras/{p['court_id']}/status"
    rotation_path = f"{base}/api/v1/admin/dispositivos/{p['device_id']}/credenciais/rotacao"
    event = {'id': p['event_id'], 'device': p['device_id'], 'timestamp': p['timestamp'],
             'sha256': p['sha256'], 'object_key': f"replays/{p['device_id']}/{p['event_id']}.mp4",
             'config': json.dumps({'quadra_id': p['court_id'], 'bucket': p['bucket']})}
    snapshot = event.copy()
    with tempfile.TemporaryDirectory(prefix='clicklance-be026-') as directory, \
            patch.object(Config, 'CONFIG_URL', f"{base}/api/v1/dispositivos/{p['device_id']}/config"), \
            patch.object(Config, 'DEVICE_TOKEN', p['device_token']), \
            patch.object(Config, 'API_URL', f'{base}/api/v1/replays'):
        cache = Path(directory) / 'cache.sqlite'
        current_store = store(cache)
        remote = RemoteConfig(current_store)
        remote.refresh()
        assert current_store.enabled is True
        cloud = CloudGateway(s3=object())
        cloud.register(event)
        response = requests.patch(status_path, headers=admin_headers, json={'active': False}, timeout=10)
        assert response.status_code == 200
        remote.refresh()
        assert current_store.enabled is False
        try:
            cloud.register(event)
            raise AssertionError('inactive retry must be rejected')
        except PermanentFailure as error:
            assert str(error) == 'HTTP_403'
        current_store.db.close()

        # Reinicio sem rede usa a inativacao persistida, sem reabilitar captura.
        current_store = store(cache)
        remote = RemoteConfig(current_store)
        with patch.object(remote.http, 'get', side_effect=requests.ConnectionError('offline fixture')):
            remote.initialize()
        assert remote.cached()['active'] is False and current_store.enabled is False
        response = requests.patch(status_path, headers=admin_headers, json={'active': True}, timeout=10)
        assert response.status_code == 200
        remote.refresh()
        assert current_store.enabled is True

        response = requests.post(rotation_path, headers=admin_headers, json={}, timeout=10)
        assert response.status_code == 200 and response.headers['Cache-Control'] == 'no-store'
        new_token = response.json()['device_token']
        assert new_token != p['device_token']
        remote.refresh()
        assert current_store.enabled is False
        try:
            cloud.register(event)
            raise AssertionError('revoked retry must be rejected')
        except PermanentFailure as error:
            assert str(error) == 'HTTP_401'
        with patch.object(Config, 'DEVICE_TOKEN', new_token):
            remote.refresh()
            assert current_store.enabled is True
            cloud.register(event)
        assert event == snapshot
        current_store.db.close()
    print('GATEWAY_OPERATIONS_OK inactive/disk_cache/restart_offline/reactivate/rotation/retry')


if __name__ == '__main__':
    run(json.load(sys.stdin))
