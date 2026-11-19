# Deploy checklist: AURA MUSIC

Date: 2026-11-19  Commit: `66fe8db` (nested `Echo-Music` repo; web app committed locally, not pushed)  Go from user: **yes** — "ab deploy krna hai"; host = Netlify, Play Console account = yes, domain = free Netlify subdomain.

## Decisions from the user

| Choice | Answer |
|---|---|
| Website host | **Netlify** (static, drag-and-drop or CLI) |
| Play Console developer account | **Yes** ($25 already paid by user) |
| Domain | **Free Netlify subdomain** — placeholder `https://aura-music.netlify.app` is already in `index.html` canonical + OG tags |

> Account login, domain claiming and the actual publish are done by the user. No credentials, card or live key was entered by the agent.

## Preflight (all must pass)

- [x] **e2e suite green:** Backend `test_server.py` 10/10 + `test_music.py` 12/12 OK; all web JS passes `node --check`. Full browser click-through of the rebuilt web app is recorded in `docs/WEB_2_0_QA.md`: 24 real search results, 206 Range audio stream with real playback to 213 s, 58 LRCLIB lines / 370 exact word spans, 31 Music Flow tracks, all routes/state flows, keyboard shortcuts, mobile layout and a 149,665,142-byte real APK download. No current 4xx/5xx was observed; two stale pre-fix lyric 404 entries remain only in the QA tab's console history.
- [x] **no open S1 or S2 bugs:** 0 unit-test failures/errors/skips (41 XML suites, 211 tests across `app` + `core`; re-verified after the final 15-look Premium Lyrics and full-screen CODE POETRY renderer/overlay edits); backend 10/10 OK; device smoke test on OnePlus A6010 passed all 9 lyric modes, Premium Lyrics AUTO/manual, rebuilt CODE POETRY, 3D PATTERN, no app-attributed FATAL/ANR/wrong-thread errors.
- [x] **parity:** N/A — the only `features.csv` in the tree is the replica-recon pack's Calendly example, not an AURA MUSIC matrix. Feature acceptance was verified directly on device instead: CODE POETRY, 15-look Premium Lyrics, 3D visualizer, glassmorphism, real 3D effects.
- [x] **rebrand sweep clean:** `python .claude\skills\replica-brand\sweep.py . --config replica\brand.json --json` → `[]` (exit 0). `replica/brand-sweep-report.json` = `[]`.
- [x] **store listing passes:** `python .claude\skills\replica-launch\listing.py replica\launch\listing.json` → 0 errors, 2 warnings (intentional all-caps "AURA MUSIC" / "AUTO"). Title 25/30, short 75/80, full 1436/4000.
- [x] **production build passes:** signed `assembleUniversalGmsRelease bundleUniversalGmsRelease` → `BUILD SUCCESSFUL` after the 15-look Premium Lyrics redesign, `lintVitalUniversalGmsRelease` clean after removing the WorkManager startup initializer (app implements `Configuration.Provider`). R8/L8 Kotlin-metadata warnings are pre-existing and non-blocking.
- [x] **privacy policy and terms live, every processor listed:** `website/privacy.html` + `website/terms.html`, linked from every page footer. Privacy lists Google Fonts, optional Groq via self-hosted backend, user-chosen streaming providers, hosting/CDN. No ads/analytics/crash SDKs; app also has `Echo-Music/PRIVACY_POLICY.md` for Play Console.
- [x] **account deletion works:** N/A — no account system exists (privacy policy section 1); uninstall/clear-data removes all local state.
- [x] **favicon, titles, OG image are ours:** AURA orbital mark generated fresh with PIL — `favicon.ico`, `favicon-192.png`, `favicon-512.png`, `apple-touch-icon.png`, `og-image.png` (1200×630, no third-party logo). Titles + canonical/OG tags in every page.

### Release package verified

| Check | Result |
|---|---|
| Package / version | `echo.music.iad1tya`, versionCode `167`, versionName `1.4.6` |
| APK signature | valid, 1 signer, cert SHA-256 `740963cf50ba9ed77b0c2fc91a91d02c1e0fe9fec15dc8342dc5911ceec74cd3` (upload key `auraupload`) |
| APK SHA-256 | `F46917BAE7299546ECD01815728F9DE783F641A9988F56C05D3C97158148AB16` (149665142 bytes) |
| AAB SHA-256 | `6C9F33EF5B55B53B4C4A8B136601357D1CF45F8AFF9FC1451AE78F55C07305D3` (186386586 bytes) |
| 16 KB pages | zipalign `-c -P 16 -v 4` exit 0; ELF `BAD_COUNT 0`; AAB `PAGE_ALIGNMENT_16K` |

Canonical artifacts: `AURA-Music-UniversalGms-Release.apk` / `.aab` (repo root) and identical copies in `website/downloads/` (hashes match).

## Production services

No database, Stripe, OAuth, outbound email or analytics. The **Python backend is required** for the web app's catalog, Range-proxied audio and lyrics; it is self-hosted by the user and needs `yt-dlp` plus a Node JS runtime. No account, API key, cookie or po-token is used: `GROQ_API_KEY` is optional and never in the web frontend. Netlify itself needs no live keys.

**Outstanding before store publish (not a deploy blocker):** GPLv3 source-availability / third-party OFL notice review. The app is GPLv3; ensure a source offer is present in the Play listing or app.

## Backend requirement for the web app

`website/` is a static shell, but Home/Discover, Search, player streams and
LyricsPlay call the same-origin `/api/*` endpoints implemented in `backend/`.
A pure Netlify static deploy will render the UI but **cannot play music or load
lyrics** without that backend. Either run `backend/server.py` on a host reachable
by the deployed site and point the frontend at it, or deploy the Python backend
on a host that supports it (e.g. a small container/VM behind HTTPS). Do not put
private keys or cookies in frontend code to work around this.

## Host and domain — Netlify (user steps)

The site has no build step. Two ways, both done by the user in their own Netlify account:

### Option A — Netlify Drop (fastest)

1. Log in at <https://app.netlify.com> (or use drop without login at <https://app.netlify.com/drop>).
2. Drag the **`P:\youtube-creat\website`** folder onto the dropzone.
3. Set the site name to **`aura-music`** → live URL `https://aura-music.netlify.app`.
4. Build settings: **no build command**, publish directory **`.`** (root).
5. Open the live URL and confirm: hero loads, style lab shows 15 looks + AUTO, both download buttons work, Privacy/Terms links work.

> ⚠️ **Large-file check:** the APK is ~150 MB and the AAB ~186 MB. If Netlify rejects the upload because of a per-file size limit, use **Option B**.

### Option B — site on Netlify + downloads from GitHub Releases (recommended if A fails)

1. Deploy the site with the `downloads/` folder **removed** from the folder you drop (rename it temporarily).
2. Create a GitHub release for the repo and attach `AURA-Music-UniversalGms-Release.apk` and `.aab` (GitHub Releases allows multi-hundred-MB assets).
3. In `website/index.html`, change the two download `href`s:
   - `downloads/AURA-Music-UniversalGms-Release.apk` → `https://github.com/<you>/<repo>/releases/download/v1.4.6/AURA-Music-UniversalGms-Release.apk`
   - `downloads/AURA-Music-UniversalGms-Release.aab` → the matching `.aab` URL
4. Redeploy by dragging the updated folder onto the site's **Deploys → dropzone**.

### Custom domain (only if user later buys one)

| record | name | value |
|---|---|---|
| A | @ | Netlify apex IP shown in Domain settings |
| CNAME | www | `<site>.netlify.app` |
| TXT | @ | verification value if Netlify asks |

Pick one canonical host and redirect the other; HTTPS is automatic. Update the canonical/OG tags in `index.html` and the text in `og-image.png` when the final domain is known.

## Play Console (user steps)

1. Sign in at <https://play.google.com/console> → **Create app** → name **AURA MUSIC: Lyric Visuals**, default language English (US).
2. **Production → Create new release** → upload `P:\youtube-creat\AURA-Music-UniversalGms-Release.aab` (versionCode 167, versionName 1.4.6).
3. Paste the store text from `replica/launch/listing.json` (`google_play` block) into title / short / full description.
4. **App content** questionnaire: declare the microphone permission and the optional backend; link the hosted privacy policy (`https://aura-music.netlify.app/privacy.html`); select "No" for ads and for targeted audience/children.
5. Data safety form: "Data collected" = none uploaded; local processing only; optional state sent to user's own backend. Mirrors section 4 of the privacy policy.
6. Add screenshots + a 512×512 feature graphic (the `favicon-512.png` brand mark can be reused).
7. **Internal testing** first → opt-in via Play Store testing link → confirm install + launch on a phone → then **Production → Roll out**.
8. Keep the upload keystore `Echo-Music/keystore.jks` (alias `auraupload`) safe; Play App Signing will enroll with this upload key.

## Watch it in the first week

- Netlify: **Deploys** tab for failed builds; browser console on the live URL; download-click test from a phone (not just desktop).
- Play Console: **Android vitals** (ANRs / crashes), review the "App content" and "Data safety" declarations for any policy warning, and replies to user reviews.
- If Option B is used: check the GitHub release asset URLs still return 200 after any edit.

**Status:** everything the agent can do locally is complete and verified. Nothing was published — the user performs the Netlify drag-and-drop and the Play Console upload/rollout.
