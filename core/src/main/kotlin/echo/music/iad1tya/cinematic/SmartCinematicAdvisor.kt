package echo.music.iad1tya.cinematic

import kotlin.random.Random

/**
 * Local, persistent preference learning for SMART Cinematic AI.
 *
 * This does not retrain any foundation model. It turns observed listening
 * signals into a transparent weighted recommendation and keeps the decision
 * stable for a song so playback is never disrupted.
 */
data class SmartSongSignals(
  val trackKey: String,
  val playCount: Int = 0,
  val skipCount: Int = 0,
  val themeCounts: Map<CinematicTheme, Int> = emptyMap(),
  val recentThemes: List<CinematicTheme> = emptyList(),
  val rejectedThemes: Set<CinematicTheme> = emptySet(),
  val mood: CinematicMood = CinematicMood.NEUTRAL,
  val energy: Float = 0.5f,
  val genreTags: List<String> = emptyList(),
  val lyricsAvailable: Boolean = false,
  val favorites: Set<CinematicTheme> = emptySet(),
)

class SmartCinematicAdvisor(private val random: Random = Random.Default) {
  fun recommend(signals: SmartSongSignals): CinematicTheme {
    val tempo =
      when {
        signals.energy > 0.68f -> TempoBias.FAST
        signals.energy < 0.3f -> TempoBias.SLOW
        else -> TempoBias.MEDIUM
      }
    val weights =
      CinematicTheme.entries.associateWith { theme ->
        var weight = 1f
        if (theme.moods.contains(signals.mood)) weight += 2.4f
        if (theme.tempo == tempo || theme.tempo == TempoBias.ANY) weight += 0.8f
        if (theme in signals.favorites) weight += 1.5f
        if (signals.genreTags.any { theme.displayName.contains(it, ignoreCase = true) }) {
          weight += 0.7f
        }
        val kept = signals.themeCounts[theme] ?: 0
        if (kept > 0 && signals.skipCount == 0) weight += (kept.coerceAtMost(6) * 0.35f)
        if (theme in signals.rejectedThemes) weight *= 0.12f
        if (theme in signals.recentThemes) weight *= 0.35f
        if (signals.playCount >= 2 && signals.lyricsAvailable) {
          // Repeated listens with no explicit rejection strengthen the current fit.
          weight += (signals.themeCounts[theme] ?: 0) * 0.15f
        }
        weight.coerceAtLeast(0.02f)
      }
    var roll = random.nextDouble() * weights.values.sum()
    for ((theme, weight) in weights) {
      roll -= weight
      if (roll <= 0.0) return theme
    }
    return weights.maxByOrNull { it.value }?.key ?: CinematicTheme.GOLDEN_CINEMA
  }

  /** Deterministic tie-breaker so offline recommendations stay stable. */
  fun stableTheme(seed: String): CinematicTheme {
    val index = (seed.hashCode().toLong() and Long.MAX_VALUE) % CinematicTheme.entries.size
    return CinematicTheme.entries[index.toInt()]
  }
}
