# Premium Lyrics — 15 looks + AUTO

A unified full-screen lyric surface (`echo.music.iad1tya.ui.player.LyricsModeChoice.PREMIUM_LYRICS`) that
reads the **existing** cinematic engine. It never creates a second player, lyric parser, animation clock,
or AI lyric source. Every glyph comes from `CinematicEngineState.words`, with real timestamps.

## What was built

- `PremiumLyricStyle`: `AUTO` + **15** selectable looks. The old 50-style family/variant system is gone;
  each look is now a standalone art direction with its own palette, typeface and signature motion.
- `PremiumLook` / `PremiumLyricFont`: five typographic voices — Plus Jakarta Sans (signature sans),
  Outfit (geometric display), Cormorant Garamond Italic (cinematic serif), BBH Bartle (graphic poster)
  and system monospace (terminal). The 15 looks are AURORA, NOIR, GOLDEN, NEON, EMBER, PRISM, CHROME,
  TERMINAL, HOLOGRAM, ORBIT, TIDAL, DEPTH, ECHO, POSTER and STARDUST.
- `PremiumLyricsPolicy`: pure Kotlin grouping of the shared flat word stream into phrase rows, frame
  construction, and beat-aware AUTO selection with hysteresis (1.8 s on a strong drop, 4 s on a look
  change, 6 s on a same-look refresh) so it never strobes on every kick.
- `PremiumLyricsRenderer`: original Android-Canvas renderer on true black. All 15 looks share one
  editorial typography baseline — large centered lines with auto-fit, a heavier 1.16× current word,
  real font-weight contrast, tight negative tracking, a progressive accent→white karaoke fill and a
  thin rounded progress rule. Each look then adds its own restrained layer: light wash, gold hairline,
  amber halo, tube bloom, sparks, chromatic split, metal extrusion, phosphor scanlines, hologram scan
  bar, orbital rings, sine bed, parallax depth, ghost trails, poster offset or drifting dust.
- `PremiumLyricsOverlay`: immersive full-screen surface, glass controls, a compact look picker grouped
  by the five type voices (AUTO + all 15 looks), seek bar, transport, fullscreen toggle and mic-permission
  prompt for beat detection. Picker and controls use Plus Jakarta Sans throughout.
- Persisted preference: `premiumLyricStyle` (`PremiumLyricStyleKey`), default `AUTO`. Stored names are
  the stable enum names, so an old persisted value that no longer exists falls back to `AUTO`.

## The 15 looks

| Look | Voice | Signature |
|---|---|---|
| Aurora | Plus Jakarta Sans | Flowing ribbons of cool light |
| Noir | Cormorant Garamond Italic | Pure black, one gold hairline, no motion |
| Golden Hour | Cormorant Garamond Italic | Amber halo and letterbox bars |
| Neon | Outfit | Cyan tubes with magenta bloom |
| Ember | Plus Jakarta Sans | Rising sparks and a warm heat haze |
| Prism | Plus Jakarta Sans | Chromatic split around every word |
| Chrome | Outfit | Polished metal, extruded layers, indigo edge |
| Terminal | System monospace | Green phosphor scanlines and a block cursor |
| Hologram | Outfit | Travelling scan bar and ghosted layers |
| Orbit | Plus Jakarta Sans | Elliptical rings and a satellite dot |
| Tidal | Plus Jakarta Sans | Words riding a beat-driven sine bed |
| Depth | Outfit | Layered extrusion with parallax |
| Echo | Cormorant Garamond Italic | Ghost trails and a mirrored reflection |
| Poster | BBH Bartle | Bold graphic type with a hard offset |
| Stardust | Plus Jakarta Sans | Drifting stars and cool dust |

## AUTO ("BeatIQ")

`PremiumLyricsPolicy.chooseAutoStyle` combines real `AudioFeatures` (`energy`, `intensity`, `beat`,
`bpm`, `bass`, `drop`, `silence`) with the engine's `SongSection` and `CinematicMood`:

| Signal | Look bias |
|---|---|
| Silence / instrumental / quiet | NOIR |
| Drop or peak + aggressive/dark | TERMINAL |
| Drop or peak + party/energetic | POSTER |
| Other drops or peaks | NEON |
| High BPM / drive + dreamy/lo-fi | TIDAL |
| High BPM / drive + romantic | ORBIT |
| High BPM / drive, other | PRISM |
| Sad / emotional | ECHO |
| Chill / lo-fi | STARDUST |
| Romantic / dreamy | AURORA |
| Motivational | DEPTH |
| Neutral / remaining | CHROME |

AUTO always resolves to one of the 15 selectable looks. Hysteresis holds the current look until the
threshold above is met. Groq stays backend-only and never supplies lyrics or timestamps.

## Typography pass (AMOLED parity)

The first renderer draft used one flat size and synthetic bold for every word, which read as cheap.
The current pipeline matches the AMOLED player's editorial rhythm instead:

- base size `min(width / 9.5, height / 13.5)` clamped 32–108 px, with per-line emphasis and a
  width auto-fit pass that scales glyphs **and** spacing together so no line overflows the screen;
- current word 1.16× its neighbours, real bold typeface, quieter past/future words and dimmed
  ±1/±2 phrase rows;
- progressive karaoke fill clipped to the word's verified timestamp progress, plus a short glowing
  underline — visual only, never a change to word text or timings;
- measurement uses the same typeface, size and letter spacing as drawing, so bold words never run
  past the measured bounds;
- effects are tuned down (softer bloom, fewer echo/extrusion layers, subtler scanlines) so all 15
  looks stay readable at a glance, even on a large screen.

No React/Tailwind code, original artwork, logo, font file or copy was copied into the app. The four
bundled OFL fonts are credited in `THIRD_PARTY_NOTICES.md` with full license texts under `licenses/`.
