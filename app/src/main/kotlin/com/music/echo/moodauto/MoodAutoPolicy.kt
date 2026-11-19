package com.music.echo.moodauto

import com.music.echo.premiumlyrics.PremiumLyricStyle
import echo.music.iad1tya.cinematic.AudioFeatures
import echo.music.iad1tya.cinematic.CinematicMood
import echo.music.iad1tya.cinematic.CinematicTheme
import echo.music.iad1tya.cinematic.SongSection
import echo.music.iad1tya.ui.player.FullVisualizerStyle

/**
 * Pure mapping from the song's mood to one combined lyrics + visual experience.
 *
 * It only steers presentation. Lyric words, word timestamps and playback stay
 * exactly as the shared cinematic engine produced them. Backend/AI signals are
 * accepted only as hints and are discounted unless they are confident.
 */
enum class MoodFamily {
  CALM,
  ROMANTIC,
  DREAMY,
  BRIGHT,
  DARK,
  NEUTRAL,
}

data class MoodAutoInput(
  val mood: CinematicMood,
  val moodConfidence: Float,
  val features: AudioFeatures,
  val section: SongSection,
  val engineTheme: CinematicTheme,
  val aiMood: CinematicMood? = null,
  val aiConfidence: Float = 0f,
  val aiThemeHint: CinematicTheme? = null,
  val aiDegraded: Boolean = true,
)

data class MoodAutoExperience(
  val mood: CinematicMood,
  val family: MoodFamily,
  val confidence: Float,
  val lyricStyle: PremiumLyricStyle,
  val visualStyle: FullVisualizerStyle,
  /** Null means "keep the already selected cinematic theme". */
  val theme: CinematicTheme?,
  val motionScale: Float,
  val glowScale: Float,
  val particleScale: Float,
  val headline: String,
  val subtitle: String,
)

object MoodAutoPolicy {
  private const val MIN_CONFIDENCE = 0.32f

  /** Resolves the presentation recipe. No lyric or timestamp is ever changed. */
  fun resolve(input: MoodAutoInput): MoodAutoExperience {
    val usableAiMood =
      input.aiMood
        ?.takeIf { !input.aiDegraded && it != CinematicMood.NEUTRAL }
        ?.takeIf { input.aiConfidence > MIN_CONFIDENCE }
    val aiUsable = usableAiMood != null
    val mood = usableAiMood ?: input.mood
    val confidence = if (aiUsable) input.aiConfidence else input.moodConfidence
    val confident = mood != CinematicMood.NEUTRAL && confidence >= MIN_CONFIDENCE
    val family = if (confident) familyOf(mood) else MoodFamily.NEUTRAL

    val quiet =
      input.features.silence ||
        input.section == SongSection.INSTRUMENTAL ||
        input.features.energy < 0.22f
    val drive =
        input.features.drop > 0.6f ||
          input.features.bpm >= 126f ||
          input.features.energy > 0.68f

    val lyricStyle =
      when {
        !confident -> PremiumLyricStyle.AURORA
        quiet && family != MoodFamily.BRIGHT -> PremiumLyricStyle.NOIR
        else ->
          when (family) {
            MoodFamily.CALM ->
              when (mood) {
                CinematicMood.SAD -> PremiumLyricStyle.ECHO
                CinematicMood.ROMANTIC -> PremiumLyricStyle.ORBIT
                CinematicMood.EMOTIONAL -> PremiumLyricStyle.GOLDEN
                else -> PremiumLyricStyle.STARDUST
              }
            MoodFamily.ROMANTIC -> PremiumLyricStyle.AURORA
            MoodFamily.DREAMY -> PremiumLyricStyle.TIDAL
            MoodFamily.BRIGHT ->
              when {
                drive -> PremiumLyricStyle.POSTER
                mood == CinematicMood.MOTIVATIONAL -> PremiumLyricStyle.DEPTH
                else -> PremiumLyricStyle.NEON
              }
            MoodFamily.DARK -> PremiumLyricStyle.TERMINAL
            MoodFamily.NEUTRAL -> PremiumLyricStyle.AURORA
          }
      }

    val visualStyle =
      when {
        !confident -> FullVisualizerStyle.SPECTRUM
        quiet -> FullVisualizerStyle.AURORA
        else ->
          when (family) {
            MoodFamily.CALM -> FullVisualizerStyle.WAVE
            MoodFamily.ROMANTIC -> FullVisualizerStyle.RINGS
            MoodFamily.DREAMY -> FullVisualizerStyle.PARTICLES
            MoodFamily.BRIGHT ->
              if (drive) FullVisualizerStyle.THREE_D else FullVisualizerStyle.RINGS
            MoodFamily.DARK -> FullVisualizerStyle.RADIAL
            MoodFamily.NEUTRAL -> FullVisualizerStyle.SPECTRUM
          }
      }

    // A low-confidence hint must not replace the theme already chosen by the
    // on-device selector. A confident, non-degraded AI theme hint is honoured.
    val theme =
      when {
        aiUsable && input.aiThemeHint != null -> input.aiThemeHint
        !confident -> null
        else -> themeFor(mood, drive)
      }

    val familyMotion =
      when (family) {
        MoodFamily.CALM -> 0.5f
        MoodFamily.ROMANTIC -> 0.62f
        MoodFamily.DREAMY -> 0.72f
        MoodFamily.BRIGHT -> if (drive) 1.25f else 1.05f
        MoodFamily.DARK -> 0.9f
        MoodFamily.NEUTRAL -> 0.85f
      }
    val familyGlow =
      when (family) {
        MoodFamily.CALM -> 0.62f
        MoodFamily.ROMANTIC -> 0.9f
        MoodFamily.DREAMY -> 0.85f
        MoodFamily.BRIGHT -> 1.25f
        MoodFamily.DARK -> 0.8f
        MoodFamily.NEUTRAL -> 0.9f
      }
    val familyParticles =
      when (family) {
        MoodFamily.CALM -> 0.35f
        MoodFamily.ROMANTIC -> 0.6f
        MoodFamily.DREAMY -> 0.75f
        MoodFamily.BRIGHT -> 1.2f
        MoodFamily.DARK -> 0.7f
        MoodFamily.NEUTRAL -> 0.5f
      }

    val quietFactor = if (quiet) 0.82f else 1f
    val energyFactor = 0.9f + input.features.energy.coerceIn(0f, 1f) * 0.2f
    val (headline, subtitle) = copyFor(mood, family, confident)

    return MoodAutoExperience(
      mood = mood,
      family = family,
      confidence = confidence,
      lyricStyle = lyricStyle,
      visualStyle = visualStyle,
      theme = theme,
      motionScale = (familyMotion * quietFactor).coerceIn(0.3f, 1.6f),
      glowScale = (familyGlow * energyFactor).coerceIn(0.35f, 1.6f),
      particleScale = (familyParticles * energyFactor).coerceIn(0.2f, 1.6f),
      headline = headline,
      subtitle = subtitle,
    )
  }

  private fun familyOf(mood: CinematicMood): MoodFamily =
    when (mood) {
      CinematicMood.SAD,
      CinematicMood.EMOTIONAL,
      CinematicMood.CHILL,
      CinematicMood.LO_FI -> MoodFamily.CALM
      CinematicMood.ROMANTIC -> MoodFamily.ROMANTIC
      CinematicMood.DREAMY -> MoodFamily.DREAMY
      CinematicMood.PARTY,
      CinematicMood.ENERGETIC,
      CinematicMood.MOTIVATIONAL -> MoodFamily.BRIGHT
      CinematicMood.AGGRESSIVE,
      CinematicMood.DARK -> MoodFamily.DARK
      CinematicMood.NEUTRAL -> MoodFamily.NEUTRAL
    }

  private fun themeFor(mood: CinematicMood, drive: Boolean): CinematicTheme =
    when (mood) {
      CinematicMood.SAD -> CinematicTheme.RAINY_NIGHTS
      CinematicMood.EMOTIONAL -> CinematicTheme.GOLDEN_CINEMA
      CinematicMood.ROMANTIC -> CinematicTheme.ROMANTIC_BLOOM
      CinematicMood.DREAMY -> CinematicTheme.DREAMSCAPE
      CinematicMood.CHILL -> CinematicTheme.MIDNIGHT_BLUE
      CinematicMood.LO_FI -> CinematicTheme.VINTAGE_FILM
      CinematicMood.AGGRESSIVE -> CinematicTheme.CYBER_GLITCH
      CinematicMood.DARK -> CinematicTheme.CRIMSON_MOOD
      CinematicMood.MOTIVATIONAL -> CinematicTheme.SUNSET_GLOW
      CinematicMood.PARTY -> CinematicTheme.NEON_DREAMS
      CinematicMood.ENERGETIC ->
        if (drive) CinematicTheme.THREE_D_TYPE else CinematicTheme.MOTION_BLUR
      CinematicMood.NEUTRAL -> CinematicTheme.GOLDEN_CINEMA
    }

  private fun copyFor(
    mood: CinematicMood,
    family: MoodFamily,
    confident: Boolean,
  ): Pair<String, String> {
    if (!confident) {
      return "AUTO · MOOD MATCH" to "Neutral fallback · lyrics stay perfectly on time"
    }
    val label = mood.name.replace('_', ' ').uppercase()
    return when (family) {
      MoodFamily.CALM ->
        "AUTO · $label" to "Slow glow · calm motion · words breathe with the vocal"
      MoodFamily.ROMANTIC ->
        "AUTO · $label" to "Warm bloom · soft rings · unhurried karaoke"
      MoodFamily.DREAMY ->
        "AUTO · $label" to "Drifting light · airy particles · gentle drift"
      MoodFamily.BRIGHT ->
        "AUTO · $label" to "Bright pattern · punchy pulse · beat-true words"
      MoodFamily.DARK ->
        "AUTO · $label" to "Deep contrast · sharp edges · restrained glow"
      MoodFamily.NEUTRAL ->
        "AUTO · MOOD MATCH" to "Neutral fallback · lyrics stay perfectly on time"
    }
  }
}
