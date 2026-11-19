# Security Policy — AURA MUSIC

## Supported versions

| Version | Supported |
|---|---|
| 1.4.x | Yes — development builds based on this line |
| 1.3.x | No |
| < 1.3 | No |

The delivered artifact is a **debug** build. No production release, signed AAB or store listing has been shipped, and no production signing key has been created for AURA MUSIC.

## Reporting a vulnerability

If you find a security issue:

1. Do **not** publish exploit details in a public issue or forum.
2. If a source repository is published for AURA MUSIC, use its private security-reporting channel. No separate contact address is published in this document.
3. Include a description, reproduction steps, affected build, potential impact and any suggested fix.
4. Allow time for a fix before public disclosure.

## Secrets and sensitive files

Never commit the following:

- `local.properties` — local SDK paths
- `google-services.json` — Firebase configuration (currently absent; analytics/crashlytics are disabled)
- `*.keystore` / `*.jks` — signing keys
- `secrets.properties` and any `.env`-style files
- `**/assets/po_token.html` — authentication tokens
- `GROQ_API_KEY` — belongs in the backend environment at `P:\youtube-creat\backend` only, never in the app or in source control
- build outputs (`.apk`, `.aab`, `.class`) and IDE-local state

## Developer practices

- Keep credentials out of source, logs and test fixtures.
- Use environment variables or untracked local configuration for anything machine-specific.
- Keep dependencies current and review changes before merging.
- Preserve third-party license headers and notices; do not strip GPLv3, Apache-2.0 or OFL text.
- Keep the publish payload minimal: theme colors/name, audio features, playback position, playing state, lyrics and word timings. No audio samples and no credentials are part of it.
- The Android receiver shows "connected" only when `paired` is true and `last_poll_at` is not null; avoid UI states that imply a live session when none exists.

## User practices

- Install only artifacts you built and verified yourself; no official download channel is published.
- Check the requested permissions, especially `RECORD_AUDIO`, which is used for local FFT analysis.
- Do not share your pairing code, and unpair receivers you no longer control.
- Remember that charging/data-only USB-C cables do not carry video; Big Screen output requires a real display path.

## Data privacy

- Local library, lyrics and learning data stay on the device.
- Firebase analytics and crash reporting are disabled because `google-services.json` is absent.
- The Groq-backed lyrics feature is opt-in and runs against a backend you configure; its API key never ships with the app.
- No personal data is sold or used for advertising.

---

Created & Owned by AURA MUSIC. © 2026 AURA MUSIC. All rights reserved.
