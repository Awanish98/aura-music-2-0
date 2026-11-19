package echo.music.iad1tya.cinematic

import kotlin.random.Random

enum class ThemeMode { AUTO, MANUAL, SMART }

/**
 * Chooses a theme with a shuffle bag (fair distribution), mood/energy affinity
 * and an explicit never-immediately-repeat rule. The selected theme is stable
 * for the whole song unless section transitions are explicitly enabled.
 */
class ThemeSelector(private val random: Random = Random.Default) {
  private val bag = mutableListOf<CinematicTheme>()
  private val usage = mutableMapOf<CinematicTheme, Int>()
  private var lastTheme: CinematicTheme? = null

  fun selectForSong(
    songId: String,
    mode: ThemeMode,
    manualTheme: CinematicTheme,
    favorites: Set<CinematicTheme>,
    mood: CinematicMood,
    genreTags: List<String>,
    energy: Float,
    shuffle: Boolean,
    previousTheme: CinematicTheme? = lastTheme,
    smartSuggestion: CinematicTheme? = null,
  ): CinematicTheme {
    if (lastTheme != null && songId != lastSongId) {
      // A new song reached the selector; playback itself is untouched.
      lastSongId = songId
    }
    val selected =
      when (mode) {
        ThemeMode.MANUAL ->
          if (shuffle) {
            pickWeighted(
              CinematicTheme.entries.filter { it != previousTheme },
              favorites,
              mood,
              genreTags,
              energy,
            )
          } else {
            manualTheme
          }
        ThemeMode.AUTO -> nextAuto(previousTheme, favorites, mood, genreTags, energy)
        ThemeMode.SMART ->
          smartSuggestion ?: nextAuto(previousTheme, favorites, mood, genreTags, energy)
      }
    lastTheme = selected
    return selected
  }

  /** Optional, user-gated theme change at a musical section boundary. */
  fun selectForSection(
    section: SongSection,
    current: CinematicTheme,
    enabled: Boolean,
    mode: ThemeMode,
    favorites: Set<CinematicTheme>,
    mood: CinematicMood,
    genreTags: List<String>,
    energy: Float,
  ): CinematicTheme? {
    if (!enabled || mode == ThemeMode.MANUAL) return null
    val chance =
      when (section) {
        SongSection.CHORUS -> 0.6f
        SongSection.BRIDGE -> 0.45f
        SongSection.INSTRUMENTAL -> 0.35f
        SongSection.OUTRO -> 0.2f
        else -> return null
      }
    if (random.nextFloat() > chance) return null
    val candidates = CinematicTheme.entries.filter { it != current }
    val selected = pickWeighted(candidates, favorites, mood, genreTags, energy)
    lastTheme = selected
    return selected
  }

  private fun nextAuto(
    previous: CinematicTheme?,
    favorites: Set<CinematicTheme>,
    mood: CinematicMood,
    genreTags: List<String>,
    energy: Float,
  ): CinematicTheme {
    if (bag.isEmpty()) {
      bag.addAll(CinematicTheme.entries.shuffled(random))
      if (previous != null) {
        bag.remove(previous)
        bag.add(previous)
      }
    }
    var selected = pickWeighted(bag, favorites, mood, genreTags, energy)
    bag.remove(selected)
    if (selected == previous && bag.isNotEmpty()) {
      selected = bag.removeAt(0)
    }
    usage[selected] = (usage[selected] ?: 0) + 1
    return selected
  }

  private fun pickWeighted(
    candidates: List<CinematicTheme>,
    favorites: Set<CinematicTheme>,
    mood: CinematicMood,
    genreTags: List<String>,
    energy: Float,
  ): CinematicTheme {
    if (candidates.isEmpty()) return CinematicTheme.GOLDEN_CINEMA
    val tags = genreTags.map { it.trim().lowercase() }
    val tempo =
      when {
        energy > 0.68f -> TempoBias.FAST
        energy < 0.3f -> TempoBias.SLOW
        else -> TempoBias.MEDIUM
      }
    val weights =
      candidates.map { theme ->
        var weight = 1f
        if (theme.moods.contains(mood)) weight += 2.2f
        if (theme in favorites) weight += 1.1f
        if (theme.tempo == tempo || theme.tempo == TempoBias.ANY) weight += 0.7f
        if (tags.any { tag -> theme.displayName.contains(tag, ignoreCase = true) }) weight += 0.6f
        val used = usage[theme] ?: 0
        weight = (weight - used * 0.24f).coerceAtLeast(0.08f)
        weight
      }
    var roll = random.nextDouble() * weights.sum()
    for (index in candidates.indices) {
      roll -= weights[index]
      if (roll <= 0.0) return candidates[index]
    }
    return candidates.last()
  }

  private var lastSongId: String? = null
}
