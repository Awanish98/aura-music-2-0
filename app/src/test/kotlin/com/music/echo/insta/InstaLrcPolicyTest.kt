package com.music.echo.insta

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstaLrcPolicyTest {
  private fun words() =
    listOf(
      InstaWord("Love", 1_000L, 1_600L, true),
      InstaWord("is", 1_600L, 1_900L, true),
      InstaWord("a", 1_900L, 2_100L, true),
      InstaWord("dream", 2_100L, 2_900L, false),
      InstaWord("tonight", 3_000L, 3_800L, true),
    )

  @Test
  fun smart_hybrid_keeps_oversized_bold_typography() {
    val hybrid = InstaVisualStyle.SMART_HYBRID
    assertEquals(
      InstaVisualStyle.BOLD_TYPOGRAPHY,
      InstaLrcPolicy.chooseStyle(hybrid, "ROMANTIC", 0.4f, "VERSE", "en"),
    )
    assertEquals(
      InstaVisualStyle.BOLD_TYPOGRAPHY,
      InstaLrcPolicy.chooseStyle(hybrid, "NEUTRAL", 0.9f, "VERSE", "en"),
    )
    assertEquals(
      InstaVisualStyle.BOLD_TYPOGRAPHY,
      InstaLrcPolicy.chooseStyle(hybrid, "NEUTRAL", 0.2f, "INSTRUMENTAL", "en"),
    )
    assertEquals(
      InstaVisualStyle.BOLD_TYPOGRAPHY,
      InstaLrcPolicy.chooseStyle(hybrid, "NEUTRAL", 0.4f, "VERSE", "hi"),
    )
    assertEquals(
      InstaVisualStyle.BOLD_TYPOGRAPHY,
      InstaLrcPolicy.chooseStyle(InstaVisualStyle.BOLD_TYPOGRAPHY, "ROMANTIC", 0.1f, "OUTRO", "hi"),
    )
  }

  @Test
  fun detects_devanagari_and_latin_lyrics() {
    assertEquals("hi", InstaLrcPolicy.detectLanguage("प्यार है"))
    assertEquals("en", InstaLrcPolicy.detectLanguage("love is here"))
  }

  @Test
  fun marks_important_and_repeated_words_without_forcing_every_word() {
    val repeated =
      listOf(
        InstaWord("dream", 1_000L, 1_500L, true),
        InstaWord("of", 1_500L, 1_700L, true),
        InstaWord("dream", 1_700L, 2_200L, true),
        InstaWord("the", 2_200L, 2_400L, true),
      )
    val scores = InstaLrcPolicy.importance(repeated, "en")
    assertTrue(scores[0]!! >= 0.6f)
    assertTrue(scores[1]!! < 0.6f)
    assertTrue(scores[3]!! < 0.6f)
  }

  @Test
  fun groups_words_by_timing_gaps() {
    val closeWords = words()
    assertEquals(1, InstaLrcPolicy.groupWords(closeWords).size)

    val withGap = closeWords + InstaWord("later", 5_000L, 5_600L)
    val groups = InstaLrcPolicy.groupWords(withGap)
    assertEquals(2, groups.size)
    assertEquals(5, groups[0].size)
    assertEquals(1, groups[1].size)
  }

  @Test
  fun frames_are_deterministic_for_a_player_position() {
    val phrase = InstaLrcPolicy.groupWords(words()).first()
    val preset = InstaPresets.byStyle(InstaVisualStyle.MINIMAL_KINETIC)
    val first = InstaLrcPolicy.framesForPhrase(phrase, 2_300L, preset, InstaVisualStyle.MINIMAL_KINETIC, 0.5f)
    val second = InstaLrcPolicy.framesForPhrase(phrase, 2_300L, preset, InstaVisualStyle.MINIMAL_KINETIC, 0.5f)
    assertEquals(first, second)
    assertTrue(first.any { it.active })
  }

  @Test
  fun seeking_backwards_selects_the_earlier_word() {
    val phrase = InstaLrcPolicy.groupWords(words()).first()
    val preset = InstaPresets.byStyle(InstaVisualStyle.BOLD_TYPOGRAPHY)
    val early = InstaLrcPolicy.framesForPhrase(phrase, 1_200L, preset, InstaVisualStyle.BOLD_TYPOGRAPHY, 0.5f)
    val late = InstaLrcPolicy.framesForPhrase(phrase, 2_400L, preset, InstaVisualStyle.BOLD_TYPOGRAPHY, 0.5f)
    assertEquals("Love", early.first { it.active }.word.text)
    assertEquals("dream", late.first { it.active }.word.text)
  }

  @Test
  fun estimated_timing_is_carried_into_the_frame() {
    val phrase = InstaLrcPolicy.groupWords(words()).first()
    val preset = InstaPresets.byStyle(InstaVisualStyle.VINTAGE_FILM)
    val frames = InstaLrcPolicy.framesForPhrase(phrase, 2_400L, preset, InstaVisualStyle.VINTAGE_FILM, 0.3f)
    assertFalse(frames.first { it.word.text == "dream" }.word.verified)
    assertFalse(InstaLrcPolicy.isVerified(phrase))
  }

  @Test
  fun frames_respect_safe_margins() {
    val phrase = InstaLrcPolicy.groupWords(words()).first()
    val preset = InstaPresets.byStyle(InstaVisualStyle.BOLD_TYPOGRAPHY).copy(safeMargin = 0.2f)
    val frames = InstaLrcPolicy.framesForPhrase(phrase, 2_300L, preset, InstaVisualStyle.BOLD_TYPOGRAPHY, 0.8f)
    assertTrue(frames.all { it.x >= 0.2f && it.x <= 0.8f })
    assertTrue(frames.all { it.y >= 0.2f && it.y <= 0.8f })
  }

  @Test
  fun audio_reaction_only_decorates_and_never_changes_lyric_timing() {
    val phrase = InstaLrcPolicy.groupWords(words()).first()
    val preset = InstaPresets.byStyle(InstaVisualStyle.BOLD_TYPOGRAPHY)
    val quiet =
      InstaLrcPolicy.framesForPhrase(
        phrase = phrase,
        positionMs = 2_300L,
        preset = preset,
        style = InstaVisualStyle.BOLD_TYPOGRAPHY,
        energy = 0f,
        beatImpact = 0f,
        bass = 0f,
      )
    val loudEnergy = 1f
    val loud =
      InstaLrcPolicy.framesForPhrase(
        phrase = phrase,
        positionMs = 2_300L,
        preset = preset,
        style = InstaVisualStyle.BOLD_TYPOGRAPHY,
        energy = loudEnergy,
        beatImpact = 1f,
        bass = 1f,
      )

    assertEquals(quiet.size, loud.size)
    quiet.zip(loud).forEach { (calm, reactive) ->
      assertEquals(calm.word, reactive.word)
      assertEquals(calm.index, reactive.index)
      assertEquals(calm.active, reactive.active)
      assertEquals(calm.revealChars, reactive.revealChars)
      assertEquals(calm.important, reactive.important)
    }
    assertTrue(loud.any { it.sizeScale > quiet.first { same -> same.index == it.index }.sizeScale })
  }

  @Test
  fun low_power_removes_gpu_heavy_decoration_without_changing_word_selection() {
    val phrase = InstaLrcPolicy.groupWords(words()).first()
    val base = InstaPresets.byStyle(InstaVisualStyle.BOLD_TYPOGRAPHY)
    val normal =
      InstaLrcPolicy.framesForPhrase(phrase, 2_300L, base, InstaVisualStyle.BOLD_TYPOGRAPHY, 0.7f, 1f, 1f)
    val lowPower =
      InstaLrcPolicy.framesForPhrase(
        phrase,
        2_300L,
        base.copy(lowPower = true),
        InstaVisualStyle.BOLD_TYPOGRAPHY,
        0.7f,
        1f,
        1f,
      )

    assertEquals(normal.map { it.word.text to it.active }, lowPower.map { it.word.text to it.active })
    assertTrue(lowPower.all { it.rotationDegrees == 0f })
    assertTrue(lowPower.all { it.glow == 0f })
    assertTrue(lowPower.all { it.blurPasses == 0 })
  }

  @Test
  fun phrase_lookup_follows_seeking_across_before_inside_and_after() {
    val phrases = InstaLrcPolicy.groupWords(words() + InstaWord("again", 8_000L, 8_800L))
    assertEquals("Love", InstaLrcPolicy.phraseAt(phrases, 500L).first().text)
    assertEquals("dream", InstaLrcPolicy.phraseAt(phrases, 2_400L).last { it.startMs <= 2_400L }.text)
    assertEquals("again", InstaLrcPolicy.phraseAt(phrases, 9_500L).first().text)
  }

  @Test
  fun handwritten_accent_is_reserved_for_important_words() {
    val phrase = InstaLrcPolicy.groupWords(words()).first()
    val preset =
      InstaPresets.byStyle(InstaVisualStyle.MINIMAL_KINETIC)
        .copy(pattern = com.music.echo.insta.InstaAnimationPattern.HANDWRITTEN_ACCENT)
    val frames =
      InstaLrcPolicy.framesForPhrase(phrase, 2_300L, preset, InstaVisualStyle.MINIMAL_KINETIC, 0.4f)

    assertTrue(frames.first { it.word.text == "dream" }.pattern == com.music.echo.insta.InstaAnimationPattern.HANDWRITTEN_ACCENT)
    assertTrue(frames.first { it.word.text == "a" }.pattern != com.music.echo.insta.InstaAnimationPattern.HANDWRITTEN_ACCENT)
  }

  @Test
  fun empty_and_single_word_lyrics_are_safe() {
    assertTrue(InstaLrcPolicy.groupWords(emptyList()).isEmpty())
    assertTrue(
      InstaLrcPolicy
        .framesForPhrase(emptyList(), 1_000L, InstaPresets.BUILT_IN.first(), InstaVisualStyle.SMART_HYBRID, 0.5f)
        .isEmpty(),
    )
    val single = listOf(InstaWord("alone", 1_000L, 1_400L, false))
    val frames =
      InstaLrcPolicy.framesForPhrase(single, 1_200L, InstaPresets.BUILT_IN.first(), InstaVisualStyle.SMART_HYBRID, 0.5f)
    assertEquals(1, frames.size)
    assertTrue(frames.single().active)
    assertFalse(frames.single().word.verified)
  }

  @Test
  fun render_spec_round_trips_custom_background_paths() {
    val spec =
      InstaRenderSpec(
        words = words(),
        title = "Title",
        artist = "Artist",
        preset = InstaPresets.BUILT_IN.first(),
        effectiveStyle = InstaVisualStyle.VINTAGE_CINEMATIC,
        artworkUrl = null,
        customImagePath = "content://media/images/1",
        customVideoPath = "content://media/videos/2",
        estimatedTiming = true,
      )
    val json = Json.encodeToString(InstaRenderSpec.serializer(), spec)
    val decoded = Json.decodeFromString(InstaRenderSpec.serializer(), json)

    assertEquals(spec.customImagePath, decoded.customImagePath)
    assertEquals(spec.customVideoPath, decoded.customVideoPath)
    assertTrue(decoded.estimatedTiming)
    assertEquals(spec.words, decoded.words)
  }
}
