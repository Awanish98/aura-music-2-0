package echo.music.iad1tya.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.music.iad1tya.R
import echo.music.iad1tya.models.MediaMetadata
import echo.music.iad1tya.ui.component.ImmersiveOverlayEffect
import echo.music.iad1tya.ui.component.safeTopInset

/**
 * Full-screen cinematic lyrics. All timing, theme and audio decisions come from
 * [CinematicEngineState], which is shared with Big Screen mode so there is only
 * one audio capture and one theme selection per song.
 */
@Composable
fun CinematicLyricsOverlay(
  mediaMetadata: MediaMetadata?,
  engine: CinematicEngineState,
  settings: CinematicSettingsState,
  onDismiss: () -> Unit,
  onOpenInstaLrc: () -> Unit,
) {
  var showControls by remember { mutableStateOf(false) }
  var showSettings by remember { mutableStateOf(false) }

  BackHandler {
    if (showSettings) showSettings = false else onDismiss()
  }
  ImmersiveOverlayEffect()

  BoxWithConstraints(
    modifier =
      Modifier
        .fillMaxSize()
        .background(androidx.compose.ui.graphics.Color.Black)
        .clickable { showControls = !showControls }
  ) {
    CinematicBackdrop(engine.visual, Modifier.fillMaxSize())
    CinematicParticles(engine.visual, Modifier.fillMaxSize())

    if (engine.words.isEmpty()) {
      CinematicInstrumentalScene(engine.visual, Modifier.fillMaxSize())
      Text(
        text =
          if (mediaMetadata != null) "Instrumental · ${mediaMetadata.title}"
          else "Waiting for playback…",
        color = Color(engine.visual.theme.text.toInt()).copy(alpha = 0.7f),
        fontStyle = FontStyle.Italic,
        fontSize = 18.sp,
        modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
      )
    } else {
      CinematicLyricsLayer(
        visual = engine.visual,
        words = engine.words,
        screenWidth = maxWidth,
        screenHeight = maxHeight,
        modifier = Modifier.fillMaxSize(),
      )
    }

    AnimatedVisibility(
      visible = showControls,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.TopStart).safeTopInset().padding(12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDismiss) {
          Icon(
            painter = painterResource(R.drawable.close),
            contentDescription = "Close cinematic lyrics",
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(24.dp),
          )
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = { showSettings = true }) {
          Text("Aa", color = Color(0xFFE8C77B), fontSize = 18.sp)
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = { engine.onAdjustTiming(-100L) }) {
          Text("−", color = Color.White, fontSize = 22.sp)
        }
        IconButton(onClick = { engine.onAdjustTiming(100L) }) {
          Text("+", color = Color.White, fontSize = 22.sp)
        }
      }
    }

    if (!engine.timingVerified && !showControls) {
      Text(
        text = "Approx. word timing · tap for controls",
        color = Color.White.copy(alpha = 0.35f),
        fontSize = 10.sp,
        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
      )
    }

    AnimatedVisibility(visible = showSettings, enter = fadeIn(), exit = fadeOut()) {
      CinematicSettingsPanel(
        settings = settings,
        engine = engine,
        onDismiss = { showSettings = false },
        onOpenInstaLrc = {
          showSettings = false
          onOpenInstaLrc()
        },
      )
    }
  }
}
