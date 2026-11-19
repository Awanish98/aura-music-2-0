"""Deterministic tests for the AURA MUSIC web backend extension.

Run with: python test_music.py
No network calls are made: provider interaction is covered by the manual
smoke probe during QA, while these tests lock down parsing and routing.
"""

from __future__ import annotations

import json
import threading
import unittest
from urllib import error as urlerror
from urllib import request as urlrequest

import music
from server import build_server


class MusicParsingTests(unittest.TestCase):
    def test_parse_enhanced_lrc_preserves_exact_word_timestamps(self) -> None:
        synced = (
            "[00:18.89]<00:18.89>We're <00:19.25>no <00:19.53>strangers\n"
            "[00:22.82]<00:22.82>You <00:23.23>know <00:23.48>the <00:23.76>rules"
        )
        parsed = music.parse_lrc(synced)
        self.assertEqual(len(parsed["lines"]), 2)
        first = parsed["lines"][0]
        self.assertEqual(first["startMs"], 18890)
        self.assertEqual(first["text"], "We're no strangers")
        self.assertEqual(first["words"][0], {"text": "We're", "startMs": 18890, "endMs": 19250})
        self.assertEqual(first["words"][1]["startMs"], 19250)
        self.assertFalse(parsed["approximateWords"])

    def test_parse_line_only_lrc_marks_words_approximate(self) -> None:
        parsed = music.parse_lrc("[01:02.50]Some line without word timing")
        self.assertEqual(parsed["lines"][0]["startMs"], 62500)
        self.assertTrue(parsed["approximateWords"])
        self.assertTrue(all(w.get("approximate") for w in parsed["lines"][0]["words"]))

    def test_clean_title_removes_noise_but_keeps_song(self) -> None:
        self.assertEqual(
            music.clean_title("Rick Astley - Never Gonna Give You Up (Official Music Video) [HD]"),
            "Rick Astley - Never Gonna Give You Up",
        )

    def test_normalize_track_requires_stable_id(self) -> None:
        with self.assertRaises(music.MusicError):
            music.normalize_track({"title": "x", "url": "https://example.com/nope"})
        track = music.normalize_track({
            "id": "dQw4w9WgXcQ", "title": "Song (Official Video)", "channel": "Artist",
            "duration": 213.4, "thumbnails": [{"url": "https://i.ytimg.com/vi/x/hq.jpg"}],
        })
        self.assertEqual(track["id"], "dQw4w9WgXcQ")
        self.assertEqual(track["durationMs"], 213400)
        self.assertEqual(track["title"], "Song")

    def test_apk_info_reads_verified_asset(self) -> None:
        info = music.apk_info()
        self.assertEqual(info["versionName"], "1.4.6")
        self.assertEqual(info["versionCode"], 167)
        self.assertEqual(info["assets"]["apk"]["sha256"].lower(), info["sha256"].lower())
        self.assertGreater(info["assets"]["apk"]["bytes"], 100_000_000)

    def test_lyrics_query_strips_upload_noise(self) -> None:
        self.assertEqual(
            music.lyrics_query("Rick Astley - Never Gonna Give You Up Remaster", "Rick Astley"),
            ("Never Gonna Give You Up", "Rick Astley"),
        )
        self.assertEqual(
            music.lyrics_query("Never Gonna Give You Up (2022 Remaster)", "Rick Astley"),
            ("Never Gonna Give You Up", "Rick Astley"),
        )

    def test_pick_lyric_candidate_prefers_synced_near_duration(self) -> None:
        candidates = [
            {"trackName": "Never Gonna Give You Up", "artistName": "Rick Astley",
             "duration": 213, "plainLyrics": "plain only"},
            {"trackName": "Never Gonna Give You Up", "artistName": "Rick Astley",
             "duration": 210, "syncedLyrics": "[00:01.00]We're no strangers"},
        ]
        chosen = music._pick_lyric_candidate(
            candidates, 213, "Never Gonna Give You Up", "Rick Astley"
        )
        self.assertIn("syncedLyrics", chosen)
        self.assertEqual(chosen["duration"], 210)
        with self.assertRaises(music.MusicError):
            music._pick_lyric_candidate([], 213, "Unknown", "")

    def test_invalid_range_rejected_before_network(self) -> None:
        with self.assertRaises(music.MusicError) as ctx:
            music.open_stream("dQw4w9WgXcQ", "bytes=50-10")
        self.assertEqual(ctx.exception.status, 416)


class WebRouteTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.server = build_server()
        cls.port = cls.server.server_address[1]
        cls.thread = threading.Thread(target=cls.server.serve_forever, kwargs={"poll_interval": 0.05}, daemon=True)
        cls.thread.start()
        cls.base = f"http://127.0.0.1:{cls.port}"

    @classmethod
    def tearDownClass(cls) -> None:
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=5)

    def _get(self, path: str, headers: dict[str, str] | None = None):
        req = urlrequest.Request(self.base + path, headers=headers or {})
        with urlrequest.urlopen(req, timeout=30) as res:
            return res.status, dict(res.headers), res.read()

    def test_public_config(self) -> None:
        status, _, body = self._get("/api/config")
        payload = json.loads(body)
        self.assertEqual(status, 200)
        self.assertEqual(payload["brand"], "AURA MUSIC 2.0")
        self.assertFalse(payload["capabilities"]["cloudSync"])
        self.assertFalse(payload["capabilities"]["authentication"])

    def test_apk_metadata_route(self) -> None:
        status, _, body = self._get("/api/download/apk")
        payload = json.loads(body)
        self.assertEqual(status, 200)
        self.assertTrue(payload["assets"]["apk"]["url"].startswith("/downloads/"))

    def test_short_search_query_validation(self) -> None:
        with self.assertRaises(urlerror.HTTPError) as ctx:
            self._get("/api/catalog/search?q=x")
        self.assertEqual(ctx.exception.code, 400)

    def test_lyrics_validation(self) -> None:
        with self.assertRaises(urlerror.HTTPError) as ctx:
            self._get("/api/lyrics?artist=Rick")
        self.assertEqual(ctx.exception.code, 400)


if __name__ == "__main__":
    unittest.main(verbosity=2)
