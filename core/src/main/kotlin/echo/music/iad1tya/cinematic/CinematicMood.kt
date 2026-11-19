package echo.music.iad1tya.cinematic

/**
 * Coarse mood buckets used by the cinematic lyrics engine.
 *
 * These are *hints*, not claims of perfect emotion recognition. The estimator
 * combines real metadata tags with audio-derived energy; when only tempo is
 * available it deliberately stays at [NEUTRAL] with low confidence.
 */
enum class CinematicMood {
  SAD,
  ROMANTIC,
  EMOTIONAL,
  DREAMY,
  CHILL,
  LO_FI,
  AGGRESSIVE,
  MOTIVATIONAL,
  PARTY,
  DARK,
  ENERGETIC,
  NEUTRAL,
}

data class MoodResult(val mood: CinematicMood, val confidence: Float)
