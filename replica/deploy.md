# Deploy checklist: AURA MUSIC 2.0

Date: 2026-11-20 · Repository: <https://github.com/Awanish98/aura-music-2-0> ·
Publish export: `P:\All-project\P__youtube-creat\AURA-Music-2.0-Publish`.

## Architecture

| Part | Host | Purpose |
|---|---|---|
| `website/` | GitHub Pages | Static PWA shell, player UI, LyricsPlay, settings, APK page |
| `backend/` | Render (Docker, free web service) | Real catalog, Range audio proxy, LRCLIB lyrics, APK metadata |
| Signed APK/AAB | GitHub Release `v1.4.6` | Working downloads from static hosting |
| Library/settings | Browser `localStorage` | Local-first persistence; no account backend exists |

The static site alone cannot search, stream or load lyrics. `website/config.js`
points the frontend at `https://aura-music-2-0-api.onrender.com/api` in production
and at `http://127.0.0.1:8787/api` on localhost. A one-off override is available
with `?api=https://your-backend/api`, which is stored in `localStorage` under
`aura2:v1:apiBase`. No token, cookie or private credential is ever placed in the frontend.

## Files prepared

- `.github/workflows/deploy-pages.yml` — builds `_site/` from `website/` and deploys with `actions/deploy-pages@v4`.
- `render.yaml` — Render Blueprint for `aura-music-2-0-api`, free plan, Docker, health check `/api/health`.
- `Dockerfile` — Python 3.12 slim + `yt-dlp` + Node JS runtime, binds `0.0.0.0:10000`.
- `.dockerignore` — keeps binaries, caches and nested repos out of the image.
- `website/config.js` — API base and GitHub Release asset URLs.
- `backend/music.py` — APK metadata falls back to release URLs when local binaries are absent on the host.

## Preflight (all local checks green)

- [x] `python backend/test_server.py` — 15 tests, OK (AI suggest validation/fallback/cache, Last.fm signature + route validation).
- [x] `python backend/test_music.py` — 13 tests, OK (new: unknown-uploader artist derivation for lyrics).
- [x] `python -m py_compile backend/music.py backend/server.py` — OK.
- [x] `node --check` on `website/config.js`, `website/sw.js` and all `website/js/*.js` — OK.
- [x] Browser QA: 58 lines / 370 exact word spans rendered before playback, in Cinematic and AMOLED views; active line/word tracking at 30 s; playback started from LyricsPlay preview and lyrics survived the track-change reload.
- [x] Glass materials verified in computed styles: sidebar `blur(34px) saturate(1.55)`, panels `blur(28px) saturate(1.55)`, no horizontal overflow at desktop width.
- [x] Rebrand sweep: `python .claude/skills/replica-brand/sweep.py . --config replica/brand.json --json` → `[]`.
- [x] Store listing lint: 0 errors, 2 intentional all-caps warnings.
- [x] Signed release verified: valid v2 signature, cert SHA-256 `740963cf50ba9ed77b0c2fc91a91d02c1e0fe9fec15dc8342dc5911ceec74cd3`, 16 KB page alignment clean.

## Release package

| Check | Result |
|---|---|
| Package / version | `echo.music.iad1tya`, versionCode `167`, versionName `1.4.6` |
| APK SHA-256 | `F46917BAE7299546ECD01815728F9DE783F641A9988F56C05D3C97158148AB16` (149,665,142 bytes) |
| AAB SHA-256 | `6C9F33EF5B55B53B4C4A8B136601357D1CF45F8AFF9FC1451AE78F55C07305D3` (186,386,586 bytes) |
| 16 KB pages | zipalign `-c -P 16 -v 4` exit 0; ELF `BAD_COUNT 0`; AAB `PAGE_ALIGNMENT_16K` |

APK/AAB are gitignored. They are attached to the published `v1.4.6` GitHub
Release so the download page works from GitHub Pages. GitHub's 100 MB asset
limit was worked around by uploading 8 MB chunks to the draft release
(`scripts/upload_release_chunks.py`, resumable + duplicate-safe) and joining
them with the `Assemble signed release assets` Action
(`.github/workflows/assemble-release-assets.yml` +
`scripts/assemble_release_assets.py`) when the release was published. The
Action verifies both SHA-256 hashes before uploading the final assets.

## Deployment steps

1. Push the publish export to `origin/main`:
   `git -C P:\youtube-creat\AURA-Music-2.0-Publish push origin main`
2. Enable Pages for workflow builds:
   `gh api -X PUT /repos/Awanish98/aura-music-2-0/pages -f build_type=workflow`
3. Watch the Pages action and confirm <https://awanish98.github.io/aura-music-2-0/>.
4. In Render, create a new **Blueprint** from the repository and apply `render.yaml`,
   or create a Docker web service named `aura-music-2-0-api` with the same env vars.
5. Confirm `GET https://aura-music-2-0-api.onrender.com/api/health` returns 200.
   On a cold free-tier start this can take ~50 s; the frontend retries once.
6. Load the Pages URL, confirm `BACKEND ONLINE`, search a real song, play it and
   open LyricsPlay in Cinematic and AMOLED views.
7. If the Render service name changes, update `website/config.js` or open the site
   once with `?api=https://actual-service.onrender.com/api`.
8. For AI Mood Radio, add `GROQ_API_KEY` in the Render dashboard for
   `aura-music-2-0-api` (or apply the Blueprint and fill the `sync:false` value).
   Never put the key in `website/`, git or a query string. After redeploy,
   `/api/health` must report `groq_configured: true` and `model: "qwen/qwen3.8-27b"`.

## Play Console (user steps)

1. Sign in at <https://play.google.com/console> → **Create app** → name **AURA MUSIC: Lyric Visuals**, default language English (US).
2. **Production → Create new release** → upload `P:\youtube-creat\AURA-Music-UniversalGms-Release.aab` (versionCode 167, versionName 1.4.6).
3. Paste the store text from `replica/launch/listing.json` (`google_play` block).
4. **App content**: declare the microphone permission and optional backend; link the hosted privacy policy; select “No” for ads and children.
5. **Data safety**: no data uploaded; local processing only; optional state sent to the user’s own backend.
6. Add screenshots and a 512×512 feature graphic (`favicon-512.png` is ours).
7. Internal testing first, then Production rollout. Keep `Echo-Music/keystore.jks` safe.

## Verified live state (2026-11-20)

| Surface | Check | Result |
|---|---|---|
| Pages | `https://awanish98.github.io/aura-music-2-0/` | live, mobile word-sync engine + 18 classic looks present |
| Backend | `GET /api/health` on `aura-music-2-0-api.onrender.com` | HTTP 200 |
| Config | `GET /api/config` | catalog/streaming/lyrics/APK capabilities available |
| Search | `GET /api/catalog/search?q=Never Gonna Give You Up` | real YouTube results |
| Lyrics | `GET /api/lyrics?...` | LRCLIB result with `hasWordSync: true` |
| Audio | `GET /api/stream/audio?id=dQw4w9WgXcQ` with Range | HTTP 206, `audio/mp4` |
| Browser QA | live Pages + live backend | `BACKEND ONLINE`, playback advanced to 4.5 s, 58 lines / 370 words, mobile active-word CSS vars updating, classic toggle returns `data-engine="classic"`, `look="aurora"`, no console errors |
| Downloads | live `#/download` page | signed 1.4.6 (167) identity, correct SHA-256, release URLs return 206 |

**Status:** frontend, backend and signed downloads are live and verified. The
Play Console rollout remains a user step (Google account). No credentials were
entered or stored by the agent; Render login was completed by the user in the
browser.

## UI alignment QA (2026-11-20)

- Compact 761–1060px sidebar: nav labels are real `.nav-label` spans and hide cleanly; every rail control is a centred 52×44 square with `title`/`aria-label`; the `PLAYLISTS` heading and APK copy no longer wrap.
- Top-bar APK keeps both its icon and `APK` text after icon hydration: `data-icon` now lives on an inner span, so `hydrateIcons()` no longer wipes the label.
- Geometry sweep at 320/390/760/800/1000/1060/1440px across home, search, library, musicflow, video, download, settings and lyrics: no horizontal overflow and no clipped text outside intentional scrollers.
- Narrow fixes: `.settings-grid` becomes one column at ≤400px so download/settings cards fit a 320px viewport; the LyricsPlay look menu is viewport-anchored at ≤700px and stays fully on screen down to 320px.
- Overlay QA at 320/390/760/1000/1440px: mini-player, full player, LyricsPlay and look menu stay inside the viewport; the bottom nav sits flush above the mini-player. Oversized blurred backgrounds are decorative and clipped by their overlay. No app JavaScript errors were observed.
- Service worker cache version bumped to `aura-web-2.0.4` so returning users receive the rebuilt shell immediately.

## AI Mood Radio (2026-11-20)

- New public `/api/ai/suggest` sends a mood/vibe to Groq (`qwen/qwen3.8-27b`,
  strict JSON) and resolves every proposed title/artist through the real
  provider search. The model only proposes a plan; it never fabricates catalog
  entries. Responses include `source`, `degraded`, `theme`, `energy`, suggested
  picks, resolved tracks and `X-AURA-Cache`.
- The key is server-side only (`GROQ_API_KEY` env var, `sync:false` in
  `render.yaml`). The browser calls an IP-rate-limited endpoint and cannot read
  the secret. There is no key in `website/`, git history or URLs. `gpt-oss-20b`
  failed JSON validation in testing; Qwen returned valid JSON reliably.
- Local smoke test with the env var set: `/api/health` →
  `groq_configured:true`, `model:qwen/qwen3.8-27b`; `POST /api/ai/suggest`
  → `source:"groq"`, `degraded:false`, 4 real resolved tracks. With no key the
  endpoint degrades to `source:"local"` using real provider results only.
- New `#/mood` route with 12 mood chips and a free-text vibe field, sidebar and
  bottom-nav entries, home-page promo chips, loading/error/empty states, and
  "Play in cinematic lyrics" (classic engine + `CINEMATIC` look + LyricsPlay).
- Browser QA at 320/390/760/1440px: no horizontal overflow, 6-column bottom nav,
  12 chips and search fit; home promo chips navigate to `#/mood` and build a mix;
  `ai-cinematic` opened LyricsPlay with `data-engine="classic"` and
  `look="cinema"`, started real playback, and produced no console errors.
- Resolved: `GROQ_API_KEY` is set in the Render dashboard (as an Environment
  Variable, not a Secret File). Live `/api/health` reports
  `groq_configured:true`, `model:"qwen/qwen3.8-27b"` and frontend payloads
  return `source:"groq"`, `degraded:false`.

## Last.fm scrobbling (2026-11-20)

- Optional scrobbling, now-playing and track.love. The browser never holds the
  shared secret: it calls signed backend proxies (`auth.getToken`,
  `auth.getSession`, `track.updateNowPlaying`, `track.scrobble`,
  `track.love`/`track.unlove`) with the user's own session key only.
- `LASTFM_API_KEY` and `LASTFM_SECRET` are `sync:false` env vars in
  `render.yaml`; values are filled only in the Render dashboard. All Last.fm
  routes are IP-rate-limited and return `503 lastfm_unavailable` when unset.
- Connect flow: Settings → **Connect to Last.fm** (opens the approval popup) →
  approve in Last.fm → **I approved — finish connecting**. The session key is
  stored in `localStorage` under `aura2:v1:settings`; **Disconnect** clears it.
- Scrobbling thresholds follow the Android defaults: min 30 s and 50 % of the
  track, capped at 240 s. Configurable in Settings. All calls are best-effort
  and can never interrupt playback.
- New `website/js/lastfm.js` is in the service-worker shell; cache bumped to
  `aura-web-2.0.5`. Backend test suite now has 15 tests including a fixed
  signature vector (`0e922fa4d29391f6e1c456a9ba2d7543`).
- **Pending user step:** approve AURA in the Last.fm popup, then finish connect
  in Settings. Verify `/api/config` shows `lastfmConfigured:true` and
  `capabilities.lastfm:true` after the Render redeploy.
