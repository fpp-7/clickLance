"""Etapas duráveis; fontes e artefatos só são liberados depois do commit correspondente."""
import hashlib
import json
import os
import threading
import time
from pathlib import Path

import boto3
import requests
from botocore.config import Config as AWSConfig
from botocore.exceptions import ClientError, NoCredentialsError, PartialCredentialsError

from .config import Config
from .logger import logger


class PermanentFailure(ValueError):
    pass


def file_hash(path):
    digest = hashlib.sha256()
    with open(path, 'rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


class CloudGateway:
    def __init__(self, s3=None, http=None):
        self._s3 = s3
        self.http = http or requests.Session()

    @property
    def s3(self):
        if self._s3 is None:
            self._s3 = boto3.client(
                's3', aws_access_key_id=Config.AWS_ACCESS_KEY_ID,
                aws_secret_access_key=Config.AWS_SECRET_ACCESS_KEY,
                aws_session_token=Config.AWS_SESSION_TOKEN, region_name=Config.AWS_REGION,
                config=AWSConfig(connect_timeout=5, read_timeout=30,
                                 retries={'max_attempts': 0}),
            )
        return self._s3

    def upload(self, event):
        config = json.loads(event['config'])
        bucket = config['bucket']
        if not bucket:
            raise PermanentFailure('MISSING_BUCKET')
        if file_hash(event['clip']) != event['sha256']:
            raise PermanentFailure('LOCAL_CONTENT_CHANGED')
        key = event['object_key']
        # Escrita condicional elimina sobrescrita em resposta incerta ou concorrente.
        try:
            with open(event['clip'], 'rb') as stream:
                self.s3.put_object(Bucket=bucket, Key=key, Body=stream,
                                   ContentType='video/mp4', Metadata={'sha256': event['sha256']},
                                   IfNoneMatch='*')
        except ClientError as exc:
            if exc.response['ResponseMetadata']['HTTPStatusCode'] != 412:
                raise
        remote = self.s3.head_object(Bucket=bucket, Key=key)
        if (remote.get('Metadata', {}).get('sha256') != event['sha256']
                or remote.get('ContentLength') != Path(event['clip']).stat().st_size):
            raise PermanentFailure('REMOTE_CONTENT_CONFLICT')

    def register(self, event):
        if not Config.DEVICE_TOKEN:
            raise PermanentFailure('MISSING_DEVICE_TOKEN')
        config = json.loads(event['config'])
        payload = {
            'contrato_versao': 1, 'evento_id': event['id'], 'dispositivo_id': event['device'],
            'quadra_id': config['quadra_id'], 'timestamp': event['timestamp'],
            'objeto': {'bucket': config['bucket'], 'key': event['object_key'], 'sha256': event['sha256']},
        }
        response = self.http.post(Config.API_URL, json=payload, timeout=(5, 15),
                                  allow_redirects=False,
                                  headers={'X-Device-Token': Config.DEVICE_TOKEN,
                                           'Idempotency-Key': event['id']})
        if 300 <= response.status_code < 400:
            raise PermanentFailure('HTTP_REDIRECT')
        if response.status_code in (400, 401, 403, 404, 409, 410, 422):
            raise PermanentFailure(f'HTTP_{response.status_code}')
        response.raise_for_status()
        data = response.json()
        if data.get('evento_id') != event['id'] or data.get('sha256') != event['sha256']:
            raise PermanentFailure('INVALID_REMOTE_CONFIRMATION')


class PersistentPipeline:
    def __init__(self, store, renderer, cloud=None):
        self.store, self.renderer = store, renderer
        self.cloud = cloud or CloudGateway()

    def step(self, event_id):
        event = self.store.get(event_id)
        state = event['state']
        logger.info('evento_id=%s estado=%s tentativa=%d', event_id, state, event['attempts'] + 1)
        started = time.monotonic()
        try:
            self._perform(event)
        finally:
            logger.info('evento_id=%s estado=%s duracao_s=%.3f', event_id, state, time.monotonic() - started)

    def _perform(self, event):
        event_id, state = event['id'], event['state']
        if state in ('ACEITO', 'AGUARDANDO_VIDEO'):
            self.store.advance(event_id, 'AGUARDANDO_VIDEO') if state == 'ACEITO' else None
            request, records = self.store.dvr.wait_replay(event_id)
            destination = Path(self.renderer.output_dir).resolve() / f'lance_{event_id}.mp4'
            temporary = destination.with_suffix('.working.mp4')
            self.renderer._render_replay(request, records, str(temporary))
            with temporary.open('rb+') as stream:
                os.fsync(stream.fileno())
            os.replace(temporary, destination)
            digest = file_hash(destination)
            conf = json.loads(event['config'])
            key = f"replays/{conf['device']}/{event_id}.mp4"
            self.store.advance(event_id, 'CLIPE_PRONTO', clip=str(destination),
                               sha256=digest, object_key=key, release=True)
        elif state == 'CLIPE_PRONTO':
            self.cloud.upload(event)
            self.store.advance(event_id, 'UPLOAD_CONFIRMADO')
        elif state == 'UPLOAD_CONFIRMADO':
            self.cloud.register(event)
            self.store.advance(event_id, 'REGISTRO_CONFIRMADO')
            self.renderer._cleanup(event['clip'])

    def run_once(self):
        event = self.store.due()
        if not event:
            return False
        try:
            self.step(event['id'])
        except Exception as exc:
            permanent = isinstance(exc, (PermanentFailure, NoCredentialsError, PartialCredentialsError))
            if isinstance(exc, ClientError):
                permanent = exc.response.get('ResponseMetadata', {}).get('HTTPStatusCode') in (400, 401, 403)
            self.store.fail(event['id'], exc, permanent=permanent)
            logger.warning('evento_id=%s erro=%s', event['id'], type(exc).__name__)
        return True

    def run(self, stop: threading.Event):
        while not stop.is_set():
            if not self.run_once():
                stop.wait(0.25)
