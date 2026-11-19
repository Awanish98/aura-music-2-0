# AURA MUSIC

**AURA MUSIC** is an Android music client rebuilt clean-room on the AURA MUSIC codebase. It pairs a normal music library and streaming experience with a cinematic lyrics and audio-reactivity layer.

**Created & Owned by AURA MUSIC.** · Developed by AURA MUSIC.

---

## What it does

- **Streaming and library** — search and play music from YouTube Music (`https://music.youtube.com/`), play music already stored on your device, and keep playlists, favorites and a queue.
- **Cinematic word-by-word lyrics** — a full-screen kinetic typography overlay with **15 themes**; per-word highlighting follows the lyrics timestamps when the provider supplies them, and falls back to estimated word spacing for line-synced lyrics.
- **Cinematic AI** — **AUTO**, **MANUAL** and **SMART** modes. SMART keeps a local learning profile so preferences persist between sessions.
- **Real FFT audio analysis** — BPM/beat/bass/mid/treble/kick/snare/drop/intensity/silence/energy are derived on the device through the Android Visualizer bridge.
- **Lyrics intelligence (optional)** — Groq-backed lyric enhancement through a Python-standard-library backend you configure yourself. The API key stays on the backend; the app never ships a key.
- **Big Screen** — send the cinematic experience to an Android external display through `Presentation`, or pair a desktop browser receiver with a short pairing code. The phone remains the audio source and remote.
- **Everyday playback** — background playback, a 10-band equalizer, shuffle and repeat, and offline playback for local files and permitted sources.

> Big Screen output depends on the hardware and the display path available on your device. See [SETUP.md](SETUP.md) and [SECURITY.md](SECURITY.md) for the honest limitations.

---

## Build from source

Requirements: JDK 21, the Android SDK, and Android Studio or a command-line Gradle environment.

1. Point the build at your SDK by creating `local.properties` in the project root:

   ```properties
   sdk.dir=/path/to/your/android/sdk
   ```

2. Build the debug variant:

   ```powershell
   .\gradlew.bat assembleUniversalGmsDebug
   ```

   On macOS/Linux use `./gradlew assembleUniversalGmsDebug`.

3. Install the generated APK on a connected device:

   ```powershell
   adb install -r .\app\build\outputs\apk\universalGms\debug\app-universal-gms-debug.apk
   ```

`google-services.json` is not included, so Firebase analytics and crash reporting are disabled; the debug build does not need it. Full configuration, optional backend setup and the data contract are in [SETUP.md](SETUP.md).

No prebuilt download link is published for this project — build the debug variant yourself or use an artifact you produced and verified.

---

## Privacy

Playback analysis happens on the device. Music, lyrics and learned preferences stay in local storage unless you explicitly configure the optional lyrics backend. No analytics or crash-reporting service is active in the delivered debug build, the app collects no account credentials, and no personal data is sold or shared for advertising. See [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

---

## Clean-room note

AURA MUSIC is an independent rebuild. The AURA MUSIC codebase is the development base. The reference proprietary player APK kept under `reference/` is evidence only: no code, DEX, resources, icons, layouts, branding or copy from it were used. Non-user-facing identifiers inherited from the codebase (for example `echo.music.iad1tya`) are retained deliberately; see [FINAL_REPORT.md](../docs/FINAL_REPORT.md) for the full list.

---

## License

AURA MUSIC is distributed under the GNU General Public License v3.0 — see [LICENSE](LICENSE). Third-party notices and license texts, including Apache-2.0 and OFL material, are retained with their sources.

---

<div align="center">

Crafted with passion by AURA MUSIC.

© 2026 AURA MUSIC. All rights reserved.

</div>
