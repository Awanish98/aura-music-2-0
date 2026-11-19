# AURA MUSIC brand record

Date: 2026-11-18

## Official identity

- Product: **AURA MUSIC**
- No person, developer handle or third-party music-service name is used as the product identity.
- User-facing credits use only:
  - “Built for AURA MUSIC”
  - “Made for AURA MUSIC”
  - “Built for the AURA MUSIC community”
  - “© 2026 AURA MUSIC. All rights reserved.”

No new email address, social handle, website, company name, payment link or support channel was invented. The only third-party URL deliberately retained in user-facing share flows is `https://music.youtube.com/`; Contributor Covenant attribution URLs remain only because they are license text.

## Sweep scope and command

The shipped tree is everything outside `replica/`. `replica/` holds planning prompts and reference evidence only and is intentionally skipped by the sweep tool.

Configured sweep:

```powershell
python .claude\skills\replica-brand\sweep.py . --config replica\brand.json --json
```

Result on 2026-11-18: **0 hits**. Raw machine-readable output is saved at `replica/brand-sweep-report.json`.

The configured `avoid` list targets reference proprietary names, legacy promotional names and the specific person-credit phrases that were removed. The `domains` list targets every legacy domain found during the audit: share links, canvas manifests, the high-quality-audio guide site, the legacy GitHub organisation, and the former Listen Together server host. All legacy intent filters and network calls to those hosts were removed or disabled.

No legacy brand colours are configured because no verified original palette was carried into the AURA design. AURA's typography, palettes, backdrops and motion are defined by the 15 cinematic theme recipes in the app, not by reference-player assets.

## User-facing text verification

A separate targeted scan of Android resource string values and Kotlin string literals found no remaining legacy user-facing feature names or person credits. The following were replaced:

- Store description, GitHub issue templates and public docs now say **AURA MUSIC**.
- All translated `chomusic` fragments were replaced with **AURA MUSIC**; the duplicated Assamese sentence was removed.
- `developer_name` and `collaborator_*` values now say **AURA MUSIC** / **AURA MUSIC community**; the `collaborator_tboyke` resource key was renamed to `collaborator_community`.
- `wrapped_special_thanks` now says **Made for AURA MUSIC** in every locale.
- About and Welcome screens no longer fetch or display contributor identities, and the person avatar URL was replaced with the bundled AURA launcher mark.
- Source headers with prior project names were changed to **AURA MUSIC Project (C) 2026**; preserved third-party Apache/Kyant/GPL notices were not altered.

## Intentional exceptions (not user-facing branding)

The following are retained deliberately. They are not presented to users as product identity and are required for playback compatibility, licensing obligations, or stable local storage.

### 1. Preserved legal notices

Third-party Apache/Kyant/GPL notices are preserved verbatim, including contributor URLs and “Do not remove or alter this notice” statements. They are licence obligations, not UI copy, app labels, marketing text or store-listing copy.

### 2. Non-user-facing identifiers

Package, namespace, module, class, file and resource identifiers remain stable to avoid breaking builds, dependency wiring and installed-app data:

- `echo.music.iad1tya` package/namespace and `echo.music.iad1tya.debug` debug application id
- `com/music/echo/` source root
- `Echo-Music/` checkout folder and `:echomusiccanvas` module
- `EchoMusicCanvasProvider`, `EchoMusicWidgetManager` and related class names
- `echomusic_*` resource ids, drawable names and widget XML
- `com.metrolist.innertubex` third-party library namespace

### 3. Local storage and IPC keys

Changing these would strand existing user data or break persisted playback state:

- `echomusic_settings` SharedPreferences name
- `content://com.echomusic.music` local-media URI authority
- widget actions, shortcuts, ProGuard rules and JNI symbols tied to the current application id

### 4. Third-party service identifiers

Functional identifiers such as the fallback lyrics endpoint, Timber dependency coordinates and tooling attribution links are retained because changing them would break third-party integrations or build configuration without changing the product identity.
