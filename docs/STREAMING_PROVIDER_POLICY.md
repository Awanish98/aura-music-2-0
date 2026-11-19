# Streaming provider policy — AURA MUSIC

**Date:** 2026-11-18

Scope: online sources already present in the Echo-derived codebase at commit `cb398f13d8aa0fbaf7ea709221c0bccbcba612f0`, plus the optional, user-configured lyrics backend added for AURA MUSIC.

## Rules

1. Keep the existing providers and provider-specific IDs/attribution. Do not invent a "universal" track identity that erases rights metadata.
2. Metadata search, stream resolution and playback authorization are separate concerns. A search result, preview, artwork URL or webpage URL is **never** treated as a playable full track.
3. Full-track playback is only enabled where the provider's current terms and the app's existing authorized mechanism allow it.
4. Downloads/offline caching are only enabled for sources that explicitly permit them. Local files are always available offline.
5. No DRM, paywall, auth, subscription, geo-restriction or access-control bypass. No scraping behind logins.
6. Fail gracefully on expired URLs, rate limits, missing tracks and network loss; surface the real status rather than a fabricated success.
7. The optional Groq-backed lyrics enhancement is opt-in, configured by the user, and runs through a Python-standard-library backend with `GROQ_API_KEY` on the backend only. It receives lyrics/word timing and metadata — never audio, credentials or provider access tokens.
8. Big Screen output does not create a new content source. Desktop receiver status is connectivity metadata only; generic Cast-style media routing is not a cinematic visual channel.

## Inventory

| Area | Module / code | Capability note |
|---|---|---|
| YouTube Music catalog | `:innertube` (`YouTube.*` calls) | Primary online source (`https://music.youtube.com/`); playable stream resolution must stay inside its authorized path |
| Lyrics | `:lyrics` + lyric provider modules | Lyrics are metadata, not audio; no full-track inference. Word timings may be approximate and are labelled as such |
| Canvas / video art | canvas and artist-video modules | Visual only, not audio streams |
| Recognition | recognition modules | Recognition ≠ playback rights |
| Last.fm taste/annotations | in-app API clients | Metadata/recommendation only |
| Spotify import | `app/.../spotifyimport/` | Import of the user's own playlist metadata; not a playback source |
| Local media | `app/.../localmedia/` | MediaStore-backed; offline by definition; folder browsing is read-only |
| Optional Groq backend | `P:\youtube-creat\backend` | User-configured; publish payload is theme colors/name, `features(bass, beat, energy, tempo)`, `position_ms`, `duration_ms`, `playing`, `lyrics`, `lyrics_visible`, `words[{text, start_ms, end_ms}]`; no audio |
| Receiver status | `/api/receiver/status` | `paired`, `has_state`, `last_poll_at`, `receiver_name`, `expires_at`; connected only when `paired && last_poll_at != null` |

## Capability declarations

A conservative registry records search / browse / full playback / preview / download / lyrics / artwork per source. Online sources deliberately do **not** declare DOWNLOAD. Unsupported capabilities are never advertised in the UI, and downloads remain limited to sources that permit them.

## Open items

- Confirm each provider's current public terms before enabling any new playback or download mode.
- Re-verify capability declarations when provider modules change.
- Keep user-facing source attribution visible in track details.
- Recheck the backend payload after any cinematic field change so no credentials or audio data leak into it.
- No reference-player code, assets or branding were used; the APK under `reference/` remains evidence only.

---

Developed by AURA MUSIC. © 2026 AURA MUSIC. All rights reserved.
