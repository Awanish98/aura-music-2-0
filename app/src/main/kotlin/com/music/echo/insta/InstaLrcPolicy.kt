package com.music.echo.insta

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** One computed typographic frame. Coordinates and sizes are normalized to the canvas. */
data class InstaWordFrame(
  val word: InstaWord,
  val index: Int,
  val x: Float,
  val y: Float,
  val sizeScale: Float,
  val alpha: Float,
  val rotationDegrees: Float,
  val revealChars: Int,
  val glow: Float,
  val blurPasses: Int,
  val important: Boolean,
  val pattern: InstaAnimationPattern,
  val active: Boolean,
)

/**
 * Pure policy for INSTA LRC PLAY. It never reads the clock: every frame is a
 * function of the player position and the supplied lyrics, so pausing, seeking
 * and changing speed cannot desynchronise the typography.
 */
object InstaLrcPolicy {
  private val ROMANTIC_MOODS = setOf("ROMANTIC", "MELANCHOLIC", "NOSTALGIC", "DREAMY")
  private val QUIET_SECTIONS = setOf("INSTRUMENTAL", "OUTRO", "BRIDGE")
  private val HIGHLIGHT_WORDS =
    setOf(
      "love",
      "broken",
      "dream",
      "dreams",
      "fire",
      "silence",
      "memory",
      "memories",
      "forever",
      "heart",
      "home",
      "light",
      "dark",
      "rain",
      "sky",
      "wait",
      "believe",
      "pyaar",
      "ishq",
      "sapna",
      "sapne",
      "dil",
      "aag",
      "chup",
      "yaad",
      "zindagi",
      "है",
      "प्यार",
      "इश्क़",
      "सपने",
      "दिल",
      "आग",
      "चुप",
      "यादें",
      "ज़िंदगी",
    )

  fun detectLanguage(text: String): String {
    if (text.any { it in '\u0900'..'\u097F' }) return "hi"
    if (text.any { it in '\u0600'..'\u06FF' }) return "ur"
    if (text.any { it in '\u3040'..'\u30FF' }) return "ja"
    if (text.any { it in '\uAC00'..'\uD7AF' }) return "ko"
    if (text.any { it in '\u4E00'..'\u9FFF' }) return "zh"
    return "en"
  }

  fun chooseStyle(
    requested: InstaVisualStyle,
    mood: String,
    energy: Float,
    section: String,
    language: String,
    preferred: InstaVisualStyle? = null,
  ): InstaVisualStyle {
    if (requested != InstaVisualStyle.SMART_HYBRID) return requested
    val normalizedMood = mood.trim().uppercase()
    val normalizedSection = section.trim().uppercase()
    val level = energy.coerceIn(0f, 1f)
    // Every automatic look now keeps the oversized bold treatment. Mood, energy,
    // section and language only influence the preset's motion/decoration in the
    // renderer; they no longer shrink the type into the old weak themes.
    return when {
      normalizedSection in QUIET_SECTIONS && level < 0.45f -> InstaVisualStyle.BOLD_TYPOGRAPHY
      normalizedMood in ROMANTIC_MOODS || language in setOf("hi", "ur") && level < 0.7f ->
        InstaVisualStyle.BOLD_TYPOGRAPHY
      level >= 0.72f || normalizedSection == "CHORUS" -> InstaVisualStyle.BOLD_TYPOGRAPHY
      preferred != null && preferred != InstaVisualStyle.SMART_HYBRID -> preferred
      else -> InstaVisualStyle.BOLD_TYPOGRAPHY
    }
  }

  fun importance(words: List<InstaWord>, language: String): Map<Int, Float> {
    val counts =
      words
        .map { it.text.trim().trim('.', ',', '!', '?', ':', ';').lowercase() }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
    return words.mapIndexed { index, word ->
      val token = word.text.trim().trim('.', ',', '!', '?', ':', ';').lowercase()
      val keyword = if (token in HIGHLIGHT_WORDS) 0.75f else 0f
      val repeated = if ((counts[token] ?: 0) > 1) 0.35f else 0f
      val lengthBoost = if (token.length >= 7) 0.12f else 0f
      val scriptBoost = if (language != "en" && token.length >= 4) 0.08f else 0f
      index to (keyword + repeated + lengthBoost + scriptBoost).coerceIn(0f, 1f)
    }.toMap()
  }

  fun groupWords(words: List<InstaWord>, maxGapMs: Long = 520L): List<List<InstaWord>> {
    if (words.isEmpty()) return emptyList()
    val sorted = words.sortedBy { it.startMs }
    val groups = mutableListOf<MutableList<InstaWord>>()
    var current = mutableListOf(sorted.first())
    groups.add(current)
    for (index in 1 until sorted.size) {
      val previous = sorted[index - 1]
      val word = sorted[index]
      if (word.startMs - previous.endMs > maxGapMs) {
        current = mutableListOf()
        groups.add(current)
      }
      current.add(word)
    }
    return groups.map { it.toList() }
  }

  fun animationFor(
    word: InstaWord,
    index: Int,
    style: InstaVisualStyle,
    energy: Float,
    importantScore: Float,
    preset: InstaPreset,
  ): InstaAnimationPattern {
    if (preset.pattern == InstaAnimationPattern.HANDWRITTEN_ACCENT && importantScore >= 0.7f) {
      return InstaAnimationPattern.HANDWRITTEN_ACCENT
    }
    return when (style) {
      InstaVisualStyle.VINTAGE_CINEMATIC -> InstaAnimationPattern.CINEMATIC_ZOOM
      // Bold looks stay bold; each preset chooses its own gen-z motion.
      InstaVisualStyle.BOLD_TYPOGRAPHY -> preset.pattern
      InstaVisualStyle.MINIMAL_KINETIC ->
        if (index % 3 == 1) InstaAnimationPattern.STAGGERED else InstaAnimationPattern.WORD_BY_WORD
      InstaVisualStyle.VINTAGE_FILM -> InstaAnimationPattern.FADE_DISSOLVE
      InstaVisualStyle.SMART_HYBRID -> preset.pattern
    }
  }

  private fun layout(
    index: Int,
    relativeToActive: Int,
    style: InstaVisualStyle,
    preset: InstaPreset,
    important: Boolean,
  ): Pair<Float, Float> {
    var x = 0.5f
    var y = 0.5f
    when (preset.position) {
      InstaTextPosition.TOP -> y = 0.3f
      InstaTextPosition.BOTTOM -> y = 0.7f
      InstaTextPosition.LEFT -> x = 0.3f
      InstaTextPosition.RIGHT -> x = 0.7f
      InstaTextPosition.EDITORIAL -> {
        x = when (index % 3) {
          0 -> 0.3f
          1 -> 0.66f
          else -> 0.44f
        }
        y = when (index % 4) {
          0 -> 0.38f
          1 -> 0.62f
          2 -> 0.5f
          else -> 0.72f
        }
      }
      InstaTextPosition.CENTER -> Unit
    }
    y += relativeToActive * 0.075f * preset.spacing.coerceIn(0.6f, 1.8f)
    if (style == InstaVisualStyle.BOLD_TYPOGRAPHY && important) {
      x += (if (index % 2 == 0) -0.05f else 0.05f)
      y += (if (index % 3 == 0) -0.04f else 0.03f)
    }
    if (style == InstaVisualStyle.MINIMAL_KINETIC && relativeToActive != 0) {
      x += if (relativeToActive > 0) 0.07f else -0.06f
    }
    val margin = preset.safeMargin.coerceIn(0.03f, 0.2f)
    return x.coerceIn(margin, 1f - margin) to y.coerceIn(margin, 1f - margin)
  }

  /**
   * Deterministic typography frames for one phrase. [positionMs] must come from
   * the live player position; [beatImpact] and [bass] only modulate decoration,
   * never which word is selected or when it appears.
   */
  fun framesForPhrase(
    phrase: List<InstaWord>,
    positionMs: Long,
    preset: InstaPreset,
    style: InstaVisualStyle,
    energy: Float,
    beatImpact: Float = 0f,
    bass: Float = 0f,
    language: String = detectLanguage(phrase.joinToString(" ") { it.text }),
  ): List<InstaWordFrame> {
    if (phrase.isEmpty()) return emptyList()
    val scores = importance(phrase, language)
    val activeIndex =
      phrase.indexOfLast { it.startMs <= positionMs }.takeIf { it >= 0 }
        ?: phrase.indexOfFirst { it.startMs > positionMs }.takeIf { it >= 0 }
        ?: 0
    val first = (activeIndex - 1).coerceAtLeast(0)
    val last = (activeIndex + 2).coerceAtMost(phrase.lastIndex)
    val transition = preset.transitionDurationMs.coerceIn(90L, 900L)
    val speed = preset.animationSpeed.coerceIn(0.4f, 2f)
    val intensity = preset.animationIntensity.coerceIn(0f, 1f)
    val lowPower = preset.lowPower

    return (first..last).map { index ->
      val word = phrase[index]
      val duration = (word.endMs - word.startMs).coerceAtLeast(1L)
      val progress = ((positionMs - word.startMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
      val appear = ((positionMs - word.startMs + transition).toFloat() / transition).coerceIn(0f, 1f)
      val exit = ((word.endMs + transition - positionMs).toFloat() / transition).coerceIn(0f, 1f)
      val active = index == activeIndex && positionMs in word.startMs..word.endMs
      val upcoming = word.startMs > positionMs
      val past = word.endMs < positionMs
      val score = scores[index] ?: 0f
      val important = score >= 0.6f
      val (x, y) = layout(index, index - activeIndex, style, preset, important)

      val pattern = animationFor(word, index, style, energy, score, preset)
      val alpha =
        when {
          active -> (0.82f + 0.18f * appear).coerceAtMost(1f)
          upcoming -> 0.1f + 0.12f * appear * intensity
          past -> 0.18f + 0.12f * exit * intensity
          else -> 0.25f
        }
      val baseScale =
        when {
          active && important -> 1.45f
          active -> 1.05f
          upcoming -> 0.55f
          else -> 0.72f
        }
      val motion =
        when (pattern) {
          InstaAnimationPattern.SCALE_IN,
          InstaAnimationPattern.CINEMATIC_ZOOM,
          InstaAnimationPattern.BLUR_TO_FOCUS,
          -> progress * 0.34f * intensity
          else -> 0.08f * intensity * sin(progress * 3.14159f)
        }
      val beatScale = if (!lowPower) beatImpact.coerceIn(0f, 1f) * 0.12f * intensity else 0f
      val bassScale = if (!lowPower) bass.coerceIn(0f, 1f) * 0.06f * intensity else 0f
      val sizeScale = (baseScale + motion + beatScale + bassScale) * preset.fontSize.coerceIn(0.6f, 2f)
      val rotationSeed = ((index * 37) % 11).toFloat() - 5f
      val rotation =
        if (lowPower) 0f
        else when (style) {
          InstaVisualStyle.BOLD_TYPOGRAPHY -> rotationSeed * 0.7f * intensity
          InstaVisualStyle.VINTAGE_CINEMATIC -> rotationSeed * 0.16f * intensity
          else -> rotationSeed * 0.1f * intensity
        }
      val revealChars =
        when (pattern) {
          InstaAnimationPattern.LETTER_BY_LETTER,
          InstaAnimationPattern.TYPEWRITER,
          InstaAnimationPattern.MASK_REVEAL,
          -> (word.text.length * (progress * speed).coerceIn(0f, 1f)).roundToInt().coerceAtLeast(1)
          else -> word.text.length
        }
      val glow =
        if (lowPower) 0f
        else (if (important) preset.glow else preset.glow * 0.25f) *
          (0.7f + 0.3f * cos(progress * 3.14159f)).coerceIn(0f, 1f) +
          beatImpact.coerceIn(0f, 1f) * 0.2f
      val blurPasses =
        if (lowPower || preset.blur <= 0f) 0
        else if (pattern == InstaAnimationPattern.BLUR_TO_FOCUS && progress < 0.55f) 3 else 0

      InstaWordFrame(
        word = word,
        index = index,
        x = x,
        y = y,
        sizeScale = sizeScale.coerceIn(0.35f, 2.6f),
        alpha = alpha.coerceIn(0f, 1f),
        rotationDegrees = rotation,
        revealChars = revealChars.coerceIn(1, word.text.length.coerceAtLeast(1)),
        glow = glow.coerceIn(0f, 1f),
        blurPasses = blurPasses,
        important = important,
        pattern = pattern,
        active = active,
      )
    }
  }

  fun phraseAt(
    phrases: List<List<InstaWord>>,
    positionMs: Long,
  ): List<InstaWord> =
    phrases.firstOrNull { phrase ->
      positionMs in phrase.first().startMs..phrase.last().endMs
    } ?: phrases.firstOrNull { it.last().startMs > positionMs }
      ?: phrases.lastOrNull().orEmpty()

  fun isVerified(words: List<InstaWord>): Boolean = words.all { it.verified }

  fun driftFor(style: InstaVisualStyle, positionMs: Long, intensity: Float): Pair<Float, Float> {
    if (intensity <= 0f) return 0f to 0f
    val t = positionMs / 9000f
    return when (style) {
      InstaVisualStyle.VINTAGE_CINEMATIC ->
        (sin(t * 0.55f) * 8f * intensity) to (cos(t * 0.42f) * 5f * intensity)
      InstaVisualStyle.BOLD_TYPOGRAPHY ->
        (sin(t * 1.3f) * 4f * intensity) to (cos(t * 0.9f) * 3f * intensity)
      InstaVisualStyle.MINIMAL_KINETIC ->
        (sin(t * 0.8f) * 3f * intensity) to (cos(t * 1.1f) * 2f * intensity)
      InstaVisualStyle.VINTAGE_FILM ->
        (sin(t * 0.3f) * 6f * intensity) to (cos(t * 0.25f) * 4f * intensity)
      InstaVisualStyle.SMART_HYBRID -> 0f to 0f
    }
  }
}
