"""Demonstra vídeo real + SQLite + perda de ACK + rede simulada + reinício.

Sem câmera, Arduino ou serviços cloud. Artefatos ficam preservados para revisão.
"""
import json
import shutil
import tempfile
import time
from pathlib import Path
from queue import Queue

import requests

from src.config import Config
from src.diagnostics import capture_sample
from src.dvr_manager import DVRManager
from src.event_store import EventStore
from src.persistent_pipeline import PersistentPipeline
from src.serial_protocol import SerialProtocol
from src.video_processor import VideoProcessor


class SimulatedCloud:
    def __init__(self):
        self.online = False
        self.objects, self.events = {}, {}

    def upload(self, event):
        if not self.online:
            raise requests.ConnectionError('offline simulator')
        self.objects.setdefault(event['object_key'], event['sha256'])

    def register(self, event):
        self.events.setdefault(event['id'], event['sha256'])


def main():
    root = Path(tempfile.mkdtemp(prefix='clicklance-recovery-'))
    original = {name: getattr(Config, name) for name in (
        'SEGMENTS_DIR', 'OUTPUT_DIR', 'REPLAY_BEFORE_SECONDS', 'REPLAY_AFTER_SECONDS',
        'CAMERA_LATENCY_SECONDS', 'MIN_FREE_BYTES', 'S3_BUCKET_NAME', 'DEVICE_TOKEN')}
    Config.SEGMENTS_DIR, Config.OUTPUT_DIR = str(root / 'segments'), str(root / 'clips')
    Config.REPLAY_BEFORE_SECONDS, Config.REPLAY_AFTER_SECONDS = 2, 0
    Config.CAMERA_LATENCY_SECONDS, Config.MIN_FREE_BYTES = 0, 0
    Config.S3_BUCKET_NAME = 'simulated-private-bucket'
    Config.DEVICE_TOKEN = 'simulated-token'
    dvr = None
    report = {'ok': False, 'mode': 'synthetic_video_and_simulated_cloud', 'directory': str(root)}
    print(f'Artefatos da demonstração: {root}')
    try:
        dvr = DVRManager()
        dvr.segment_duration = 2
        files = capture_sample(dvr, root / 'source', synthetic=True)
        dvr.session_id = dvr.catalog.create_session(time.time(), 'synthetic')
        directory = dvr.catalog.session_directory(dvr.session_id)
        for file in files:
            shutil.copy2(file, directory / file.name)
        shutil.copy2(root / 'source' / 'segments.csv', directory / 'segments.csv')
        dvr.catalog.reconcile(dvr.session_id, finished=True)
        # Somente demonstração: clique controlado em t=5 do vídeo já concluído.
        dvr._running.set()
        dvr.observe_content(5, time.monotonic())
        store = EventStore(dvr)
        protocol = SerialProtocol(store)
        device = Config.DEVICE_ID
        session = protocol.handle(f'CL1|HELLO|{device}').split('|')[-1]
        protocol.handle(f'CL1|SYNC|{device}|{session}|1000')
        trigger = f'CL1|TRIGGER|{device}|{session}|1|1000'
        lost_ack = protocol.handle(trigger)
        if not lost_ack or '|ACCEPTED|' not in lost_ack:
            raise RuntimeError('Demonstração não obteve aceite: ' + str(lost_ack))
        event_id = lost_ack.split('|')[-1]
        renderer = VideoProcessor(Queue(), dvr, s3_client=object())
        cloud = SimulatedCloud()
        pipeline = PersistentPipeline(store, renderer, cloud)
        pipeline.step(event_id)
        pipeline.run_once()  # Falha de rede, clipe e etapa preservados.
        preserved = store.get(event_id)
        assert preserved['state'] == 'CLIPE_PRONTO' and Path(preserved['clip']).exists()
        dvr.catalog.close()
        dvr = DVRManager()
        store = EventStore(dvr)
        repeated_ack = SerialProtocol(store).handle(trigger)
        assert lost_ack == repeated_ack
        renderer = VideoProcessor(Queue(), dvr, s3_client=object())
        pipeline = PersistentPipeline(store, renderer, cloud)
        cloud.online = True
        time.sleep(max(0, preserved['next_attempt'] - time.time()) + 0.01)
        pipeline.run_once()
        pipeline.run_once()
        event = store.get(event_id)
        report.update(ok=event['state'] == 'REGISTRO_CONFIRMADO', event_id=event_id,
                      state=event['state'], same_ack_after_restart=lost_ack == repeated_ack,
                      objects=len(cloud.objects), remote_events=len(cloud.events),
                      clip_removed_after_confirmation=not Path(event['clip']).exists(),
                      attempts=[dict(row) for row in store.db.execute('SELECT * FROM attempts')])
        print(json.dumps(report, indent=2))
        return 0 if report['ok'] else 1
    finally:
        if dvr is not None:
            dvr.catalog.close()
        (root / 'report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
        for name, value in original.items():
            setattr(Config, name, value)


if __name__ == '__main__':
    raise SystemExit(main())
