package com.music.echo.ui.component

import echo.music.iad1tya.lyrics.LyricsEntry
import echo.music.iad1tya.lyrics.WordTimestamp
import echo.music.iad1tya.ui.component.KaraokeTiming
import echo.music.iad1tya.ui.component.KaraokeWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeTimingTest {

  @Test
  fun `real word timestamps are converted to absolute milliseconds`() {
    val entry =
      LyricsEntry(
        time = 12_000L,
        text = "hold the line",
        words =
          listOf(
            WordTimestamp("hold", 12.10, 12.48),
            WordTimestamp("the", 12.48, 12.60),
            WordTimestamp("line", 12.60, 13.20),
          ),
      )

    val words = KaraokeTiming.wordsFor(entry, nextLineTimeMs = 15_000L)

    assertEquals(
      listOf(
        KaraokeWord("hold", 12_100L, 12_480L),
        KaraokeWord("the", 12_480L, 12_600L),
        KaraokeWord("line", 12_600L, 13_200L),
      ),
      words,
    )
    assertTrue("line text must never override real word times", words[0].startMs != entry.time)
  }

  @Test
  fun `plain line-synced lyrics fall back to even word spacing`() {
    val entry = LyricsEntry(time = 2_000L, text = "one two three")

    val words = KaraokeTiming.wordsFor(entry, nextLineTimeMs = 5_000L)

    assertEquals(3, words.size)
    assertEquals(
      listOf(
        KaraokeWord("one", 2_000L, 3_000L, verified = false),
        KaraokeWord("two", 3_000L, 4_000L, verified = false),
        KaraokeWord("three", 4_000L, 5_000L, verified = false),
      ),
      words,
    )
  }

  @Test
  fun `progress is clamped outside the word window`() {
    val word = KaraokeWord("shine", 1_000L, 2_000L)

    assertEquals(0f, KaraokeTiming.progressFor(word, 500L), 0f)
    assertEquals(0.25f, KaraokeTiming.progressFor(word, 1_250L), 0f)
    assertEquals(1f, KaraokeTiming.progressFor(word, 2_500L), 0f)

    val instantaneous = KaraokeWord("zero", 3_000L, 3_000L)
    assertEquals(0f, KaraokeTiming.progressFor(instantaneous, 2_999L), 0f)
    assertEquals(1f, KaraokeTiming.progressFor(instantaneous, 3_000L), 0f)
  }

  @Test
  fun `line progress averages the words actually sung`() {
    val words =
      listOf(
        KaraokeWord("a", 0L, 1_000L),
        KaraokeWord("b", 1_000L, 2_000L),
        KaraokeWord("c", 2_000L, 3_000L),
      )

    assertEquals(0f, KaraokeTiming.lineProgress(words, 0L), 0f)
    assertEquals(1f / 6f, KaraokeTiming.lineProgress(words, 500L), 0.0001f)
    assertEquals(0.5f, KaraokeTiming.lineProgress(words, 1_500L), 0.0001f)
    assertEquals(1f, KaraokeTiming.lineProgress(words, 3_000L), 0f)
    assertEquals(0f, KaraokeTiming.lineProgress(emptyList(), 1_000L), 0f)
  }

  @Test
  fun `blank and whitespace-only words are ignored`() {
    val entry =
      LyricsEntry(
        time = 0L,
        text = "ignored",
        words = listOf(WordTimestamp("  ", 0.0, 0.2), WordTimestamp("kept", 0.2, 0.9)),
      )

    assertEquals(listOf(KaraokeWord("kept", 200L, 900L)), KaraokeTiming.wordsFor(entry))
    assertTrue(KaraokeTiming.wordsFor(LyricsEntry(0L, "   ")).isEmpty())
  }
}
