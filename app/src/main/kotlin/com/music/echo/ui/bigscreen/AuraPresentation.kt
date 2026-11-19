package echo.music.iad1tya.ui.bigscreen

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Real Android Presentation on an external Display. A USB-C charging cable or
 * file-transfer connection never creates a Presentation, so this cannot falsely
 * report a video connection.
 */
class AuraPresentation(
  context: Context,
  display: Display,
  private val onDisplayGone: () -> Unit,
) : Presentation(context, display) {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val composeView =
      ComposeView(context).apply {
        setViewTreeLifecycleOwner(context as? LifecycleOwner)
        setViewTreeSavedStateRegistryOwner(context as? SavedStateRegistryOwner)
        setContent { AuraBigScreenRenderer() }
      }
    setContentView(composeView)
  }

  override fun onDisplayRemoved() {
    super.onDisplayRemoved()
    onDisplayGone()
  }
}
