package echo.music.iad1tya.ui.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Makes a full-screen Dialog overlay genuinely immersive.
 *
 * The lyric/visualiser overlays are hosted in Compose [androidx.compose.ui.window.Dialog]s
 * with `decorFitsSystemWindows = false`. Those dialogs own a separate window, so
 * hiding the status bar on the Activity window is not enough: the dialog still
 * draws the notification bar over its top controls. The inset controller is
 * therefore taken from the local view, which belongs to the dialog window.
 *
 * Bars are hidden with transient-by-swipe behaviour, which is the standard
 * immersive-mode contract: they stay out of the way, and a deliberate swipe from
 * the screen edge can still reveal them. [safeTopInset] keeps the controls
 * tappable during that transient reveal.
 */
@Composable
fun ImmersiveOverlayEffect() {
  val view = LocalView.current
  DisposableEffect(view) {
    val controller = ViewCompat.getWindowInsetsController(view)
    if (controller != null) {
      controller.hide(WindowInsetsCompat.Type.systemBars())
      controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    onDispose {
      if (controller != null) {
        controller.show(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
      }
    }
  }
}

/**
 * Top padding that keeps a top-anchored control clear of the status bar and any
 * display cut-out when the system bars are shown transiently.
 */
@Composable
fun Modifier.safeTopInset(): Modifier =
  this
    .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
