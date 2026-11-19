# AURA MUSIC — Social & Immersive Experience Audit

Date: 2026-11-18  
Scope: read-only audit of `P:\youtube-creat`, then phased implementation without replacing existing working features.

## 1. Executive summary

The project already has a strong playback/lyrics foundation: one Media3 `MusicService`, one queue, Room v49, DataStore preferences, Hilt/KSP, Compose navigation, real word timestamps plus honest estimated-word fallback, and a reusable cinematic lyric state shared with Big Screen.

It does **not** have a dedicated lock-screen lyric renderer, a dedicated AMOLED lyrics mode, an app-owned social backend, a social feed/reel pipeline, or server-backed friend rooms. The existing Listen Together client is the closest reusable primitive for rooms, but it currently has no server behind its default configuration.

All new work must preserve: package ID `echo.music.iad1tya` (`.debug` for debug), existing signing config, GPLv3/Apache/OFL notices, provider capability policy, and the single-player invariant.

## 2. Playback, lyrics, MediaSession and lock screen

### Reuse points

| Capability | Existing code |
|---|---|
| Single player service and queue | `Echo-Music/app/src/main/kotlin/com/music/echo/playback/MusicService.kt`, `PlayerConnection.kt` |
| MediaSession boundary | `Echo-Music/app/src/main/kotlin/com/music/echo/playback/SessionArtworkPlayer.kt` |
| Media notification provider hook | `Echo-Music/app/src/main/kotlin/com/music/echo/echomusic/updater/downloadmanager/EchoNotificationProvider.kt` |
| Session commands | `Echo-Music/app/src/main/kotlin/com/music/echo/playback/MediaLibrarySessionCallback.kt`, `core/.../constants/MediaSessionConstants.kt` |
| Timestamped lyrics model | `Echo-Music/lyrics/src/main/kotlin/echo/music/iad1tya/lyrics/LyricsEntry.kt` (`WordTimestamp`) |
| LRC/rich-sync parsing and cache | `Echo-Music/lyrics/src/main/kotlin/echo/music/iad1tya/lyrics/LyricsUtils.kt`, `LyricsHelper.kt` |
| Word/line timing with honesty flag | `Echo-Music/app/src/main/kotlin/com/music/echo/ui/component/KaraokeTiming.kt` |
| Room-backed current lyrics | `core/.../db/DatabaseDao.kt` (`lyrics(id)`), `LyricsEntity.kt` |
| Shared lyric snapshot | `app/.../ui/player/CinematicEngine.kt`, `CinematicEngineState` |
| External display renderer | `app/.../ui/bigscreen/BigScreenSession.kt`, `BigScreenPublish.kt`, `AuraPresentation.kt`, `BigScreenRenderer.kt` |

### Gaps

- No `KeyguardManager`, lock-screen activity, `setShowWhenLocked`, or lock-screen-specific notification body.
- No service-side active lyric line/word published into MediaSession metadata or notification extras.
- Global AMOLED/pure-black exists (`PreferenceKeys.PureBlackKey`, `ui/theme/Theme.kt`, `ThemeScreen.kt`), but cinematic lyrics do not consume it and there is no AMOLED lyrics mode.
- No tests for lock-screen notification, AMOLED lyrics rendering, Big Screen publisher, or Presentation lifecycle.

### Safe approach

Android must not be forced into unsupported overlays. Phase 1 will:

1. Publish the current lyric line/word into `SessionArtworkPlayer` metadata extras.
2. Let `EchoNotificationProvider` render that line as the media notification text, respecting a privacy setting.
3. Add an opt-in lock-screen-style Activity that uses `setShowWhenLocked` only when the user enables it, and falls back to the system media notification on secure/limited lock screens.
4. Reuse real word timestamps, line-level LRC as line-level only, estimated words only with `verified=false`, and existing per-song correction preferences.

## 3. Social/backend/real-time

### Reuse points

- `app/.../listentogether/ListenTogetherClient.kt` — WebSocket transport, pairing, reconnect/backoff, ping.
- `app/.../listentogether/ListenTogetherManager.kt` — room orchestration, queue/playback sync, chat, kick/block, suggestions.
- `app/.../listentogether/Protocol.kt`, `MessageCodec.kt`, `app/src/main/proto/listentogether.proto` — room/track/user/sync schema.
- `app/.../ui/screens/CommentTogether.kt` — chat UI with replies.
- `app/.../ui/component/musicflow/MusicFlowPage.kt` — vertical full-screen music/lyrics shell and double-tap like gesture.
- `app/.../discord/DiscordOAuthRepository.kt` — OAuth PKCE pattern, not an app-owned account system.
- `backend/server.py` — Python 3.12 stdlib HTTP server with bearer auth, rate limiting, SHA-256 analysis cache, secure receiver relay, and 10 tests.

### Gaps

- `ListenTogetherServers.defaultServerUrl` is empty; no Listen Together server exists.
- No app-owned auth/profile/friend graph, persisted feed/posts, reel upload/transcode/storage, comments/likes/reactions, moderation, or push notifications.
- Backend is in-memory receiver/Groq relay only; no WebSocket, DB, Redis, object storage, or workers.
- Local-only blocked users and fragile Base64 reply encoding in chat.

### Safe approach

Reuse the LT protocol for Friend Rooms rather than inventing a second protocol. Add a lightweight Python service (`backend/social_server.py` or extend `server.py`) with SQLite for local/dev and Postgres-compatible schema, WebSockets, room state, and object-storage abstraction. Do not upload copyrighted/local music; rooms broadcast legitimate source IDs and each client resolves them through its own supported provider.

## 4. Architecture and release constraints

- `settings.gradle.kts`: 22 modules; `FAIL_ON_PROJECT_REPOS`; Java 21; compileSdk 37/targetSdk 36/minSdk 26.
- App flavors: `abi` (`universal`, `arm64`, `armeabi`, `x86`, `x86_64`) × `variant` (`foss`, `gms`). `:playback` only has `variant`.
- Room version 49. Add new migrations to **both** builders: `core/.../db/MusicDatabase.kt` and `app/.../di/AppModule.kt`.
- Stable DataStore keys must not be renamed.
- Branding: AURA MUSIC / AURA MUSIC; approved phrases and intentional non-user-facing Echo/metrolist identifiers are documented in `replica/brand.md`.
- Brand sweep must report `[]` before release.
- Release blockers remain: no release keystore/AAB, R8 unvalidated, GPL/third-party notice distribution review, store listing/privacy review, physical Big Screen verification, and artifact/doc drift.

## 5. Implementation order

1. **Dynamic Lock Screen** — lyric resolver, MediaSession/notification extras, opt-in Activity, settings, tests.
2. **AMOLED Lyrics Mode** — dedicated black renderer, settings, low-power behavior, tap-to-seek, landscape, external-display reuse.
3. **Reel Creator + Export** — local/authorized media only, 9:16 preview/export, rights gate, 720p/1080p, gallery/share.
4. **Social Feed + Interactions** — posts, likes/comments/reactions, follow/block/report, privacy, feeds.
5. **Friend Rooms** — server-backed rooms, invite/code/QR, host controls, queue/voting, drift correction.
6. **Live Chat + Reactions** — structured messages/replies, typing/moderation, rate-limited reactions.
7. **Sync, Security, Optimization** — reconnection, auth hardening, R8, size/perf/battery validation, release artifacts.

Phases 1–2 can be implemented immediately on the existing engine. Phases 3–6 require new backend services and storage; they will be added modularly and kept offline-safe until configured.

## 6. Phase 1 verification record (2026-11-18)

Implemented and verified on device `ed1916ca` with `universalGmsDebug` (`1.4.6`, code `167`):

- Active lyric line/word is published through `SessionArtworkPlayer` metadata extras and rendered by `EchoNotificationProvider` in the media notification. Device initially showed a stale notification body until a playback state change forced a rebuild; after play/pause it showed the live lyric line.
- `LockScreenLyricsActivity` is a MediaController-only preview/overlay. It showed live metadata, active word, next word and next line for `Slow Motion` and `Mama (feat. William Singe)`; Previous/Play/Pause/Next all changed MediaSession state.
- Tap-to-seek is offset-based: tapping the progress bar moved the paused session from `101475 ms` to `133587 ms` (duration `150021 ms`). The earlier `clickable` implementation was a no-op and was replaced by `pointerInput` + `detectTapGestures`.
- Overlay opt-in is enforced synchronously before first draw. With `lock_screen_overlay_enabled=true`, window flags include `SHOW_WHEN_LOCKED TURN_SCREEN_ON`. With it disabled, those flags are absent. `setShowWhenLocked()` alone was not sufficient on this device, so explicit `window.addFlags()` is applied only when opted in.
- Settings writes use a durable IO scope; selecting `AMOLED` persisted across immediate back navigation after the previous `rememberPreference` write was cancelled.
- Landscape rotation retained the Activity and live lyric state.
- Verification commands: `assembleUniversalGmsDebug :core:testDebugUnitTest :app:testUniversalGmsDebugUnitTest` → BUILD SUCCESSFUL; backend `python test_server.py` → 10 tests OK; brand sweep → `[]`; 6 `LockScreenLyricResolverTest` JVM tests pass.
- Coverage limitation: notification-extra rendering is device-verified only; there is no JVM test for Android notification recovery, so it must be rechecked on device after notification-provider changes.

## 7. Phase 2 verification record (2026-11-18)

Implemented on `ed1916ca` with `universalGmsDebug`:

- `AmoledLyricsPolicy` is a pure JVM policy: true black, low-power effect removal, capped standard-mode accent, and clamped tap-offset seek math. Three `AmoledLyricsPolicyTest` cases pass.
- `AmoledLyricsOverlay` reuses `CinematicEngineState` and `CinematicLyricsLayer`; it does not create a second player or lyric resolver. Low power skips Visualizer capture and uses a 100 ms engine poll; normal AMOLED keeps a restrained accent.
- The player's existing lyrics button opens the AMOLED renderer when the mode is enabled. It shows `AMOLED lyrics` as its content description. The overlay showed live words for `Heat Waves` and `Kendrick Lamar, SZA`, with title, artist, time labels and the correct `AMOLED · Low power` / `AMOLED` badge.
- Tap-to-seek is offset-based through a consuming pointer handler on the bar. On a prepared paused track, tapping near the end moved the session from `153899 ms` to `205769 ms` (duration `235561 ms`); the displayed time changed from `2:33` to `3:25`. The earlier no-op came from tapping above the 17 px visible bar (`y=2170` instead of `y=2200`) and from a full-screen toggle layer; the reveal layer now exists only while controls are hidden, and `Hide` restores it.
- Keep-screen-on is applied only while the overlay is open: `KEEP_SCREEN_ON` was present in the window flags during the overlay and absent after closing it. `requestedOrientation` is set to `SCREEN_ORIENTATION_SENSOR` while open and restored on dismiss.
- Landscape rotation retained the same Activity, overlay, live lyrics, controls and wake flag. Returning to portrait retained the same session.
- Low-power toggling persists: switching it off wrote `amoled_lyrics_low_power=false`, the overlay badge changed to `AMOLED`, and switching it back on restored `AMOLED · Low power`. `amoled_lyrics_mode=true` persisted across immediate back navigation via the durable IO write scope.
- Big Screen's wired/desktop renderer consumes the same sanitized visual settings, so enabling AMOLED renders on pure black there too. No physical external display was available for hardware confirmation.
- Verification commands: `assembleUniversalGmsDebug :core:testDebugUnitTest :app:testUniversalGmsDebugUnitTest` → BUILD SUCCESSFUL; backend `python test_server.py` → 10 tests OK; brand sweep → `[]`.
