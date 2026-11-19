package echo.music.iad1tya.ui.bigscreen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import echo.music.iad1tya.cinematic.CinematicTheme
import echo.music.iad1tya.cinematic.ThemeMode
import echo.music.iad1tya.constants.BigScreenDesktopConnectedKey
import echo.music.iad1tya.constants.BigScreenDesktopPairCodeKey
import echo.music.iad1tya.constants.BigScreenDesktopReceiverNameKey
import echo.music.iad1tya.constants.BigScreenDesktopSessionKey
import echo.music.iad1tya.constants.BigScreenLyricsVisibleKey
import echo.music.iad1tya.ui.player.CinematicEngineState
import echo.music.iad1tya.ui.player.CinematicSettingsState
import echo.music.iad1tya.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phone-side Big Screen remote. Every transport command is routed to exactly
 * one active target: the Cast SDK when Cast is connected, otherwise the phone
 * player. Only visual state is published to wired/desktop displays.
 */
@Composable
fun BigScreenRemoteControl(
  controller: BigScreenController,
  settings: CinematicSettingsState,
  engine: CinematicEngineState,
  isCasting: Boolean,
  castDeviceName: String?,
  castPlaying: Boolean,
  positionMs: Long,
  durationMs: Long,
  volume: Float,
  maxVolume: Float,
  shuffleEnabled: Boolean,
  songLiked: Boolean,
  onDismiss: () -> Unit,
  onTogglePlayPause: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onSeek: (Long) -> Unit,
  onVolumeChange: (Float) -> Unit,
  onToggleShuffle: () -> Unit,
  onToggleLike: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val connection by BigScreenSession.connection.collectAsState()
  val displays = rememberExternalDisplays()
  val client = remember { DesktopReceiverClient() }

  var lyricsVisible by rememberPreference(BigScreenLyricsVisibleKey, true)
  var sessionToken by rememberPreference(BigScreenDesktopSessionKey, "")
  var pairCode by rememberPreference(BigScreenDesktopPairCodeKey, "")
  var desktopConnected by rememberPreference(BigScreenDesktopConnectedKey, false)
  var receiverName by rememberPreference(BigScreenDesktopReceiverNameKey, "")
  var receiverNameInput by remember { mutableStateOf(receiverName.ifBlank { "Laptop" }) }
  var pairError by remember { mutableStateOf<String?>(null) }
  var pairing by remember { mutableStateOf(false) }
  var createdUrl by remember { mutableStateOf("") }

  val gold = Color(0xFFE8C77B)
  val surface = Color(0xFF08090C)
  val text = Color(0xFFF3EDE2)
  val subtle = Color(0xFF9AA0AA)

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    Column(
      Modifier
        .fillMaxSize()
        .background(surface)
        .verticalScroll(rememberScrollState())
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          "AURA BIG SCREEN",
          color = gold,
          fontFamily = FontFamily.Serif,
          fontWeight = FontWeight.Bold,
          fontSize = 20.sp,
          letterSpacing = 2.sp,
          modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) { Text("CLOSE", color = text) }
      }

      SectionLabel("CONNECTION")
      ConnectionCard(
        connection = connection,
        isCasting = isCasting,
        castDeviceName = castDeviceName,
        pairPending = sessionToken.isNotBlank() && !desktopConnected,
        subtle = subtle,
        text = text,
      )

      if (displays.isNotEmpty()) {
        SectionLabel("DETECTED EXTERNAL DISPLAYS")
        displays.forEach { display ->
          Row(
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFF12141A))
              .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(Modifier.weight(1f)) {
              Text(display.name, color = text, fontSize = 13.sp)
              Text(
                if (display.isPresentationReady) "Android reports video output available"
                else "No presentation output detected",
                color = subtle,
                fontSize = 11.sp,
              )
            }
            Button(
              onClick = { controller.connect(display); createdUrl = "" },
              colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color.Black),
            ) { Text("CONNECT", fontSize = 11.sp) }
          }
        }
      }

      SectionLabel("DESKTOP RECEIVER")
      Text(
        "Pair a browser receiver on another device. Audio remains on your phone; only lyrics and visual state are sent. USB charging/data alone is not video output.",
        color = subtle,
        fontSize = 11.sp,
        lineHeight = 15.sp,
      )
      OutlinedTextField(
        value = receiverNameInput,
        onValueChange = { receiverNameInput = it },
        label = { Text("Receiver name") },
        singleLine = true,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth(),
      )
      Button(
        onClick = {
          pairError = null
          pairing = true
          scope.launch(Dispatchers.IO) {
            val session =
              client.createSession(
                baseUrl = settings.backendBaseUrl.value,
                clientToken = settings.backendToken.value,
                receiverName = receiverNameInput.ifBlank { "Desktop receiver" },
              )
            withContext(Dispatchers.Main) {
              pairing = false
              if (session != null && session.code.length == 6 && session.sessionToken.isNotBlank()) {
                sessionToken = session.sessionToken
                pairCode = session.code
                receiverName = receiverNameInput.ifBlank { "Desktop receiver" }
                desktopConnected = false
                createdUrl = session.receiverUrl
              } else {
                pairError = "Could not create a pairing session. Check the backend URL and token."
              }
            }
          }
        },
        enabled = settings.backendBaseUrl.value.isNotBlank() && !pairing,
        colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color.Black),
      ) {
        if (pairing) CircularProgressIndicator(color = Color.Black, modifier = Modifier.height(16.dp))
        else Text("CREATE PAIRING CODE")
      }
      if (settings.backendBaseUrl.value.isBlank()) {
        Text(
          "Set the secure backend URL in Cinematic Studio → SMART settings first.",
          color = subtle,
          fontSize = 11.sp,
        )
      }
      pairError?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 11.sp) }
      if (pairCode.length == 6) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            pairCode,
            color = gold,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            letterSpacing = 6.sp,
            modifier = Modifier.weight(1f),
          )
          if (createdUrl.isNotBlank()) {
            TextButton(
              onClick = {
                runCatching {
                  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(createdUrl)))
                }
              }
            ) { Text("OPEN RECEIVER", color = text) }
          }
        }
        Text(
          if (desktopConnected) "Receiver connected and polling."
          else "Enter this code on the receiver. Connection is shown only after the receiver polls.",
          color = if (desktopConnected) gold else subtle,
          fontSize = 11.sp,
        )
      }

      SectionLabel("REMOTE CONTROLS")
      Text(
        connection?.let(::mediaLine) ?: "Controlling playback on this phone",
        color = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
      )
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = onPrevious) { Text("⏮", color = text, fontSize = 26.sp) }
        Button(
          onClick = onTogglePlayPause,
          colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color.Black),
        ) {
          Text(
            if (isCasting) (if (castPlaying) "PAUSE" else "PLAY")
            else (if (engine.features.silence) "PLAY" else "PLAY / PAUSE"),
            fontSize = 12.sp,
          )
        }
        TextButton(onClick = onNext) { Text("⏭", color = text, fontSize = 26.sp) }
      }
      Slider(
        value = positionMs.coerceAtMost(durationMs).toFloat(),
        onValueChange = { onSeek(it.toLong()) },
        valueRange = 0f..(durationMs.coerceAtLeast(1L)).toFloat(),
        modifier = Modifier.fillMaxWidth(),
      )
      Text(
        "${formatMs(positionMs)} / ${formatMs(durationMs)}",
        color = subtle,
        fontSize = 11.sp,
        modifier = Modifier.align(Alignment.End),
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Volume", color = text, fontSize = 12.sp, modifier = Modifier.weight(0.18f))
        Slider(
          value = volume,
          onValueChange = onVolumeChange,
          valueRange = 0f..1f,
          modifier = Modifier.weight(0.82f),
        )
      }
      Text(
        if (isCasting) "Cast volume is controlled by the Cast SDK." else "Phone media volume ($maxVolume steps)",
        color = subtle,
        fontSize = 10.sp,
      )

      SectionLabel("LIBRARY & VISUALS")
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Lyrics on big screen", color = text, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(checked = lyricsVisible, onCheckedChange = { lyricsVisible = it })
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Playback shuffle", color = text, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(checked = shuffleEnabled, onCheckedChange = { onToggleShuffle() })
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Favourite song", color = text, fontSize = 12.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onToggleLike) {
          Text(if (songLiked) "♥ SAVED" else "♡ ADD", color = if (songLiked) gold else text)
        }
      }

      SectionLabel("THEME MODE")
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RemoteModeButton("AUTO", settings.mode.value == ThemeMode.AUTO, gold, text) {
          settings.mode.value = ThemeMode.AUTO
        }
        RemoteModeButton("MANUAL", settings.mode.value == ThemeMode.MANUAL, gold, text) {
          settings.mode.value = ThemeMode.MANUAL
        }
        RemoteModeButton("SMART", settings.mode.value == ThemeMode.SMART, gold, text) {
          settings.mode.value = ThemeMode.SMART
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Theme shuffle", color = text, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(checked = settings.shuffle.value, onCheckedChange = { settings.shuffle.value = it })
      }
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(CinematicTheme.entries) { theme ->
          val active = theme == engine.theme
          Button(
            onClick = { engine.onSelectTheme(theme) },
            colors =
              ButtonDefaults.buttonColors(
                containerColor = if (active) gold else Color(theme.background.toInt()),
                contentColor = if (active) Color.Black else Color(theme.text.toInt()),
              ),
          ) {
            Text(
              "${if (theme in settings.favorites.value) "★ " else ""}${theme.displayName}",
              fontSize = 10.sp,
            )
          }
        }
      }

      OutlinedButton(
        onClick = {
          if (connection?.kind == BigScreenConnectionKind.WIRED_DISPLAY) {
            controller.disconnectWired()
          }
          if (connection?.kind == BigScreenConnectionKind.DESKTOP_RECEIVER || sessionToken.isNotBlank()) {
            val token = sessionToken
            scope.launch(Dispatchers.IO) {
              if (token.isNotBlank() && settings.backendBaseUrl.value.isNotBlank()) {
                client.disconnect(settings.backendBaseUrl.value, settings.backendToken.value, token)
              }
            }
            sessionToken = ""
            pairCode = ""
            desktopConnected = false
            createdUrl = ""
            if (connection?.kind == BigScreenConnectionKind.DESKTOP_RECEIVER) {
              BigScreenSession.setConnection(null)
            }
          }
        },
        modifier = Modifier.fillMaxWidth(),
      ) { Text("DISCONNECT", color = Color(0xFFFF8A80)) }

      Spacer(Modifier.height(12.dp))
    }
  }
}

@Composable
private fun SectionLabel(label: String) {
  Text(
    label,
    color = Color(0xFF9AA0AA),
    fontSize = 10.sp,
    letterSpacing = 1.6.sp,
    fontWeight = FontWeight.SemiBold,
  )
}

@Composable
private fun ConnectionCard(
  connection: BigScreenConnection?,
  isCasting: Boolean,
  castDeviceName: String?,
  pairPending: Boolean,
  subtle: Color,
  text: Color,
) {
  val title: String
  val detail: String
  when {
    connection != null -> {
      title = connection.name
      detail = connection.detail
    }
    isCasting -> {
      title = "Cast media route · ${castDeviceName ?: "Cast device"}"
      detail =
        "Existing Cast SDK routing is active. Generic Cast receivers do not receive AURA cinematic visual state."
    }
    pairPending -> {
      title = "Desktop pairing pending"
      detail = "Waiting for the receiver to poll this session before showing connected."
    }
    else -> {
      title = "No external screen connected"
      detail = "Connect a presentation-ready display or pair a desktop receiver."
    }
  }
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(Color(0xFF12141A))
      .padding(14.dp),
  ) {
    Text(title, color = text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    Text(detail, color = subtle, fontSize = 11.sp, lineHeight = 15.sp)
  }
}

@Composable
private fun RemoteModeButton(
  label: String,
  active: Boolean,
  gold: Color,
  text: Color,
  onClick: () -> Unit,
) {
  Button(
    onClick = onClick,
    colors = ButtonDefaults.buttonColors(
      containerColor = if (active) gold else Color(0xFF1A1C22),
      contentColor = if (active) Color.Black else text,
    ),
  ) { Text(label, fontSize = 10.sp) }
}

private fun mediaLine(connection: BigScreenConnection): String =
  when (connection.kind) {
    BigScreenConnectionKind.WIRED_DISPLAY -> "Wired display · ${connection.name}"
    BigScreenConnectionKind.WIRELESS_DISPLAY -> "Wireless display · ${connection.name}"
    BigScreenConnectionKind.DESKTOP_RECEIVER -> "Desktop receiver · ${connection.name}"
  }

private fun formatMs(value: Long): String {
  val total = (value / 1000L).coerceAtLeast(0L)
  return "%d:%02d".format(total / 60L, total % 60L)
}
