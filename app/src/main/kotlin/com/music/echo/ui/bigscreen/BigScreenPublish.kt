package echo.music.iad1tya.ui.bigscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import echo.music.iad1tya.constants.BigScreenDesktopConnectedKey
import echo.music.iad1tya.constants.BigScreenDesktopReceiverNameKey
import echo.music.iad1tya.constants.BigScreenDesktopSessionKey
import echo.music.iad1tya.constants.BigScreenLyricsVisibleKey
import echo.music.iad1tya.ui.player.CinematicEngineState
import echo.music.iad1tya.ui.player.CinematicSettingsState
import echo.music.iad1tya.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Publishes the already-computed cinematic state to whichever Big Screen
 * surface is actually connected. Audio still plays only on the phone; no
 * playback command is issued here.
 *
 * A desktop receiver is only reported as connected after the paired browser
 * has polled the backend session, so the UI never claims a false connection.
 */
@Composable
fun BigScreenPublisher(
  engine: CinematicEngineState,
  settings: CinematicSettingsState,
  title: String,
  artist: String,
  playing: Boolean,
  durationMs: Long,
  lyricsText: String,
) {
  val connection by BigScreenSession.connection.collectAsState()
  var lyricsVisible by rememberPreference(BigScreenLyricsVisibleKey, true)
  var sessionToken by rememberPreference(BigScreenDesktopSessionKey, "")
  var desktopConnected by rememberPreference(BigScreenDesktopConnectedKey, false)
  var receiverName by rememberPreference(BigScreenDesktopReceiverNameKey, "")
  val client = remember { DesktopReceiverClient() }

  val current by rememberUpdatedState(engine)
  val currentTitle by rememberUpdatedState(title)
  val currentArtist by rememberUpdatedState(artist)
  val currentPlaying by rememberUpdatedState(playing)
  val currentDuration by rememberUpdatedState(durationMs)
  val currentLyrics by rememberUpdatedState(lyricsText)
  val currentLyricsVisible by rememberUpdatedState(lyricsVisible)
  val currentConnection by rememberUpdatedState(connection)
  val currentSessionToken by rememberUpdatedState(sessionToken)
  val currentBaseUrl by rememberUpdatedState(settings.backendBaseUrl.value)
  val currentClientToken by rememberUpdatedState(settings.backendToken.value)
  val currentReceiverName by rememberUpdatedState(receiverName)

  LaunchedEffect(
    connection,
    currentBaseUrl,
    currentClientToken,
    currentSessionToken,
    currentLyricsVisible,
    desktopConnected,
  ) {
    val pendingDesktop =
      !desktopConnected && currentSessionToken.isNotBlank() && currentBaseUrl.isNotBlank()
    if (currentConnection == null && !pendingDesktop) return@LaunchedEffect

    while (isActive) {
      val snapshot = current
      val visual =
        BigScreenVisualState(
          visual = snapshot.visual,
          words = snapshot.words,
          title = currentTitle,
          artist = currentArtist,
          lyricsVisible = currentLyricsVisible,
        )
      if (currentConnection != null) BigScreenSession.publish(visual)

      val publishDesktop =
        pendingDesktop || currentConnection?.kind == BigScreenConnectionKind.DESKTOP_RECEIVER
      if (publishDesktop) {
        val published =
          client.publish(
            baseUrl = currentBaseUrl,
            clientToken = currentClientToken,
            sessionToken = currentSessionToken,
            state = visual,
            durationMs = currentDuration,
            playing = currentPlaying,
            lyricsText = currentLyrics,
          )
        if (published && pendingDesktop && currentConnection == null && !desktopConnected) {
          val status =
            client.status(
              baseUrl = currentBaseUrl,
              clientToken = currentClientToken,
              sessionToken = currentSessionToken,
            )
          if (status?.paired == true && status.lastPollAt != null) {
            BigScreenSession.setConnection(
              BigScreenConnection(
                kind = BigScreenConnectionKind.DESKTOP_RECEIVER,
                name = currentReceiverName.ifBlank { "Desktop receiver" },
                detail = "Paired browser receiver · audio stays on phone",
              )
            )
            desktopConnected = true
          }
        }
      }
      delay(120L)
    }
  }
}
