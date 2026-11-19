package com.music.echo.codepoetry

import echo.music.iad1tya.ui.component.KaraokeTiming
import echo.music.iad1tya.ui.component.KaraokeWord
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Deterministic, testable policy for CODE POETRY.
 *
 * It never invents lyrics or timestamps: the only words it sees are the ones the
 * shared cinematic engine produced from the real LRC/word-timestamp data. Effects
 * are a pure function of playback position, so seek/pause/resume need no reset.
 */
object CodePoetryPolicy {
  private const val PHRASE_GAP_MS = 620L
  private const val MAX_WORDS_PER_PHRASE = 9

  /** Pure hex parser so the policy stays JVM-testable without Android stubs. */
  private fun parseHexArgb(raw: String?): Int? {
    val hex = raw?.trim().orEmpty().removePrefix("#")
    if (hex.isBlank()) return null
    return runCatching {
      when (hex.length) {
        6 -> (0xFF000000L or hex.toLong(16)).toInt()
        8 -> hex.toLong(16).toInt()
        else -> null
      }
    }.getOrNull()
  }

  fun config(
    settings: CodePoetrySettingsState,
    lowPower: Boolean,
  ): CodePoetryConfig {
    val palette = settings.palette.value
    val customCursor = parseHexArgb(settings.cursorColor.value)
    return CodePoetryConfig(
      typingSpeed = settings.typingSpeed.value.coerceIn(0.25f, 3f),
      fontSize = settings.fontSize.value.coerceIn(0.7f, 1.8f),
      palette = palette,
      cursorArgb = customCursor ?: palette.cursor.toInt(),
      cameraIntensity =
        (if (lowPower) 0f else settings.cameraIntensity.value).coerceIn(0f, 1f),
      glitchIntensity =
        (if (lowPower) 0f else settings.glitchIntensity.value).coerceIn(0f, 1f),
      grain = (if (lowPower) 0f else settings.grain.value).coerceIn(0f, 1f),
      popupFrequency = settings.popupFrequency.value.coerceIn(0f, 1f),
      bugEnabled = settings.bugEnabled.value && !lowPower,
      autoComposition = settings.autoComposition.value,
      composition = settings.composition.value,
      alwaysOn = settings.alwaysOn.value,
      lowPower = lowPower,
    )
  }

  /** Rebuild line boundaries from the real word timing (gaps / long-line breaks). */
  fun groupPhrases(words: List<KaraokeWord>): List<List<KaraokeWord>> {
    if (words.isEmpty()) return emptyList()
    val phrases = mutableListOf<List<KaraokeWord>>()
    var current = mutableListOf<KaraokeWord>()
    words.forEach { word ->
      val previous = current.lastOrNull()
      val tooFar =
        previous != null && word.startMs - previous.endMs > PHRASE_GAP_MS
      val tooLong = current.size >= MAX_WORDS_PER_PHRASE
      if (previous != null && (tooFar || tooLong)) {
        phrases.add(current.toList())
        current = mutableListOf()
      }
      current.add(word)
    }
    if (current.isNotEmpty()) phrases.add(current.toList())
    return phrases
  }

  fun phraseIndexAt(
    phrases: List<List<KaraokeWord>>,
    positionMs: Long,
  ): Int {
    if (phrases.isEmpty()) return 0
    val exact = phrases.indexOfLast { it.first().startMs <= positionMs }
    if (exact >= 0) return exact
    return phrases.indexOfFirst { it.first().startMs > positionMs }.takeIf { it >= 0 } ?: 0
  }

  private fun popupInterval(frequency: Float): Int =
    (13 - (frequency.coerceIn(0f, 1f) * 9f)).roundToInt().coerceIn(4, 13)

  private fun bugInterval(): Int = 7

  private fun stableHash(text: String): Int =
    text.fold(0) { acc, char -> acc * 31 + char.code }.let { if (it == Int.MIN_VALUE) 0 else abs(it) }

  private fun tokenKind(
    word: KaraokeWord,
    index: Int,
    isCurrent: Boolean,
    lineActive: Boolean,
  ): CodeTokenKind {
    if (!lineActive) return CodeTokenKind.COMMENT
    val hash = stableHash(word.text)
    if (isCurrent) {
      return when (hash % 3) {
        0 -> CodeTokenKind.IMPORTANT
        1 -> CodeTokenKind.EMPHASIS
        else -> CodeTokenKind.KEYWORD
      }
    }
    return when {
      hash % 11 == 0 -> CodeTokenKind.IMPORTANT
      hash % 7 == 0 -> CodeTokenKind.SECONDARY
      hash % 5 == 0 -> CodeTokenKind.EMPHASIS
      index == 0 -> CodeTokenKind.KEYWORD
      else -> CodeTokenKind.NORMAL
    }
  }

  private fun typedText(
    word: KaraokeWord,
    positionMs: Long,
    typingSpeed: Float,
  ): String {
    if (positionMs >= word.endMs) return word.text
    val progress = KaraokeTiming.progressFor(word, positionMs)
    // Smoothstep removes the linear "stepping" feel at the start and end of a
    // word while keeping the reveal a pure function of the player position.
    val eased = progress * progress * (3f - 2f * progress)
    val shaped = (eased * typingSpeed).coerceIn(0f, 1f)
    val chars = (word.text.length * shaped).roundToInt().coerceIn(0, word.text.length)
    return word.text.take(chars)
  }

  /**
   * Blend the live FFT energy with Groq's track-level hint. The AI signal is
   * trusted only in proportion to its confidence, so a weak/offline analysis can
   * never override what the audio is actually doing.
   */
  private fun effectiveEnergy(config: CodePoetryConfig, live: Float): Float {
    val ai = config.ai
    if (!ai.available || ai.confidence <= 0f) return live.coerceIn(0f, 1f)
    val trusted = ai.confidence.coerceIn(0f, 1f)
    return (live * (1f - trusted) + ai.energyHint.coerceIn(0f, 1f) * trusted).coerceIn(0f, 1f)
  }

  private fun compositionFor(
    phraseIndex: Int,
    config: CodePoetryConfig,
    energy: Float,
    phraseProgress: Float,
  ): CodeComposition {
    if (!config.autoComposition) return config.composition
    val interval = popupInterval(config.popupFrequency)
    val driven = effectiveEnergy(config, energy)
    val sectionBoost =
      when (config.ai.sectionName?.trim()?.uppercase()) {
        "CHORUS", "HOOK", "DROP", "REFRAIN" -> 0.10f
        "BRIDGE", "BUILD", "VERSE" -> 0.02f
        "INTRO", "OUTRO", "SILENCE" -> -0.06f
        else -> 0f
      }
    val moodBoost =
      when (config.ai.mood?.trim()?.uppercase()) {
        "EUPHORIC", "ENERGETIC", "PASSIONATE", "HYPE" -> 0.08f
        "MELANCHOLIC", "CALM", "SOMBER", "DREAMY" -> -0.04f
        else -> 0f
      }
    val intensity = (driven + sectionBoost + moodBoost).coerceIn(0f, 1f)
    return when {
      config.popupFrequency > 0.02f && phraseIndex % interval == interval - 1 -> CodeComposition.ERROR_POPUP
      config.bugEnabled && phraseIndex % bugInterval() == 2 -> CodeComposition.BUG_SYMBOL
      intensity > 0.70f -> CodeComposition.MACRO_WORD
      intensity > 0.46f && phraseIndex % 3 == 0 -> CodeComposition.TYPING
      phraseIndex % 4 == 1 && phraseProgress < 0.18f -> CodeComposition.TRANSITION
      else -> CodeComposition.FULL_EDITOR
    }
  }

  private fun cameraFor(
    phraseIndex: Int,
    phraseProgress: Float,
    composition: CodeComposition,
    config: CodePoetryConfig,
    energy: Float,
  ): CodeCameraFrame {
    val intensity = if (config.lowPower) 0f else config.cameraIntensity
    // Continuous, low-frequency motion tied to real elapsed playback instead of
    // a per-phrase sawtooth. The 0.5rad/s camera + 0.12rad/s accent drift reads
    // as a slow cinematic push-in rather than a jittery beat-triggered jolt.
    val t = config.ai.let { if (it.available) it.confidence else 0f }
    val motionTime = phraseIndex.toFloat() + phraseProgress
    val base = sin(motionTime * 0.5f + t * 0.4f)
    val accent = sin(motionTime * 0.12f + 0.7f) * 0.35f + sin(motionTime * 0.19f) * 0.15f
    val counter = cos(motionTime * 0.37f + t * 0.3f)
    val macro = composition == CodeComposition.MACRO_WORD
    val reactive = 0.5f + effectiveEnergy(config, energy) * 0.5f
    return CodeCameraFrame(
      zoom = 1f + intensity * ((if (macro) 0.16f else 0.05f) + 0.025f * abs(base)) * reactive,
      panX = intensity * (0.018f * base + accent * 0.012f),
      panY = intensity * (0.013f * counter + accent * 0.009f),
      rotation = intensity * (0.9f * base + accent * 0.6f),
      perspectiveX = intensity * (4f * counter + accent * 2f),
      perspectiveY = intensity * (3f * base + accent * 1.5f),
      // CODE POETRY is a crisp editor: no edge blur, in any mode.
      blur = 0f,
    )
  }

  private fun popupTextFor(
    phrase: List<KaraokeWord>,
    title: String,
  ): String {
    val seed =
      phrase.firstOrNull()?.text.orEmpty().replace(Regex("[^A-Za-z0-9]"), "").take(18)
        .ifBlank { title.replace(Regex("[^A-Za-z0-9]"), "").take(18) }
    return if (seed.isBlank()) "memory.exe is not responding" else "$seed.exe is not responding"
  }

  fun buildFrame(
    phrases: List<List<KaraokeWord>>,
    positionMs: Long,
    config: CodePoetryConfig,
    isPlaying: Boolean,
    energy: Float,
    title: String,
    artist: String,
    frameIndex: Int,
  ): CodeFrameSpec {
    if (phrases.isEmpty()) {
      return CodeFrameSpec(
        lines = emptyList(),
        composition = CodeComposition.FULL_EDITOR,
        camera = CodeCameraFrame(1f, 0f, 0f, 0f, 0f, 0f, 0f),
        config = config,
        positionMs = positionMs,
        isPlaying = isPlaying,
        timingVerified = true,
        title = title,
        artist = artist,
        frameIndex = frameIndex,
        popupText = "",
        popupAgeMs = Long.MAX_VALUE,
        showBug = false,
        driftX = 0f,
        driftY = 0f,
      )
    }

    val activeIndex = phraseIndexAt(phrases, positionMs)
    val active = phrases[activeIndex]
    val startMs = active.first().startMs
    val endMs = active.last().endMs
    val length = (endMs - startMs).coerceAtLeast(1L)
    val phraseProgress = ((positionMs - startMs).toFloat() / length).coerceIn(0f, 1f)

    val composition = compositionFor(activeIndex, config, energy, phraseProgress)
    val camera = cameraFor(activeIndex, phraseProgress, composition, config, energy)

    val windowRange =
      when (composition) {
        CodeComposition.MACRO_WORD,
        CodeComposition.TYPING,
        CodeComposition.TRANSITION,
        -> activeIndex..activeIndex
        else -> (activeIndex - 2).coerceAtLeast(0)..(activeIndex + 2).coerceAtMost(phrases.lastIndex)
      }

    fun lineAt(index: Int): CodeLineFrame {
      val phrase = phrases[index]
      val lineActive = index == activeIndex
      return CodeLineFrame(
        number = index + 1,
        isActive = lineActive,
        verified = phrase.all { it.verified },
        words =
          phrase.mapIndexed { wordIndex, word ->
            val isCurrent =
              lineActive && positionMs in word.startMs..word.endMs
            CodeWordFrame(
              text = word.text,
              typed = typedText(word, positionMs, config.typingSpeed),
              startMs = word.startMs,
              endMs = word.endMs,
              verified = word.verified,
              isCurrent = isCurrent,
              progress = KaraokeTiming.progressFor(word, positionMs),
              kind = tokenKind(word, wordIndex, isCurrent, lineActive),
            )
          },
      )
    }

    // The editor needs the whole real lyric history so completed lines can move
    // upward. The public `lines` window and its range stay exactly as before.
    val editorLines = phrases.indices.map(::lineAt)
    val lines = windowRange.map(::lineAt)

    val interval = popupInterval(config.popupFrequency)
    val showPopup =
      config.popupFrequency > 0.02f &&
        activeIndex % interval == interval - 1 &&
        composition == CodeComposition.ERROR_POPUP
    val showBug =
      config.bugEnabled &&
        activeIndex % bugInterval() == 2 &&
        composition == CodeComposition.BUG_SYMBOL

    // Slow drift protects AMOLED panels from burn-in during always-on playback.
    val driftX = if (config.alwaysOn) sin(positionMs / 47000f) * 4f else 0f
    val driftY = if (config.alwaysOn) cos(positionMs / 61000f) * 3f else 0f

    return CodeFrameSpec(
      lines = lines,
      editorLines = editorLines,
      composition = composition,
      camera = camera,
      config = config,
      positionMs = positionMs,
      isPlaying = isPlaying,
      timingVerified = phrases.flatten().all { it.verified },
      title = title,
      artist = artist,
      frameIndex = frameIndex,
      popupText = if (showPopup) popupTextFor(active, title) else "",
      popupAgeMs = if (showPopup) positionMs - startMs else Long.MAX_VALUE,
      showBug = showBug,
      driftX = driftX,
      driftY = driftY,
      aiLabel =
        if (config.ai.available && config.ai.confidence > 0f) {
          listOfNotNull(config.ai.sectionName, config.ai.themeHint).joinToString(" · ")
        } else {
          ""
        },
    )
  }
}
