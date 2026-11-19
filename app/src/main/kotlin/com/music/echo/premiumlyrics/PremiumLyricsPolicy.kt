package com.music.echo.premiumlyrics

import echo.music.iad1tya.cinematic.AudioFeatures
import echo.music.iad1tya.cinematic.CinematicMood
import echo.music.iad1tya.cinematic.SongSection
import echo.music.iad1tya.ui.component.KaraokeTiming
import echo.music.iad1tya.ui.component.KaraokeWord

/** One phrase reconstructed from the shared engine's flat word stream. */
data class PremiumLyricLine(
  val words: List<KaraokeWord>,
  val startMs: Long,
  val endMs: Long,
)

/** Pure render input. Every word is copied verbatim from the shared engine. */
data class PremiumWordFrame(
  val text: String,
  val startMs: Long,
  val endMs: Long,
  val progress: Float,
  val isCurrent: Boolean,
  val isPast: Boolean,
  val lineIndex: Int,
  val wordIndex: Int,
  val lineProgress: Float,
)

data class PremiumLineFrame(
  val words: List<PremiumWordFrame>,
  val isActive: Boolean,
)

data class PremiumFrame(
  val lines: List<PremiumLineFrame>,
  val style: PremiumLyricStyle,
  val features: AudioFeatures,
  val positionMs: Long,
  val durationMs: Long,
  val frameTimeMs: Long,
  val title: String,
  val artist: String,
  val timingVerified: Boolean,
  val lowPower: Boolean,
  val speed: Float,
  val glow: Float,
)

object PremiumLyricsPolicy {
  private const val DEFAULT_PHRASE_GAP_MS = 900L

  /**
   * Rebuilds phrase rows from word timestamps. This is layout only: it never
   * rewrites, invents or retimes a single word.
   */
  fun groupLines(words: List<KaraokeWord>, gapMs: Long = DEFAULT_PHRASE_GAP_MS): List<PremiumLyricLine> {
    val result = mutableListOf<PremiumLyricLine>()
    var current = mutableListOf<KaraokeWord>()
    words.forEach { word ->
      if (current.isNotEmpty() && word.startMs - current.last().endMs > gapMs) {
        result += PremiumLyricLine(current.toList(), current.first().startMs, current.last().endMs)
        current = mutableListOf()
      }
      current += word
    }
    if (current.isNotEmpty()) {
      result += PremiumLyricLine(current.toList(), current.first().startMs, current.last().endMs)
    }
    return result
  }

  /** Maps shared timestamps into a flat, immutable frame for the renderer. */
  fun buildFrame(
    words: List<KaraokeWord>,
    positionMs: Long,
    durationMs: Long,
    style: PremiumLyricStyle,
    features: AudioFeatures,
    frameTimeMs: Long,
    title: String,
    artist: String,
    timingVerified: Boolean,
    lowPower: Boolean,
    motionScale: Float = 1f,
    glowScale: Float = 1f,
  ): PremiumFrame {
    val lines =
      groupLines(words).mapIndexed { lineIndex, line ->
        val progress = KaraokeTiming.lineProgress(line.words, positionMs)
        PremiumLineFrame(
          words =
            line.words.mapIndexed { wordIndex, word ->
              PremiumWordFrame(
                text = word.text,
                startMs = word.startMs,
                endMs = word.endMs,
                progress = KaraokeTiming.progressFor(word, positionMs),
                isCurrent = positionMs in word.startMs..word.endMs,
                isPast = positionMs > word.endMs,
                lineIndex = lineIndex,
                wordIndex = wordIndex,
                lineProgress = progress,
              )
            },
          isActive = positionMs in line.startMs..line.endMs,
        )
      }
    return PremiumFrame(
      lines = lines,
      style = style,
      features = features,
      positionMs = positionMs,
      durationMs = durationMs,
      frameTimeMs = frameTimeMs,
      title = title,
      artist = artist,
      timingVerified = timingVerified,
      lowPower = lowPower,
      speed = (if (lowPower) 0.35f else style.speed) * motionScale.coerceIn(0.3f, 1.6f),
      glow = (if (lowPower) 0.35f else style.glow) * glowScale.coerceIn(0.3f, 1.6f),
    )
  }

  /** All words of a frame, in order. Used by tests to prove no lyric invention. */
  fun frameWords(frame: PremiumFrame): List<String> =
    frame.lines.flatMap { line -> line.words.map { it.text } }

  private enum class EnergyClass { QUIET, GROOVE, DRIVE, PEAK }

  private fun energyClass(features: AudioFeatures): EnergyClass {
    val value =
      (features.energy * 0.6f + features.intensity * 0.25f + features.beat * 0.15f)
        .coerceIn(0f, 1f)
    return when {
      features.silence || value < 0.2f -> EnergyClass.QUIET
      value < 0.48f -> EnergyClass.GROOVE
      value < 0.74f -> EnergyClass.DRIVE
      else -> EnergyClass.PEAK
    }
  }

  private fun lookFor(input: PremiumAutoInput): PremiumLook {
    val energy = energyClass(input.features)
    if (input.section == SongSection.INSTRUMENTAL || energy == EnergyClass.QUIET) {
      return PremiumLook.NOIR
    }
    if (input.features.drop > 0.55f || energy == EnergyClass.PEAK) {
      return when (input.mood) {
        CinematicMood.AGGRESSIVE, CinematicMood.DARK -> PremiumLook.TERMINAL
        CinematicMood.PARTY, CinematicMood.ENERGETIC -> PremiumLook.POSTER
        else -> PremiumLook.NEON
      }
    }
    if (input.features.bpm >= 128f || energy == EnergyClass.DRIVE) {
      return when (input.mood) {
        CinematicMood.DREAMY, CinematicMood.LO_FI -> PremiumLook.TIDAL
        CinematicMood.ROMANTIC -> PremiumLook.ORBIT
        else -> PremiumLook.PRISM
      }
    }
    return when (input.mood) {
      CinematicMood.SAD, CinematicMood.EMOTIONAL -> PremiumLook.ECHO
      CinematicMood.CHILL, CinematicMood.LO_FI -> PremiumLook.STARDUST
      CinematicMood.ROMANTIC, CinematicMood.DREAMY -> PremiumLook.AURORA
      CinematicMood.MOTIVATIONAL -> PremiumLook.DEPTH
      else -> PremiumLook.CHROME
    }
  }

  /**
   * Beat-driven AUTO selection. [heldSinceMs] is how long the current style has
   * been active; a short hold prevents strobe-like switching on every kick.
   */
  fun chooseAutoStyle(
    input: PremiumAutoInput,
    current: PremiumLyricStyle,
    heldSinceMs: Long,
  ): PremiumLyricStyle {
    val look = lookFor(input)
    val candidate =
      PremiumLyricStyle.SELECTABLE.firstOrNull { it.look == look }
        ?: PremiumLyricStyle.SELECTABLE.first()
    val strongSwitch = input.features.drop > 0.7f && heldSinceMs >= 1_800L
    if (current == PremiumLyricStyle.AUTO) return candidate
    if (current.look != look && (strongSwitch || heldSinceMs >= 4_000L)) return candidate
    if (current.look == look && (strongSwitch || heldSinceMs >= 6_000L)) return candidate
    return current
  }
}

data class PremiumAutoInput(
  val features: AudioFeatures,
  val section: SongSection,
  val mood: CinematicMood,
  val positionMs: Long,
)
