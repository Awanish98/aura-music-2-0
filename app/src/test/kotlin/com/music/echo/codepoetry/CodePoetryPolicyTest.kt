package com.music.echo.codepoetry

import echo.music.iad1tya.ui.component.KaraokeWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodePoetryPolicyTest {
  private fun config(
    typingSpeed: Float = 1f,
    lowPower: Boolean = false,
    autoComposition: Boolean = false,
    composition: CodeComposition = CodeComposition.FULL_EDITOR,
    popupFrequency: Float = 0f,
    bugEnabled: Boolean = false,
    alwaysOn: Boolean = false,
    cameraIntensity: Float = if (lowPower) 0f else 0.6f,
  ): CodePoetryConfig =
    CodePoetryConfig(
      typingSpeed = typingSpeed,
      fontSize = 1f,
      palette = CodePalette.MIDNIGHT,
      cursorArgb = CodePalette.MIDNIGHT.cursor.toInt(),
      cameraIntensity = cameraIntensity,
      glitchIntensity = if (lowPower) 0f else 0.4f,
      grain = if (lowPower) 0f else 0.2f,
      popupFrequency = popupFrequency,
      bugEnabled = bugEnabled,
      autoComposition = autoComposition,
      composition = composition,
      alwaysOn = alwaysOn,
      lowPower = lowPower,
    )

  private fun words() =
    listOf(
      KaraokeWord("we", 1_000L, 1_300L, true),
      KaraokeWord("were", 1_300L, 1_800L, true),
      KaraokeWord("never", 1_800L, 2_500L, true),
      KaraokeWord("alone", 3_600L, 4_400L, false),
      KaraokeWord("tonight", 4_500L, 5_200L, true),
    )

  private fun manyPhrases() =
    (0..7).map { index ->
      val start = 1_000L + index * 2_000L
      listOf(KaraokeWord("line$index", start, start + 700L, true))
    }

  @Test
  fun groups_words_by_real_timing_gaps_and_long_line_breaks() {
    val phrases = CodePoetryPolicy.groupPhrases(words())
    assertEquals(2, phrases.size)
    assertEquals(listOf("we", "were", "never"), phrases[0].map { it.text })
    assertEquals(listOf("alone", "tonight"), phrases[1].map { it.text })

    val longLine =
      (0 until 10).map { index ->
        KaraokeWord("w$index", index * 100L, index * 100L + 90L)
      }
    assertEquals(listOf(9, 1), CodePoetryPolicy.groupPhrases(longLine).map { it.size })
    assertTrue(CodePoetryPolicy.groupPhrases(emptyList()).isEmpty())
  }

  @Test
  fun phrase_lookup_follows_seeking_before_inside_and_after() {
    val phrases = CodePoetryPolicy.groupPhrases(words())
    assertEquals(0, CodePoetryPolicy.phraseIndexAt(phrases, 500L))
    assertEquals(0, CodePoetryPolicy.phraseIndexAt(phrases, 2_000L))
    assertEquals(1, CodePoetryPolicy.phraseIndexAt(phrases, 4_000L))
    assertEquals(1, CodePoetryPolicy.phraseIndexAt(phrases, 9_000L))
  }

  @Test
  fun frames_are_deterministic_for_a_player_position() {
    val phrases = CodePoetryPolicy.groupPhrases(words())
    val cfg = config()
    val first =
      CodePoetryPolicy.buildFrame(phrases, 2_000L, cfg, true, 0.5f, "Title", "Artist", 7)
    val second =
      CodePoetryPolicy.buildFrame(phrases, 2_000L, cfg, true, 0.5f, "Title", "Artist", 7)
    assertEquals(first, second)
  }

  @Test
  fun frame_uses_only_words_from_the_shared_engine() {
    val phrases = CodePoetryPolicy.groupPhrases(words())
    val frame =
      CodePoetryPolicy.buildFrame(
        phrases = phrases,
        positionMs = 2_000L,
        config = config(),
        isPlaying = true,
        energy = 0.5f,
        title = "Title",
        artist = "Artist",
        frameIndex = 7,
      )
    assertEquals(
      phrases.flatten().map { it.text },
      frame.lines.flatMap { line -> line.words.map { it.text } },
    )
    assertEquals(1, frame.lines.count { it.isActive })
  }

  @Test
  fun typing_speed_controls_character_reveal_without_inventing_words() {
    val phrase = listOf(KaraokeWord("hello", 1_000L, 2_000L, true))
    val normal =
      CodePoetryPolicy.buildFrame(listOf(phrase), 1_400L, config(), true, 0f, "T", "A", 7)
        .lines
        .single()
        .words
        .single()
    val fast =
      CodePoetryPolicy.buildFrame(
        listOf(phrase),
        1_400L,
        config(typingSpeed = 2f),
        true,
        0f,
        "T",
        "A",
        7,
      )
        .lines
        .single()
        .words
        .single()
    assertEquals("hello", normal.text)
    assertEquals("he", normal.typed)
    assertEquals("hell", fast.typed)
  }

  @Test
  fun estimated_word_timing_is_carried_into_the_frame() {
    val phrases = CodePoetryPolicy.groupPhrases(words())
    val frame =
      CodePoetryPolicy.buildFrame(phrases, 4_000L, config(), true, 0f, "T", "A", 7)
    assertFalse(frame.timingVerified)
    assertFalse(frame.lines.first { it.words.any { word -> word.text == "alone" } }.verified)

    val verifiedPhrases = listOf(words().map { it.copy(verified = true) })
    val verified =
      CodePoetryPolicy.buildFrame(verifiedPhrases, 1_100L, config(), true, 0f, "T", "A", 7)
    assertTrue(verified.timingVerified)
  }

  @Test
  fun fixed_composition_ignores_auto_switching_triggers() {
    val frame =
      CodePoetryPolicy.buildFrame(
        manyPhrases(),
        7_100L,
        config(
          autoComposition = false,
          composition = CodeComposition.MACRO_WORD,
          popupFrequency = 1f,
          bugEnabled = true,
        ),
        true,
        1f,
        "T",
        "A",
        7,
      )
    assertEquals(CodeComposition.MACRO_WORD, frame.composition)
    assertFalse(frame.showBug)
    assertTrue(frame.popupText.isBlank())
  }

  @Test
  fun auto_composition_schedules_popup_and_bug_without_affecting_words() {
    val phrases = manyPhrases()
    val popupFrame =
      CodePoetryPolicy.buildFrame(
        phrases,
        phrases[3].first().startMs + 40L,
        config(autoComposition = true, popupFrequency = 1f),
        true,
        0.1f,
        "T",
        "A",
        7,
      )
    assertEquals(CodeComposition.ERROR_POPUP, popupFrame.composition)
    assertTrue(popupFrame.popupText.isNotBlank())

    val bugFrame =
      CodePoetryPolicy.buildFrame(
        phrases,
        phrases[2].first().startMs + 40L,
        config(autoComposition = true, bugEnabled = true),
        true,
        0.1f,
        "T",
        "A",
        7,
      )
    assertEquals(CodeComposition.BUG_SYMBOL, bugFrame.composition)
    assertTrue(bugFrame.showBug)
    assertEquals(
      phrases.take(5).flatten().map { it.text },
      bugFrame.lines.flatMap { line -> line.words.map { word -> word.text } },
    )
  }

  @Test
  fun editor_history_keeps_every_real_phrase_without_changing_the_window() {
    val phrases = manyPhrases()
    val frame =
      CodePoetryPolicy.buildFrame(
        phrases,
        7_100L,
        config(),
        true,
        0f,
        "T",
        "A",
        7,
      )

    // The original window contract is untouched: active phrase 4 => numbers 2..6.
    assertEquals((2..6).toList(), frame.lines.map { it.number })
    assertEquals(8, frame.editorLines.size)
    assertEquals((1..8).toList(), frame.editorLines.map { it.number })
    assertEquals(
      phrases.flatten().map { it.text },
      frame.editorLines.flatMap { line -> line.words.map { it.text } },
    )
    assertEquals(1, frame.editorLines.count { it.isActive })
    assertEquals(4, frame.editorLines.first { it.isActive }.number)
  }

  @Test
  fun code_editor_never_adds_edge_blur() {
    val frame =
      CodePoetryPolicy.buildFrame(
        manyPhrases(),
        3_100L,
        config(cameraIntensity = 1f),
        true,
        1f,
        "T",
        "A",
        7,
      )
    assertEquals(0f, frame.camera.blur, 0f)
  }

  @Test
  fun low_power_camera_is_neutral() {
    val frame =
      CodePoetryPolicy.buildFrame(
        manyPhrases(),
        3_100L,
        config(lowPower = true),
        true,
        1f,
        "T",
        "A",
        7,
      )
    assertEquals(1f, frame.camera.zoom, 0f)
    assertEquals(0f, frame.camera.panX, 0f)
    assertEquals(0f, frame.camera.panY, 0f)
    assertEquals(0f, frame.camera.rotation, 0f)
    assertEquals(0f, frame.camera.blur, 0f)
  }

  @Test
  fun always_on_drift_protects_amoled_and_normal_mode_stays_still() {
    val phrases = manyPhrases()
    val normal =
      CodePoetryPolicy.buildFrame(phrases, 1_100L, config(), true, 0f, "T", "A", 7)
    val alwaysOn =
      CodePoetryPolicy.buildFrame(
        phrases,
        1_100L,
        config(alwaysOn = true),
        true,
        0f,
        "T",
        "A",
        7,
      )
    assertEquals(0f, normal.driftX, 0f)
    assertEquals(0f, normal.driftY, 0f)
    assertTrue(alwaysOn.driftX != 0f || alwaysOn.driftY != 0f)
  }

  @Test
  fun empty_lyrics_render_a_safe_waiting_state() {
    val frame =
      CodePoetryPolicy.buildFrame(emptyList(), 1_234L, config(), true, 0f, "T", "A", 7)
    assertTrue(frame.lines.isEmpty())
    assertTrue(frame.timingVerified)
    assertEquals(1f, frame.camera.zoom, 0f)
  }

  @Test
  fun groq_hints_steer_composition_without_changing_words_or_timestamps() {
    val phrases = CodePoetryPolicy.groupPhrases(words())
    val plain = config(autoComposition = true)
    val energetic =
      plain.copy(
        ai =
          CodeAiDirection(
            energyHint = 0.95f,
            confidence = 1f,
            sectionName = "CHORUS",
            mood = "EUPHORIC",
            available = true,
          ),
      )
    val plainFrame =
      CodePoetryPolicy.buildFrame(phrases, 2_000L, plain, true, 0.2f, "T", "A", 7)
    val aiFrame =
      CodePoetryPolicy.buildFrame(phrases, 2_000L, energetic, true, 0.2f, "T", "A", 7)
    assertEquals(CodeComposition.MACRO_WORD, aiFrame.composition)
    assertTrue(aiFrame.camera.zoom >= plainFrame.camera.zoom)
    assertEquals("CHORUS", aiFrame.aiLabel)
    // Real words and their real timestamps are untouched by AI direction.
    val realWords = phrases.flatten()
    assertTrue(
      aiFrame.lines
        .flatMap { line -> line.words.map { it.text to it.startMs } }
        .all { (text, start) -> realWords.any { it.text == text && it.startMs == start } },
    )
    assertEquals("n", aiFrame.lines.first().words.last { it.text == "never" }.typed)
    assertEquals("n", plainFrame.lines.first().words.last { it.text == "never" }.typed)
  }

  @Test
  fun low_confidence_groq_hint_cannot_override_live_audio() {
    val phrases = CodePoetryPolicy.groupPhrases(words())
    val weak = config(autoComposition = true).copy(
      ai = CodeAiDirection(energyHint = 1f, confidence = 0f, available = true),
    )
    val frame = CodePoetryPolicy.buildFrame(phrases, 2_000L, weak, true, 0f, "T", "A", 7)
    assertEquals(CodeComposition.FULL_EDITOR, frame.composition)
    assertEquals("", frame.aiLabel)
  }

  @Test
  fun smoothstep_typing_still_reveals_only_real_characters() {
    val phrase = listOf(KaraokeWord("hello", 0L, 1_000L, true))
    val frame = CodePoetryPolicy.buildFrame(listOf(phrase), 500L, config(), true, 0f, "T", "A", 7)
    val word = frame.lines.single().words.single()
    assertEquals("hello", word.text)
    assertEquals("hel", word.typed)
  }
}
