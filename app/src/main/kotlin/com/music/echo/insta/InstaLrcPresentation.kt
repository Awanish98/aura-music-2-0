package com.music.echo.insta

import echo.music.iad1tya.cinematic.CinematicReactions
import echo.music.iad1tya.ui.player.CinematicEngineState

/** Human-readable provenance shown next to the renderer so AI is never overstated. */
data class InstaSpecResult(
  val spec: InstaRenderSpec,
  val status: String,
  val degradedAi: Boolean,
  val language: String,
)

/**
 * Builds the one frame specification used by live playback and preset previews.
 * It reads the existing cinematic engine rather than creating a second lyrics
 * pipeline or a second player.
 */
object InstaLrcPresentation {
  fun buildSpec(
    engine: CinematicEngineState,
    settings: InstaLrcSettingsState,
    title: String,
    artist: String,
    artworkUrl: String?,
    reaction: InstaReactionInput,
    positionMs: Long = engine.visual.positionMs + settings.timingOffsetMs.value,
  ): InstaSpecResult {
    val basePreset = settings.preset
    // Safe mode is a strict local fallback: true black, no glow, no grain and no
    // per-frame audio decoration. It never changes lyrics timing.
    val preset =
      if (settings.safeMode.value) {
        basePreset.copy(
          background = InstaBackground.PURE_BLACK,
          glow = 0f,
          grainIntensity = 0f,
          blur = 0f,
          animationIntensity = 0.25f,
          pattern = InstaAnimationPattern.WORD_BY_WORD,
          lowPower = true,
        )
      } else {
        basePreset
      }
    val words =
      engine.words.map { word ->
        InstaWord(
          text = word.text,
          startMs = word.startMs,
          endMs = word.endMs,
          verified = word.verified,
        )
      }
    val language = InstaLrcPolicy.detectLanguage(words.joinToString(" ") { it.text })
    val ai = engine.ai
    val trustedAi = ai?.takeIf { !it.degraded && it.confidence > 0.3f }
    val mode = settings.mode.value

    val mood = trustedAi?.mood?.name ?: engine.mood.name
    val energy =
      trustedAi?.energyHint
        ?: if (reaction.silence) 0f else reaction.energy.coerceIn(0f, 1f)
    val section =
      trustedAi
        ?.sections
        ?.firstOrNull { positionMs in it.startMs..it.endMs }
        ?.name
        ?: engine.section.name
    val requestedStyle =
      if (mode == InstaLrcMode.MANUAL) preset.style else InstaVisualStyle.SMART_HYBRID
    val effectiveStyle =
      InstaLrcPolicy.chooseStyle(
        requested = requestedStyle,
        mood = mood,
        energy = energy,
        section = section,
        language = language,
        preferred = preset.style.takeIf { it != InstaVisualStyle.SMART_HYBRID },
      )

    val status =
      when (mode) {
        InstaLrcMode.MANUAL -> "MANUAL · ${preset.name} · ${effectiveStyle.label}"
        InstaLrcMode.AUTO -> "AUTO · ${effectiveStyle.label} · local selection"
        InstaLrcMode.SMART_AI ->
          when {
            ai == null -> "SMART AI · no plan · local selection"
            ai.degraded -> "SMART AI · offline · local selection"
            trustedAi == null -> "SMART AI · low confidence · local selection"
            else -> "SMART AI · cached/online plan · ${effectiveStyle.label}"
          }
      }

    return InstaSpecResult(
      spec =
        InstaRenderSpec(
          words = words,
          title = title,
          artist = artist,
          preset = preset,
          effectiveStyle = effectiveStyle,
          mood = mood,
          section = section,
          energy = energy,
          language = language,
          artworkUrl = artworkUrl,
          customImagePath = settings.imageUri.value.takeIf { it.isNotBlank() },
          customVideoPath = settings.videoUri.value.takeIf { it.isNotBlank() },
          estimatedTiming = words.isNotEmpty() && !engine.timingVerified,
        ),
      status = status,
      degradedAi = mode == InstaLrcMode.SMART_AI && trustedAi == null,
      language = language,
    )
  }

  fun reactionFor(engine: CinematicEngineState): InstaReactionInput {
    if (engine.features.silence) {
      return InstaReactionInput(energy = 0f, silence = true)
    }
    val factors = CinematicReactions.forFrame(engine.features)
    return InstaReactionInput(
      beat = factors.beat.coerceIn(0f, 1f),
      bass = factors.bass.coerceIn(0f, 1f),
      energy = factors.energy.coerceIn(0f, 1f),
      silence = factors.silence,
    )
  }
}
