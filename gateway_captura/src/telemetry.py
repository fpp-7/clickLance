"""Heartbeat isolado do aceite/pipeline; whitelist sem URLs, erros brutos ou segredos."""
from datetime import datetime, timezone
import math
import requests

from .config import Config
from .logger import logger
from .operations import health_report


def payload(store):
    report = health_report(store)
    progress = report['capture_progress_age_seconds']
    def age(value):
        return max(0, min(315360000, math.ceil(value)))
    return {
        'schema_version': 1,
        'observed_at': datetime.fromtimestamp(report['generated_utc'], timezone.utc).isoformat(timespec='microseconds'),
        'gateway_version': report['version'],
        'capture_state': report['state'],
        'pending_events': min(1000000, report['pending']),
        'oldest_pending_age_seconds': age(report['oldest_pending_age_seconds']),
        'capture_progress_age_seconds': None if progress is None else age(progress),
    }


def send_once(store, http=None):
    if not Config.HEALTH_URL:
        return
    # Snapshot e seus locks terminam antes da rede. Nao alterar eventos/config em caso de erro.
    data = payload(store)
    response = (http or requests).post(Config.HEALTH_URL, json=data,
                                     headers={'X-Device-Token': Config.DEVICE_TOKEN},
                                     timeout=(3, 5), allow_redirects=False)
    if response.status_code != 204:
        raise RuntimeError('HEARTBEAT_REJECTED')


def run(store, stop):
    with requests.Session() as http:
        while not stop.is_set():
            try:
                send_once(store, http)
            except Exception as error:
                logger.warning('Telemetria indisponivel (%s); pipeline preservado.', type(error).__name__)
            stop.wait(max(60, Config.HEALTH_INTERVAL_SECONDS))
