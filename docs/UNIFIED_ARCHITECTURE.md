# Unified architecture — AURA MUSIC

**Date:** 2026-11-18

## Principles

1. **One codebase:** the Echo source is the development base. The reference proprietary player APK under `reference/` is evidence only; nothing from it is copied.
2. **One playback engine:** Media3/ExoPlayer in `:playback` (`MusicService`, `PlayerConnection`, queue classes). No second player service.
3. **One authoritative queue/state:** `MusicService.activeQueue`; local and online items are media items in the same session.
4. **Shared track abstraction only at the boundary:** provider ID, stable source-specific ID, title/artist/album/art, duration, content URI or authorized stream reference, availability/offline status, rights/quality metadata. Provider-specific metadata stays intact.
5. **Capability-declared providers:** adapters declare search / browse / full playback / preview / download / lyrics / artwork. Unsupported capabilities are never advertised.
6. **Room migrations, never destructive schema resets.**
7. **Local-first analysis:** FFT-derived features are computed on the device; audio samples never leave it.
8. **Opt-in intelligence:** Groq-backed lyrics enhancement runs only against a backend the user configures; the key lives on that backend.
9. **Honest output states:** Big Screen reports what is actually attached and paired; no implied connection when status says otherwise.

## Layers

| Layer | Implementation | Role |
|---|---|---|
| UI | Compose screens under `app/src/main/kotlin/com/music/echo/ui/screens/` | Home, Search, Library, player, equalizer, settings; AURA MUSIC presentation and Big Screen remote |
| Cinematic UI | Word-by-word overlay, 15 themes, AUTO/MANUAL/SMART controls | Per-word highlighting, approximate-timing warning where needed, theme color dictionary |
| State | ViewModels in `app/.../viewmodels/` | Per-screen state; playback state from one `PlayerConnection` |
| Domain/data | `core`, `domain`, `metadata`, Room and DataStore | Shared models and persistence |
| Learning | Room v49 `CinematicLearningEntity` | SMART profile persistence, local only |
| Playback | `:playback` — `MusicService`, `ExoDownloadService`, queues, DSP hooks, Bluetooth tracker | Single owner of playback, downloads and audio focus |
| Online sources | `:innertube` + provider modules | Authorized stream resolution only; attribution preserved |
| Local sources | `app/.../localmedia/LocalSongScanner.kt`, `SupportedLocalAudio.kt`, folder browser | MediaStore scanning and permission-safe folder browsing |
| Audio output | `:usbaudio`, audio-device UI, `audio-dsp`, `dsp-core` | Output/device controls and real DSP state |
| Audio analysis | Android `Visualizer` bridge | FFT-derived BPM/beat/bass/mid/treble/kick/snare/drop/intensity/silence/energy |
| Lyrics | `:lyrics` + provider modules | Timed/unsynced lyrics, offset, word timings and sync UI |
| Optional backend | Python-standard-library service at `P:\youtube-creat\backend` | Groq lyrics intelligence, publish state and receiver status |
| Big Screen | `Presentation` via `DisplayManager`; desktop browser receiver | Secondary visual output; phone remains audio source and remote |

## Navigation target

Home/Discover · Search (Online + On-device) · Library (Songs, Albums, Artists, Genres, Folders) · Playlists · Downloads · Now Playing · Cinematic Lyrics · Equalizer · Audio output · Big Screen · Settings/Extensions. Reuse the existing floating tab bar and mini-player; no second UI framework.

## Data flow

1. Playback state and FFT features originate in the player/analysis layer on the device.
2. The cinematic layer combines them with lyrics and word timings and current theme state.
3. If the user configured the backend, that combined state is published using the documented contract: theme color dictionary + `theme_name`, `features(bass, beat, energy, tempo)`, `position_ms`, `duration_ms`, `playing`, `lyrics`, `lyrics_visible`, `words[{text, start_ms, end_ms}]`.
4. Receiver status (`paired`, `has_state`, `last_poll_at`, `receiver_name`, `expires_at`) determines whether the app shows a connected receiver. Connected means `paired && last_poll_at != null` only.

## Non-goals

No DRM/paywall/geo bypass. No fake EQ sliders, fabricated word-timed lyrics or fake codec/quality claims. No reference branding, icons, layouts or code. No claim that generic Cast media routing carries cinematic visual state.

## Intentional identifier exceptions

Package, module and resource identifiers inherited from the AURA MUSIC codebase — including `echo.music.iad1tya`, `echo.music.iad1tya.debug`, Gradle module names and `com/music/echo` paths — remain unchanged. They are non-user-facing exceptions to the AURA MUSIC rebrand.
