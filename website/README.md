# AURA MUSIC 2.0 website

Premium, dependency-free web application. No framework, package manager or build
step: the shell is static HTML/CSS/ES modules, and an optional Python stdlib
backend supplies real catalog, stream and lyrics data.

## Run locally

```powershell
python backend/server.py --host 127.0.0.1 --port 8787
# open http://127.0.0.1:8787/
```

`yt-dlp` and a Node.js JS runtime are required on the backend host for YouTube
resolution. Lyrics come from LRCLIB. No API keys, login cookies or po-tokens are
read or exposed to the browser.

## Structure

- `index.html` — app shell, navigation, player and LyricsPlay overlays
- `config.js` — backend API base, GitHub Release URLs; override with `?api=…`
- `js/` — state, storage, API client, router, player/visualizer, lyrics and views
- `styles/` — AMOLED tokens, layout, components, player, lyrics and premium glass/motion CSS
- `manifest.webmanifest`, `sw.js`, icons — installable PWA; static shell only is cached
- `legacy/` — archived original landing page (kept for reference)
- `downloads/` — signed APK/AAB payloads (generated locally, not committed)

## Backend endpoints

`/api/config`, `/api/download/apk`, `/api/catalog/search`, `/api/catalog/discover`,
`/api/catalog/related`, `/api/stream/info`, `/api/stream/audio` (Range-aware proxy),
`/api/lyrics`. See `backend/README.md` and `docs/WEB_2_0_QA.md`.

## What is and is not offline

Favorites, playlists, history, settings and user-imported local audio stay on the
device. Copyrighted streamed audio is never cached. There is no account or cloud
sync backend, so the web app is local-first by design.

LyricsPlay can search and preview real word-synced lyrics before playback, and
offers two dedicated views alongside the 15 preserved looks: **Cinematic** and
**AMOLED**. Words and timestamps always come from the provider unchanged.

The download page ships the verified signed **1.4.6 / 167** binary; the 2.0 brand
applies to this web experience only. APK/AAB files are deliberately absent from
Git; production downloads use the `v1.4.6` GitHub Release URLs in `config.js`.

## Production

GitHub Pages serves this folder via `.github/workflows/deploy-pages.yml`; the
Python backend is deployed with `render.yaml` and `Dockerfile`. In production the
frontend calls `https://aura-music-2-0-api.onrender.com/api` unless overridden.
