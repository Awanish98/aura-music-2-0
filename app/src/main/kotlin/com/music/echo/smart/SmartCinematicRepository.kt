package echo.music.iad1tya.smart

import echo.music.iad1tya.cinematic.CinematicMood
import echo.music.iad1tya.cinematic.CinematicTheme
import echo.music.iad1tya.cinematic.SmartSongSignals
import echo.music.iad1tya.db.daos.CinematicLearningDao
import echo.music.iad1tya.db.entities.CinematicLearningEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device SMART Cinematic AI memory.
 *
 * All data stays in the private Room database. This is preference learning,
 * not foundation-model training. Failures must never affect playback, so
 * every public method is exception-safe.
 */
@Singleton
class SmartCinematicRepository @Inject constructor(
  private val dao: CinematicLearningDao,
) {
  suspend fun signals(
    trackKey: String,
    mood: CinematicMood,
    energy: Float,
    genreTags: List<String>,
    lyricsAvailable: Boolean,
    favorites: Set<CinematicTheme>,
  ): SmartSongSignals =
    runCatching {
      val entity = dao.find(trackKey) ?: return@runCatching SmartSongSignals(trackKey)
      SmartSongSignals(
        trackKey = trackKey,
        playCount = entity.playCount,
        skipCount = entity.skipCount,
        themeCounts = readThemeCounts(entity.themeCountsJson),
        recentThemes = listOfNotNull(entity.lastTheme?.let(CinematicTheme::fromName)),
        rejectedThemes = readThemeList(entity.rejectedThemesJson),
        mood = entity.aiMood?.let { runCatching { CinematicMood.valueOf(it) }.getOrNull() } ?: mood,
        energy = energy,
        genreTags = genreTags,
        lyricsAvailable = lyricsAvailable,
        favorites = favorites,
      )
    }.getOrElse {
      SmartSongSignals(trackKey)
    }

  suspend fun recordPlay(
    trackKey: String,
    title: String,
    artist: String,
    theme: CinematicTheme,
    mood: CinematicMood,
  ) {
    runCatching {
      val existing = dao.find(trackKey) ?: CinematicLearningEntity(trackKey = trackKey)
      val counts = readThemeCounts(existing.themeCountsJson).toMutableMap()
      counts[theme] = (counts[theme] ?: 0) + 1
      dao.upsert(
        existing.copy(
          title = title.ifBlank { existing.title },
          artist = artist.ifBlank { existing.artist },
          playCount = existing.playCount + 1,
          preferredTheme = theme.name,
          themeCountsJson = writeThemeCounts(counts),
          lastMood = mood.name,
          lastTheme = theme.name,
          updatedAt = System.currentTimeMillis(),
        )
      )
    }
  }

  suspend fun recordSkip(
    trackKey: String,
    title: String,
    artist: String,
    theme: CinematicTheme?,
  ) {
    runCatching {
      val existing = dao.find(trackKey) ?: CinematicLearningEntity(trackKey = trackKey)
      val rejected = readThemeList(existing.rejectedThemesJson).toMutableSet()
      // Playback reports the track but not the visual theme; the last theme we
      // recorded for this song is the honest one to learn from.
      val skippedTheme = theme ?: existing.lastTheme?.let { CinematicTheme.fromName(it) }
      if (skippedTheme != null) rejected += skippedTheme
      dao.upsert(
        existing.copy(
          title = title.ifBlank { existing.title },
          artist = artist.ifBlank { existing.artist },
          skipCount = existing.skipCount + 1,
          rejectedThemesJson = writeThemeList(rejected.toList().takeLast(6).toSet()),
          updatedAt = System.currentTimeMillis(),
        )
      )
    }
  }

  suspend fun recordManualTheme(
    trackKey: String,
    title: String,
    artist: String,
    theme: CinematicTheme,
  ) {
    runCatching {
      val existing = dao.find(trackKey) ?: CinematicLearningEntity(trackKey = trackKey)
      val counts = readThemeCounts(existing.themeCountsJson).toMutableMap()
      counts[theme] = (counts[theme] ?: 0) + 2
      dao.upsert(
        existing.copy(
          title = title.ifBlank { existing.title },
          artist = artist.ifBlank { existing.artist },
          preferredTheme = theme.name,
          themeCountsJson = writeThemeCounts(counts),
          lastTheme = theme.name,
          updatedAt = System.currentTimeMillis(),
        )
      )
    }
  }

  suspend fun saveAiPlan(
    trackKey: String,
    mood: CinematicMood,
    planJson: String,
  ) {
    runCatching {
      val existing = dao.find(trackKey) ?: CinematicLearningEntity(trackKey = trackKey)
      dao.upsert(
        existing.copy(
          aiMood = mood.name,
          // Do not overwrite lastTheme with an AI hint: that field records the
          // theme actually played and is used to learn from skips.
          aiPlanJson = planJson,
          aiUpdatedAt = System.currentTimeMillis(),
          updatedAt = System.currentTimeMillis(),
        )
      )
    }
  }

  suspend fun cachedPlan(trackKey: String, ttlMs: Long): String? =
    runCatching {
      val entity = dao.find(trackKey) ?: return@runCatching null
      val plan = entity.aiPlanJson ?: return@runCatching null
      if (System.currentTimeMillis() - entity.aiUpdatedAt > ttlMs) null else plan
    }.getOrNull()

  fun observeProfiles(): Flow<List<CinematicLearningEntity>> = dao.observeAll()

  suspend fun clear() = runCatching { dao.clear() }

  private fun readThemeCounts(json: String): Map<CinematicTheme, Int> =
    runCatching {
      val obj = JSONObject(json)
      obj.keys().asSequence().mapNotNull { name ->
        val theme = runCatching { CinematicTheme.valueOf(name) }.getOrNull()
        theme?.let { it to obj.optInt(name, 0) }
      }.toMap()
    }.getOrDefault(emptyMap())

  private fun writeThemeCounts(counts: Map<CinematicTheme, Int>): String =
    JSONObject().apply {
      counts.forEach { (theme, count) -> put(theme.name, count) }
    }.toString()

  private fun readThemeList(json: String): Set<CinematicTheme> =
    runCatching {
      val array = JSONArray(json)
      (0 until array.length()).mapNotNull { index ->
        runCatching { CinematicTheme.valueOf(array.getString(index)) }.getOrNull()
      }.toSet()
    }.getOrDefault(emptySet())

  private fun writeThemeList(themes: Set<CinematicTheme>): String =
    JSONArray().apply { themes.forEach { put(it.name) } }.toString()
}
