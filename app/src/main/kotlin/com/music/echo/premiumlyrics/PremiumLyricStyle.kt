package com.music.echo.premiumlyrics

/**
 * Typographic voices used by the premium looks. Plus Jakarta Sans is the
 * signature voice; the others are deliberate accents for a handful of looks
 * so all fifteen feel distinct instead of being recolours of one design.
 */
enum class PremiumLyricFont {
  SANS, // Plus Jakarta Sans — clean editorial signature
  DISPLAY, // Outfit — geometric, modern
  SERIF, // Cormorant Garamond Italic — cinematic, luxurious
  POSTER, // BBH Bartle — bold, graphic
  MONO, // system monospace — terminal
  ;
}

/**
 * Fifteen distinct premium looks. Each look owns its own palette, typeface and
 * signature motion; none is a recolour of another. All paint on true black so
 * the result keeps the AMOLED contrast.
 */
enum class PremiumLook(val label: String, val font: PremiumLyricFont) {
  AURORA("Aurora", PremiumLyricFont.SANS),
  NOIR("Noir", PremiumLyricFont.SERIF),
  GOLDEN("Golden Hour", PremiumLyricFont.SERIF),
  NEON("Neon", PremiumLyricFont.DISPLAY),
  EMBER("Ember", PremiumLyricFont.SANS),
  PRISM("Prism", PremiumLyricFont.SANS),
  CHROME("Chrome", PremiumLyricFont.DISPLAY),
  TERMINAL("Terminal", PremiumLyricFont.MONO),
  HOLOGRAM("Hologram", PremiumLyricFont.DISPLAY),
  ORBIT("Orbit", PremiumLyricFont.SANS),
  TIDAL("Tidal", PremiumLyricFont.SANS),
  DEPTH("Depth", PremiumLyricFont.DISPLAY),
  ECHO("Echo", PremiumLyricFont.SERIF),
  POSTER("Poster", PremiumLyricFont.POSTER),
  STARDUST("Stardust", PremiumLyricFont.SANS),
  ;
}

/**
 * The fifteen selectable looks plus AUTO. AUTO is a real style choice: it reads
 * the existing beat/BPM/energy features and switches looks with hysteresis.
 *
 * Names, palettes and motion parameters are original. No lyric text or timing
 * lives here; this enum only controls how the shared engine output is painted.
 */
enum class PremiumLyricStyle(
  val label: String,
  val look: PremiumLook,
  val primaryArgb: Long,
  val accentArgb: Long,
  val glowArgb: Long,
  val speed: Float,
  val density: Float,
  val glow: Float,
) {
  AUTO(
    "AUTO · BeatIQ",
    PremiumLook.AURORA,
    0xFFF2F5FF,
    0xFF6CE5FF,
    0xFFB388FF,
    1f,
    0.5f,
    1f,
  ),

  // Flowing light ribbons — the quiet, closest relative of the AMOLED look.
  AURORA("Aurora", PremiumLook.AURORA, 0xFFEAF2FF, 0xFF6FE7FF, 0xFFA78BFA, 0.7f, 0.4f, 0.95f),

  // Quiet luxury: pure black, one gold hairline, no motion at all.
  NOIR("Noir", PremiumLook.NOIR, 0xFFF5F1E8, 0xFFE8C77B, 0xFF8B7355, 0.45f, 0.15f, 0.45f),

  // Warm cinema: amber halo and letterbox bars, editorial serif.
  GOLDEN("Golden Hour", PremiumLook.GOLDEN, 0xFFFFF6E5, 0xFFF5C86B, 0xFFFF8A5B, 0.6f, 0.4f, 1.05f),

  // Electric signage: cyan tubes, magenta bloom.
  NEON("Neon", PremiumLook.NEON, 0xFFF0FDFF, 0xFF22D3EE, 0xFFF472B6, 1f, 0.6f, 1.5f),

  // Fire: rising sparks and a warm heat haze.
  EMBER("Ember", PremiumLook.EMBER, 0xFFFFF4EC, 0xFFFF8A4C, 0xFFFF3D5A, 0.9f, 0.8f, 1.3f),

  // Chromatic aberration: red/blue split around every word.
  PRISM("Prism", PremiumLook.PRISM, 0xFFFFFFFF, 0xFF7DD3FC, 0xFFF0ABFC, 0.85f, 0.55f, 1f),

  // Polished metal: silver gradient, extruded layers, indigo edge.
  CHROME("Chrome", PremiumLook.CHROME, 0xFFFFFFFF, 0xFFCBD5E1, 0xFF818CF8, 0.8f, 0.5f, 1.1f),

  // Green phosphor terminal: scanlines and a block cursor.
  TERMINAL("Terminal", PremiumLook.TERMINAL, 0xFFDCFCE7, 0xFF34D399, 0xFF6EE7B7, 0.9f, 0.55f, 0.9f),

  // Precision hologram: travelling scan bar and ghosted layers.
  HOLOGRAM("Hologram", PremiumLook.HOLOGRAM, 0xFFF0F9FF, 0xFF7DD3FC, 0xFFA78BFA, 1f, 0.6f, 1.3f),

  // Elliptical rings and a satellite dot orbiting the active line.
  ORBIT("Orbit", PremiumLook.ORBIT, 0xFFF0FDFA, 0xFF5EEAD4, 0xFF818CF8, 0.85f, 0.5f, 1.05f),

  // Words ride a sine bed that follows the actual beat.
  TIDAL("Tidal", PremiumLook.TIDAL, 0xFFEFF6FF, 0xFF60A5FA, 0xFFC084FC, 0.85f, 0.55f, 1f),

  // Layered extrusion with parallax depth.
  DEPTH("Depth", PremiumLook.DEPTH, 0xFFF8FAFC, 0xFF94A3B8, 0xFF38BDF8, 0.9f, 0.55f, 1f),

  // Ghost trails and a mirrored reflection, cinematic serif.
  ECHO("Echo", PremiumLook.ECHO, 0xFFF8FAFC, 0xFFC4B5FD, 0xFF67E8F9, 0.8f, 0.5f, 1.1f),

  // Bold poster type: hard offset shadow, grain, warm peach.
  POSTER("Poster", PremiumLook.POSTER, 0xFFFFF7ED, 0xFFFFB4A2, 0xFFF472B6, 0.7f, 0.5f, 1.05f),

  // Drifting stars and cool dust around the words.
  STARDUST("Stardust", PremiumLook.STARDUST, 0xFFF8FAFC, 0xFFE0E7FF, 0xFF7DD3FC, 0.8f, 0.85f, 1.15f),
  ;

  companion object {
    /** All 15 handcrafted looks, excluding AUTO. */
    val SELECTABLE: List<PremiumLyricStyle> = entries.filterNot { it == AUTO }

    fun fromName(value: String?): PremiumLyricStyle =
      entries.find { it.name.equals(value, ignoreCase = true) } ?: AUTO

    fun isSelectable(value: PremiumLyricStyle): Boolean = value != AUTO
  }
}
