# Baseline audit (Phase 1) — historical evidence

**Date:** 2026-11-17
**Workspace:** `P:\youtube-creat`
**Final product identity:** AURA MUSIC, developed by AURA MUSIC. The codebase base at this point was the unmodified Echo checkout.

This document records the original audit only. Later cinematic, AI and Big Screen work is recorded in `docs/IMPLEMENTATION_LOG.md`; current status is in `docs/FINAL_REPORT.md`.

## Source

- Local Echo-based checkout: `P:\youtube-creat\Echo-Music`
- Upstream commit: `cb398f13d8aa0fbaf7ea709221c0bccbcba612f0`
- Branch: `main` (shallow clone, `--depth 1`)
- Working tree: clean at clone time; only `local.properties` added afterwards (gitignored)
- License: GNU GPL v3 (`Echo-Music/LICENSE`)
- No repository URL is published here.

## Environment

| Item | Version / path |
|---|---|
| JDK | Temurin 21.0.13+9 (`C:\Users\awani\AppData\Local\Programs\jdk21\jdk-21.0.13+9`) |
| Existing system JDK | Microsoft OpenJDK 17.0.21 (left unchanged) |
| Node | 26.12.1 (for REA tooling) |
| Android SDK | `C:\Users\awani\AppData\Local\Android\Sdk` — platforms 35, 37, 37.0; build-tools 34/35/36; NDK 28.2.13676358 |
| Gradle wrapper | 9.8.2 |
| AGP / Kotlin / Compose | 9.0.0 / 2.3.10 / 1.10.2 |
| Device | `00330663D005686`, model `A069P` (`FroggerPro`), USB debugging connected |
| adb | available in PATH, device state `device` |

**Note:** repo `AGENT.md` stated compileSdk 36 / NDK 27, but `app/build.gradle.kts` at this commit declared compileSdk 37 / NDK 28.2.13676358 / targetSdk 36 / minSdk 26. Build config was treated as authoritative.

## App identifiers (intentional exceptions)

- namespace / applicationId: `echo.music.iad1tya`
- versionCode 167 / versionName 1.4.6
- debug applicationIdSuffix `.debug`
- Development/test variant: `universalGmsDebug`
- 2 flavor dimensions: `abi` (universal, arm64, armeabi, x86, x86_64) × `variant` (foss, gms; `gms` used here)

These package, module and resource identifiers are non-user-facing and are retained deliberately through the AURA MUSIC rebrand.

## Configuration / secrets inventory (no values printed)

- Hard-coded in `app/build.gradle.kts`: Last.fm API key/secret, Discord application ID and redirect scheme.
- From `local.properties` or env: `GH_CLIENT_ID`, `GH_CLIENT_SECRET`.
- Gradle properties: `FLOW_NEURO_BASE_URL`, `FLOW_NEURO_API_KEY` (template default `dummy_key`).
- Optional: `app/google-services.json` was absent — Firebase analytics/crashlytics disabled; build still supported.
- Release signing env: `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`; no release keystore created or used.
- Later work added `GROQ_API_KEY` in the backend environment only (`P:\youtube-creat\backend`); it is never part of the Android app.

## Reference player APK

See the reference-player feature matrix document in `docs/` for the historical feature matrix.

- The reference proprietary player APK under `reference/` was supplied as evidence only.
- Size: 19,000,861 bytes; SHA-256 `399fd04b54f014f5bb8b800903c0484022103d173fe4829060559c70c9a52ae5`
- VersionCode `1034004`, versionName `build-1034-bundle-play`
- minSdk 21, targetSdk 36, compileSdk 36
- 2 DEX files, 2,383 resource entries, 2,739 ZIP entries, no bundled native libraries
- Signature schemes declared: v1 + v2 + v3 (one signer, consistent), source stamp present; signatures not cryptographically verified
- JADX/APKTool absent; REA's APK package inspection blocked by Windows Application Control policy. **No decompilation was performed and no code or assets were copied.**

## Baseline build

- Command: `.\gradlew.bat assembleUniversalGmsDebug`
- Full log: `docs/baseline-build.log`
- Result: `BUILD SUCCESSFUL in 21m 37s`, `365 actionable tasks: 365 executed`, `GRADLE_EXIT=0`.
- APK: `app/build/outputs/apk/universalGms/debug/app-universal-gms-debug.apk`, 212,220,417 bytes, SHA-256 `C4C01BB540071FA0F0257F6F24A2F865570F66174CEC1DD287C442944110AD49`.

## Baseline tests

- Command: `.\gradlew.bat :core:testDebugUnitTest :app:testUniversalGmsDebugUnitTest`
- Full log: `docs/unit-tests.log`
- Result: `BUILD SUCCESSFUL in 6m 36s`, `298 actionable tasks: 17 executed, 281 up-to-date`, `TEST_EXIT=0`.
- Counts: 30 test classes, 130 tests, 0 failures, 0 errors, 0 skipped.

## Baseline device install / smoke test

- Device: `ed1916ca` (`ONEPLUS_A6010` / `OnePlus6T`), USB debugging `device`.
- `adb install -r <apk>` → `Success`. Launched via `monkey`; resumed activity `echo.music.iad1tya.debug/echo.music.iad1tya.MainActivityAlias`.
- UI hierarchy (`uiautomator dump`) confirmed Home content and working Search/other tabs; no FATAL/ANR after launch or taps.
- Screenshot: `docs/device-home.png` (valid PNG; UI verified via hierarchy, not visually by the agent).
- Not verified at the time: online playback (device initially had no network), audio routes, EQ/DSP, lyrics. A later correction found 417 local audio files on the device (mostly `.opus`), so local-library verification was possible.
- Full current evidence: `docs/FINAL_REPORT.md`.

## Unresolved questions / limitations

- Reference player DEX-level feature verification was impossible on this host until JADX/APKTool or a permitted REA native backend was available.
- Provider terms and authorized stream resolution were not audited in this phase.
- No production release was built or signed.
