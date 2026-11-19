package com.music.echo.moodauto

import com.music.echo.premiumlyrics.PremiumLyricsPolicy
import com.music.echo.premiumlyrics.PremiumLyricStyle
import echo.music.iad1tya.cinematic.AudioFeatures
import echo.music.iad1tya.cinematic.CinematicMood
import echo.music.iad1tya.cinematic.CinematicTheme
import echo.music.iad1tya.cinematic.SongSection
import echo.music.iad1tya.ui.component.KaraokeWord
import echo.music.iad1tya.ui.player.FullVisualizerStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodAutoPolicyTest {
  private fun input(
    mood: CinematicMood = CinematicMood.NEUTRAL,
    confidence: Float = 0.8f,
    energy: Float = 0.5f,
    bpm: Float = 100f,
    section: SongSection = SongSection.VERSE,
    aiMood: CinematicMood? = null,
    aiConfidence: Float = 0f,
    aiThemeHint: CinematicTheme? = null,
    aiDegraded: Boolean = true,
  ) =
    MoodAutoInput(
      mood = mood,
      moodConfidence = confidence,
      features =
        AudioFeatures(
          energy = energy,
          bpm = bpm,
          bass = 0.4f,
          intensity = energy,
          silence = false,
        ),
      section = section,
      engineTheme = CinematicTheme.MIDNIGHT_BLUE,
      aiMood = aiMood,
      aiConfidence = aiConfidence,
      aiThemeHint = aiThemeHint,
      aiDegraded = aiDegraded,
    )

  @Test
  fun sadSongGetsCalmAestheticAndRainyTheme() {
    val experience = MoodAutoPolicy.resolve(input(CinematicMood.SAD, 0.86f, energy = 0.24f, bpm = 72f))
    assertEquals(MoodFamily.CALM, experience.family)
    assertEquals(PremiumLyricStyle.ECHO, experience.lyricStyle)
    assertEquals(FullVisualizerStyle.WAVE, experience.visualStyle)
    assertEquals(CinematicTheme.RAINY_NIGHTS, experience.theme)
    assertTrue(experience.headline.contains("SAD"))
  }

  @Test
  fun romanticSongGetsWarmSlowPresentation() {
    val experience = MoodAutoPolicy.resolve(input(CinematicMood.ROMANTIC, 0.74f, energy = 0.38f))
    assertEquals(MoodFamily.ROMANTIC, experience.family)
    assertEquals(PremiumLyricStyle.AURORA, experience.lyricStyle)
    assertEquals(CinematicTheme.ROMANTIC_BLOOM, experience.theme)
    assertTrue(experience.motionScale < 1f)
  }

  @Test
  fun calmMotionIsSlowerThanEnergeticMotion() {
    val calm = MoodAutoPolicy.resolve(input(CinematicMood.SAD, 0.8f, energy = 0.2f, bpm = 70f))
    val energetic =
      MoodAutoPolicy.resolve(
        input(CinematicMood.ENERGETIC, 0.9f, energy = 0.86f, bpm = 140f),
      )
    assertTrue(calm.motionScale < energetic.motionScale)
    assertTrue(calm.glowScale < energetic.glowScale)
    assertTrue(calm.particleScale < energetic.particleScale)
    assertEquals(FullVisualizerStyle.THREE_D, energetic.visualStyle)
    assertEquals(CinematicTheme.THREE_D_TYPE, energetic.theme)
  }

  @Test
  fun lowConfidenceFallsBackToEngineThemeWithoutClaimingMood() {
    val experience = MoodAutoPolicy.resolve(input(CinematicMood.NEUTRAL, 0.1f))
    assertEquals(MoodFamily.NEUTRAL, experience.family)
    assertNull(experience.theme)
    assertEquals(PremiumLyricStyle.AURORA, experience.lyricStyle)
    assertEquals(FullVisualizerStyle.SPECTRUM, experience.visualStyle)
    assertFalse(experience.headline.contains("SAD"))
  }

  @Test
  fun degradedBackendHintIsIgnored() {
    val experience =
      MoodAutoPolicy.resolve(
        input(
          mood = CinematicMood.NEUTRAL,
          confidence = 0.12f,
          aiMood = CinematicMood.PARTY,
          aiConfidence = 0.95f,
          aiThemeHint = CinematicTheme.NEON_DREAMS,
          aiDegraded = true,
        ),
      )
    assertEquals(MoodFamily.NEUTRAL, experience.family)
    assertNull(experience.theme)
  }

  @Test
  fun backendHintOnlySteersVisualsAndNeverRewritesLyrics() {
    val experience =
      MoodAutoPolicy.resolve(
        input(
          mood = CinematicMood.NEUTRAL,
          confidence = 0.12f,
          aiMood = CinematicMood.PARTY,
          aiConfidence = 0.82f,
          aiThemeHint = CinematicTheme.NEON_DREAMS,
          aiDegraded = false,
        ),
      )
    assertEquals(MoodFamily.BRIGHT, experience.family)
    assertEquals(CinematicTheme.NEON_DREAMS, experience.theme)

    val words =
      listOf(
        KaraokeWord("dil", 1_000L, 1_400L),
        KaraokeWord("se", 1_450L, 1_700L),
      )
    val frame =
      PremiumLyricsPolicy.buildFrame(
        words = words,
        positionMs = 1_200L,
        durationMs = 200_000L,
        style = experience.lyricStyle,
        features = AudioFeatures.IDLE,
        frameTimeMs = 2_000L,
        title = "title",
        artist = "artist",
        timingVerified = true,
        lowPower = false,
        motionScale = experience.motionScale,
        glowScale = experience.glowScale,
      )
    assertEquals(listOf("dil", "se"), PremiumLyricsPolicy.frameWords(frame))
    assertEquals(
      listOf(1_000L, 1_450L),
      frame.lines.flatMap { line -> line.words.map { word -> word.startMs } },
    )
    assertNotEquals(experience.lyricStyle.speed, frame.speed)
  }
}
