package com.music.echo.premiumlyrics

import echo.music.iad1tya.cinematic.AudioFeatures
import echo.music.iad1tya.cinematic.CinematicMood
import echo.music.iad1tya.cinematic.SongSection
import echo.music.iad1tya.ui.component.KaraokeWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumLyricsPolicyTest {
  private val phrases =
    listOf(
      KaraokeWord("Neon", 1_000L, 1_400L),
      KaraokeWord("skies", 1_400L, 2_000L),
      KaraokeWord("over", 4_000L, 4_500L),
      KaraokeWord("the", 4_500L, 4_700L),
      KaraokeWord("city", 4_700L, 5_600L),
    )

  private fun input(
    features: AudioFeatures = AudioFeatures(energy = 0.5f, intensity = 0.5f, beat = 0.4f, bpm = 110f),
    section: SongSection = SongSection.VERSE,
    mood: CinematicMood = CinematicMood.NEUTRAL,
  ) = PremiumAutoInput(features, section, mood, 2_000L)

  @Test
  fun `catalog has exactly fifteen distinct selectable looks`() {
    assertEquals(16, PremiumLyricStyle.entries.size)
    assertEquals(15, PremiumLyricStyle.SELECTABLE.size)
    assertEquals(15, PremiumLook.entries.size)
    assertEquals(
      PremiumLook.entries.toSet(),
      PremiumLyricStyle.SELECTABLE.map { it.look }.toSet(),
    )
    PremiumLook.entries.forEach { look ->
      assertEquals(1, PremiumLyricStyle.SELECTABLE.count { it.look == look })
    }
    assertEquals(
      PremiumLyricStyle.SELECTABLE.size,
      PremiumLyricStyle.SELECTABLE.map { it.name }.toSet().size,
    )
    assertEquals(
      PremiumLyricStyle.SELECTABLE.size,
      PremiumLyricStyle.SELECTABLE.map { it.label }.toSet().size,
    )
    assertFalse(PremiumLyricStyle.SELECTABLE.contains(PremiumLyricStyle.AUTO))
  }

  @Test
  fun `grouping preserves every shared word verbatim and splits on real gaps`() {
    val lines = PremiumLyricsPolicy.groupLines(phrases)
    assertEquals(2, lines.size)
    assertEquals(phrases.map { it.text }, lines.flatMap { it.words.map { it.text } })
    assertEquals(1_000L, lines.first().startMs)
    assertEquals(5_600L, lines.last().endMs)
  }

  @Test
  fun `built frame uses only words from the shared engine`() {
    val frame =
      PremiumLyricsPolicy.buildFrame(
        words = phrases,
        positionMs = 1_500L,
        durationMs = 10_000L,
        style = PremiumLyricStyle.NEON,
        features = AudioFeatures(energy = 0.8f),
        frameTimeMs = 42L,
        title = "Track",
        artist = "Artist",
        timingVerified = true,
        lowPower = false,
      )
    assertEquals(phrases.map { it.text }, PremiumLyricsPolicy.frameWords(frame))
    assertTrue(frame.lines.first().words.first().isPast)
    assertTrue(frame.lines.first().words[1].isCurrent)
    assertEquals(10_000L, frame.durationMs)
  }

  @Test
  fun `low power damps motion without changing lyrics`() {
    val full =
      PremiumLyricsPolicy.buildFrame(
        phrases, 1_500L, 10_000L, PremiumLyricStyle.PRISM,
        AudioFeatures(), 0L, "", "", true, false,
      )
    val low =
      PremiumLyricsPolicy.buildFrame(
        phrases, 1_500L, 10_000L, PremiumLyricStyle.PRISM,
        AudioFeatures(), 0L, "", "", true, true,
      )
    assertTrue(low.speed < full.speed)
    assertTrue(low.glow < full.glow)
    assertEquals(PremiumLyricsPolicy.frameWords(full), PremiumLyricsPolicy.frameWords(low))
  }

  @Test
  fun `auto always resolves to a real selectable style`() {
    val styles =
      listOf(
        input(),
        input(AudioFeatures(energy = 0.05f, silence = true)),
        input(AudioFeatures(energy = 0.9f, intensity = 0.9f, drop = 0.9f, bpm = 140f, silence = false)),
        input(section = SongSection.INSTRUMENTAL, mood = CinematicMood.DARK),
      ).map { PremiumLyricsPolicy.chooseAutoStyle(it, PremiumLyricStyle.AUTO, 0L) }
    assertTrue(styles.all { it != PremiumLyricStyle.AUTO })
    assertTrue(styles.all { PremiumLyricStyle.SELECTABLE.contains(it) })
  }

  @Test
  fun `quiet music prefers the calm noir look`() {
    val chosen =
      PremiumLyricsPolicy.chooseAutoStyle(
        input(AudioFeatures(energy = 0.04f, intensity = 0.02f, silence = true)),
        PremiumLyricStyle.AUTO,
        0L,
      )
    assertEquals(PremiumLook.NOIR, chosen.look)
  }

  @Test
  fun `auto holds a look briefly instead of strobing on every beat`() {
    val current = PremiumLyricStyle.AURORA
    val loud =
      input(AudioFeatures(energy = 0.95f, intensity = 0.9f, drop = 0.9f, bpm = 150f, silence = false))
    val held =
      PremiumLyricsPolicy.chooseAutoStyle(loud, current, 1_000L)
    assertEquals(current, held)
    val switched =
      PremiumLyricsPolicy.chooseAutoStyle(loud, current, 5_000L)
    assertNotEquals(current, switched)
  }
}
