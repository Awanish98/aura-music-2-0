package echo.music.iad1tya.cinematic

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CinematicEngineTest {
  private fun frame(bins: Int, magnitude: (Int) -> Int): ByteArray {
    val fft = ByteArray(bins * 2)
    for (bin in 1 until bins) {
      val value = magnitude(bin)
      fft[bin * 2] = value.toByte()
      fft[bin * 2 + 1] = 0
    }
    return fft
  }

  @Test
  fun `silent frames stay calm and report silence`() {
    val analyzer = RealtimeAudioAnalyzer()
    var features = AudioFeatures.IDLE
    for (i in 0 until 20) features = analyzer.accept(ByteArray(512), 48_000, i * 32L)
    assertTrue(features.silence)
    assertEquals(0f, features.beat, 0.01f)
    assertEquals(0f, features.kick, 0.01f)
  }

  @Test
  fun `bass transient produces onset kick and no invented silence`() {
    val analyzer = RealtimeAudioAnalyzer()
    val quiet = frame(256) { 0 }
    val bassHit = frame(256) { bin -> if (bin in 1..3) 110 else 0 }
    repeat(10) { analyzer.accept(quiet, 48_000, it * 32L) }
    val features = analyzer.accept(bassHit, 48_000, 320L)
    assertTrue("bass=${features.bass}", features.bass > 0.2f)
    assertTrue("onset=${features.onset}", features.onset > 0f)
    assertTrue("kick=${features.kick}", features.kick > 0f)
    assertFalse(features.silence)
  }

  @Test
  fun `theme selector is fair and never repeats immediately`() {
    val selector = ThemeSelector(Random(11))
    val seen = mutableSetOf<CinematicTheme>()
    var previous: CinematicTheme? = null
    repeat(90) { song ->
      val theme =
        selector.selectForSong(
          songId = "song-$song",
          mode = ThemeMode.AUTO,
          manualTheme = CinematicTheme.GOLDEN_CINEMA,
          favorites = emptySet(),
          mood = CinematicMood.NEUTRAL,
          genreTags = emptyList(),
          energy = 0.5f,
          shuffle = false,
          previousTheme = previous,
        )
      if (previous != null) assertNotEquals(previous, theme)
      seen += theme
      previous = theme
    }
    assertEquals(CinematicTheme.entries.size, seen.size)
  }

  @Test
  fun `mood needs metadata for a confident label`() {
    val loud = AudioFeatures(energy = 0.9f, bpm = 132f, intensity = 0.9f)
    assertEquals(CinematicMood.NEUTRAL, MoodEstimator.estimate(loud, emptyList()).mood)
    val tagged = MoodEstimator.estimate(loud, listOf("sad", "melancholy"))
    assertEquals(CinematicMood.SAD, tagged.mood)
    assertTrue(tagged.confidence > 0.5f)
  }

  @Test
  fun `sections mark intro instrumental gap and outro`() {
    val lyrics =
      listOf(
        LyricPoint(10_000L, "hello"),
        LyricPoint(12_000L, "again"),
        LyricPoint(30_000L, "after break"),
      )
    val sections = SectionEstimator.estimate(lyrics, emptyList(), 50_000L)
    assertEquals(SongSection.INTRO, SectionEstimator.sectionAt(sections, 2_000L))
    assertTrue(sections.any { it.section == SongSection.INSTRUMENTAL })
    assertEquals(SongSection.OUTRO, SectionEstimator.sectionAt(sections, 49_000L))
  }

  @Test
  fun `all fifteen recipes are genuinely distinct`() {
    assertEquals(15, CinematicTheme.entries.size)
    assertEquals(
      CinematicTheme.entries.size,
      CinematicTheme.entries.map { it.backdrop }.toSet().size,
    )
    assertEquals(
      CinematicTheme.entries.size,
      CinematicTheme.entries.map { it.motion to it.typeface }.toSet().size,
    )
  }

  @Test
  fun `bpm is estimated from epoch-like timestamps`() {
    val analyzer = RealtimeAudioAnalyzer()
    val quiet = frame(256) { 0 }
    val hit = frame(256) { bin -> if (bin in 1..3) 110 else 0 }
    val epochBase = 1_700_000_000_000L
    repeat(10) { analyzer.accept(quiet, 48_000, epochBase + it * 32L) }
    var latest = AudioFeatures.IDLE
    for (beat in 0 until 5) {
      val at = epochBase + 1_000L + beat * 500L
      analyzer.accept(quiet, 48_000, at - 64L)
      analyzer.accept(quiet, 48_000, at - 32L)
      latest = analyzer.accept(hit, 48_000, at)
    }
    assertTrue("bpm=$latest.bpm", latest.bpm in 100f..140f)
  }

  @Test
  fun `reactions scale transients with intensity and calm in silence`() {
    val quietHit =
      CinematicReactions.forFrame(
        AudioFeatures(beat = 1f, kick = 1f, drop = 1f, bass = 0.5f, intensity = 0.1f, silence = false)
      )
    val loudHit =
      CinematicReactions.forFrame(
        AudioFeatures(beat = 1f, kick = 1f, drop = 1f, bass = 0.5f, intensity = 0.9f, silence = false)
      )
    val silent =
      CinematicReactions.forFrame(
        AudioFeatures(beat = 1f, kick = 1f, drop = 1f, silence = true, intensity = 0f)
      )
    assertTrue(loudHit.kick > quietHit.kick)
    assertTrue(loudHit.drop > quietHit.drop)
    assertTrue(loudHit.bass > quietHit.bass)
    assertEquals(0f, silent.kick, 0.001f)
    assertEquals(0f, silent.drop, 0.001f)
    assertTrue("liveliness=${silent.liveliness}", silent.liveliness <= 0.2f)
    assertTrue(loudHit.impact <= 1f && loudHit.bass <= 1f)
  }

  @Test
  fun `smart advisor penalizes rejected themes and rewards favorites`() {
    val advisor = SmartCinematicAdvisor(Random(7))
    val rejected = CinematicTheme.CYBER_GLITCH
    val favorite = CinematicTheme.GOLDEN_CINEMA
    val signals =
      SmartSongSignals(
        trackKey = "song",
        playCount = 4,
        themeCounts = mapOf(rejected to 6, favorite to 6),
        rejectedThemes = setOf(rejected),
        favorites = setOf(favorite),
      )
    var rejectedPicks = 0
    var favoritePicks = 0
    repeat(400) {
      when (advisor.recommend(signals)) {
        rejected -> rejectedPicks++
        favorite -> favoritePicks++
        else -> Unit
      }
    }
    assertTrue("rejected=$rejectedPicks favorite=$favoritePicks", favoritePicks > rejectedPicks * 2)
  }

  @Test
  fun `theme selector weighting prefers favorites without breaking fairness`() {
    val favorite = CinematicTheme.DREAMSCAPE
    var favoritePicks = 0
    var controlPicks = 0
    // Identical seeds isolate the favorite bonus from random chance.
    repeat(200) { i ->
      fun pick(favorites: Set<CinematicTheme>) =
        ThemeSelector(Random(5 + i))
          .selectForSong(
            songId = "song-$i",
            mode = ThemeMode.AUTO,
            manualTheme = CinematicTheme.GOLDEN_CINEMA,
            favorites = favorites,
            mood = CinematicMood.NEUTRAL,
            genreTags = emptyList(),
            energy = 0.5f,
            shuffle = false,
          )
      if (pick(setOf(favorite)) == favorite) favoritePicks++
      if (pick(emptySet()) == favorite) controlPicks++
    }
    assertTrue("favorite=$favoritePicks control=$controlPicks", favoritePicks > controlPicks)
  }

  @Test
  fun `known sections override estimates and unknown labels are ignored`() {
    assertEquals(SongSection.CHORUS, SectionEstimator.sectionFromName("chorus"))
    assertEquals(null, SectionEstimator.sectionFromName("drop"))
    val estimated =
      listOf(SongSectionSpan(SongSection.VERSE, 0L, 60_000L))
    val known = listOf(SongSectionSpan(SongSection.INSTRUMENTAL, 20_000L, 30_000L))
    val merged = SectionEstimator.withKnownSections(estimated, known, 60_000L)
    assertEquals(SongSection.INSTRUMENTAL, SectionEstimator.sectionAt(merged, 25_000L))
    assertEquals(SongSection.VERSE, SectionEstimator.sectionAt(merged, 10_000L))
  }
}
