package echo.music.iad1tya.smart

import echo.music.iad1tya.cinematic.CinematicMood
import echo.music.iad1tya.cinematic.CinematicTheme
import echo.music.iad1tya.cinematic.SongSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class GroqLyricsClientTest {
  private val client = GroqLyricsClient()

  @Test
  fun `parses mood energy hint theme hint and structural sections`() {
    val json =
      """
      {
        "mood": "ROMANTIC",
        "confidence": 0.82,
        "energy_hint": 0.31,
        "theme_hint": "ROMANTIC_BLOOM",
        "genre_tags": ["indie", "ballad"],
        "sections": [
          {"name": "intro", "start_ms": 0, "end_ms": 9000},
          {"name": "chorus", "start_ms": 9000, "end_ms": 30000}
        ],
        "explanation": "slow romantic arrangement"
      }
      """.trimIndent()
    val parsed = client.parse(json)
    assertEquals(CinematicMood.ROMANTIC, parsed.mood)
    assertEquals(0.82f, parsed.confidence, 0.001f)
    assertEquals(0.31f, parsed.energyHint, 0.001f)
    assertEquals(CinematicTheme.ROMANTIC_BLOOM, parsed.themeHint)
    assertEquals(listOf("indie", "ballad"), parsed.genreTags)
    assertEquals(2, parsed.sections.size)
    assertEquals(
      SongSection.CHORUS,
      echo.music.iad1tya.cinematic.SectionEstimator.sectionFromName(parsed.sections[1].name)
    )
    assertFalse(parsed.degraded)
  }

  @Test
  fun `malformed or absent fields degrade without inventing a plan`() {
    val degraded = client.parse("not json")
    assertTrue(degraded.degraded)
    assertEquals(CinematicMood.NEUTRAL, degraded.mood)
    assertTrue(degraded.sections.isEmpty())

    val partial = client.parse("""{"mood":"UNKNOWN","energy_hint":5}""")
    assertEquals(CinematicMood.NEUTRAL, partial.mood)
    assertEquals(1f, partial.energyHint, 0.001f)
    assertTrue(partial.themeHint == null)
  }
}
