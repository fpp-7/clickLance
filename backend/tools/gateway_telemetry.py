"""BE028: EventStore/DVR locais reais, sem camera; cliente heartbeat contra API real."""
import json
import sys
import tempfile
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'gateway_captura'))
from src.config import Config
from src.dvr_manager import DVRManager
from src.event_store import EventStore
from src.telemetry import send_once


def run(p):
    with tempfile.TemporaryDirectory(prefix='clicklance-health-') as directory:
        with patch.multiple(Config, SEGMENTS_DIR=str(Path(directory)/'segments'), OUTPUT_DIR=str(Path(directory)/'clips'),
                            DEVICE_ID=p['device_id'], DEVICE_TOKEN=p['device_token'], S3_BUCKET_NAME='health-fixture',
                            HEALTH_URL=p['base_url']+'/api/v1/dispositivos/'+p['device_id']+'/saude'):
            dvr = DVRManager()
            try:
                store = EventStore(dvr)
                send_once(store)
                assert store.db.execute('SELECT COUNT(*) FROM events').fetchone()[0] == 0
            finally:
                dvr.catalog.close()
    print('GATEWAY_TELEMETRY_OK local_health/real_http/no_pipeline_mutation')


if __name__ == '__main__':
    run(json.load(sys.stdin))
