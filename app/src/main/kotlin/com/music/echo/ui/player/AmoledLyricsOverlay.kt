package echo.music.iad1tya.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.music.iad1tya.R
import echo.music.iad1tya.models.MediaMetadata
import echo.music.iad1tya.ui.component.ImmersiveOverlayEffect
import echo.music.iad1tya.ui.component.safeTopInset
import echo.music.iad1tya.utils.makeTimeString
import kotlin.math.abs

/**
 * True-black lyrics renderer. It deliberately shares the cinematic timing and
 * theme state instead of starting another player or lyric engine.
 *
 * Low power mode removes glow/particles/background work and lets the caller poll
 * position less often. All other modes keep a restrained accent but still never
 * paint a bright backdrop.
 */
@Composable
fun AmoledLyricsOverlay(
  mediaMetadata: MediaMetadata?,
  engine: CinematicEngineState,
  settings: CinematicSettingsState,
  positionMs: Long,
  durationMs: Long,
  onSeek: (Long) -> Unit,
  onDismiss: () -> Unit,
  onOpenInstaLrc: () -> Unit,
) {
  val lowPower = settings.amoledLowPower.value
  val visual = remember(engine.visual, lowPower) { AmoledLyricsPolicy.sanitize(engine.visual, lowPower) }
  val currentOnSeek = rememberUpdatedState(onSeek)
  var showControls by remember { mutableStateOf(false) }
  var showSettings by remember { mutableStateOf(false) }

  BackHandler {
    when {
      showSettings -> showSettings = false
      showControls -> showControls = false
      else -> onDismiss()
    }
  }
  ImmersiveOverlayEffect()

  BoxWithConstraints(
    modifier =
      Modifier
        .fillMaxSize()
        .background(Color.Black),
  ) {
    // Only present while controls are hidden. Once controls are visible this
    // layer disappears, so the seek bar and buttons receive taps directly.
    if (!showControls) {
      Box(modifier = Modifier.fillMaxSize().clickable { showControls = true })
    }

    if (engine.words.isEmpty()) {
      Text(
        text =
          if (mediaMetadata != null) "Instrumental · ${mediaMetadata.title}"
          else "Waiting for playback…",
        color = Color.White.copy(alpha = 0.55f),
        fontStyle = FontStyle.Italic,
        fontSize = 17.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
      )
    } else {
      CinematicLyricsLayer(
        visual = visual,
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
            contentDescription = "Close AMOLED lyrics",
            tint = Color.White,
            modifier = Modifier.size(22.dp),
          )
        }
        Spacer(Modifier.width(6.dp))
        TextButton(onClick = { showControls = false }) {
          Text("Hide", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = { showSettings = true }) {
          Text("Aa", color = Color(0xFFE8C77B), fontSize = 18.sp)
        }
        Spacer(Modifier.width(6.dp))
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
        color = Color.White.copy(alpha = 0.32f),
        fontSize = 10.sp,
        modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 16.dp, vertical = 58.dp),
      )
    }

    Column(
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
      if (showControls) {
        Text(
          text = mediaMetadata?.title.orEmpty(),
          color = Color.White.copy(alpha = 0.85f),
          fontSize = 13.sp,
          maxLines = 1,
        )
        val artist = mediaMetadata?.artists?.firstOrNull()?.name.orEmpty()
        if (artist.isNotBlank()) {
          Text(
            text = artist,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            maxLines = 1,
          )
        }
        Spacer(Modifier.height(10.dp))
      }

      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.16f))
            // A single consuming gesture handler owns this area. Consuming both
            // down and up stops the parent tap-to-toggle layer, while the release
            // position still gives an accurate seek offset.
            .pointerInput(durationMs) {
              awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                val up = waitForUpOrCancellation()
                if (up != null) {
                  up.consume()
                  val stayedOnBar =
                    abs(up.position.x - down.position.x) < 64f &&
                      abs(up.position.y - down.position.y) < 64f
                  if (stayedOnBar && durationMs > 0L) {
                    currentOnSeek.value(
                      AmoledLyricsPolicy.seekPosition(up.position.x, size.width, durationMs)
                    )
                  }
                }
              }
            },
      ) {
        if (durationMs > 0) {
          Box(
            modifier =
              Modifier
                .fillMaxWidth((positionMs.coerceIn(0L, durationMs).toFloat() / durationMs.toFloat()))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFE8C77B)),
          )
        }
      }

      if (showControls) {
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
          Text(makeTimeString(positionMs), color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
          Text(
            if (lowPower) "AMOLED · Low power" else "AMOLED",
            color = Color.White.copy(alpha = 0.32f),
            fontSize = 10.sp,
          )
          Text(makeTimeString(durationMs), color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
        }
      }
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
