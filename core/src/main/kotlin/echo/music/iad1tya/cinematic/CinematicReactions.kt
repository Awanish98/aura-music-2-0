package echo.music.iad1tya.cinematic

/**
 * Intensity-scaled reaction factors shared by every cinematic renderer.
 *
 * The analyser describes what is in the audio; these factors decide how much
 * of it the visuals may express. Transients are deliberately softened for quiet
 * passages and silenced during genuine silence, so no beat produces an
 * aggressive flash. Pure JVM code: no Android types, fully unit-testable.
 */
data class ReactionFactors(
  val intensity: Float,
  val liveliness: Float,
  val impact: Float,
  val bass: Float,
  val mid: Float,
  val treble: Float,
  val energy: Float,
  val beat: Float,
  val kick: Float,
  val snare: Float,
  val drop: Float,
  val onset: Float,
  val silence: Boolean,
  val bpm: Float,
) {
  companion object {
    val IDLE =
      ReactionFactors(
        intensity = 0f,
        liveliness = 0.2f,
        impact = 0f,
        bass = 0f,
        mid = 0f,
        treble = 0f,
        energy = 0f,
        beat = 0f,
        kick = 0f,
        snare = 0f,
        drop = 0f,
        onset = 0f,
        silence = true,
        bpm = 0f,
      )
  }
}

object CinematicReactions {
  /** Converts one analysed frame into bounded, non-aggressive visual factors. */
  fun forFrame(features: AudioFeatures): ReactionFactors {
    val intensity = features.intensity.coerceIn(0f, 1f)
    val silence = features.silence
    // Ambient motion eases down in quiet music and nearly stops in silence.
    val liveliness = (if (silence) 0.2f else 0.42f + 0.58f * intensity).coerceIn(0f, 1f)
    // Transient impact tracks intensity; quiet songs never flash on every beat.
    val impact = (if (silence) 0f else 0.28f + 0.72f * intensity).coerceIn(0f, 1f)
    val bandScale = (if (silence) 0.3f else 0.55f + 0.45f * intensity).coerceIn(0f, 1f)
    return ReactionFactors(
      intensity = intensity,
      liveliness = liveliness,
      impact = impact,
      bass = (features.bass * bandScale).coerceIn(0f, 1f),
      mid = (features.mid * bandScale).coerceIn(0f, 1f),
      treble = (features.treble * bandScale).coerceIn(0f, 1f),
      energy = (features.energy * bandScale).coerceIn(0f, 1f),
      beat = (features.beat * impact).coerceIn(0f, 1f),
      kick = (features.kick * impact).coerceIn(0f, 1f),
      snare = (features.snare * impact).coerceIn(0f, 1f),
      drop = (features.drop * impact).coerceIn(0f, 1f),
      onset = (features.onset * impact).coerceIn(0f, 1f),
      silence = silence,
      bpm = features.bpm,
    )
  }
}
