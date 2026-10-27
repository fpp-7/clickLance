"""Opt-in integration against a disposable local backend and its local media directory.

No camera, Arduino or AWS is used. The actual gateway notification method is used.
"""
from datetime import datetime, timezone
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import unittest
from unittest.mock import patch
from urllib.parse import urljoin, urlsplit
from uuid import uuid4
from zoneinfo import ZoneInfo

os.environ["CLICKLANCE_LOAD_ENV"] = "0"
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import requests
from src.config import Config
from src.video_processor import VideoProcessor

ENVIRONMENT = ("CLICKLANCE_TEST_BACKEND_URL", "CLICKLANCE_TEST_ADMIN_TOKEN", "CLICKLANCE_TEST_LOCAL_STORAGE_DIR")


@unittest.skipUnless(all(os.getenv(key) for key in ENVIRONMENT), "Local backend integration environment not configured")
@unittest.skipUnless(shutil.which("ffmpeg"), "FFmpeg unavailable")
class BackendContractTest(unittest.TestCase):
    def test_gateway_notification_catalog_and_private_playback(self):
        target = urlsplit(os.environ[ENVIRONMENT[0]])
        self.assertIn(target.hostname, {"localhost", "127.0.0.1", "::1"}, "Use only a disposable localhost backend")
        self.assertIn(target.scheme, {"http", "https"})
        origin = f"{target.scheme}://{target.netloc}"
        api = origin + "/api/v1"
        workspace = Path(__file__).resolve().parents[2]
        storage = Path(os.environ[ENVIRONMENT[2]]).resolve()
        self.assertTrue(storage.is_relative_to(workspace), "Local storage must be inside the project workspace")
        storage.mkdir(parents=True, exist_ok=True)
        admin_headers = {"X-Admin-Token": os.environ[ENVIRONMENT[1]]}
        session = requests.Session()
        company_id, media = None, None

        def call(method, path, *, body=None, headers=None, expected=200):
            response = session.request(method, api + path, json=body, headers=headers, timeout=(5, 20))
            # Do not expose provisioned tokens or admin secrets in failed assertions.
            self.assertEqual(response.status_code, expected, f"{method} {path}: HTTP {response.status_code}")
            return response.json()

        try:
            company = call("POST", "/empresas", body={"nome": "Smoke gateway " + uuid4().hex[:8], "dias_retencao_padrao": 2},
                           headers=admin_headers, expected=201)
            company_id = company["id"]
            court = call("POST", "/quadras", body={"empresa_id": company_id, "nome": "Quadra sintetica",
                         "duracao_segmento_segundos": 10, "segmentos_concatenar": 12, "fuso_horario": "America/Sao_Paulo"},
                         headers=admin_headers, expected=201)
            device = call("POST", "/dispositivos", body={"id": "smoke_" + uuid4().hex[:12], "quadra_id": court["id"]},
                          headers=admin_headers, expected=201)
            config = call("GET", f"/dispositivos/{device['id']}/config", headers={"X-Device-Token": device["token_acesso"]})
            self.assertEqual(config["quadra_id"], court["id"])
            self.assertEqual(config["duracao_segmento_segundos"], 10)
            event_id = str(uuid4())
            key = f"replays/{court['id']}/{event_id}.mp4"
            media = storage / key
            media.parent.mkdir(parents=True, exist_ok=True)
            subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-n", "-f", "lavfi", "-i",
                            "testsrc=size=160x120:rate=10", "-t", "2", "-an", "-c:v", "libx264", "-preset", "ultrafast",
                            "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(media)],
                           check=True, capture_output=True, timeout=30)
            expected_bytes = media.read_bytes()
            event_time = datetime.now(timezone.utc)
            event = {"evento_id": event_id, "dispositivo_id": device["id"], "timestamp": event_time.timestamp()}

            # Repeating the actual notification method exercises real serialization and headers.
            worker = VideoProcessor.__new__(VideoProcessor)
            worker.http = session
            with patch.multiple(Config, API_URL=api + "/replays", DEVICE_TOKEN=device["token_acesso"]):
                worker._notify_backend(event, key)
                worker._notify_backend(event, key)

            day = event_time.astimezone(ZoneInfo("America/Sao_Paulo")).date().isoformat()
            listing = call("GET", f"/quadras/{court['id']}/lances?data={day}&pagina=0&tamanho=25")
            self.assertEqual(listing["total_lances"], 1, "Repeated notification must not duplicate the replay")
            self.assertEqual(len(listing["lances"]), 1)
            clip = listing["lances"][0]
            detail = call("GET", f"/lances/{clip['id']}")
            self.assertEqual(detail["id"], clip["id"])
            self.assertEqual(detail["quadra_id"], court["id"])
            self.assertEqual(datetime.fromisoformat(detail["timestamp_evento"].replace("Z", "+00:00")), event_time)
            playback = urljoin(origin, detail["video_url"])
            self.assertTrue(urlsplit(playback).path.startswith("/api/v1/media/"), "Integration requires signed local storage mode")
            video = session.get(playback, timeout=(5, 20))
            self.assertEqual(video.status_code, 200)
            self.assertTrue(video.headers.get("Content-Type", "").startswith("video/mp4"))
            self.assertEqual(hashlib.sha256(video.content).digest(), hashlib.sha256(expected_bytes).digest())
            partial = session.get(playback, headers={"Range": "bytes=0-127"}, timeout=(5, 20))
            self.assertEqual(partial.status_code, 206)
            self.assertEqual(partial.content, expected_bytes[:128])
            self.assertEqual(partial.headers.get("Content-Range"), f"bytes 0-127/{len(expected_bytes)}")

            # Tampering with the signed token must not grant a media response.
            signature = playback.rsplit(".", 1)[1]
            tampered = playback.rsplit(".", 1)[0] + "." + ("A" if signature[0] != "A" else "B") + signature[1:]
            invalid = session.get(tampered, timeout=(5, 20))
            self.assertIn(invalid.status_code, {403, 404})
        finally:
            # Test-created records remain as inactive history; the backend must be disposable.
            if company_id:
                session.patch(api + f"/empresas/{company_id}", json={"ativo": False}, headers=admin_headers, timeout=(5, 20))
            if media is not None and media.is_relative_to(storage):
                media.unlink(missing_ok=True)
            session.close()


if __name__ == "__main__":
    unittest.main()
