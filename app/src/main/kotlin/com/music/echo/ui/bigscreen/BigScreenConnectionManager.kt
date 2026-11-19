package echo.music.iad1tya.ui.bigscreen

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import echo.music.iad1tya.constants.BigScreenAskAgainKey
import echo.music.iad1tya.constants.BigScreenAutoConnectTrustedKey
import echo.music.iad1tya.constants.BigScreenTrustedDisplaysKey
import echo.music.iad1tya.utils.rememberPreference
import kotlinx.coroutines.delay

data class ExternalDisplay(val id: Int, val name: String, val isPresentationReady: Boolean)

class BigScreenController(private val activity: Activity) {
  private var presentation: AuraPresentation? = null
  private var displayId: Int? = null

  val isWiredConnected: Boolean
    get() = presentation?.isShowing == true

  val connectedDisplayId: Int?
    get() = displayId

  fun connect(display: ExternalDisplay) {
    if (displayId == display.id && isWiredConnected) return
    disconnectWired(clearSession = false)
    val androidDisplay =
      activity
        .getSystemService(Context.DISPLAY_SERVICE)
        ?.let { it as? DisplayManager }
        ?.getDisplay(display.id) ?: return
    val next =
      AuraPresentation(activity, androidDisplay) {
        disconnectWired(clearSession = true)
      }
    runCatching { next.show() }
      .onFailure {
        next.dismiss()
        return
      }
    presentation = next
    displayId = display.id
    BigScreenSession.setConnection(
      BigScreenConnection(
        kind = BigScreenConnectionKind.WIRED_DISPLAY,
        name = display.name,
        detail = "Wired external display",
      )
    )
  }

  fun disconnectWired(clearSession: Boolean = true) {
    presentation?.dismiss()
    presentation = null
    displayId = null
    if (clearSession && BigScreenSession.connection.value?.kind == BigScreenConnectionKind.WIRED_DISPLAY) {
      BigScreenSession.setConnection(null)
    }
  }

  fun release() {
    disconnectWired(clearSession = true)
  }
}

@Composable
fun rememberBigScreenController(): BigScreenController {
  val context = LocalContext.current
  val controller = remember(context) { BigScreenController(context as Activity) }
  DisposableEffect(controller) {
    onDispose { controller.release() }
  }
  return controller
}

@Composable
fun rememberExternalDisplays(): List<ExternalDisplay> {
  val context = LocalContext.current
  var displays by remember { mutableStateOf(emptyList<ExternalDisplay>()) }
  DisposableEffect(context) {
    val manager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    fun refresh() {
      displays =
        manager?.displays.orEmpty().filter { it.isRealExternalDisplay() }.map { display ->
          ExternalDisplay(
            id = display.displayId,
            name = display.displayName(),
            isPresentationReady =
              display.flags and Display.FLAG_PRESENTATION != 0 &&
                display.state == Display.STATE_ON,
          )
        }
    }
    val listener =
      object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = refresh()
      }
    manager?.registerDisplayListener(listener, null)
    refresh()
    onDispose { manager?.unregisterDisplayListener(listener) }
  }
  return displays
}

private fun Display.isRealExternalDisplay(): Boolean {
  if (displayId == Display.DEFAULT_DISPLAY) return false
  // FLAG_PRESENTATION is the reliable public signal that Android can place a
  // Presentation here. Charging/data-only USB connections never set it.
  return flags and Display.FLAG_PRESENTATION != 0
}

private fun Display.displayName(): String =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    name.ifBlank { "External display" }
  } else {
    "External display"
  }

@Composable
fun BigScreenConnectionManager(
  controller: BigScreenController,
  enabled: Boolean = true,
) {
  val displays = rememberExternalDisplays()
  var askAgain by rememberPreference(BigScreenAskAgainKey, true)
  var autoConnect by rememberPreference(BigScreenAutoConnectTrustedKey, false)
  var trusted by rememberPreference(BigScreenTrustedDisplaysKey, emptySet<String>())
  var pending by remember { mutableStateOf<ExternalDisplay?>(null) }
  var dontAskAgain by remember { mutableStateOf(false) }
  var trustDevice by remember { mutableStateOf(false) }

  LaunchedEffect(displays, enabled, askAgain, autoConnect, trusted) {
    // Give the display stack a moment to settle after an adapter is plugged in.
    delay(450L)
    if (!enabled || displays.isEmpty()) {
      if (controller.isWiredConnected && displays.isEmpty()) controller.disconnectWired()
      return@LaunchedEffect
    }
    val wired = BigScreenSession.connection.value?.kind == BigScreenConnectionKind.WIRED_DISPLAY
    if (wired) {
      val stillPresent = displays.any { it.id.toString() == trusted.firstOrNull() }
      if (!stillPresent && displays.none { it.id == controller.connectedDisplayId }) {
        controller.disconnectWired()
      }
      return@LaunchedEffect
    }
    val candidate = displays.firstOrNull { it.isPresentationReady } ?: displays.first()
    if (autoConnect && trusted.contains(candidate.id.toString())) {
      controller.connect(candidate)
    } else if (askAgain) {
      pending = candidate
    }
  }

  val display = pending
  if (display != null && enabled) {
    BigScreenPromptDialog(
      displayName = display.name,
      dontAskAgain = dontAskAgain,
      autoTrust = trustDevice,
      onDontAskAgainChange = { dontAskAgain = it },
      onAutoTrustChange = { trustDevice = it },
      onConnect = {
        if (trustDevice) trusted = trusted + display.id.toString()
        if (dontAskAgain) askAgain = false
        controller.connect(display)
        pending = null
      },
      onDismiss = {
        if (dontAskAgain) askAgain = false
        if (trustDevice) trusted = trusted + display.id.toString()
        pending = null
      },
    )
  }
}

@Composable
private fun BigScreenPromptDialog(
  displayName: String,
  dontAskAgain: Boolean,
  autoTrust: Boolean,
  onDontAskAgainChange: (Boolean) -> Unit,
  onAutoTrustChange: (Boolean) -> Unit,
  onConnect: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        "Want to listen and see on the big screen?",
        style = MaterialTheme.typography.titleMedium,
      )
    },
    text = {
      androidx.compose.foundation.layout.Column {
        Text(
          "Enjoy your music with cinematic lyrics and immersive visuals.",
          style = MaterialTheme.typography.bodyMedium,
        )
        Text("Detected: $displayName", style = MaterialTheme.typography.bodySmall)
        RowCheck("Don't ask again", dontAskAgain, onDontAskAgainChange)
        RowCheck("Automatically connect to trusted devices", autoTrust, onAutoTrustChange)
      }
    },
    confirmButton = {
      TextButton(onClick = onConnect) { Text("YES, CONNECT") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("NOT NOW") }
    },
  )
}

@Composable
private fun RowCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  androidx.compose.foundation.layout.Row(
    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
  ) {
    Checkbox(checked = checked, onCheckedChange = onChange)
    Text(label, style = MaterialTheme.typography.bodySmall)
  }
}
