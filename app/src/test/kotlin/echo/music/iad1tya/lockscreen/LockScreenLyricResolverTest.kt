package echo.music.iad1tya.lockscreen

import echo.music.iad1tya.lyrics.LyricsEntry
import echo.music.iad1tya.lyrics.WordTimestamp
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenLyricResolverTest {

  private val resolver = LockScreenLyricResolver()

  @Test
  fun `uses real word timestamps and reports verified timing`() {
    val entries =
      listOf(
        LyricsEntry(
          time = 1_000L,
          text = "hello world",
          words =
            listOf(
              WordTimestamp("hello", 1.0, 1.6),
              WordTimestamp("world", 1.6, 2.4),
            ),
        ),
        LyricsEntry(time = 2_500L, text = "next line"),
      )

    val frame = resolver.resolve(entries, 1_300L)

    assertEquals("hello world", frame.line)
    assertEquals("next line", frame.nextLine)
    assertEquals("hello", frame.activeWord)
    assertEquals("world", frame.upcomingWord)
    assertTrue(frame.verified)
    assertTrue(abs(frame.wordProgress - 0.5f) < 0.01f)
  }

  @Test
  fun `line level LRC is estimated and never reported as verified words`() {
    val entries =
      listOf(
        LyricsEntry(time = 0L, text = "one two three four"),
        LyricsEntry(time = 2_000L, text = "next line"),
      )

    val frame = resolver.resolve(entries, 600L)

    assertEquals("one two three four", frame.line)
    assertEquals("two", frame.activeWord)
    assertEquals("three", frame.upcomingWord)
    assertFalse(frame.verified)
  }

  @Test
  fun `seek and song change are resolved from position and entries`() {
    val firstSong =
      listOf(
        LyricsEntry(time = 0L, text = "first song line"),
        LyricsEntry(time = 1_000L, text = "first song second"),
      )
    val secondSong =
      listOf(
        LyricsEntry(time = 0L, text = "second song line"),
        LyricsEntry(time = 1_000L, text = "second song second"),
      )

    assertEquals("first song line", resolver.resolve(firstSong, 100L).line)
    assertEquals("first song second", resolver.resolve(firstSong, 1_100L).line)
    assertEquals("second song line", resolver.resolve(secondSong, 100L).line)
  }

  @Test
  fun `position before first lyric shows next line without claiming a lyric line`() {
    val entries =
      listOf(
        LyricsEntry(time = 5_000L, text = "first real line"),
      )

    val frame = resolver.resolve(entries, 1_000L)

    assertEquals("", frame.line)
    assertEquals("first real line", frame.nextLine)
    assertFalse(frame.verified)
  }

  @Test
  fun `applies manual correction from playback service`() {
    val corrected = LockScreenLyricResolver { 2_000L }
    val entries =
      listOf(
        LyricsEntry(
          time = 1_000L,
          text = "corrected line",
          words = listOf(WordTimestamp("corrected", 1.0, 2.0)),
        ),
      )

    val frame = corrected.resolve(entries, 1_100L)

    assertEquals("corrected line", frame.line)
    assertEquals("corrected", frame.activeWord)
    assertEquals(3_100L, frame.positionMs)
    assertTrue(frame.verified)
  }

  @Test
  fun `empty or instrumental lyrics produce no lyric frame`() {
    val frame = resolver.resolve(emptyList(), 1_000L)

    assertFalse(frame.hasLyrics)
    assertEquals("", frame.line)
    assertEquals("", frame.activeWord)
  }
}
