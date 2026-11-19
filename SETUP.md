# Setup — AURA MUSIC

This document covers building AURA MUSIC from the local Echo-based checkout and, optionally, configuring the lyrics backend and Big Screen receiver.

## Prerequisites

- Android Studio with the Android SDK installed
- JDK 21
- Git
- Python 3.12 if you want to run or modify the optional lyrics backend (standard library only)
- A physical Android device for full verification (emulators are not representative of the audio/display paths)

## 1. Open the checkout

Use your local source tree at `P:\youtube-creat\Echo-Music`. No clone URL or prebuilt download is published for this project.

## 2. Configure `local.properties`

Create `local.properties` in the project root:

```properties
sdk.dir=/path/to/your/android/sdk
```

Example paths:

- Windows: `C:\\Users\\username\\AppData\\Local\\Android\\sdk`
- macOS: `/Users/username/Library/Android/sdk`
- Linux: `/home/username/Android/sdk`

This file is machine-specific and must never be committed.

## 3. Firebase (optional, currently disabled)

`google-services.json` is absent. Firebase Analytics and Crashlytics are therefore disabled, and the debug build does not require the file. Do not fabricate or commit one. If you later add your own Firebase project, keep its configuration file untracked.

## 4. Build and run

```powershell
# debug build
.\gradlew.bat assembleUniversalGmsDebug

# build + unit tests used for verification
.\gradlew.bat assembleUniversalGmsDebug :core:testDebugUnitTest :app:testUniversalGmsDebugUnitTest --console=plain
```

Install on a connected device:

```powershell
adb install -r .\app\build\outputs\apk\universalGms\debug\app-universal-gms-debug.apk
```

Release builds require signing credentials that are not part of the repository and are not configured here; no release AAB has been produced.

## 5. Optional Groq-backed lyrics intelligence

Lyrics enhancement is off unless you configure a backend. The backend lives at `P:\youtube-creat\backend` and uses only the Python standard library.

1. Set `GROQ_API_KEY` in the backend's environment — never in the Android app, source files or this workspace's tracked files.
2. Start the backend you configured, then enter its endpoint in the app's settings.
3. Verify the backend with:

   ```powershell
   cd P:\youtube-creat\backend
   python test_server.py
   ```

The backend receives, and the app publishes, only this state payload:

| Field | Meaning |
|---|---|
| theme color dictionary, `theme_name` | Active cinematic theme |
| `features.bass`, `features.beat`, `features.energy`, `features.tempo` | On-device FFT-derived values |
| `position_ms`, `duration_ms`, `playing` | Playback state |
| `lyrics`, `lyrics_visible` | Current lyric text and visibility |
| `words[].text`, `words[].start_ms`, `words[].end_ms` | Word-level timing, when available |

No audio samples or credentials are sent. The app reads receiver status fields `paired`, `has_state`, `last_poll_at`, `receiver_name` and `expires_at`, and shows a receiver as connected only when `paired && last_poll_at != null`.

## 6. Big Screen

AURA MUSIC can output cinematic visuals in two ways:

- **Android external display** — `Presentation` is wired through `DisplayManager`; the app opens a presentation when a compatible external display is available.
- **Desktop browser receiver** — pair the receiver using the code shown by the app, then let the browser poll the backend for state.

The phone remains the audio source and remote in both cases. The Big Screen remote exposes transport/next, lyrics toggle, shuffle, favorites, theme modes/cards and disconnect.

Hardware honesty:

- A charging/data-only USB-C cable is **not** video output.
- No physical HDMI adapter, Chromecast, Miracast device or laptop receiver was available for verification. With no external display attached, the app correctly reports **"No external screen connected"** and shows the USB/video caveat.
- Generic Google Cast-style media routing, where present, moves media playback only. It is not presented as a receiver for AURA MUSIC's cinematic visual state.

## 7. Files that must stay untracked

- `local.properties`
- `google-services.json`
- `*.keystore` / `*.jks`
- `secrets.properties`, `.env` files and `GROQ_API_KEY`
- `**/assets/po_token.html`
- generated build outputs

## Troubleshooting

**SDK location not found** — create `local.properties` with the correct `sdk.dir`.

**Firebase-related errors** — none are expected without `google-services.json`; the debug build supports its absence.

**Gradle sync issues** — clean and rebuild with `.\gradlew.bat clean` followed by the build command in section 4.

**No video on USB-C** — confirm the cable and port actually support DisplayPort Alternate Mode; most charging/data-only cables do not.

**Backend shows no state** — confirm the endpoint is reachable, `GROQ_API_KEY` is set only on the backend, and the status payload reports `paired = true` with a non-null `last_poll_at`.

## License

AURA MUSIC is licensed under the GNU General Public License v3.0 — see [LICENSE](LICENSE). Apache-2.0 and OFL notices for third-party material are retained with their sources.

---

Developed by AURA MUSIC. © 2026 AURA MUSIC. All rights reserved.
