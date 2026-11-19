package echo.music.iad1tya.cinematic

/**
 * Combines real metadata/genre tags with live audio energy into a mood hint.
 * Tempo alone never produces a confident emotion label.
 */
object MoodEstimator {
  private val KEYWORDS:
    Map<CinematicMood, List<String>> =
    mapOf(
      CinematicMood.SAD to
        listOf("sad", "sorrow", "melanchol", "heartbreak", "depress", "lonely", "grief", "tear"),
      CinematicMood.ROMANTIC to
        listOf("romance", "romantic", "love", "valentine", "wedding", "affection"),
      CinematicMood.EMOTIONAL to
        listOf("emotional", "sentimental", "ballad", "soulful", "heartfelt", "passionate"),
      CinematicMood.DREAMY to
        listOf("dream", "ambient", "ethereal", "atmospheric", "shoegaze", "float"),
      CinematicMood.CHILL to
        listOf("chill", "relax", "calm", "downtempo", "easy listening", "acoustic", "soft"),
      CinematicMood.LO_FI to listOf("lofi", "lo-fi", "hip hop", "jazz", "study", "boom bap"),
      CinematicMood.AGGRESSIVE to
        listOf("aggressive", "hardcore", "metal", "punk", "rage", "phonk", "drill", "grind"),
      CinematicMood.MOTIVATIONAL to
        listOf("motivat", "inspir", "uplift", "anthem", "workout", "epic", "triumph"),
      CinematicMood.PARTY to
        listOf("party", "dance", "house", "edm", "techno", "club", "festival", "electro"),
      CinematicMood.DARK to
        listOf("dark", "gothic", "noir", "ominous", "horror", "black metal", "doom"),
      CinematicMood.ENERGETIC to
        listOf("energetic", "fast", "upbeat", "high energy", "drum and bass", "dnb", "rock"),
    )

  fun estimate(audio: AudioFeatures, tags: List<String>, title: String = ""): MoodResult {
    val scores = mutableMapOf<CinematicMood, Float>()
    fun add(mood: CinematicMood, weight: Float) {
      scores[mood] = (scores[mood] ?: 0f) + weight
    }

    val normalizedTags = tags.map { it.trim().lowercase() }.filter { it.isNotBlank() }
    val normalizedTitle = title.trim().lowercase()

    for (tag in normalizedTags) {
      for ((mood, words) in KEYWORDS) {
        if (words.any { tag.contains(it) }) add(mood, 2.2f)
      }
    }
    if (normalizedTitle.isNotBlank()) {
      for ((mood, words) in KEYWORDS) {
        if (words.any { normalizedTitle.contains(it) }) add(mood, 0.7f)
      }
    }

    val hasMetadata = normalizedTags.isNotEmpty() || normalizedTitle.isNotBlank()
    // Audio can nudge a mood but is never allowed to invent one on its own.
    if (audio.energy > 0.72f && audio.bpm >= 118f) {
      add(CinematicMood.ENERGETIC, 0.65f)
      if (hasMetadata) add(CinematicMood.PARTY, 0.35f)
    } else if (audio.energy < 0.26f && audio.bpm in 1f..100f) {
      add(CinematicMood.CHILL, 0.6f)
      if (hasMetadata) add(CinematicMood.SAD, 0.3f)
    } else if (audio.bass > 0.75f && audio.intensity > 0.7f) {
      add(CinematicMood.AGGRESSIVE, 0.5f)
    }

    val best = scores.maxByOrNull { it.value }
    if (best == null || best.value < 1.4f) {
      return MoodResult(CinematicMood.NEUTRAL, if (!hasMetadata) 0.1f else 0.25f)
    }
    val total = scores.values.sum()
    val rawConfidence = best.value / (total + 0.5f)
    val confidence = if (hasMetadata) rawConfidence.coerceIn(0f, 1f) else rawConfidence.coerceAtMost(0.38f)
    return MoodResult(best.key, confidence)
  }
}
