# Privacy Policy — AURA MUSIC

**Last updated: 2026-11-18**

AURA MUSIC is developed by AURA MUSIC. This policy describes what the app stores, what stays on your device, and what happens only if you choose to configure the optional lyrics backend.

## 1. Data that stays on the device

| Data | Where it lives | Purpose |
|---|---|---|
| Playlists, favorites, queue and playback history | Local Room database and DataStore preferences | Keep your library usable across sessions |
| Lyrics, lyrics offset and cached metadata | Local storage | Display synchronized and cinematic lyrics |
| Cinematic theme choice, AI mode and SMART learning profile | Local Room database (`CinematicLearningEntity`) | Remember your preferences and improve later sessions |
| Big Screen pairing state and receiver name | Local app state | Reconnect a paired receiver you approved |

Uninstalling the app, or clearing its data through Android settings, removes this local information. There is no account sign-in in AURA MUSIC and no profile synchronized to a server owned by this project.

## 2. Audio and visual analysis

Real-time FFT analysis (BPM, beat, bass, mid, treble, kick, snare, drop, intensity, silence and energy) is computed on the device using Android's `Visualizer` API. The app requests `RECORD_AUDIO` for this purpose. Audio samples are analysed locally and are **not** uploaded, recorded to a file, or forwarded to the lyrics backend. You can deny or revoke the permission in Android settings; the visual analysis features will then be unavailable while the rest of the app continues to work.

## 3. Optional Groq-backed lyrics intelligence

Cinematic lyrics enhancement can be enabled only by you. It runs through a Python-standard-library backend you configure and host yourself (in this workspace, `P:\youtube-creat\backend`). The `GROQ_API_KEY` credential lives on that backend environment only; it is never embedded in the app or in this repository.

When the app publishes state to the backend it sends the fields below. No audio is part of this payload:

- theme color dictionary and `theme_name`
- `features`: `bass`, `beat`, `energy`, `tempo`
- `position_ms`, `duration_ms`, `playing`
- `lyrics`, `lyrics_visible`
- `words`: `{ text, start_ms, end_ms }` entries

If you never configure the backend, this data is never sent. Backend status exposed to the app contains `paired`, `has_state`, `last_poll_at`, `receiver_name` and `expires_at`; the app reports a receiver as connected only when `paired` is true and `last_poll_at` is not null.

## 4. Analytics, advertising and third parties

- `google-services.json` is absent from the delivered build, so Firebase Analytics and Firebase Crashlytics are disabled.
- There are no advertisements, advertising identifiers, attribution SDKs or payment flows in AURA MUSIC.
- Streaming and metadata requests go to the services you choose to use, such as YouTube Music (`https://music.youtube.com/`). Those services apply their own privacy terms; AURA MUSIC does not relay your local library or learning profile to them.
- Big Screen output uses the Android display APIs or a receiver you pair yourself. A generic media-routing service, if your device offers one, routes media playback only and is not a channel for AURA MUSIC's cinematic visual state.

## 5. No sale of data

AURA MUSIC does not sell, trade or rent personal information, and does not use your listening data for marketing.

## 6. Your choices

- Deny or revoke `RECORD_AUDIO` to disable audio analysis.
- Leave the lyrics backend unconfigured to keep all lyrics processing local.
- Choose MANUAL instead of SMART cinematic mode, or clear local app data to remove the learning profile.
- Unpair a Big Screen receiver at any time from the Big Screen remote.
- Uninstall the app to remove all locally stored data.

## 7. Children

AURA MUSIC is not directed at children under 13, and no personal information is knowingly collected from them.

## 8. Changes

This policy may be updated as the project changes. The date at the top always indicates the current version. Material changes will be reflected in the project documentation.

---

**By using AURA MUSIC you accept the data handling described in this policy.**
