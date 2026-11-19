# AURA MUSIC 2.0 — Web QA record

Verification performed after the Phase 2/3 web implementation. All results below
come from real requests, real playback and real DOM inspection — not from mocked
or fabricated responses.

## Environment

- Backend: `python backend/server.py --host 127.0.0.1 --port 8787`
- Browser QA tab: in-app Chromium, live viewport **538 × 672** (mobile breakpoint)
- Catalog/audio source: public YouTube via `yt-dlp` + Node JS runtime
- Lyrics source: LRCLIB `/api/get` with `/api/search` fallback

## Automated checks

| Check | Result |
|---|---|
| `python backend/test_server.py` | 10 tests, OK |
| `python backend/test_music.py` | 12 tests, OK (includes exact-word LRC, approximate LRC, lyric query normalization, candidate scoring, Range rejection, APK hash) |
| `python -m py_compile backend/music.py backend/server.py` | OK |
| `node --check` on all `website/js/*.js` and `website/sw.js` | OK, zero failures |
| Temporary probe scripts `backend/_probe_*.py` | Removed |

## Catalog and streaming (real data)

- `/api/config` returned the AURA MUSIC 2.0 capability set: `cloudSync: false`, `authentication: false`.
- `/api/catalog/search?q=never gonna give you up` returned **24 real YouTube matches**; first id `dQw4w9WgXcQ`.
- Discover returned real sections: trending, new releases, chill, bollywood, focus.
- First result played a real stream: `GET /api/stream/audio` → **206**, `Content-Type: audio/mp4`, `Content-Range: bytes 0-1023/3449447`, `Accept-Ranges: bytes`.
- Audio element reached `currentTime ≈ 16 s`, `duration 213.09 s`, `readyState 4`, `playing: true`, no media error.

## Player, queue and Media Session

| Action | Observed |
|---|---|
| Pause / play (`k`) | `paused` toggled `true` → `false` |
| Seek to 50 % | `currentTime ≈ 108 s`, 51 % of duration |
| Shuffle | Active state `on` |
| Repeat cycle | `all` → `one` → `off` |
| Volume | `audio.volume = 0.35` |
| Media Session | `metadata.title` set to current track, `playbackState: playing` |
| Full player | Overlay opened; canvas present; artwork from real thumbnail |
| Visualizer | Cycled all 8 modes: `SPECTRUM`, `RADIAL`, `WAVE`, `PARTICLES`, `RINGS`, `AURORA`, `BARS`, **`3D PATTERN`** |
| Keyboard | `ArrowRight` +5 s, `m` mute, `Escape` closes overlay, `/` focuses top search |

## LyricsPlay

- Live `/api/lyrics` for the provider title `Rick Astley - Never Gonna Give You Up Remaster` (artist `Rick Astley`, 213 s) returned **58 lines / 370 words** from LRCLIB with `hasWordSync: true` and `approximateWords: false`.
- First line preserved source timing exactly: `[00:18.89]` → `18890 ms`, words `We're 18890–19250`, `no 19250–19530`, `strangers 19530–20070`, `to 20070–20330`, `love 20330–20890`.
- Immersive overlay rendered 58 line nodes and 370 word nodes; at `currentTime ≈ 21 s` the active line was **“We're no strangers to love”**, current word **“love”**, with 4 sung words highlighted.
- Look picker exposed **16 options** (`AUTO` + the 15 preserved AURA looks); AUTO selected a mood look from the real track.
- Line-only LRC words are marked `approximate` by the parser and the UI shows the explicit note “Word timing is estimated from line timing for this track; line timing is exact.” Exact word-synced tracks do **not** show that note.

## Views and stateful flows

| Route / flow | Observed |
|---|---|
| `/library` | 24 real items, tabs for Songs / Playlists / Favorites / History / Local files |
| Favorites | Adding a track produced 1 row in the Favorites tab and persisted to `aura2:v1:library` |
| Playlist creation | `window.prompt` → “QA Mix” created a 50-track playlist and opened its tab |
| `/history` | 2 recently played real tracks; Clear history action present |
| `/queue` | 66 queued rows with jump/remove controls |
| `/musicflow` | Start flow added **31 real related tracks** via three public search pages |
| `/video` | 12 video cards; play button inserted a `youtube-nocookie.com/embed/<id>` iframe |
| `/downloads` | Importing a synthetic local MP3 produced 1 local row and a toast; no copyrighted audio cached |
| `/download` | Real signed APK link, version **1.4.6 (167)**, 142.7 MB, release date, SHA-256, install steps, changelog |
| `/settings` | `lyricLook` set to `neon` persisted to `aura2:v1:settings` |
| `/lyrics` | 15 look tiles + overlay/full-player entry points |

APK download verification: `GET /api/download/apk` → 200; the actual file
`/downloads/AURA-Music-UniversalGms-Release.apk` served **149,665,142 bytes** with
`Content-Type: application/vnd.android.package-archive` and HTTP 200. SHA-256 of
the shipped file matches `F46917BAE7299546ECD01815728F9DE783F641A9988F56C05D3C97158148AB16`.

## Responsive and accessibility

- Live mobile layout at 538 px: no horizontal overflow (`scrollWidth === clientWidth === 529`), sidebar hidden, fixed 5-column bottom nav and fixed mini player.
- CSS breakpoints verified at 1060 px (sidebar collapses to icons), 760 px (bottom nav replaces sidebar) and 400 px (secondary top actions hidden); `prefers-reduced-motion` gates in `base.css` and `tokens.css`.
- Controls are native buttons/links/inputs, shortcuts ignore keystrokes while typing in inputs, `/` focuses search, `Escape` closes overlays.

## Real bugs found and fixed during QA

1. Direct `#/search?q=…` never ran the query → `router.js` now calls `runSearch(q)` and `views.js` records `loadedQuery`.
2. Service worker cache-first served stale JS after deploys → static assets are network-first with cache fallback; version bumped to `aura-web-2.0.1`.
3. Backend static cache keyed only by path served stale edits → key now includes file mtime and size.
4. Seek range (0–1000) was passed as seconds to a millisecond API → fixed to use `state.player.duration`.
5. Search filter chips used values that did not match the filter code (`videos` instead of `long`/`quick`) → corrected values and labels.
6. Title cleanup left an unmatched `)` (e.g. `… Remaster)`) → unmatched leading/trailing brackets are now stripped.
7. Lyrics returned a false 404 for provider titles with an artist prefix / `Remaster` → `lyrics_query()` normalizes the lookup and LRCLIB `/api/search` provides a scored fallback; live lookup now returns genuine word sync.

Two stale 404 console entries from the pre-fix lyric title remain in the tab's
console history; the current request succeeds and no current 4xx/5xx was observed.

## Genuine limitations

- There is no `2.0` APK. The web product is branded AURA MUSIC 2.0; the download page honestly ships the verified signed **1.4.6 / 167** binary.
- No shared sync/auth backend exists. Favorites, playlists, history, files and preferences are local-first only.
- Copyrighted streamed audio is Range-proxied but never cached. Offline playback is limited to user-imported, user-owned audio files.
- The web catalog uses public YouTube search through `yt-dlp`; titles are provider labels, not curated official metadata. No login cookie, po-token or private key is used or exposed.
- Static `/downloads/*.apk` requests return 200 with the full body rather than 206; the `/api/download/apk` endpoint does support Range. Downloads work, but resumable downloads through the static URL are not implemented.
- `browser.network.list` failed with a serialization error in this harness, so request verification used DOM state, console messages, direct `fetch` probes and backend logs instead of the request list.
- Desktop-width visual emulation was unavailable in this harness (fixed 538 px viewport). Desktop layout was verified from the CSS breakpoint rules plus the earlier legacy 1440 px pass, not from a live screenshot of the new app shell.
- Deployment to Netlify and Play Console still requires the user's credentials/access.
