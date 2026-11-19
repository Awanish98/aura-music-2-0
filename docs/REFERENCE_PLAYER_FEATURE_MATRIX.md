# Reference Player ↔ AURA MUSIC feature matrix (Phase 1 evidence)

**Status:** historical baseline audit plus later clean-room implementation. No behaviour was copied from the reference product.
**Date of audit:** 2026-11-17 · **Current document date:** 2026-11-18

## Evidence sources

- Echo source (codebase base): `Echo-Music` at commit `cb398f13d8aa0fbaf7ea709221c0bccbcba612f0` (branch `main`, shallow clone).
- Reference proprietary player APK under `reference/`: 19,000,861 bytes, SHA-256 `399fd04b54f014f5bb8b800903c0484022103d173fe4829060559c70c9a52ae5`.
- Reference facts: `aapt dump badging` + static ZIP inventory (reference-player badging log in `docs/`). Manifest: versionCode `1034004`, versionName `build-1034-bundle-play`, minSdk 21, targetSdk 36, 2 DEX files, 2,383 resource entries, no bundled native libraries.
- **JADX/APKTool unavailable.** REA's APK package inspection was also blocked on this host by a Windows Application Control policy, so no DEX was decompiled. Reference internal architecture is **not** claimed as verified; only manifest declarations and observable product concepts were used, and no proprietary code or asset was copied.

## Matrix

| Feature | Reference player evidence (verified vs inferred) | AURA MUSIC implementation evidence | Keep / reuse / build decision | Files/modules involved | Risk / dependencies | Test needed |
|---|---|---|---|---|---|---|
| Local media scanning | Inferred: storage and foreground-service declarations | Verified: `LocalSongScanner.kt`, `SupportedLocalAudio.kt`, Library filter `Local` | Reuse existing scanner; extend sorting/filtering only | `app/.../localmedia/`, `ui/screens/library/` | Storage permission model on API 26–36 | First scan, incremental scan, permission denied/revoked |
| Folder browsing | Inferred from local-library product concept; not verifiable without DEX | Built independently: read-only MediaStore folder browser and Compose screen | Independent implementation | `app/.../localmedia/`, `ui/screens/library/` | Low | Folder list, empty/missing dirs |
| Songs/albums/artists/genres | Inferred from library product concept | Verified: Library tabs, Room entities, album/artist ViewModels | Reuse | `ui/screens/library/`, `viewmodels/`, `core` Room | Low | Existing library tests |
| Queue / play next / reorder | Not verifiable statically | Verified: `playback/queues/`, `MusicService.activeQueue`, Compose reorderable dep | Reuse single authoritative queue | `:playback`, `MusicService.kt` | Must not introduce a second player | Queue reorder, play-next, background restoration |
| Playlists / favorites | Inferred | Verified: playlist screens, `PlaylistViewModel`, Room playlist tables | Reuse | `ui/screens/playlist/`, `db/` | Room migrations required for schema changes | Playlist CRUD + migration tests |
| Now playing / notification / lock screen | Inferred: media-playback foreground service, notification and wake-lock declarations | Verified: `MusicService`, `MediaLibrarySessionCallback`, `PlayerConnection`, session artwork | Reuse Media3/MediaSession stack | `:playback` | Single playback owner is non-negotiable | Notification controls, process recreation |
| Audio focus / noisy route / Bluetooth | Verified manifest declarations | Verified: audio-focus request, Bluetooth tracker, audio-device sheet, noisy-route handling | Reuse | `MusicService.kt`, audio-device UI, `:usbaudio` | Route changes on real device | Wired/BT/speaker route, focus interruption |
| Equalizer / DSP | Not verifiable statically | Verified: 10-band equalizer, `audio-dsp`, `dsp-core`, playback hooks; now reachable directly from the player | Reuse real chain; no decorative sliders | `audio-dsp`, `dsp-core`, `ui/screens/equalizer/` | Effects must reflect real engine state | EQ on/off, clipping, unsupported effects |
| USB audio output | Inferred: connected-device foreground service | Verified: `:usbaudio` module | Reuse | `:usbaudio` | Device-specific | USB DAC if available |
| Lyrics (timed / sync offset) | Not verifiable statically | Verified: `:lyrics` + provider modules, `lyricsOffset`, sync code; now with cinematic word-by-word overlay and 15 themes | Reuse providers; improve sync UI independently | `:lyrics`, provider modules, cinematic overlay | Provider availability/network | LRC, unsynced, offset, network failure; approximate warning |
| Cinematic AI / local learning | Not applicable | Built clean-room: AUTO/MANUAL/SMART, Room v49 `CinematicLearningEntity` | Build | cinematic AI and persistence layers | Local-only learning data | Mode switching, profile persistence |
| Real-time FFT analysis | Not verifiable statically | Built clean-room on Android `Visualizer`: BPM/beat/bass/mid/treble/kick/snare/drop/intensity/silence/energy | Build | player/analysis layer | `RECORD_AUDIO` permission | Permission granted/denied, live values |
| Big Screen output | Not verifiable statically | Built clean-room: `Presentation` via `DisplayManager` plus paired desktop browser receiver | Build | Big Screen layer, backend status API | Physical display availability | Empty state, pairing, disconnect |
| Downloads / offline | Not verifiable statically | Verified: `ExoDownloadService.kt`, `DownloadUtil.kt`, download manager | Reuse **only** for permitted sources | `:playback`, app download UI | Provider terms; no DRM/paywall bypass | Permitted download, offline playback |
| Search / online discovery | Not applicable (local-first reference) | Verified: search screens, `:innertube`, `:metadata` | Preserve unchanged | `:innertube`, `ui/screens/search/` | Provider terms/rate limits | Search by song/artist/album, error states |
| Recognition | Not verifiable statically | Verified: recognition modules and UI | Preserve | recognition modules | External service | Recognition flow |
| Widgets / shortcuts | Inferred: shortcut permission | Verified: widget and quick-settings tile code | Preserve | `app/.../widget/`, `quicksettings/` | Launcher API limits | Widget add/tap |
| Billing / licensing | Verified: billing permission in reference manifest | No equivalent found in AURA MUSIC | **Do not port** | — | Licensing/paywall logic is out of scope | N/A |

## Compliance boundary

The reference player is proprietary. No decompiled code, resources, icons, layouts, strings or branding may be copied into AURA MUSIC. Every "build" decision above means an independent implementation from publicly documented Android APIs and the existing AURA MUSIC codebase. AURA MUSIC is GPLv3; any distributed derivative must keep notices and satisfy source-availability obligations. The reference player APK remains reference-only evidence under `reference/`.
