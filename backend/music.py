"""Authorized music resolution for the AURA MUSIC 2.0 web experience.

Design rules (see docs/STREAMING_PROVIDER_POLICY.md):
* YouTube is the catalog/stream source already used by the Android app. This
  module resolves *public* videos through yt-dlp; no login, cookie, po-token or
  private API key is ever read, stored or returned to the browser.
* Metadata, stream resolution and playback are separate. A search result is not
  a playable stream until ``resolve()`` produces a current, authorized URL.
* Only the signed APK and public static assets are downloadable through this
  module. Copyrighted streamed audio is proxied with Range support and is never
  cached to disk.
* Every failure is surfaced as a typed ``MusicError`` so the UI can show a real
  error, never a fabricated success.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Any, Callable
from urllib import error as urlerror
from urllib import parse as urlparse
from urllib import request as urlrequest

try:
    import yt_dlp
except ImportError as exc:  # pragma: no cover - deployment guard
    raise RuntimeError("yt-dlp is required for music resolution (pip install yt-dlp)") from exc


USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
)

RESOLVE_TTL = 5.5 * 3600      # direct media URLs typically expire in ~6 hours
SEARCH_TTL = 10 * 60
LYRICS_TTL = 24 * 3600
DISCOVER_TTL = 15 * 60

VIDEO_ID_RE = re.compile(r"^[A-Za-z0-9_-]{11}$")
RANGE_RE = re.compile(r"^bytes=(\d+)-(\d*)$")
LINE_RE = re.compile(r"^\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\](.*)$")
WORD_RE = re.compile(r"<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>([^<]*)")

# Common YouTube title noise. The Android app ships a larger MetadataCleaner;
# this is the safe, dependency-free subset used to label search results.
TITLE_NOISE = re.compile(
    r"\s*[\(\[]?(?:official(?:\s*music)?\s*(?:video|audio|lyrics?)|audio\s*video|"
    r"lyrics?\s*video|music\s*video|hd|hq|4k|8k|full\s*song|visualizer|"
    r"live(?:\s*(?:performance|session))?|teaser|explicit|clean\s*version|"
    r"new\s+song\s+\d{4}|(?:\d{4}\s*)?re_?master(?:ed)?)[\)\]]?",
    re.IGNORECASE,
)

# Upload titles frequently prefix the channel and append release noise. These
# only steer the LRCLIB lookup; they never alter the displayed track title.
LYRIC_ARTIST_PREFIX_RE = re.compile(r"^\s*([^\-—–:]{2,60})\s*[-—–:]\s*", re.UNICODE)
LYRIC_SUFFIX_NOISE_RE = re.compile(
    r"\s*[\(\[]?\s*(?:\d{4}\s*)?(?:re-?master(?:ed)?|remix|radio\s*edit|"
    r"lyrics?|official(?:\s*(?:music)?\s*(?:video|audio))?|hd|hq|4k|8k|"
    r"visualizer|full\s*song|live(?:\s*(?:performance|session))?)\s*[\)\]]?\s*$",
    re.IGNORECASE,
)

DISCOVER_QUERIES: tuple[tuple[str, str], ...] = (
    ("trending", "ytsearch8:trending music hits 2026"),
    ("new-releases", "ytsearch8:new music releases 2026 official"),
    ("chill", "ytsearch8:chill lofi beats study"),
    ("bollywood", "ytsearch8:bollywood hits new songs"),
    ("focus", "ytsearch8:ambient focus music instrumental"),
)

_lock = threading.RLock()
_resolve_cache: dict[str, tuple[float, dict[str, Any]]] = {}
_search_cache: dict[str, tuple[float, dict[str, Any]]] = {}
_lyrics_cache: dict[str, tuple[float, dict[str, Any]]] = {}
_discover_cache: tuple[float, list[dict[str, Any]]] | None = None


class MusicError(Exception):
    """Client-safe music error with an HTTP status and stable code."""

    def __init__(self, status: int, message: str, code: str = "music_error") -> None:
        super().__init__(message)
        self.status = status
        self.message = message
        self.code = code


# --------------------------------------------------------------------------- helpers

def _text(value: Any, limit: int = 240) -> str:
    return value.strip()[:limit] if isinstance(value, str) else ""


def _ms(seconds: Any) -> int:
    try:
        return max(0, int(float(seconds) * 1000))
    except (TypeError, ValueError):
        return 0


def clean_title(title: str) -> str:
    cleaned = TITLE_NOISE.sub(" ", title or "")
    # Removing a noise phrase can leave an unmatched bracket behind, e.g.
    # "(Official Video) (4K Remaster)" -> "Song Remaster)".
    cleaned = re.sub(r"^\s*[\(\[]+\s*", "", cleaned)
    cleaned = re.sub(r"\s*[\)\]]+\s*$", "", cleaned)
    cleaned = re.sub(r"\s{2,}", " ", cleaned).strip(" -|")
    return cleaned[:120] or _text(title, 120)


def _thumbnail(info: dict[str, Any]) -> str:
    thumbs = info.get("thumbnails") or []
    for thumb in reversed(thumbs):
        url = _text(thumb.get("url") if isinstance(thumb, dict) else "", 600)
        if url.startswith("https://"):
            return url
    video_id = _text(info.get("id"), 32)
    return f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg" if VIDEO_ID_RE.fullmatch(video_id) else ""


def _artist(info: dict[str, Any]) -> str:
    for key in ("artist", "channel", "uploader", "creator"):
        value = _text(info.get(key), 120)
        if value:
            return value
    return "Unknown artist"


def normalize_track(info: dict[str, Any]) -> dict[str, Any]:
    video_id = _text(info.get("id"), 32)
    if not VIDEO_ID_RE.fullmatch(video_id):
        # Flat search entries sometimes expose only a page URL.
        match = re.search(r"(?:v=|youtu\.be/|/vi/)([A-Za-z0-9_-]{11})", _text(info.get("url"), 600))
        if match:
            video_id = match.group(1)
    if not VIDEO_ID_RE.fullmatch(video_id):
        raise MusicError(502, "The provider returned a track without a stable id.", "bad_provider_data")
    title = clean_title(_text(info.get("title"), 240))
    duration = info.get("duration")
    return {
        "id": video_id,
        "videoId": video_id,
        "title": title,
        "artist": _artist(info),
        "album": _text(info.get("album"), 160),
        "durationMs": _ms(duration),
        "artwork": _thumbnail(info),
        "type": "track",
        "provider": "youtube",
        "sourceAttribution": "YouTube",
        "pageUrl": f"https://www.youtube.com/watch?v={video_id}",
        "explicit": bool(info.get("availability") == "unavailable") if False else False,
        "year": info.get("upload_year") or info.get("release_year"),
    }


def _ydl_options(extra: dict[str, Any] | None = None) -> dict[str, Any]:
    options: dict[str, Any] = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": True,
        "socket_timeout": 20,
        "retries": 1,
        # Node is installed on the target host; yt-dlp needs a JS runtime for
        # some YouTube formats. Passing a dict is required by the yt-dlp API.
        "js_runtimes": {"node": {}},
    }
    if extra:
        options.update(extra)
    return options


def _with_player_client(extra: dict[str, Any] | None, client: str) -> dict[str, Any]:
    """Copy options and force one YouTube player client without mutating callers."""
    options = dict(extra or {})
    extractor_args = dict(options.get("extractor_args") or {})
    youtube_args = dict(extractor_args.get("youtube") or {})
    youtube_args["player_client"] = [client]
    extractor_args["youtube"] = youtube_args
    options["extractor_args"] = extractor_args
    return options


def _extract(query: str, extra: dict[str, Any] | None = None) -> dict[str, Any]:
    # Search normally works from every host, but the player API used for stream
    # resolution is IP-sensitive. Try the default first, then clients that are
    # known to resolve public videos without a login or po-token.
    attempts: list[dict[str, Any] | None] = [extra]
    for client in ("tv_embedded", "android_vr", "web_embedded", "mweb"):
        attempts.append(_with_player_client(extra, client))

    last_error: Exception | None = None
    for attempt in attempts:
        try:
            with yt_dlp.YoutubeDL(_ydl_options(attempt)) as ydl:
                info = ydl.extract_info(query, download=False)
            if isinstance(info, dict):
                return info
            last_error = MusicError(502, "The provider returned an unexpected response.", "bad_provider_data")
        except MusicError:
            raise
        except Exception as exc:  # network, extraction, rate limit, region, ...
            last_error = exc
    raise MusicError(
        502,
        "The music provider is temporarily unavailable. Try again.",
        "provider_unavailable",
    ) from last_error


# --------------------------------------------------------------------------- catalog

def search(query: str, limit: int = 18) -> dict[str, Any]:
    query = _text(query, 120)
    if len(query) < 2:
        raise MusicError(400, "Enter at least two characters to search.", "query_too_short")
    limit = max(1, min(30, limit))
    key = f"{query.lower()}|{limit}"
    now = time.monotonic()
    with _lock:
        cached = _search_cache.get(key)
        if cached and cached[0] > now:
            return cached[1]

    info = _extract(f"ytsearch{limit}:{query}", {"extract_flat": True, "playlistend": limit})
    tracks: list[dict[str, Any]] = []
    for entry in (info.get("entries") or [])[:limit]:
        if not isinstance(entry, dict):
            continue
        try:
            tracks.append(normalize_track(entry))
        except MusicError:
            continue
    if not tracks:
        raise MusicError(404, "No tracks matched that search.", "no_results")
    result = {"query": query, "tracks": tracks, "source": "YouTube", "approximate": True}
    with _lock:
        _search_cache[key] = (now + SEARCH_TTL, result)
    return result


def discover() -> dict[str, Any]:
    global _discover_cache
    now = time.monotonic()
    with _lock:
        if _discover_cache and _discover_cache[0] > now:
            return {"sections": _discover_cache[1], "cached": True}

    sections: list[dict[str, Any]] = []

    def load(title: str, query: str) -> dict[str, Any]:
        info = _extract(query, {"extract_flat": True, "playlistend": 8})
        tracks = []
        for entry in (info.get("entries") or [])[:8]:
            if isinstance(entry, dict):
                try:
                    tracks.append(normalize_track(entry))
                except MusicError:
                    continue
        return {"id": title, "title": title.replace("-", " ").title(), "tracks": tracks}

    with ThreadPoolExecutor(max_workers=3) as pool:
        futures = [pool.submit(load, sid, query) for sid, query in DISCOVER_QUERIES]
        for future in as_completed(futures):
            try:
                section = future.result()
                if section["tracks"]:
                    sections.append(section)
            except MusicError:
                continue
    order = {sid: i for i, (sid, _) in enumerate(DISCOVER_QUERIES)}
    sections.sort(key=lambda s: order.get(s["id"], 99))
    if not sections:
        raise MusicError(502, "Discover is unavailable right now. Try search instead.", "discover_unavailable")

    with _lock:
        _discover_cache = (now + DISCOVER_TTL, sections)
    return {"sections": sections, "cached": False}


def related(track_id: str, artist: str = "", title: str = "") -> dict[str, Any]:
    if not VIDEO_ID_RE.fullmatch(_text(track_id, 32)):
        raise MusicError(400, "A valid track id is required.", "invalid_id")
    query = _text(f"{artist} {title} similar songs", 120).strip() or _text(track_id, 32)
    return search(query, limit=12)


# --------------------------------------------------------------------------- stream

def resolve(video_id: str) -> dict[str, Any]:
    video_id = _text(video_id, 32)
    if not VIDEO_ID_RE.fullmatch(video_id):
        raise MusicError(400, "A valid track id is required.", "invalid_id")
    now = time.monotonic()
    with _lock:
        cached = _resolve_cache.get(video_id)
        if cached and cached[0] > now:
            return cached[1]

    info = _extract(
        f"https://www.youtube.com/watch?v={video_id}",
        {"format": "bestaudio[ext=m4a]/bestaudio/best", "noplaylist": True},
    )
    formats = info.get("requested_formats") or [info]
    chosen: dict[str, Any] = formats[0] if isinstance(formats, list) and formats else info
    url = _text(chosen.get("url"), 4000)
    if not url.startswith(("https://", "http://")):
        raise MusicError(502, "No playable audio stream is available for this track.", "no_stream")
    headers = dict(chosen.get("http_headers") or {})
    headers.setdefault("User-Agent", USER_AGENT)
    headers["Accept"] = "*/*"
    resolved = {
        "id": video_id,
        "url": url,
        "headers": {str(k): str(v) for k, v in headers.items()},
        "expiresAt": time.time() + RESOLVE_TTL,
        "durationMs": _ms(info.get("duration")),
        "title": clean_title(_text(info.get("title"), 240)),
    }
    with _lock:
        _resolve_cache[video_id] = (now + RESOLVE_TTL, resolved)
        if len(_resolve_cache) > 400:
            stale = [k for k, v in _resolve_cache.items() if v[0] <= now]
            for k in stale:
                _resolve_cache.pop(k, None)
    return resolved


def stream_info(video_id: str) -> dict[str, Any]:
    resolved = resolve(video_id)
    return {
        "id": video_id,
        "streamUrl": f"/api/stream/audio?id={video_id}",
        "durationMs": resolved["durationMs"],
        "title": resolved["title"],
        "expiresAt": resolved["expiresAt"],
        "provider": "youtube",
    }


def open_stream(video_id: str, range_header: str | None) -> Any:
    """Return an upstream response context manager for a Range-aware proxy.

    The caller owns closing it and streaming its bytes. ``None`` range means a
    full-body request. An invalid Range is rejected before any upstream call.
    """
    if range_header:
        match = RANGE_RE.fullmatch(range_header.strip())
        if not match:
            raise MusicError(416, "Invalid byte range.", "invalid_range")
        start = int(match.group(1))
        end = int(match.group(2)) if match.group(2) else 0
        if end and end < start:
            raise MusicError(416, "Invalid byte range.", "invalid_range")

    resolved = resolve(video_id)
    headers = dict(resolved["headers"])
    if range_header:
        headers["Range"] = range_header.strip()
    request = urlrequest.Request(resolved["url"], headers=headers, method="GET")
    try:
        return urlrequest.urlopen(request, timeout=30)
    except urlerror.HTTPError as exc:
        if exc.code == 416:
            raise MusicError(416, "Requested range is not satisfiable.", "range_not_satisfiable") from exc
        if exc.code in (401, 403, 429):
            _invalidate(video_id)
            raise MusicError(502, "The stream URL expired. Retry to resolve it again.", "stream_expired") from exc
        raise MusicError(502, "The audio stream could not be opened.", "stream_open_failed") from exc
    except Exception as exc:
        raise MusicError(502, "The audio stream could not be reached.", "stream_unreachable") from exc


def _invalidate(video_id: str) -> None:
    with _lock:
        _resolve_cache.pop(video_id, None)


# --------------------------------------------------------------------------- lyrics

def _ts_to_ms(minutes: str, seconds: str, fraction: str | None) -> int:
    frac = (fraction or "0").ljust(3, "0")[:3]
    return (int(minutes) * 60 + int(seconds)) * 1000 + int(frac)


def parse_lrc(synced: str) -> dict[str, Any]:
    """Parse LRC / enhanced LRC into lines with genuine word timestamps.

    When the source includes ``<MM:SS.xx>`` word tags, those exact words and
    timings are preserved. When only line timestamps exist, words are marked
    ``approximate`` and distributed across the line purely for the karaoke fill.
    """
    lines: list[dict[str, Any]] = []
    for raw in (synced or "").splitlines():
        match = LINE_RE.match(raw.strip())
        if not match:
            continue
        start = _ts_to_ms(match.group(1), match.group(2), match.group(3))
        rest = match.group(4)
        word_matches = list(WORD_RE.finditer(rest))
        words: list[dict[str, Any]] = []
        approximate = False
        if word_matches:
            for i, wm in enumerate(word_matches):
                wstart = _ts_to_ms(wm.group(1), wm.group(2), wm.group(3))
                wend = (
                    _ts_to_ms(word_matches[i + 1].group(1), word_matches[i + 1].group(2), word_matches[i + 1].group(3))
                    if i + 1 < len(word_matches) else start + 2000
                )
                text = wm.group(4).strip()
                if text:
                    words.append({"text": text, "startMs": wstart, "endMs": max(wstart, wend)})
        else:
            tokens = [t for t in rest.split() if t]
            if tokens:
                approximate = True
                span = 2000
                step = span // len(tokens)
                for i, token in enumerate(tokens):
                    wstart = start + i * step
                    words.append({"text": token, "startMs": wstart, "endMs": wstart + step,
                                  "approximate": True})
        text = " ".join(word["text"] for word in words) if word_matches else rest.strip()
        text = re.sub(r"\s{2,}", " ", text).strip()
        if not text:
            continue
        end = words[-1]["endMs"] if words else start + 2000
        lines.append({"startMs": start, "endMs": max(end, start + 1), "text": text,
                      "words": words, "approximate": approximate})
    lines.sort(key=lambda line: line["startMs"])
    return {"lines": lines, "approximateWords": any(line.get("approximate") for line in lines)}


def lyrics_query(title: str, artist: str) -> tuple[str, str]:
    """Return LRCLIB-friendly (title, artist) from a provider track label.

    Search results are labelled from YouTube uploads, e.g.
    ``"Rick Astley - Never Gonna Give You Up Remaster"`` with artist
    ``"Rick Astley"``. LRCLIB indexes the canonical song title, so strip the
    matching artist prefix and release noise before looking lyrics up.
    """
    clean = clean_title(title)
    clean_artist = clean_title(artist)
    unknown_artist = (not clean_artist) or clean_artist.casefold().startswith("unknown")
    prefix = LYRIC_ARTIST_PREFIX_RE.match(clean)
    if prefix:
        lead = prefix.group(1).strip(" -|\"'“”")
        matches = clean_artist and (
            lead.casefold() in clean_artist.casefold() or clean_artist.casefold() in lead.casefold()
        )
        if lead and (matches or unknown_artist):
            clean = clean[prefix.end():].strip()
            if unknown_artist:
                clean_artist = lead
    previous = None
    while previous != clean:
        previous = clean
        clean = LYRIC_SUFFIX_NOISE_RE.sub("", clean).strip(" -|\"'“”")
    return clean or clean_title(title), clean_artist


def _lrclib_json(path: str, params: dict[str, Any], *, missing_ok: bool = False) -> Any:
    url = "https://lrclib.net/api/" + path + "?" + urlparse.urlencode(params)
    request = urlrequest.Request(url, headers={
        "User-Agent": "AuraMusicWeb/2.0 (LRCLIB client; local deployment)",
        "Accept": "application/json",
    })
    try:
        with urlrequest.urlopen(request, timeout=15) as response:
            return json.loads(response.read(400_000).decode("utf-8"))
    except urlerror.HTTPError as exc:
        if exc.code == 404:
            if missing_ok:
                return []
            raise MusicError(404, "No synced lyrics were found for this track.", "lyrics_not_found") from exc
        raise MusicError(502, "The lyrics service is temporarily unavailable.", "lyrics_unavailable") from exc
    except Exception as exc:
        raise MusicError(502, "The lyrics service could not be reached.", "lyrics_unreachable") from exc


def _pick_lyric_candidate(
    candidates: Any, duration: int, title: str, artist: str
) -> dict[str, Any]:
    """Choose the closest synced LRCLIB match from a list of search results."""
    scored: list[tuple[int, int, int, int, dict[str, Any]]] = []
    wanted_title = title.casefold()
    wanted_artist = artist.casefold()
    for item in candidates if isinstance(candidates, list) else []:
        if not isinstance(item, dict):
            continue
        synced = _text(item.get("syncedLyrics"), 400_000)
        plain = _text(item.get("plainLyrics"), 400_000)
        if not synced and not plain:
            continue
        found_title = _text(item.get("trackName"), 200).casefold()
        found_artist = _text(item.get("artistName"), 200).casefold()
        title_score = (
            0 if found_title == wanted_title
            else 1 if wanted_title and (wanted_title in found_title or found_title in wanted_title)
            else 2
        )
        artist_score = (
            0 if found_artist == wanted_artist
            else 1 if wanted_artist and (wanted_artist in found_artist or found_artist in wanted_artist)
            else 2
        )
        try:
            found_duration = int(item.get("duration") or 0)
        except (TypeError, ValueError):
            found_duration = 0
        duration_score = abs(found_duration - duration) if duration and found_duration else 0
        scored.append((0 if synced else 1, title_score, artist_score, duration_score, item))
    if not scored:
        raise MusicError(404, "No synced lyrics were found for this track.", "lyrics_not_found")
    scored.sort(key=lambda row: row[:4])
    return scored[0][4]


def get_lyrics(title: str, artist: str, duration_ms: int = 0) -> dict[str, Any]:
    title = _text(title, 200)
    artist = _text(artist, 200)
    if not title:
        raise MusicError(400, "A track title is required for lyrics.", "invalid_title")
    duration = max(0, int(duration_ms or 0) // 1000)
    key = f"{title.lower()}|{artist.lower()}|{duration}"
    now = time.monotonic()
    with _lock:
        cached = _lyrics_cache.get(key)
        if cached and cached[0] > now:
            return cached[1]

    lookup_title, lookup_artist = lyrics_query(title, artist)
    if lookup_artist.casefold().startswith("unknown"):
        lookup_artist = ""
    params: dict[str, Any] = {"track_name": lookup_title}
    if lookup_artist:
        params["artist_name"] = lookup_artist
    if duration:
        params["duration"] = duration

    payload: Any = None
    try:
        payload = _lrclib_json("get", params)
    except MusicError as exc:
        if exc.code != "lyrics_not_found":
            raise
    if payload is None:
        # YouTube labels do not always match LRCLIB's canonical title. Search
        # and choose the closest synced match instead of reporting a false 404.
        candidates = _lrclib_json("search", {
            "track_name": lookup_title,
            **({"artist_name": lookup_artist} if lookup_artist else {}),
        }, missing_ok=True)
        payload = _pick_lyric_candidate(candidates, duration, lookup_title, lookup_artist)

    synced = payload.get("syncedLyrics") or ""
    plain = payload.get("plainLyrics") or ""
    parsed = parse_lrc(synced)
    result = {
        "source": "LRCLIB",
        "hasWordSync": any(line.get("words") and not line.get("approximate") for line in parsed["lines"]),
        "instrumental": bool(payload.get("instrumental")),
        "title": _text(payload.get("trackName"), 200) or title,
        "artist": _text(payload.get("artistName"), 200) or artist,
        "album": _text(payload.get("albumName"), 200),
        "plain": plain,
        "lines": parsed["lines"],
        "approximateWords": parsed["approximateWords"] and not payload.get("hasWordSync"),
        "attribution": "Lyrics provided by LRCLIB",
    }
    if not result["lines"]:
        raise MusicError(404, "This track has no synchronized lyrics.", "lyrics_not_found")
    with _lock:
        _lyrics_cache[key] = (now + LYRICS_TTL, result)
    return result


# --------------------------------------------------------------------------- download

def apk_info(web_root: str | None = None) -> dict[str, Any]:
    """Return verified APK metadata read from the shipped asset and docs."""
    root = web_root or os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "website")
    downloads = os.path.join(root, "downloads")
    apk = os.path.join(downloads, "AURA-Music-UniversalGms-Release.apk")
    aab = os.path.join(downloads, "AURA-Music-UniversalGms-Release.aab")
    apk_hash = "F46917BAE7299546ECD01815728F9DE783F641A9988F56C05D3C97158148AB16"
    aab_hash = "6C9F33EF5B55B53B4C4A8B136601357D1CF45F8AFF9FC1451AE78F55C07305D3"
    release_base = os.environ.get(
        "AURA_RELEASE_BASE",
        "https://github.com/Awanish98/aura-music-2-0/releases/download/v1.4.6",
    ).rstrip("/")
    release_urls = {
        "apk": os.environ.get("AURA_APK_URL", f"{release_base}/AURA-Music-UniversalGms-Release.apk"),
        "aab": os.environ.get("AURA_AAB_URL", f"{release_base}/AURA-Music-UniversalGms-Release.aab"),
    }
    fallback_meta = {
        "apk": {"file": os.path.basename(apk), "bytes": 149_665_142, "sha256": apk_hash},
        "aab": {"file": os.path.basename(aab), "bytes": 186_386_586, "sha256": aab_hash},
    }
    info = {
        "brand": "AURA MUSIC 2.0",
        "appName": "AURA MUSIC",
        "package": "echo.music.iad1tya",
        "versionName": "1.4.6",
        "versionCode": 167,
        "releaseDate": "2026-11-19",
        "minAndroid": 8,
        "sha256": apk_hash,
        "changelog": [
            "15 Premium Lyrics looks with AUTO mood matching and CODE POETRY mode",
            "3D PATTERN visualizer, canvas and album-art reactive backgrounds",
            "Media3 playback with queue, downloads and Android Auto / Cast support",
            "Music Flow, recognition, scrobbling and cloud library integrations",
        ],
        "assets": {},
    }
    for label, path in (("apk", apk), ("aab", aab)):
        fallback = fallback_meta[label]
        if os.path.isfile(path):
            size = os.path.getsize(path)
            digest = hashlib.sha256()
            with open(path, "rb") as handle:
                for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                    digest.update(chunk)
            url = os.environ.get(f"AURA_{label.upper()}_URL") or f"/downloads/{os.path.basename(path)}"
            sha = digest.hexdigest().upper()
        else:
            size = fallback["bytes"]
            url = release_urls[label]
            sha = fallback["sha256"]
        info["assets"][label] = {
            "file": fallback["file"],
            "url": url,
            "bytes": size,
            "sizeMb": round(size / (1024 * 1024), 1),
            "sha256": sha,
        }
    return info
