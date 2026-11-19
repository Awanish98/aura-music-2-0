# Contributing to AURA MUSIC

AURA MUSIC is a clean-room Android music client built on the AURA MUSIC codebase and developed by AURA MUSIC. Thanks for helping improve it.

## Table of contents

- [Code of conduct](#code-of-conduct)
- [Getting started](#getting-started)
- [Development setup](#development-setup)
- [Sensitive information](#sensitive-information)
- [Contribution guidelines](#contribution-guidelines)
- [Pull requests](#pull-requests)
- [Testing](#testing)
- [Coding standards](#coding-standards)
- [Documentation](#documentation)
- [Clean-room boundary](#clean-room-boundary)
- [Release process](#release-process)

## Code of conduct

Participation is governed by [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). Be respectful, use welcoming language, accept constructive criticism, and do not publish other people's private information.

## Getting started

Prerequisites:

- Android Studio with the Android SDK
- JDK 21
- Git
- Kotlin and Jetpack Compose familiarity
- Python 3.12 if you want to work on the optional lyrics backend

Work from your local checkout of the project. No repository URL or download link is published here; use the copy of the source you already have.

## Development setup

1. Create `local.properties` in the project root and set your SDK path:

   ```properties
   sdk.dir=/path/to/your/android/sdk
   ```

2. Firebase is optional and currently disabled — `google-services.json` is absent. The debug build does not require it.

3. Build and test:

   ```powershell
   .\gradlew.bat assembleUniversalGmsDebug :core:testDebugUnitTest :app:testUniversalGmsDebugUnitTest --console=plain
   ```

4. If you work on the lyrics backend, configure `GROQ_API_KEY` in the backend environment only (`P:\youtube-creat\backend`) and run its tests:

   ```powershell
   python test_server.py
   ```

## Sensitive information

Never commit:

- `local.properties`
- `google-services.json`
- `*.keystore` / `*.jks`
- `secrets.properties` or any `.env`-style file
- `**/assets/po_token.html`
- `GROQ_API_KEY` or any other credential
- generated `.apk`, `.aab` and other build outputs

If credentials are committed by accident, rotate them and tell the maintainer through the project's own reporting channel. No separate contact address is published here.

## Contribution guidelines

Welcome contributions include bug fixes, new features, tests, documentation, accessibility improvements and performance work. Before starting a large change, describe the approach in an issue so the design can be discussed.

Keep these invariants:

- One playback engine (Media3/ExoPlayer in `:playback`); never add a second player.
- One authoritative queue and playback state.
- Capability-declared providers; never advertise an unsupported capability.
- Room migrations are additive and tested; never reset a database destructively.
- Word-level lyric timing is never fabricated. When a provider supplies only line timing, say so in the UI.
- Cinematic state reported to the desktop receiver uses the documented publish contract only.

## Pull requests

- Use a clear, Conventional Commits-style title, for example `feat(lyrics): add theme variant` or `fix(playback): restore queue after rotation`.
- Explain what changed and why; link related issues.
- Include screenshots or recordings for UI changes.
- Provide step-by-step testing instructions.
- Add or update tests for behaviour changes.
- Keep license headers and third-party notices intact.

## Testing

- Unit tests for pure logic and state transformations.
- UI tests for critical flows, various screen sizes and orientations.
- Device smoke tests for playback, lyrics, Big Screen and permission-denied states where possible.

Commands:

```powershell
# build + JVM unit tests
.\gradlew.bat assembleUniversalGmsDebug :core:testDebugUnitTest :app:testUniversalGmsDebugUnitTest --console=plain

# backend tests (from P:\youtube-creat\backend)
python test_server.py
```

## Coding standards

- Follow the Kotlin coding conventions and Android lifecycle best practices.
- Prefer `val`, data classes for value holders and sealed types for state.
- Use Jetpack Compose for UI and ViewModels for screen state.
- Keep provider-specific metadata and attribution intact.
- Names: PascalCase classes, camelCase functions and variables, UPPER_SNAKE_CASE constants, lowercase packages.

Source layout remains the Echo-derived module structure (`app/.../com/music/echo/`, `:core`, `:lyrics`, `:playback`, `:innertube`, `:usbaudio`, and others). Package, module and resource identifiers inherited from that codebase are **intentional exceptions** to the AURA MUSIC rebrand and should not be renamed casually.

## Documentation

Document public APIs with KDoc, comment non-obvious logic, and keep the documents in `docs/` aligned with behaviour. Use `docs/FINAL_REPORT.md` for verified evidence and `docs/IMPLEMENTATION_LOG.md` for the chronological change record.

## Clean-room boundary

The reference proprietary player APK under `reference/` is reference-only evidence. Do not copy its code, DEX, resources, icons, layouts, strings or branding. No login scraping, DRM, paywall, geo-restriction or access-control bypass is acceptable. All rebuild work is independent, based on publicly documented Android APIs and the existing AURA MUSIC codebase.

## Release process

No production release has been shipped. Before any release:

- [ ] All builds and tests pass
- [ ] Version numbers and documentation are updated
- [ ] GPLv3 source-availability obligations and third-party notices are reviewed
- [ ] Release signing is configured outside source control
- [ ] R8/minification and resource shrinking are validated
- [ ] A signed AAB and store listing are prepared only after the above

---

Crafted with passion by AURA MUSIC. © 2026 AURA MUSIC. All rights reserved.
