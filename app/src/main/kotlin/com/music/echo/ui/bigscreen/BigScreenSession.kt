package echo.music.iad1tya.ui.bigscreen

import echo.music.iad1tya.ui.player.CinematicVisualState
import echo.music.iad1tya.ui.component.KaraokeWord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class BigScreenConnectionKind { WIRED_DISPLAY, WIRELESS_DISPLAY, DESKTOP_RECEIVER }

data class BigScreenConnection(
  val kind: BigScreenConnectionKind,
  val name: String,
  val detail: String = "",
)

/** Render payload shared by the phone, wired Presentation and web receiver. */
data class BigScreenVisualState(
  val visual: CinematicVisualState,
  val words: List<KaraokeWord>,
  val title: String,
  val artist: String,
  val lyricsVisible: Boolean = true,
)

/**
 * Process-wide Big Screen session. Audio always remains on the phone; only
 * visual state is published here. Nothing in this session starts playback.
 */
object BigScreenSession {
  private val _connection = MutableStateFlow<BigScreenConnection?>(null)
  val connection: StateFlow<BigScreenConnection?> = _connection

  private val _visual = MutableStateFlow<BigScreenVisualState?>(null)
  val visual: StateFlow<BigScreenVisualState?> = _visual

  fun setConnection(connection: BigScreenConnection?) {
    _connection.value = connection
    if (connection == null) _visual.value = null
  }

  fun publish(state: BigScreenVisualState) {
    if (_connection.value != null) _visual.value = state
  }
}
