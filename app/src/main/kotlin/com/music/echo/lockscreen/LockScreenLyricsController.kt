package echo.music.iad1tya.lockscreen

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import echo.music.iad1tya.constants.CinematicLyricCorrectionsKey
import echo.music.iad1tya.constants.LockScreenLyricsEnabledKey
import echo.music.iad1tya.constants.LockScreenLyricsVisibleKey
import echo.music.iad1tya.db.MusicDatabase
import echo.music.iad1tya.db.entities.LyricsEntity
import echo.music.iad1tya.lyrics.LyricsEntry
import echo.music.iad1tya.lyrics.LyricsUtils
import echo.music.iad1tya.playback.SessionArtworkPlayer
import echo.music.iad1tya.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Publishes the active lyric frame into the MediaSession boundary.
 *
 * It never touches ExoPlayer: the player, queue and timers stay owned by
 * [echo.music.iad1tya.playback.MusicService]. All failures are swallowed so
 * lock-screen lyrics can never interrupt playback.
 */
class LockScreenLyricsController(
  private val context: Context,
  private val database: MusicDatabase,
  private val sessionPlayer: () -> SessionArtworkPlayer?,
  private val scope: CoroutineScope,
) {
  private val resolver = LockScreenLyricResolver { correctionMs }
  private var job: Job? = null
  @Volatile private var publish = false
  private var correctionMs = 0L
  private var correctionsJson = "{}"
  private var cachedTrackId: String? = null
  private var cachedEntries: List<LyricsEntry> = emptyList()

  fun start() {
    if (job?.isActive == true) return
    job =
      scope.launch {
        launch { observePreferences() }
        while (isActive) {
          runCatching { tick() }
          delay(250L)
        }
      }
  }

  fun stop() {
    job?.cancel()
    job = null
    sessionPlayer()?.setLockScreenLyricsEnabled(false)
  }

  /** Reapply preference state after the MediaSession wrapper is replaced. */
  fun resync() {
    cachedTrackId = null
    sessionPlayer()?.setLockScreenLyricsEnabled(publish)
  }

  private suspend fun observePreferences() {
    val store: DataStore<Preferences> = context.dataStore
    combine(
      store.data.map { it[LockScreenLyricsEnabledKey] ?: false },
      store.data.map { it[LockScreenLyricsVisibleKey] ?: true },
      store.data.map { it[CinematicLyricCorrectionsKey].orEmpty() },
    ) { enabled, visible, corrections ->
      Pair(enabled && visible, corrections)
    }.collectLatest { state ->
      publish = state.first
      correctionsJson = state.second
      sessionPlayer()?.setLockScreenLyricsEnabled(state.first)
    }
  }

  private suspend fun tick() {
    val player = sessionPlayer() ?: return
    val trackId = player.currentMediaItem?.mediaId.orEmpty()
    if (trackId != cachedTrackId) {
      cachedTrackId = trackId
      cachedEntries = loadEntries(trackId)
    }
    if (!publish) return
    correctionMs =
      runCatching { JSONObject(correctionsJson.ifBlank { "{}" }).optLong(trackId, 0L) }
        .getOrDefault(0L)
    player.updateLockScreenLyric(resolver.resolve(cachedEntries, player.currentPosition))
  }

  private suspend fun loadEntries(trackId: String): List<LyricsEntry> {
    if (trackId.isBlank()) return emptyList()
    return runCatching {
      database
        .lyrics(trackId)
        .firstOrNull()
        ?.takeIf { it.lyrics != LyricsEntity.LYRICS_NOT_FOUND }
        ?.lyrics
        ?.let(LyricsUtils::parseLyrics)
        .orEmpty()
    }.getOrDefault(emptyList())
  }
}
