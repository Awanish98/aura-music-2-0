package echo.music.iad1tya.lockscreen

import echo.music.iad1tya.lyrics.LyricsEntry
import echo.music.iad1tya.ui.component.KaraokeTiming

/**
 * One lock-screen lyric frame derived from real playback position.
 *
 * [verified] is false only when the provider supplied line timestamps but no
 * word timestamps; in that case the active word is an honest even-spaced
 * estimate and the UI must label it as approximate.
 */
data class LockScreenLyricFrame(
  val line: String = "",
  val nextLine: String = "",
  val activeWord: String = "",
  val upcomingWord: String = "",
  val wordProgress: Float = 0f,
  val verified: Boolean = true,
  val positionMs: Long = 0L,
) {
  val hasLyrics: Boolean get() = line.isNotBlank() || nextLine.isNotBlank()

  companion object {
    val EMPTY = LockScreenLyricFrame()
  }
}

/** Pure resolver shared by the notification publisher and lock-screen UI. */
class LockScreenLyricResolver(
  private val correctionMs: () -> Long = { 0L },
) {
  fun resolve(entries: List<LyricsEntry>, rawPositionMs: Long): LockScreenLyricFrame {
    val sorted = entries.filter { it.text.isNotBlank() }.sortedBy { it.time }
    if (sorted.isEmpty()) return LockScreenLyricFrame.EMPTY

    val positionMs = (rawPositionMs + correctionMs()).coerceAtLeast(0L)
    var index = sorted.indexOfLast { it.time <= positionMs }
    if (index < 0) {
      return LockScreenLyricFrame(
        line = "",
        nextLine = sorted.first().text,
        verified = sorted.first().words?.isNotEmpty() == true,
        positionMs = positionMs,
      )
    }

    val entry = sorted[index]
    val next = sorted.getOrNull(index + 1)
    val words = KaraokeTiming.wordsFor(entry, next?.time)
    val active = words.lastOrNull { positionMs >= it.startMs }
    val upcoming = words.firstOrNull { positionMs < it.startMs }
    return LockScreenLyricFrame(
      line = entry.text,
      nextLine = next?.text.orEmpty(),
      activeWord = active?.text.orEmpty(),
      upcomingWord = upcoming?.text.orEmpty(),
      wordProgress = active?.let { KaraokeTiming.progressFor(it, positionMs) } ?: 0f,
      verified = entry.words?.isNotEmpty() == true,
      positionMs = positionMs,
    )
  }
}
