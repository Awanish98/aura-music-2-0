package echo.music.iad1tya.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmoledLyricsPolicyTest {
  @Test
  fun lowPowerForcesTrueBlackAndRemovesEffects() {
    val result =
      AmoledLyricsPolicy.settings(
        CinematicVisualSettings(
          glow = 2f,
          particles = 2f,
          bassReaction = 2f,
          backgroundEffects = true,
          motion = 2f,
        ),
        lowPower = true,
      )

    assertTrue(result.pureBlack)
    assertTrue(result.lowPower)
    assertFalse(result.backgroundEffects)
    assertTrue(result.reducedMotion)
    assertEquals(0f, result.glow, 0f)
    assertEquals(0f, result.particles, 0f)
    assertEquals(0f, result.bassReaction, 0f)
    assertEquals(0f, result.motion, 0f)
  }

  @Test
  fun standardAmoledCapsEffectsButKeepsReadableAccent() {
    val result =
      AmoledLyricsPolicy.settings(
        CinematicVisualSettings(glow = 2f, particles = 2f, motion = 2f),
        lowPower = false,
      )

    assertTrue(result.pureBlack)
    assertFalse(result.backgroundEffects)
    assertTrue(result.glow <= 0.35f)
    assertTrue(result.particles <= 0.15f)
    assertTrue(result.motion <= 0.35f)
  }

  @Test
  fun seekPositionUsesTapOffsetAndClamps() {
    assertEquals(75_000L, AmoledLyricsPolicy.seekPosition(750f, 1000, 100_000L))
    assertEquals(0L, AmoledLyricsPolicy.seekPosition(-40f, 1000, 100_000L))
    assertEquals(100_000L, AmoledLyricsPolicy.seekPosition(1_500f, 1000, 100_000L))
    assertEquals(0L, AmoledLyricsPolicy.seekPosition(500f, 0, 100_000L))
    assertEquals(0L, AmoledLyricsPolicy.seekPosition(500f, 1000, 0L))
  }
}
