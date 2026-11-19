package com.music.echo.insta

import kotlinx.serialization.Serializable

/** Original visual presets inspired by the supplied written references. */
@Serializable
enum class InstaVisualStyle(val label: String, val description: String) {
  VINTAGE_CINEMATIC(
    "Vintage Cinematic",
    "Antique gold serif, floral shadows, painterly light and slow parallax.",
  ),
  BOLD_TYPOGRAPHY(
    "Bold Typography",
    "Oversized high-contrast words with procedural distress and hard editorial placement.",
  ),
  MINIMAL_KINETIC(
    "Minimal Kinetic",
    "Black canvas, restrained white type, small handwritten gold accents and staggered motion.",
  ),
  VINTAGE_FILM(
    "Vintage Film",
    "Muted green-grey grain, soft scratches and atmospheric cinematic fades.",
  ),
  SMART_HYBRID(
    "Smart Hybrid",
    "Chooses among the four original styles from mood, section, energy, language and preference.",
  );

  companion object {
    fun fromName(value: String?): InstaVisualStyle =
      entries.find { it.name.equals(value, ignoreCase = true) } ?: VINTAGE_CINEMATIC
  }
}

@Serializable
enum class InstaBackground(val label: String) {
  PURE_BLACK("True black AMOLED"),
  VINTAGE_PAINTING("Vintage painting"),
  FLORAL_TEXTURE("Floral texture"),
  CINEMATIC_SCENERY("Cinematic scenery"),
  VINTAGE_FILM("Vintage film"),
  GRADIENT("Gradient"),
  ALBUM_ARTWORK("Blurred album artwork"),
  CUSTOM_IMAGE("Custom image"),
  CUSTOM_VIDEO("Custom video");

  companion object {
    fun fromName(value: String?): InstaBackground =
      entries.find { it.name.equals(value, ignoreCase = true) } ?: PURE_BLACK
  }
}

@Serializable
enum class InstaFont(val label: String) {
  SERIF("Elegant serif"),
  SANS("Modern sans"),
  DISPLAY("Display"),
  MONO("Monospace"),
  HANDWRITTEN("Handwritten accent"),
  GENZ("Gen Z display");

  companion object {
    fun fromName(value: String?): InstaFont =
      entries.find { it.name.equals(value, ignoreCase = true) } ?: SERIF
  }
}

@Serializable
enum class InstaAnimationPattern(val label: String) {
  WORD_BY_WORD("Word by word"),
  LETTER_BY_LETTER("Letter by letter"),
  TYPEWRITER("Typewriter"),
  SCALE_IN("Scale in"),
  CINEMATIC_ZOOM("Cinematic zoom"),
  SPLIT_TEXT("Split text"),
  VERTICAL_REVEAL("Vertical reveal"),
  HORIZONTAL_SLIDE("Horizontal slide"),
  BLUR_TO_FOCUS("Blur to focus"),
  FADE_DISSOLVE("Fade and dissolve"),
  MASK_REVEAL("Mask reveal"),
  STAGGERED("Staggered typography"),
  EDITORIAL("Editorial layout"),
  HANDWRITTEN_ACCENT("Handwritten accent");

  companion object {
    fun fromName(value: String?): InstaAnimationPattern =
      entries.find { it.name.equals(value, ignoreCase = true) } ?: WORD_BY_WORD
  }
}

@Serializable
enum class InstaTextAlign(val label: String) {
  START("Start"),
  CENTER("Center"),
  END("End");

  companion object {
    fun fromName(value: String?): InstaTextAlign =
      entries.find { it.name.equals(value, ignoreCase = true) } ?: CENTER
  }
}

@Serializable
enum class InstaTextPosition(val label: String) {
  CENTER("Center"),
  TOP("Top"),
  BOTTOM("Bottom"),
  LEFT("Left"),
  RIGHT("Right"),
  EDITORIAL("Editorial");

  companion object {
    fun fromName(value: String?): InstaTextPosition =
      entries.find { it.name.equals(value, ignoreCase = true) } ?: CENTER
  }
}

/** A complete, saveable customization. All numeric values are bounded by policy on use. */
@Serializable
data class InstaPreset(
  val name: String = "Neon Pulse",
  val style: InstaVisualStyle = InstaVisualStyle.BOLD_TYPOGRAPHY,
  val background: InstaBackground = InstaBackground.PURE_BLACK,
  val font: InstaFont = InstaFont.DISPLAY,
  val fontSize: Float = 1.5f,
  val fontWeight: Int = 900,
  val alignment: InstaTextAlign = InstaTextAlign.CENTER,
  val position: InstaTextPosition = InstaTextPosition.CENTER,
  val spacing: Float = 1f,
  val wordSpacing: Float = 1f,
  val animationSpeed: Float = 1f,
  val transitionDurationMs: Long = 320L,
  val backgroundIntensity: Float = 0.7f,
  val grainIntensity: Float = 0.35f,
  val blur: Float = 0f,
  val glow: Float = 0.5f,
  val animationIntensity: Float = 0.7f,
  val highlightColor: Long = 0xFF7ED5FFL,
  val safeMargin: Float = 0.1f,
  val pattern: InstaAnimationPattern = InstaAnimationPattern.WORD_BY_WORD,
  val lowPower: Boolean = false,
  val watermark: String = "AURA MUSIC",
)

/** Serializable word payload shared by live playback and the settings preview. */
@Serializable
data class InstaWord(
  val text: String,
  val startMs: Long,
  val endMs: Long,
  val verified: Boolean = true,
)

/** All inputs the frame renderer needs. No Android or Compose types are included. */
@Serializable
data class InstaRenderSpec(
  val words: List<InstaWord>,
  val title: String,
  val artist: String,
  val preset: InstaPreset,
  val effectiveStyle: InstaVisualStyle,
  val mood: String = "NEUTRAL",
  val section: String = "VERSE",
  val energy: Float = 0.5f,
  val language: String = "unknown",
  val artworkUrl: String? = null,
  val customImagePath: String? = null,
  val customVideoPath: String? = null,
  val estimatedTiming: Boolean = false,
)

object InstaPresets {
  /**
   * All looks share the oversized bold typography treatment the user liked.
   * Enum names and list order are intentionally unchanged so saved DataStore
   * presets keep decoding; only the visual definitions are upgraded.
   */
  val BUILT_IN: List<InstaPreset> =
    listOf(
      InstaPreset(
        name = "Neon Pulse",
        style = InstaVisualStyle.BOLD_TYPOGRAPHY,
        background = InstaBackground.PURE_BLACK,
        font = InstaFont.DISPLAY,
        fontSize = 1.5f,
        fontWeight = 900,
        spacing = 0.94f,
        backgroundIntensity = 0.9f,
        grainIntensity = 0.12f,
        glow = 0.5f,
        animationIntensity = 0.85f,
        highlightColor = 0xFF7ED5FFL,
        safeMargin = 0.1f,
        pattern = InstaAnimationPattern.SCALE_IN,
      ),
      InstaPreset(
        name = "Bold Typography",
        style = InstaVisualStyle.BOLD_TYPOGRAPHY,
        background = InstaBackground.PURE_BLACK,
        font = InstaFont.DISPLAY,
        fontSize = 1.55f,
        fontWeight = 900,
        spacing = 0.92f,
        grainIntensity = 0.16f,
        glow = 0.08f,
        animationIntensity = 0.9f,
        highlightColor = 0xFFFFFFFFL,
        safeMargin = 0.1f,
        pattern = InstaAnimationPattern.SCALE_IN,
      ),
      InstaPreset(
        name = "Chrome Pop",
        style = InstaVisualStyle.BOLD_TYPOGRAPHY,
        background = InstaBackground.GRADIENT,
        font = InstaFont.GENZ,
        fontSize = 1.45f,
        fontWeight = 900,
        spacing = 0.9f,
        backgroundIntensity = 0.85f,
        grainIntensity = 0.08f,
        glow = 0.55f,
        animationIntensity = 0.8f,
        highlightColor = 0xFFFFFFFFL,
        safeMargin = 0.11f,
        pattern = InstaAnimationPattern.MASK_REVEAL,
      ),
      InstaPreset(
        name = "Rose Riot",
        style = InstaVisualStyle.BOLD_TYPOGRAPHY,
        background = InstaBackground.PURE_BLACK,
        font = InstaFont.GENZ,
        fontSize = 1.42f,
        fontWeight = 900,
        spacing = 0.95f,
        grainIntensity = 0.28f,
        glow = 0.32f,
        animationIntensity = 0.75f,
        highlightColor = 0xFFFF8FB1L,
        safeMargin = 0.11f,
        pattern = InstaAnimationPattern.HORIZONTAL_SLIDE,
      ),
      InstaPreset(
        name = "Afterglow",
        style = InstaVisualStyle.BOLD_TYPOGRAPHY,
        background = InstaBackground.PURE_BLACK,
        font = InstaFont.DISPLAY,
        fontSize = 1.38f,
        fontWeight = 900,
        spacing = 0.92f,
        grainIntensity = 0f,
        glow = 0f,
        animationIntensity = 0.35f,
        highlightColor = 0xFFFF6B57L,
        safeMargin = 0.12f,
        lowPower = true,
        pattern = InstaAnimationPattern.WORD_BY_WORD,
      ),
    )

  fun byStyle(style: InstaVisualStyle): InstaPreset =
    BUILT_IN.firstOrNull { it.style == style } ?: BUILT_IN.first()

  fun find(name: String?): InstaPreset? =
    BUILT_IN.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
