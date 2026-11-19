package com.music.echo.codepoetry

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.music.iad1tya.R
import echo.music.iad1tya.ui.component.ImmersiveOverlayEffect
import echo.music.iad1tya.ui.component.safeTopInset
import echo.music.iad1tya.models.MediaMetadata
import echo.music.iad1tya.ui.player.CinematicEngineState
import echo.music.iad1tya.ui.theme.OutfitFontFamily
import echo.music.iad1tya.ui.theme.bbhBartle
import echo.music.iad1tya.utils.makeTimeString

/**
 * Full-screen CODE POETRY surface. It renders the shared [CinematicEngineState]
 * through [CodePoetryRenderer]; there is no second ExoPlayer, lyric parser or
 * animation clock. Every frame is a pure function of the player position, so
 * pause, resume, seek and speed changes all stay in sync automatically.
 */
@Composable
fun CodePoetryOverlay(
  mediaMetadata: MediaMetadata?,
  engine: CinematicEngineState,
  settings: CodePoetrySettingsState,
  positionMs: Long,
  durationMs: Long,
  isPlaying: Boolean,
  isFullscreen: Boolean,
  lowPower: Boolean,
  energy: Float,
  audioReactiveAvailable: Boolean,
  onTogglePlayPause: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onSeek: (Long) -> Unit,
  onDismiss: () -> Unit,
  onOpenSettings: () -> Unit,
  onToggleFullscreen: () -> Unit,
  onRequestAudio: () -> Unit,
) {
  val context = LocalContext.current
  var showControls by remember { mutableStateOf(false) }
  var showMore by remember { mutableStateOf(false) }

  val baseConfig =
    CodePoetryPolicy.config(settings, lowPower || settings.alwaysOn.value)
  // Groq stays backend-side: only the already-parsed, non-degraded analysis is
  // folded into visual direction. It never supplies lyrics or timestamps.
  val analysis = engine.ai?.takeIf { !it.degraded }
  val positionMs = engine.visual.positionMs
  val config =
    if (analysis != null) {
      baseConfig.copy(
        ai =
          CodeAiDirection(
            energyHint = analysis.energyHint,
            confidence = analysis.confidence,
            themeHint = analysis.themeHint?.name,
            sectionName =
              analysis.sections
                .firstOrNull { positionMs in it.startMs..it.endMs }
                ?.name,
            mood = analysis.mood.name,
            available = true,
          ),
      )
    } else {
      baseConfig
    }
  val phrases = remember(engine.words) { CodePoetryPolicy.groupPhrases(engine.words) }
  val frameIndex = (System.currentTimeMillis() / 33L).toInt()
  val frame =
    CodePoetryPolicy.buildFrame(
      phrases = phrases,
      positionMs = engine.visual.positionMs,
      config = config,
      isPlaying = isPlaying,
      energy = energy,
      title = mediaMetadata?.title.orEmpty(),
      artist = mediaMetadata?.artists?.firstOrNull()?.name.orEmpty(),
      frameIndex = frameIndex,
    )

  BackHandler {
    when {
      showMore -> showMore = false
      showControls -> showControls = false
      else -> onDismiss()
    }
  }
  ImmersiveOverlayEffect()

  BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
    Canvas(
      modifier =
        Modifier
          .fillMaxSize()
          .clickable { showControls = !showControls }
    ) {
      drawIntoCanvas { canvas ->
        CodePoetryRenderer.render(
          canvas = canvas.nativeCanvas,
          width = size.width.toInt(),
          height = size.height.toInt(),
          frame = frame,
        )
      }
    }

    AnimatedVisibility(
      visible = showControls,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().safeTopInset(),
    ) {
      Surface(
        color = Color(0xE608090C),
        shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
      ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) {
              Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = "Exit CODE POETRY",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
              )
            }
            Text(
              text = "CODE POETRY  ·  ${frame.composition.label}",
              color = Color(0xFFFF5F57),
              fontSize = 15.sp,
              fontFamily = bbhBartle,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            IconButton(onClick = onTogglePlayPause) {
              Icon(
                painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
              )
            }
            IconButton(onClick = onOpenSettings) {
              Icon(
                painter = painterResource(R.drawable.palette),
                contentDescription = "CODE POETRY settings",
                tint = Color(0xFFFF5F57),
                modifier = Modifier.size(24.dp),
              )
            }
            Box {
              IconButton(onClick = { showMore = !showMore }) {
                Icon(
                  painter = painterResource(R.drawable.more_vert),
                  contentDescription = "More controls",
                  tint = Color.White,
                  modifier = Modifier.size(24.dp),
                )
              }
              DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                DropdownMenuItem(
                  text = { Text("Previous song", fontFamily = OutfitFontFamily) },
                  onClick = { showMore = false; onPrevious() },
                )
                DropdownMenuItem(
                  text = { Text("Next song", fontFamily = OutfitFontFamily) },
                  onClick = { showMore = false; onNext() },
                )
                DropdownMenuItem(
                  text = { Text("Timing −100 ms", fontFamily = OutfitFontFamily) },
                  onClick = { showMore = false; engine.onAdjustTiming(-100L) },
                )
                DropdownMenuItem(
                  text = { Text("Timing +100 ms", fontFamily = OutfitFontFamily) },
                  onClick = { showMore = false; engine.onAdjustTiming(100L) },
                )
                DropdownMenuItem(
                  text = {
                    Text(
                      if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                      fontFamily = OutfitFontFamily,
                    )
                  },
                  onClick = { showMore = false; onToggleFullscreen() },
                )
                if (!audioReactiveAvailable) {
                  DropdownMenuItem(
                    text = { Text("Enable reactive audio", fontFamily = OutfitFontFamily) },
                    onClick = { showMore = false; onRequestAudio() },
                  )
                }
              }
            }
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
          ) {
            Text(
              makeTimeString(positionMs),
              color = Color.White.copy(alpha = 0.85f),
              fontSize = 13.sp,
              fontFamily = OutfitFontFamily,
              fontWeight = FontWeight.Bold,
            )
            Slider(
              value = positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L)).toFloat(),
              valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
              onValueChange = { onSeek(it.toLong()) },
              modifier = Modifier.weight(1f),
            )
            Text(
              makeTimeString(durationMs),
              color = Color.White.copy(alpha = 0.85f),
              fontSize = 13.sp,
              fontFamily = OutfitFontFamily,
              fontWeight = FontWeight.Bold,
            )
          }
          if (!frame.timingVerified) {
            Text(
              "Estimated word timing · use −/+ to correct",
              color = Color(0xFFFFB4A2),
              fontSize = 13.sp,
              fontFamily = OutfitFontFamily,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
          }
        }
      }
    }

    if (!showControls) {
      Text(
        text =
          if (!frame.timingVerified) "Estimated timing · tap for controls"
          else "Tap for controls",
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 13.sp,
        fontFamily = OutfitFontFamily,
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
      )
    }
  }
}
