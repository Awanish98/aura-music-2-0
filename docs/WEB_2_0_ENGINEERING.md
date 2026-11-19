# AURA MUSIC 2.0 — Web engineering record

## Phase 1 — Required skill installation

Command executed from `P:\youtube-creat`:

```powershell
npx --yes skills add jakubkrehel/skills
```

The command was interrupted by a host restart, but the installer completed: the new skills are present in `P:\youtube-creat\.agents\skills\` and exposed to this session. Installed skills used on this project:

| Skill | Applied to |
|---|---|
| `better-interface` | Cross-discipline review orchestration and final ranked audit |
| `better-accessibility` | Native controls, focus order, ARIA, keyboard shortcuts, reduced motion, 320px/200% zoom checks |
| `better-layout` | Desktop sidebar / mobile bottom-nav structure, shared spacing edges, responsive containers |
| `better-motion` | Deliberate, reduced-motion-gated transitions; no motion-only state |
| `better-colors` | AMOLED semantic tokens, measured contrast, one accent role |
| `better-typography` | Type scale, tabular timers, 16px mobile inputs, truncation with recovery |
| `better-ui` | Surface radii, icon strokes, hover gating, scroll containment |
| `better-writing` | Verb-first actions, plain errors, forward-pointing empty states |
| `build-design` | Treat the shipped APK's visuals as the design source; report deviations |
| `reverse-engineer-anything` | APK manifest/resource/static analysis |
| `browser` | Runtime QA, console/network inspection, screenshots, Lighthouse |

Existing project instruction files read: `AGENTS.md`. No `CONTRIBUTING.md`/`CODING_STANDARDS.md` interface guidance was found inside the web workspace at the time of writing.

## Scope and identity note

No APK labelled `2.0` exists in the workspace. The shipped reference is `AURA-Music-UniversalGms-Release.apk`, package `echo.music.iad1tya`, versionName `1.4.6`, versionCode `167`. The web product is branded **AURA MUSIC 2.0** as the unified web experience; the APK download will remain honest about the verified binary version and hash.

## Working principles

1. The APK and its source are the feature checklist. Missing backend/private credentials are reported, never invented.
2. Music words, timestamps and queue semantics stay source-driven. AI/backend hints may steer visuals only.
3. No API keys, tokens or private endpoints in frontend code.
4. Static shell + small ES modules on the web; Python backend for resolver/proxy/sync-compatible endpoints.
5. Local-first persistence with an explicit compatibility data model; server sync only where a real backend exists.
6. Motion, colour and typography follow the installed `better-*` skills, gated behind reduced-motion and measured contrast.

This document is updated as implementation and verification proceed.

## Phase 2 — Web product implementation

The legacy one-page landing site was archived under `website/legacy/` and replaced
with a modular, dependency-free application shell:

- `website/index.html` + `styles/` — AMOLED/glass/cinematic design tokens, responsive
  app shell (sidebar → icon rail → mobile bottom nav), full player and immersive
  LyricsPlay overlays.
- `website/js/` — ES modules for state, local persistence, provider API, hash
  routing, playback/queue/Media Session/visualizer, lyrics rendering and all views.
- `backend/music.py` — public YouTube catalog resolution through `yt-dlp` (Node JS
  runtime, no cookies/tokens/keys), real LRCLIB lyrics with exact word tags,
  APK metadata and Range-aware upstream proxy support.
- `backend/server.py` — `/api/config`, `/api/download/apk`, `/api/catalog/search`,
  `/api/catalog/discover`, `/api/catalog/related`, `/api/stream/info`,
  `/api/stream/audio`, `/api/lyrics`, CORS, Range headers and mtime-aware static
  caching.
- `website/manifest.webmanifest`, icons and `sw.js` — installable PWA with static
  shell caching only. Audio, artwork and private user data are never cached.
- All routes are implemented: Home/Discover, Search, Library, Favorites, History,
  Queue, Music Flow, Videos, Offline/files, Artist/Album/Playlist detail, APK
  download, Settings and LyricsPlay.
- The 15 Premium Lyrics looks, `AUTO` mood matching, `CODE POETRY` lineage and the
  8th visualizer style `FullVisualizerStyle.THREE_D("3D PATTERN")` are preserved.

## Phase 3 — Verification

Full results, including real playback, lyrics word sync, every route/flow,
keyboard shortcuts, responsive checks, bugs fixed and honest limitations, are in
[`WEB_2_0_QA.md`](WEB_2_0_QA.md). Headline numbers: backend tests **10 + 12 OK**,
JS syntax checks clean, **24** real search results, **206** Range-streamed audio,
real playback to 213 s, **58 lyric lines / 370 exact word spans**, **31** Music
Flow tracks, real signed APK download of **149,665,142 bytes**.

Known limits: local-first persistence only (no sync/auth backend), no caching of
copyrighted streams, public provider labels rather than curated metadata, no real
2.0 APK (download page honestly states 1.4.6/167), and deployment still pending
user credentials.
