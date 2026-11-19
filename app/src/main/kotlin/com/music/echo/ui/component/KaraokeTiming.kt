package echo.music.iad1tya.ui.component

import echo.music.iad1tya.lyrics.LyricsEntry

/**
 * One display word with absolute playback times in milliseconds.
 *
 * [verified] distinguishes provider-supplied vocal timestamps from even-spaced
 * estimates, so the UI can label approximate timing and offer correction.
 */
data class KaraokeWord(
  val text: String,
  val startMs: Long,
  val endMs: Long,
  val verified: Boolean = true,
)

/**
 * Word-level timing for the cinematic lyrics renderer.
 *
 * Providers such as YouLyPlus, BetterLyrics, Paxsenix and SimpMusic already
 * ship real word/syllable timestamps. For plain line-synced LRC we estimate
 * even spacing across the line so the karaoke effect still works instead of
 * falling back to a single flat line.
 */
object KaraokeTiming {
  private const val DEFAULT_LINE_LENGTH_MS = 3_000L

  fun wordsFor(entry: LyricsEntry, nextLineTimeMs: Long? = null): List<KaraokeWord> {
    val timed = entry.words.orEmpty()
    if (timed.isNotEmpty()) {
      return timed.map { word ->
        KaraokeWord(
          text = word.text.trim(),
          startMs = (word.startTime * 1000.0).toLong(),
          endMs = (word.endTime * 1000.0).toLong(),
          verified = true,
        )
      }.filter { it.text.isNotEmpty() }
    }

    val tokens = entry.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return emptyList()

    val lineEnd = nextLineTimeMs ?: (entry.time + DEFAULT_LINE_LENGTH_MS)
    val length = if (lineEnd > entry.time) lineEnd - entry.time else DEFAULT_LINE_LENGTH_MS
    val step = length.toFloat() / tokens.size
    return tokens.mapIndexed { index, token ->
      KaraokeWord(
        text = token,
        startMs = entry.time + (step * index).toLong(),
        endMs = entry.time + (step * (index + 1)).toLong(),
        verified = false,
      )
    }
  }

  fun progressFor(word: KaraokeWord, positionMs: Long): Float {
    if (word.endMs <= word.startMs) return if (positionMs >= word.endMs) 1f else 0f
    return ((positionMs - word.startMs).toFloat() / (word.endMs - word.startMs)).coerceIn(0f, 1f)
  }

  fun lineProgress(words: List<KaraokeWord>, positionMs: Long): Float {
    if (words.isEmpty()) return 0f
    return words.sumOf { progressFor(it, positionMs).toDouble() }.toFloat() / words.size
  }
}
