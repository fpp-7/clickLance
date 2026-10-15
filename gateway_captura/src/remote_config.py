"""Configuração autenticada, validada e armazenada antes de aplicar."""
import json
import math
from urllib.parse import urlsplit

import requests

from .config import Config


FIELDS = {
    'REPLAY_BEFORE_SECONDS': (1, 600), 'REPLAY_AFTER_SECONDS': (0, 60),
    'SEGMENT_DURATION_SECONDS': (1, 120), 'KEYFRAME_INTERVAL': (1, 600),
    'CAMERA_TIMEOUT_SECONDS': (1, 60), 'CAMERA_LATENCY_SECONDS': (0, 60),
    'MAX_PENDING_EVENTS': (1, 1000), 'MIN_FREE_BYTES': (1024**2, 1024**4),
    'BUFFER_QUOTA_BYTES': (1024**2, 10*1024**4), 'CLIPS_QUOTA_BYTES': (1024**2, 10*1024**4),
}
CAPTURE_FIELDS = {'CAMERA_SOURCE', 'CAMERA_RTSP_URL', 'CAMERA_RTSP_TRANSPORT',
                  'SEGMENT_DURATION_SECONDS', 'KEYFRAME_INTERVAL', 'CAMERA_TIMEOUT_SECONDS',
                  'CAMERA_LATENCY_SECONDS', 'CAMERA_DEVICE_NAME'}


def validate(data):
    if not isinstance(data, dict) or set(data) != {'version', 'active', 'settings'}:
        raise ValueError('INVALID_CONFIGURATION')
    if not isinstance(data['version'], str) or not 1 <= len(data['version']) <= 64:
        raise ValueError('INVALID_CONFIGURATION_VERSION')
    if type(data['active']) is not bool or not isinstance(data['settings'], dict):
        raise ValueError('INVALID_CONFIGURATION')
    for key, value in data['settings'].items():
        if key in FIELDS:
            lower, upper = FIELDS[key]
            if type(value) not in (int, float) or not math.isfinite(value) or not lower <= value <= upper:
                raise ValueError('INVALID_CONFIGURATION_RANGE')
            if key in ('KEYFRAME_INTERVAL', 'MAX_PENDING_EVENTS', 'SEGMENT_DURATION_SECONDS') and type(value) is not int:
                raise ValueError('INVALID_CONFIGURATION_INTEGER')
        elif key == 'CAMERA_SOURCE':
            if value not in ('rtsp', 'dshow'):
                raise ValueError('INVALID_CAMERA_SOURCE')
        elif key == 'CAMERA_RTSP_TRANSPORT':
            if value not in ('tcp', 'udp'):
                raise ValueError('INVALID_CAMERA_TRANSPORT')
        elif key == 'CAMERA_RTSP_URL':
            if not isinstance(value, str) or len(value) > 2048:
                raise ValueError('INVALID_CAMERA_URL')
            parsed = urlsplit(value)
            if parsed.scheme not in ('rtsp', 'rtsps') or not parsed.hostname:
                raise ValueError('INVALID_CAMERA_URL')
        elif key == 'CAMERA_DEVICE_NAME':
            if not isinstance(value, str) or not 1 <= len(value) <= 128:
                raise ValueError('INVALID_CAMERA_DEVICE')
        else:
            raise ValueError('UNKNOWN_CONFIGURATION_SETTING')
    return data


class RemoteConfig:
    def __init__(self, store, http=None):
        self.store = store
        self.http = http or requests.Session()

    def cached(self):
        with self.store.lock:
            row = self.store.db.execute('SELECT payload FROM configuration WHERE singleton=1').fetchone()
            return validate(json.loads(row['payload'])) if row else None

    def apply(self, data):
        validate(data)
        with self.store.lock:
            previous = self.cached()
            merged = dict(previous['settings']) if previous else {
                key: getattr(Config, key) for key in FIELDS.keys() | CAPTURE_FIELDS
                if getattr(Config, key) is not None and getattr(Config, key) != ''
            }
            merged.update(data['settings'])
            data = dict(data, settings=merged)
            validate(data)
            effective_source = merged.get('CAMERA_SOURCE', Config.CAMERA_SOURCE)
            effective_url = merged.get('CAMERA_RTSP_URL', Config.CAMERA_RTSP_URL)
            if data['active'] and effective_source == 'rtsp':
                url = urlsplit(effective_url)
                if url.scheme not in ('rtsp', 'rtsps') or not url.hostname:
                    raise ValueError('MISSING_CAMERA_CONFIGURATION')
            changed = any(getattr(Config, key) != value for key, value in data['settings'].items()
                          if key in CAPTURE_FIELDS)
            if changed or not data['active']:
                self.store.dvr._running.clear()  # Impede novas aceitações durante a troca.
            with self.store.db:
                self.store.db.execute('INSERT OR REPLACE INTO configuration VALUES (1,?)', (json.dumps(data),))
            for key, value in data['settings'].items():
                setattr(Config, key, value)
            Config.CONFIG_VERSION = data['version']
            self.store.dvr.segment_duration = Config.SEGMENT_DURATION_SECONDS
            self.store.dvr.camera_name = Config.CAMERA_DEVICE_NAME
            self.store.dvr.retention_hours = Config.RETENTION_HOURS
            self.store.enabled = data['active']
            return changed

    def refresh(self):
        if not Config.CONFIG_URL:
            return False
        response = self.http.get(Config.CONFIG_URL, timeout=(5, 10),
                                 allow_redirects=False,
                                 headers={'X-Device-Token': Config.DEVICE_TOKEN})
        if response.status_code in (401, 403, 410):
            previous = self.cached() or {'version': 'revoked', 'active': False, 'settings': {}}
            previous['active'] = False
            self.apply(previous)
            return True
        if 300 <= response.status_code < 400:
            raise ValueError('CONFIGURATION_REDIRECT')
        response.raise_for_status()
        return self.apply(response.json())

    def initialize(self):
        if not Config.CONFIG_URL:
            return False  # .env é configuração local explícita.
        try:
            return self.refresh()
        except (requests.RequestException, ValueError):
            previous = self.cached()
            if previous is None:
                raise RuntimeError('CONFIGURATION_UNAVAILABLE: sem configuração válida em cache') from None
            return self.apply(previous)
