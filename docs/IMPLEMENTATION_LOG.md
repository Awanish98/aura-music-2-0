# Implementation log — clean-room cinematic and Big Screen work

All work is built on the AURA MUSIC codebase at pinned commit `cb398f13d8aa0fbaf7ea709221c0bccbcba612f0`. The reference proprietary player APK under `replica/reference/` is evidence only. No code, DEX, resources, icons, layouts, strings or branding were copied. No login scraping, DRM, paywall, geo or access-control bypass was added.

**Current date:** 2026-11-18

---

## Chronological record

### 1. Foundation (historical context)

The Echo source was audited, built and installed first; local folder browsing, a compact-library preference and a conservative provider-capability registry were added as independent, tested increments. Details are in `docs/BASELINE.md` and earlier revisions of this log; they are not repeated here.

### 2. Kinetic word-by-word lyrics overlay

**Added**

- Full-screen cinematic lyrics overlay with deterministic editorial word placement across the screen, varied size/rotation/alignment/spacing, fade, scale, blur-to-focus, slide and emphasis treatment for the current word.
- Real per-word timing consumed from provider word timestamps; plain line-synced lyrics fall back to evenly spaced words and the UI says the timing is approximate rather than claiming vocal accuracy.
- Playback position sampled directly at a short interval so highlighting follows vocals more closely than the shared player state.
- Back handling and tap-to-reveal close control.
- A bundled OFL font with its license text retained; no target-app fonts, icons, artwork or copy were reused.

**Result:** the overlay no longer behaves like a single subtitle row; on-device accessibility dumps showed words at distinct coordinates across successive samples.

### 3. Real FFT visualizer and discoverable equalizer

**Added**

- Clean-room FFT bucketing and canvas visual strip built on Android's public `Visualizer` API.
- Analysis outputs BPM/beat/bass/mid/treble/kick/snare/drop/intensity/silence/energy on the device.
- A direct player action to the existing 10-band equalizer instead of leaving it buried in settings.

**Fix during implementation:** the first bucket mapper averaged bins and flattened narrow tones; it now uses the bucket maximum. Capture-rate selection was adjusted to the API actually available on the device.

**Evidence:** `RECORD_AUDIO: granted=true`; the audio flinger listed the Visualizer after it was toggled on; the equalizer screen opened with its presets.

### 4. Cinematic AI and persistent local learning

**Added**

- AUTO, MANUAL and SMART cinematic modes.
- Room **v49** migration introducing `CinematicLearningEntity`, so SMART learning state and profile counts persist across sessions.
- Theme system expanded to **15 themes**, with a theme color dictionary published to the optional receiver.

**Constraint:** all learning data is local; no profile is uploaded and no provider identity is merged on title match alone.

### 5. Groq-backed lyrics intelligence through a user-configured backend

**Added**

- Optional Groq integration reached only through a Python-standard-library backend at `P:\youtube-creat\backend`.
- `GROQ_API_KEY` configured in the backend environment only; never in the app, source control or documentation.
- Publish contract: theme color dict + `theme_name`, `features(bass, beat, energy, tempo)`, `position_ms`, `duration_ms`, `playing`, `lyrics`, `lyrics_visible`, `words[{text, start_ms, end_ms}]`.
- Backend verification: `python test_server.py` → **10 tests, OK**.

**Boundary:** lyrics text, word timings and analysis values may be published only when the user configures the backend. Audio samples and credentials are never part of the payload.

### 6. Big Screen — external display and browser receiver

**Added**

- Android external-display output wired with `Presentation` through `DisplayManager`.
- Desktop browser receiver paired by a short pairing code; the phone stays the audio source and remote.
- Remote actions: transport/next, lyrics toggle, shuffle, favorites, theme modes/cards and disconnect.
- Receiver status read from `/api/receiver/status`: `paired`, `has_state`, `last_poll_at`, `receiver_name`, `expires_at`.
- Honest connection logic: Android shows "connected" only when `paired && last_poll_at != null`.

**Hardware honesty:** with no display attached the remote shows "No external screen connected" and the USB/video caveat. Charging/data-only USB-C is not video output. No physical HDMI adapter, Chromecast, Miracast device or laptop receiver was available for verification. Generic Cast-style media routing is not claimed to carry cinematic visual state.

### 7. Final verification after branding cleanup

```powershell
.\gradlew.bat assembleUniversalGmsDebug :core:testDebugUnitTest :app:testUniversalGmsDebugUnitTest --console=plain
```

Result: **BUILD SUCCESSFUL**; **35 test-result XML files, 158 tests, 0 failures, 0 errors, 0 skipped**.

Final verified artifact:

- Canonical copy: `P:\youtube-creat\AURA-Music-UniversalGms-Debug.apk`
- Gradle output: `P:\youtube-creat\Echo-Music\app\build\outputs\apk\universalGms\debug\app-universal-gms-debug.apk`
- Size: **218,167,024 bytes**
- SHA-256: `817CA7515DE9F1A6BE7FBC8AA89C9AC0636D3A9773BFB6116321F66074A57BD2`

Both files were rehashed after the final build and matched. The two stale `Echo-Music-UniversalGms-Debug.apk` copies were removed.

Device verification on `ed1916ca` (`ONEPLUS_A6010` / `OnePlus6T`), package `echo.music.iad1tya.debug`, versionName 1.4.6, versionCode 167, `universalGmsDebug`, `RECORD_AUDIO` granted: the final APK installed successfully; the home screen shows AURA MUSIC; the player opens; cinematic word-by-word lyrics render at distinct positions; instrumental tracks show an honest instrumental state; Cinematic Studio shows AUTO/MANUAL/SMART, theme cards, 35 private SMART profiles and the no-backend fallback; the Big Screen remote shows the honest empty state, USB/video caveat and all remote actions; landscape rotation and background/restore are fine. No app-attributable `FATAL EXCEPTION` or ANR was observed.

### 8. Branding cleanup (complete)

User-facing identity was replaced with **AURA MUSIC** and **AURA MUSIC**, using only the approved phrases. Legacy domains, default Listen Together server, legacy invite links and old user-facing feature names were removed or neutralised. Non-user-facing identifiers such as `echo.music.iad1tya`, Gradle module names, `com/music/echo` paths, Room identifiers and DataStore keys are intentional exceptions. GPLv3, Apache-2.0 and OFL notices are preserved.

The configured sweep ran after the final artifact changes and reported **0 hits**:

```powershell
python .claude\skills\replica-brand\sweep.py . --config replica\brand.json --json
```

The machine-readable result is `replica/brand-sweep-report.json`; the inventory and exception policy are in `replica/brand.md`. This satisfies the branding portion of the release gate but does not authorise a production release.

---

## Still outstanding

- GPLv3 distribution review and third-party notice check.
- Release signing, R8/resource shrinking validation, signed AAB and store listing — none done.
- Physical external-display verification and full Bluetooth/USB-DAC/audio-focus testing.
- Re-check provider terms and capability declarations before enabling new playback or download modes.

---

Crafted with passion by AURA MUSIC. © 2026 AURA MUSIC. All rights reserved.
