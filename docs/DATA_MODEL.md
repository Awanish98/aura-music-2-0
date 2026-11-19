# Data model — AURA MUSIC

**Date:** 2026-11-18

Existing persistence is Room (`core`, `app/.../db/entities`, `app/schemas/`) plus DataStore preferences. Any change requires a numbered Room migration and schema JSON under `app/schemas/`. The cinematic work introduced **Room v49** with `CinematicLearningEntity`.

## Unified track view (conceptual, not a new table)

| Field | Source |
|---|---|
| `stableId` | provider-specific ID from existing entities/InnerTube response |
| `providerId` | existing source identifier (e.g. `innertube`, `local`, `spotify_import`) |
| `title`, `artist`, `album`, `artwork` | existing metadata |
| `durationMs`, `mediaType` | existing metadata / local file metadata |
| `mediaUri` | local `content://` URI or authorized resolved stream reference |
| `availability` | online / downloaded / local / unavailable |
| `rights` / `quality` | only when the source actually reports them; otherwise null |
| `sourceAttribution` | provider name/link for UI display |

## Persisted cinematic state

| Data | Storage | Notes |
|---|---|---|
| Theme selection and cinematic mode (AUTO / MANUAL / SMART) | DataStore preferences | Local preference only |
| SMART learning snapshot and profile count | Room v49 `CinematicLearningEntity` | Persists across sessions; never uploaded |
| Lyrics and per-track `lyricsOffset` | Existing local lyrics storage | Timing comes from the provider or an explicit even-spacing fallback |
| FFT feature values | Derived at runtime | BPM/beat/bass/mid/treble/kick/snare/drop/intensity/silence/energy computed on device; not stored as permanent track facts |
| Big Screen pairing/receiver info | Local app state and backend status | Receiver considered connected only when `paired && last_poll_at != null` |

## Backend publish contract (ephemeral state, not a persisted track table)

| Field | Meaning |
|---|---|
| theme color dictionary, `theme_name` | Active cinematic theme |
| `features.bass`, `features.beat`, `features.energy`, `features.tempo` | On-device FFT-derived values |
| `position_ms`, `duration_ms`, `playing` | Playback position/duration/state |
| `lyrics`, `lyrics_visible` | Current lyric text and visibility |
| `words[].text`, `words[].start_ms`, `words[].end_ms` | Word-level timing when the provider supplies it |

`GROQ_API_KEY` is configured in the backend environment only and is never part of this data model in the app.

## Receiver status fields

`/api/receiver/status` returns `paired`, `has_state`, `last_poll_at`, `receiver_name`, `expires_at`. Absence of a paired state is shown honestly as "No external screen connected" on device.

## Rules

- Do not merge two recordings just because titles match; keep provider-specific IDs.
- Local file identity uses MediaStore ID + content URI; do not assume it is stable across devices.
- Playback queue references the same stable IDs used by Room/DataStore so restoration works across process death.
- Offline state is derived from actual downloaded/local files, not from a boolean claim.
- Lyrics timing/offset remain per-track and must not be fabricated; mark estimated word timing as approximate.
- Learning data is local and additive; migrations must never clear it destructively.
- Identifier names inherited from the codebase (`echo.music.iad1tya`, Room identifiers, DataStore keys, `com/music/echo` paths) are intentional non-user-facing exceptions to the rebrand.

---

Crafted with passion by AURA MUSIC. © 2026 AURA MUSIC. All rights reserved.
