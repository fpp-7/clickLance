"""CL1: framing limitado, sincronização e identidade serial idempotente."""
import re
import time
from uuid import uuid4

from .config import Config


IDENTIFIER = re.compile(r'[A-Za-z0-9_-]{1,32}\Z')


def uint(value, *, positive=False):
    if not value.isascii() or not value.isdecimal() or len(value) > 10:
        raise ValueError('INVALID_INTEGER')
    number = int(value)
    if number > 4294967295 or (positive and number == 0):
        raise ValueError('INVALID_INTEGER')
    return number


class LineFramer:
    def __init__(self):
        self.buffer = bytearray()
        self.started = None
        self.discard = False

    def feed(self, data: bytes, now=None):
        now = time.monotonic() if now is None else now
        if self.started is not None and now - self.started > 1:
            self.buffer.clear()
            self.discard = True
        lines = []
        for byte in data:
            if byte == 10:
                if not self.discard:
                    try:
                        body = self.buffer[:-1] if self.buffer.endswith(b'\r') else self.buffer
                        lines.append(body.decode('ascii'))
                    except UnicodeDecodeError:
                        pass
                self.buffer.clear()
                self.started = None
                self.discard = False
            elif not self.discard:
                if self.started is None:
                    self.started = now
                self.buffer.append(byte)
                if len(self.buffer) >= 192:
                    self.buffer.clear()
                    self.discard = True
        return lines


class SerialProtocol:
    def __init__(self, store):
        self.store = store
        self.session = None
        self.sync = None

    def reset_connection(self):
        self.session = None
        self.sync = None

    def format_result(self, fields, result):
        base = '|'.join(fields[:5])
        # Substitui TRIGGER preservando dispositivo/sessão/sequência.
        kind = 'ACCEPTED' if result['accepted'] else 'REJECTED'
        parts = base.split('|')
        parts[1] = kind
        return '|'.join(parts) + '|' + (result['event_id'] if result['accepted'] else result['reason'])

    def handle(self, line, *, mono=None, utc=None):
        mono = time.monotonic() if mono is None else mono
        utc = time.time() if utc is None else utc
        fields = line.split('|')
        if len(line) + 1 > 192 or len(fields) < 3 or fields[0] != 'CL1':
            return None
        if fields[2] != Config.DEVICE_ID or not IDENTIFIER.fullmatch(fields[2]):
            return None
        device = fields[2]
        if fields[1] == 'HELLO' and len(fields) == 3:
            self.session, self.sync = uuid4().hex, None
            return f'CL1|WELCOME|{device}|{self.session}'
        if len(fields) < 4 or not IDENTIFIER.fullmatch(fields[3]):
            return None
        session = fields[3]
        try:
            if fields[1] == 'SYNC' and len(fields) == 5 and session == self.session:
                counter = uint(fields[4])
                self.sync = (counter, mono, utc)
                return f'CL1|SYNCED|{device}|{session}|{counter}'
            if fields[1] != 'TRIGGER' or len(fields) != 6:
                return None
            sequence, counter = uint(fields[4], positive=True), uint(fields[5])
        except ValueError:
            return None
        payload = fields[5]
        duplicate = self.store.existing(device, session, sequence, payload)
        if duplicate is not None:
            return self.format_result(fields, duplicate)
        if session != self.session or self.sync is None:
            result = self.store.reject(device, session, sequence, payload, 'NOT_SYNCED')
        else:
            sync_counter, sync_mono, sync_utc = self.sync
            delta = ((counter - sync_counter + 2**31) % 2**32 - 2**31) / 1000
            click_mono = sync_mono + delta
            if mono - sync_mono > 3600 or mono - click_mono > 15 or click_mono > mono + 0.25:
                result = self.store.reject(device, session, sequence, payload, 'INVALID_TIME')
            else:
                result = self.store.accept(device, session, sequence, payload,
                                           click_mono, sync_utc + delta)
        return self.format_result(fields, result)

    def state_line(self):
        if self.session:
            return f'CL1|STATE|{Config.DEVICE_ID}|{self.session}|{self.store.health()}'
        return None
