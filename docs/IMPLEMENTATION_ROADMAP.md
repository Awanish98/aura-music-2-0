# Implementation roadmap — AURA MUSIC

**Date:** 2026-11-18

Each increment builds and, where relevant, runs unit tests before the next begins. Rollback means reverting the increment's checkpoint; there are no destructive database changes.

| # | Increment | Modules/area | Test | Status |
|---|---|---|---|---|
| 0 | Baseline build + install on device, capture evidence | local config only | Build log + adb install/launch | Complete (2026-11-17) |
| 1 | Local library audit: scanner coverage and supported formats | `app/.../localmedia/` | Format allowlist tests | Complete |
| 2 | Folder browsing over the existing MediaStore scanner | Compose screen + ViewModel | JVM folder grouping; device smoke | Complete |
| 3 | Compact library layout and provider capability declarations | settings, provider registry | Unit tests | Complete |
| 4 | Kinetic word-by-word cinematic lyrics overlay and 15 themes | player/lyrics UI | Timing tests; on-device word-position dumps | Complete |
| 5 | Real FFT audio analysis and direct equalizer access | Visualizer bridge, player | Spectrum tests; permission/flinger evidence | Complete |
| 6 | Cinematic AI AUTO/MANUAL/SMART with Room v49 local learning | persistence + player | Mode/profile tests; device profile count | Complete |
| 7 | Optional Groq lyrics backend (Python stdlib, key on backend only) | `P:\youtube-creat\backend` | `python test_server.py` → 10 tests OK | Complete |
| 8 | Big Screen: `Presentation` external display + paired browser receiver | Big Screen layer + backend status | Pairing/empty-state device checks | Code complete; physical display verification outstanding |
| 9 | AURA MUSIC branding cleanup and document rewrite | docs + user-facing text | Rebrand sweep | **In progress** |
| 10 | Release readiness: GPL review, signing, R8, AAB, listing | release tooling | Full verification on release build | Not started |

## Verification already achieved

```powershell
.\gradlew.bat assembleUniversalGmsDebug :core:testDebugUnitTest :app:testUniversalGmsDebugUnitTest --console=plain
```

**BUILD SUCCESSFUL** · 35 test-result XML files · 158 tests · 0 failures/errors/skips.

```powershell
python test_server.py
```

**10 tests, OK.**

Final verified debug APK: `P:\youtube-creat\AURA-Music-UniversalGms-Debug.apk`, 218,167,024 bytes, SHA-256 `817CA7515DE9F1A6BE7FBC8AA89C9AC0636D3A9773BFB6116321F66074A57BD2`. It matches the fresh Gradle output; both stale `Echo-Music-UniversalGms-Debug.apk` copies were removed.

## Device status

On `ed1916ca` (`ONEPLUS_A6010` / `OnePlus6T`), package `echo.music.iad1tya.debug`, versionName 1.4.6, versionCode 167, `universalGmsDebug`, `RECORD_AUDIO` granted: the final APK installed successfully; the home screen shows AURA MUSIC, the player opens, cinematic word-by-word lyrics render at distinct positions, instrumental tracks show an honest instrumental state, Cinematic Studio shows AUTO/MANUAL/SMART and 35 private SMART profiles with the no-backend fallback, and the Big Screen remote shows the honest empty state and all remote actions. Landscape rotation and background/restore are fine. Earlier increments also verified online search/playback once Wi-Fi was enabled on the device, and local-library playback from 417 real audio files.

## Not done / blockers

- Branding sweep is clean (0 hits) and is recorded in `replica/brand-sweep-report.json`; this satisfies the branding gate only.
- No physical HDMI adapter, Chromecast, Miracast device or laptop receiver was available; charging/data-only USB-C is not video output, and generic Cast media routing is not a cinematic receiver.
- No release signing, R8/minification, resource shrinking, signed AAB or store listing.
- GPLv3 distribution review and third-party notices still require checking.
- Full Bluetooth/USB-DAC/audio-focus testing remains outstanding.
- Provider terms and capability declarations need rechecking before new playback or download modes.

See `docs/FINAL_REPORT.md` for the full evidence and `docs/IMPLEMENTATION_LOG.md` for the chronological record.
