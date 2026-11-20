"""Standard-library unittest suite for the AURA MUSIC companion backend.

Run from this directory:  python test_server.py
"""

from __future__ import annotations

import json
import os
import threading
import time
import unittest
from dataclasses import replace
from unittest.mock import patch
from urllib import error as urlerror
from urllib import request as urlrequest

import server


class BackendTests(unittest.TestCase):
    token = "test-only-client-token-do-not-use-in-production"
    model = "test-model"

    @classmethod
    def setUpClass(cls) -> None:
        cls.saved_env = {
            key: os.environ.get(key)
            for key in ("GROQ_API_KEY", "AURA_CLIENT_TOKEN", "AURA_GROQ_MODEL")
        }
        os.environ.pop("GROQ_API_KEY", None)  # force the deterministic fallback
        os.environ["AURA_CLIENT_TOKEN"] = cls.token
        os.environ["AURA_GROQ_MODEL"] = cls.model

        config = replace(
            server.Config.from_env(),
            bind="127.0.0.1",
            port=0,
            rate_limit=200,  # avoid cross-test throttling
        )
        cls.config = config
        cls.server = server.build_server(config)
        cls.base = "http://127.0.0.1:" + str(cls.server.server_address[1])
        cls.thread = threading.Thread(
            target=cls.server.serve_forever, kwargs={"poll_interval": 0.05}, daemon=True
        )
        cls.thread.start()
        cls.opener = urlrequest.build_opener(urlrequest.ProxyHandler({}))

    @classmethod
    def tearDownClass(cls) -> None:
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=5)
        for key, value in cls.saved_env.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value

    # --------------------------------------------------------------- helpers
    def call(self, method: str, path: str, body=None, token=None, headers=None):
        request_headers = dict(headers or {})
        data = None
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            request_headers.setdefault("Content-Type", "application/json")
        if token:
            request_headers["Authorization"] = "Bearer " + token
        req = urlrequest.Request(self.base + path, data=data, headers=request_headers, method=method)
        try:
            with self.opener.open(req, timeout=5) as response:
                raw = response.read()
                return response.status, self._decode(raw), dict(response.headers)
        except urlerror.HTTPError as exc:
            return exc.code, self._decode(exc.read()), dict(exc.headers)

    @staticmethod
    def _decode(raw: bytes):
        if not raw:
            return None
        text = raw.decode("utf-8", errors="replace")
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            return text

    def analyze_body(self, **overrides):
        body = {
            "title": "Midnight Drive",
            "artist": "Aura Test",
            "lyrics": "We drive through the neon rain\nand the city glows.",
            "genre_tags": ["synthwave", "dream pop"],
            "language": "en",
            "audio": {"energy": 0.7, "bass": 0.4},
        }
        body.update(overrides)
        return body

    def create_and_pair(self, name="Laptop"):
        status, created, _ = self.call("POST", "/api/receiver/create", {}, token=self.token)
        self.assertEqual(status, 200)
        self.assertRegex(created["code"], r"^[0-9]{6}$")
        status, paired, _ = self.call(
            "POST", "/api/receiver/pair", {"code": created["code"], "name": name}
        )
        self.assertEqual(status, 200)
        return created, paired

    # ----------------------------------------------------------------- tests
    def test_health(self):
        status, body, _ = self.call("GET", "/api/health")
        self.assertEqual(status, 200)
        self.assertEqual(body["status"], "ok")
        self.assertFalse(body["groq_configured"])
        self.assertEqual(body["model"], self.model)
        self.assertIsInstance(body["receiver_session_count"], int)
        self.assertNotIn("key", json.dumps(body).lower().replace("groq_configured", ""))

    def test_auth_failure(self):
        for header in (None, "wrong-token"):
            status, body, _ = self.call(
                "POST", "/api/groq/analyze", self.analyze_body(), token=header
            )
            self.assertEqual(status, 401)
            self.assertEqual(body["code"], "unauthorized")
            self.assertNotIn("wrong-token", json.dumps(body))

    def test_groq_fallback_and_cache(self):
        status, body, headers = self.call(
            "POST", "/api/groq/analyze", self.analyze_body(), token=self.token
        )
        self.assertEqual(status, 200)
        self.assertTrue(body["degraded"])
        for field in ("mood", "confidence", "energy_hint", "genre_tags",
                      "theme_hint", "sections", "explanation", "degraded"):
            self.assertIn(field, body)
        self.assertIn(body["mood"], server.MOODS)
        self.assertIn(body["theme_hint"], server.THEMES)
        self.assertGreaterEqual(body["confidence"], 0.0)
        self.assertLessEqual(body["confidence"], 1.0)
        self.assertGreaterEqual(body["energy_hint"], 0.0)
        self.assertLessEqual(body["energy_hint"], 1.0)
        self.assertIsInstance(body["sections"], list)
        self.assertEqual(headers.get("X-AURA-Cache"), "miss")

        status2, body2, headers2 = self.call(
            "POST", "/api/groq/analyze", self.analyze_body(), token=self.token
        )
        self.assertEqual(status2, 200)
        self.assertEqual(body2, body)
        self.assertEqual(headers2.get("X-AURA-Cache"), "hit")

    def test_analysis_validation(self):
        status, body, _ = self.call(
            "POST", "/api/groq/analyze", self.analyze_body(title=""), token=self.token
        )
        self.assertEqual(status, 400)
        self.assertEqual(body["code"], "invalid_title")

        status, body, _ = self.call(
            "POST", "/api/groq/analyze", self.analyze_body(lyrics="x" * 12_001), token=self.token
        )
        self.assertEqual(status, 413)
        self.assertEqual(body["code"], "lyrics_too_long")

        req = urlrequest.Request(
            self.base + "/api/groq/analyze", data=b"{not json",
            headers={"Authorization": "Bearer " + self.token, "Content-Type": "application/json"},
            method="POST",
        )
        try:
            self.opener.open(req, timeout=5)
            self.fail("expected HTTPError")
        except urlerror.HTTPError as exc:
            self.assertEqual(exc.code, 400)

    def test_pairing_single_use(self):
        created, paired = self.create_and_pair("Living Room")
        self.assertIn("session_token", paired)
        self.assertEqual(paired["session_id"], created["session_id"])
        self.assertTrue(paired["session_token"])

        status, body, _ = self.call(
            "POST", "/api/receiver/pair", {"code": created["code"], "name": "Other"}
        )
        self.assertEqual(status, 409)
        self.assertEqual(body["code"], "code_unavailable")

        status, body, _ = self.call(
            "POST", "/api/receiver/pair", {"code": "12345", "name": "Other"}
        )
        self.assertEqual(status, 400)

    def test_publish_state_disconnect(self):
        created, paired = self.create_and_pair()
        token = paired["session_token"]

        status, _, _ = self.call("GET", "/api/receiver/state?session_token=" + token)
        self.assertEqual(status, 204)

        state = {
            "session_token": token,
            "title": "Neon Rain", "artist": "Aura", "lyrics": "neon rain",
            "lyrics_visible": True, "playing": True,
            "position_ms": 1200, "duration_ms": 200000,
            "theme": {"background": "#050407", "primary": "#e8c77b"},
            "features": {"bass": 0.6, "beat": 0.9},
            "words": [{"text": "neon", "start_ms": 1000, "end_ms": 1400}],
        }
        status, published, _ = self.call("POST", "/api/receiver/publish", state, token=self.token)
        self.assertEqual(status, 200)
        self.assertTrue(published["ok"])

        status, received, headers = self.call("GET", "/api/receiver/state?session_token=" + token)
        self.assertEqual(status, 200)
        self.assertEqual(received["title"], "Neon Rain")
        self.assertEqual(received["words"], state["words"])
        self.assertEqual(received["theme"]["primary"], "#E8C77B")
        self.assertEqual(received["features"]["bass"], 0.6)
        self.assertEqual(headers.get("Cache-Control"), "no-store")

        # The phone must not claim a desktop receiver is connected until the
        # browser has actually polled the paired session.
        status, status_body, _ = self.call(
            "GET", "/api/receiver/status?session_token=" + token, token=self.token
        )
        self.assertEqual(status, 200)
        self.assertTrue(status_body["paired"])
        self.assertTrue(status_body["has_state"])
        self.assertIsNotNone(status_body["last_poll_at"])
        self.assertEqual(status_body["receiver_name"], "Laptop")

        status, body, _ = self.call("POST", "/api/receiver/disconnect", {"session_token": token}, token=self.token)
        self.assertEqual(status, 200)
        self.assertTrue(body["removed"])

        status, body, _ = self.call("GET", "/api/receiver/state?session_token=" + token)
        self.assertEqual(status, 404)
        self.assertEqual(body["code"], "session_not_found")

    def test_publish_requires_client_auth(self):
        _, paired = self.create_and_pair()
        status, body, _ = self.call(
            "POST", "/api/receiver/publish",
            {"session_token": paired["session_token"], "title": "x"},
        )
        self.assertEqual(status, 401)

    def test_cors_preflight(self):
        status, _, headers = self.call(
            "OPTIONS", "/api/groq/analyze", headers={
                "Origin": "https://receiver.example.com",
                "Access-Control-Request-Method": "POST",
            }
        )
        self.assertEqual(status, 204)
        self.assertEqual(headers.get("Access-Control-Allow-Origin"), "*")
        self.assertIn("Authorization", headers.get("Access-Control-Allow-Headers", ""))

    def test_receiver_static_assets(self):
        status, _, headers = self.call("GET", "/receiver")
        self.assertEqual(status, 200)
        self.assertIn("text/html", headers.get("Content-Type", ""))
        self.assertIn("Content-Security-Policy", headers)

        status, _, headers = self.call("GET", "/receiver.js")
        self.assertEqual(status, 200)
        self.assertIn("javascript", headers.get("Content-Type", ""))

    def test_unknown_route(self):
        status, body, _ = self.call("GET", "/api/nope")
        self.assertEqual(status, 404)
        self.assertEqual(body["code"], "not_found")

    def test_ai_suggest_requires_vibe_or_mood(self):
        status, body, _ = self.call("POST", "/api/ai/suggest", {})
        self.assertEqual(status, 400)
        self.assertEqual(body["code"], "invalid_vibe")

    def test_ai_suggest_fallback_uses_real_tracks_and_caches(self):
        def fake_search(query, limit=18):
            return {"tracks": [{
                "id": "dQw4w9WgXcQ", "videoId": "dQw4w9WgXcQ",
                "title": "A Real Track", "artist": "Real Artist",
                "durationMs": 200000, "artwork": "", "provider": "youtube",
            }]}

        with patch.object(server.music_service, "search", side_effect=fake_search) as mocked:
            status, body, headers = self.call(
                "POST", "/api/ai/suggest", {"mood": "CHILL", "limit": 4}
            )
            self.assertEqual(status, 200)
            self.assertTrue(body["degraded"])
            self.assertEqual(body["source"], "local")
            self.assertEqual(body["mood"], "CHILL")
            self.assertEqual(body["theme"], "MIDNIGHT_BLUE")
            self.assertEqual(len(body["tracks"]), 1)
            self.assertEqual(body["tracks"][0]["id"], "dQw4w9WgXcQ")
            self.assertEqual(headers.get("X-AURA-Cache"), "miss")
            self.assertGreater(mocked.call_count, 0)

            status2, body2, headers2 = self.call(
                "POST", "/api/ai/suggest", {"mood": "CHILL", "limit": 4}
            )
            self.assertEqual(status2, 200)
            self.assertEqual(headers2.get("X-AURA-Cache"), "hit")
            self.assertEqual(body2["tracks"], body["tracks"])

    # ----------------------------------------------------------------- last.fm
    def test_lastfm_disabled_by_default(self):
        status, body, _ = self.call("GET", "/api/config")
        self.assertEqual(status, 200)
        self.assertFalse(body["lastfmConfigured"])
        self.assertFalse(body["capabilities"]["lastfm"])

        status, body, _ = self.call("POST", "/api/lastfm/token", {})
        self.assertEqual(status, 503)
        self.assertEqual(body["code"], "lastfm_unavailable")

    def test_lastfm_signature_matches_spec(self):
        cfg = replace(self.config, lastfm_api_key="k", lastfm_secret="secret")
        app = server.AuraApp(cfg)
        params = {
            "api_key": "k", "artist": "Artist", "format": "json",
            "method": "track.love", "sk": "session", "track": "Title",
        }
        self.assertEqual(app._lastfm_sign(params), "0e922fa4d29391f6e1c456a9ba2d7543")

    def test_lastfm_auth_and_scrobble_validation(self):
        cfg = replace(self.config, lastfm_api_key="key123", lastfm_secret="secret123")
        app = server.AuraApp(cfg)
        body = json.dumps({}).encode("utf-8")

        with patch.object(app, "_lastfm_call", return_value={"token": "abc123"}) as mocked:
            res = app._lastfm("/api/lastfm/token", body, "127.0.0.1")
            payload = json.loads(res.body)
            self.assertEqual(payload["token"], "abc123")
            self.assertIn("token=abc123", payload["authUrl"])
            mocked.assert_called_once_with("auth.getToken", {})

        with patch.object(app, "_lastfm_call", return_value={"ok": True}) as mocked:
            with self.assertRaises(server.ApiError) as missing:
                app._lastfm(
                    "/api/lastfm/scrobble",
                    json.dumps({"artist": "A", "track": "T"}).encode("utf-8"),
                    "127.0.0.1",
                )
            self.assertEqual(missing.exception.code, "lastfm_not_connected")

            scrobble = {
                "sessionKey": "user-session", "artist": "A", "track": "T",
                "album": "Album", "duration": 200, "timestamp": int(time.time()),
            }
            res = app._lastfm(
                "/api/lastfm/scrobble", json.dumps(scrobble).encode("utf-8"), "127.0.0.1"
            )
            self.assertTrue(json.loads(res.body)["ok"])
            method, params = mocked.call_args.args
            self.assertEqual(method, "track.scrobble")
            self.assertEqual(params["artist"], "A")
            self.assertEqual(params["album"], "Album")
            self.assertNotIn("api_sig", params)  # added by _lastfm_call, never trusted from client


if __name__ == "__main__":
    unittest.main(verbosity=2)
