package com.music.echo.insta

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import echo.music.iad1tya.R
import echo.music.iad1tya.models.MediaMetadata
import echo.music.iad1tya.ui.component.ImmersiveOverlayEffect
import echo.music.iad1tya.ui.component.safeTopInset
import echo.music.iad1tya.ui.player.CinematicEngineState
import echo.music.iad1tya.ui.theme.OutfitFontFamily
import echo.music.iad1tya.ui.theme.bbhBartle
import echo.music.iad1tya.utils.makeTimeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Full-screen INSTA LRC PLAY surface. It renders the shared [CinematicEngineState]
 * through [InstaFrameRenderer]; there is no second ExoPlayer, lyric parser or
 * animation clock. Every frame is a pure function of the player position.
 */
@Composable
fun InstaLrcOverlay(
  mediaMetadata: MediaMetadata?,
  engine: CinematicEngineState,
  settings: InstaLrcSettingsState,
  positionMs: Long,
  durationMs: Long,
  isPlaying: Boolean,
  isFullscreen: Boolean,
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
  var showStyles by remember { mutableStateOf(false) }
  var showMore by remember { mutableStateOf(false) }
  var backgroundBitmap by remember { mutableStateOf<Bitmap?>(null) }

  val instaTimeMs by
    rememberUpdatedState(engine.visual.positionMs + settings.timingOffsetMs.value)
  val reaction =
    if (settings.safeMode.value || !audioReactiveAvailable) {
      InstaReactionInput(energy = 0f, silence = true)
    } else {
      InstaLrcPresentation.reactionFor(engine)
    }
  val presentation =
    InstaLrcPresentation.buildSpec(
      engine = engine,
      settings = settings,
      title = mediaMetadata?.title.orEmpty(),
      artist = mediaMetadata?.artists?.firstOrNull()?.name.orEmpty(),
      artworkUrl = mediaMetadata?.thumbnailUrl,
      reaction = reaction,
    )
  val spec = presentation.spec
  val preset = spec.preset

  // The overlay lives in its own Dialog window, so hide the bars on that window
  // (not just the Activity's) and keep the top controls clear on transient reveal.
  ImmersiveOverlayEffect()

  BackHandler {
    when {
      showStyles -> showStyles = false
      showMore -> showMore = false
      showControls -> showControls = false
      else -> onDismiss()
    }
  }

  val imageUri =
    when (preset.background) {
      InstaBackground.CUSTOM_IMAGE -> settings.imageUri.value
      InstaBackground.CUSTOM_VIDEO -> settings.videoUri.value
      InstaBackground.ALBUM_ARTWORK -> mediaMetadata?.thumbnailUrl.orEmpty()
      else -> ""
    }
  val backgroundKey = "${preset.background.name}|$imageUri"
  LaunchedEffect(backgroundKey, audioReactiveAvailable) {
    if (imageUri.isBlank()) {
      backgroundBitmap = null
      return@LaunchedEffect
    }
    if (preset.background == InstaBackground.CUSTOM_VIDEO) {
      val retriever = MediaMetadataRetriever()
      try {
        withContext(Dispatchers.IO) {
          retriever.setDataSource(context, Uri.parse(imageUri))
        }
        while (isActive) {
          val frameMs = instaTimeMs.coerceAtLeast(0L)
          val frame =
            withContext(Dispatchers.IO) {
              retriever.getFrameAtTime(frameMs * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
          if (frame != null) backgroundBitmap = frame
          delay(if (preset.lowPower) 900L else 350L)
        }
      } finally {
        withContext(Dispatchers.IO) { retriever.release() }
      }
    } else {
      val request =
        ImageRequest.Builder(context)
          .data(imageUri)
          .size(720, 1280)
          .allowHardware(false)
          .build()
      backgroundBitmap =
        runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull()
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
    Canvas(
      modifier =
        Modifier
          .fillMaxSize()
          .clickable { showControls = !showControls }
    ) {
      drawIntoCanvas { canvas ->
        InstaFrameRenderer.render(
          canvas = canvas.nativeCanvas,
          width = size.width.toInt(),
          height = size.height.toInt(),
          timeMs = instaTimeMs,
          spec = spec,
          reaction = reaction,
          backgroundBitmap = backgroundBitmap,
          context = context,
        )
      }
    }

    AnimatedVisibility(
      visible = showControls,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
    ) {
      Surface(
        color = Color(0xE608090C),
        shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
        modifier = Modifier.safeTopInset(),
      ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) {
              Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = "Exit INSTA LRC PLAY",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
              )
            }
            Text(
              text = presentation.status,
              color = Color(0xFFE8C77B),
              fontSize = 16.sp,
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
            IconButton(onClick = { showStyles = !showStyles }) {
              Icon(
                painter = painterResource(R.drawable.palette),
                contentDescription = "Choose INSTA style",
                tint = Color(0xFFE8C77B),
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
              DropdownMenu(
                expanded = showMore,
                onDismissRequest = { showMore = false },
              ) {
                DropdownMenuItem(
                  text = {
                    Text(
                      "Previous song",
                      fontSize = 15.sp,
                      fontFamily = OutfitFontFamily,
                    )
                  },
                  onClick = { showMore = false; onPrevious() },
                )
                DropdownMenuItem(
                  text = {
                    Text(
                      "Next song",
                      fontSize = 15.sp,
                      fontFamily = OutfitFontFamily,
                    )
                  },
                  onClick = { showMore = false; onNext() },
                )
                DropdownMenuItem(
                  text = {
                    Text(
                      "Timing −100 ms",
                      fontSize = 15.sp,
                      fontFamily = OutfitFontFamily,
                    )
                  },
                  onClick = { showMore = false; engine.onAdjustTiming(-100L) },
                )
                DropdownMenuItem(
                  text = {
                    Text(
                      "Timing +100 ms",
                      fontSize = 15.sp,
                      fontFamily = OutfitFontFamily,
                    )
                  },
                  onClick = { showMore = false; engine.onAdjustTiming(100L) },
                )
                DropdownMenuItem(
                  text = {
                    Text(
                      "Typography & effects",
                      fontSize = 15.sp,
                      fontFamily = OutfitFontFamily,
                    )
                  },
                  onClick = { showMore = false; onOpenSettings() },
                )
                DropdownMenuItem(
                  text = {
                    Text(
                      if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                      fontSize = 15.sp,
                      fontFamily = OutfitFontFamily,
                    )
                  },
                  onClick = { showMore = false; onToggleFullscreen() },
                )
              }
            }
          }
          if (!audioReactiveAvailable) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "Audio-reactive effects off · timing still follows playback",
                color = Color(0xFFFFB4A2),
                fontSize = 15.sp,
                fontFamily = OutfitFontFamily,
                maxLines = 1,
                modifier = Modifier.weight(1f),
              )
              TextButton(onClick = onRequestAudio) {
                Text(
                  "Enable",
                  color = Color(0xFFFFB4A2),
                  fontSize = 15.sp,
                  fontFamily = OutfitFontFamily,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        }
      }
    }

    AnimatedVisibility(
      visible = showStyles,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.Center),
    ) {
      Surface(
        color = Color(0xF2101116),
        shape = RoundedCornerShape(20.dp),
        modifier =
          Modifier
            .padding(24.dp)
            .fillMaxWidth(0.82f)
            .fillMaxHeight(0.78f),
      ) {
        Column(
          Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            "CHOOSE A LOOK",
            color = Color(0xFFE8C77B),
            fontSize = 20.sp,
            fontFamily = bbhBartle,
            fontWeight = FontWeight.Bold,
          )
          Text(
            "AUTO still adapts the final style to the song.",
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 14.sp,
            fontFamily = OutfitFontFamily,
          )
          Spacer(Modifier.height(4.dp))
          settings.allPresets.forEach { item ->
            val active = item.name == preset.name
            Button(
              onClick = {
                settings.applyPreset(item)
                showStyles = false
              },
              modifier = Modifier.fillMaxWidth(),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = if (active) Color(0xFFE8C77B) else Color(0xFF24262D),
                  contentColor = if (active) Color.Black else Color.White,
                ),
            ) {
              Text(
                item.name,
                fontSize = 16.sp,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
              )
            }
          }
          Spacer(Modifier.height(2.dp))
          Text(
            "Shared cinematic theme: ${engine.theme.displayName}",
            color = Color.White.copy(alpha = 0.68f),
            fontSize = 14.sp,
            fontFamily = OutfitFontFamily,
          )
        }
      }
    }

    AnimatedVisibility(
      visible = showControls,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(Color(0xD608090C))
            .padding(horizontal = 20.dp, vertical = 10.dp),
      ) {
        Slider(
          value = positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L)).toFloat(),
          valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
          onValueChange = { onSeek(it.toLong()) },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(
            makeTimeString(positionMs),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.Bold,
          )
          if (!engine.timingVerified && settings.showEstimatedHint.value) {
            Text(
              "Estimated timings · −/+ corrects cached timing",
              color = Color(0xFFFFB4A2),
              fontSize = 14.sp,
              fontFamily = OutfitFontFamily,
            )
          }
          Text(
            makeTimeString(durationMs),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }

    if (!showControls) {
      Text(
        text =
          if (!engine.timingVerified && settings.showEstimatedHint.value) {
            "Estimated word timing · tap for controls"
          } else {
            "Tap for controls"
          },
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 14.sp,
        fontFamily = OutfitFontFamily,
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
      )
    }
  }
}
