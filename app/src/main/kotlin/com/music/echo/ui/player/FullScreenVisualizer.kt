package echo.music.iad1tya.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.music.iad1tya.R
import echo.music.iad1tya.cinematic.AudioFeatures
import echo.music.iad1tya.models.MediaMetadata
import echo.music.iad1tya.ui.component.ImmersiveOverlayEffect
import echo.music.iad1tya.ui.component.frostedGlassControl
import echo.music.iad1tya.ui.component.safeTopInset
import echo.music.iad1tya.ui.theme.OutfitFontFamily
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Animation styles available in the full-screen visualizer. */
enum class FullVisualizerStyle(val label: String) {
  AUTO("AUTO"),
  SPECTRUM("SPECTRUM"),
  RADIAL("RADIAL"),
  WAVE("WAVE"),
  PARTICLES("PARTICLES"),
  RINGS("RINGS"),
  AURORA("AURORA"),
  THREE_D("3D PATTERN"),
}

/** Keeps the canvas moving when capture is unavailable, without faking sync. */
internal fun simulatedVisualFeatures(t: Float): AudioFeatures {
  val beat = (0.5f + 0.5f * sin(t * 5.2f)).let { it * it }
  return AudioFeatures(
    bass = 0.28f + beat * 0.42f,
    mid = 0.3f + 0.16f * sin(t * 1.7f),
    treble = 0.22f + 0.12f * sin(t * 2.3f),
    energy = 0.42f + 0.18f * sin(t * 0.9f),
    bpm = 120f,
    beat = beat,
    kick = beat,
    snare = 0.18f + 0.12f * sin(t * 2.6f),
    drop = if (t % 4.6f < 0.22f) 1f else 0f,
    onset = beat,
    silence = false,
    intensity = 0.48f + beat * 0.3f,
  )
}

internal fun simulatedVisualSpectrum(t: Float, bars: Int): FloatArray {
  val count = bars.coerceAtLeast(1)
  return FloatArray(count) { i ->
    val fall = kotlin.math.exp(-i / 18f)
    val beat = 0.5f + 0.5f * sin(t * 5.2f)
    (0.08f + 0.62f * fall + 0.3f * beat * fall * (0.7f + 0.3f * sin(i * 0.31f + t * 2f)))
      .coerceIn(0.04f, 1f)
  }
}

/**
 * Full-screen, beat-synced visualizer.
 *
 * It reads the same [CinematicAudioState] that drives the cinematic lyrics:
 * [AudioFeatures.beat], bass/kick/energy and the FFT spectrum all come from a
 * single Visualizer attached to the existing player's audio session. There is
 * no second player, no second clock and no lyric text invented here.
 */
@Composable
fun FullScreenVisualizer(
  audio: CinematicAudioState,
  mediaMetadata: MediaMetadata?,
  isPlaying: Boolean,
  isFullscreen: Boolean,
  audioReactiveAvailable: Boolean,
  onRequestAudio: () -> Unit,
  onTogglePlayPause: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onToggleFullscreen: () -> Unit,
  onDismiss: () -> Unit,
) {
  var styleIndex by rememberSaveable { mutableIntStateOf(0) }
  var frameTime by remember { mutableLongStateOf(0L) }

  // Hide bars on this Dialog window so the top controls are never covered.
  ImmersiveOverlayEffect()

  LaunchedEffect(Unit) {
    var lastPublished = 0L
    while (true) {
      withFrameMillis { now ->
        if (now - lastPublished >= 32L) {
          lastPublished = now
          frameTime = now
        }
      }
    }
  }

  val frameSeconds = frameTime / 1000f
  val simulatedSeconds = if (isPlaying) frameSeconds else 0f
  val features = if (audioReactiveAvailable) audio.value else simulatedVisualFeatures(simulatedSeconds)
  val spectrum =
    if (audioReactiveAvailable) {
      audio.spectrum
    } else {
      simulatedVisualSpectrum(simulatedSeconds, audio.spectrum.size)
    }
  val activeStyleIndex =
    if (styleIndex == 0) {
      1 + (frameTime / 9000L).rem(FullVisualizerStyle.entries.size - 1L).toInt()
    } else {
      styleIndex
    }
  val activeStyle = FullVisualizerStyle.entries[activeStyleIndex]

  Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawBackdrop(frameSeconds, features, activeStyle)
      when (activeStyle) {
        FullVisualizerStyle.AUTO,
        FullVisualizerStyle.SPECTRUM -> drawSpectrum(frameSeconds, features, spectrum)
        FullVisualizerStyle.RADIAL -> drawRadialPulse(frameSeconds, features, spectrum)
        FullVisualizerStyle.WAVE -> drawWaveFlow(frameSeconds, features, spectrum)
        FullVisualizerStyle.PARTICLES -> drawParticles(frameSeconds, features, spectrum)
        FullVisualizerStyle.RINGS -> drawBeatRings(frameSeconds, features)
        FullVisualizerStyle.AURORA -> drawAurora(frameSeconds, features)
        FullVisualizerStyle.THREE_D -> drawThreeDPattern(frameSeconds, features, spectrum)
      }
    }

    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .safeTopInset()
          .padding(horizontal = 20.dp, vertical = 18.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
          text = "AURA VISUALIZER",
          color = Color.White,
          fontSize = 15.sp,
          fontFamily = OutfitFontFamily,
          fontWeight = FontWeight.Bold,
          letterSpacing = 2.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
          text = activeStyle.label,
          color = Color.White.copy(alpha = 0.72f),
          fontSize = 12.sp,
          fontFamily = OutfitFontFamily,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
          onClick = onToggleFullscreen,
          modifier = Modifier.frostedGlassControl(CircleShape, fallbackColor = Color.Transparent),
        ) {
          Icon(
            painter =
              painterResource(
                if (isFullscreen) R.drawable.fullscreen_exit else R.drawable.fullscreen
              ),
            contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
            tint = Color.White,
            modifier = Modifier.size(22.dp),
          )
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.frostedGlassControl(CircleShape, fallbackColor = Color.Transparent),
        ) {
          Icon(
            painter = painterResource(R.drawable.close),
            contentDescription = "Close visualizer",
            tint = Color.White,
            modifier = Modifier.size(22.dp),
          )
        }
      }

      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (!audioReactiveAvailable) {
          Text(
            text = "Simulated beat · tap for live mic sync",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontFamily = OutfitFontFamily,
            modifier =
              Modifier
                .clip(RoundedCornerShape(50))
                .frostedGlassControl(
                  RoundedCornerShape(50),
                  fallbackColor = Color.White.copy(alpha = 0.08f),
                )
                .clickable(onClick = onRequestAudio)
                .padding(horizontal = 12.dp, vertical = 5.dp),
          )
          Spacer(Modifier.height(12.dp))
        }

        Text(
          text = mediaMetadata?.title.orEmpty(),
          color = Color.White,
          fontSize = 18.sp,
          fontFamily = OutfitFontFamily,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
          maxLines = 1,
        )
        Text(
          text = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
          color = Color.White.copy(alpha = 0.62f),
          fontSize = 12.sp,
          fontFamily = OutfitFontFamily,
          textAlign = TextAlign.Center,
          maxLines = 1,
        )

        Spacer(Modifier.height(14.dp))

        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FullVisualizerStyle.entries.forEachIndexed { index, style ->
            val selected = index == styleIndex
            Text(
              text = style.label,
              color =
                if (selected) Color.White
                else Color.White.copy(alpha = 0.6f),
              fontSize = 11.sp,
              fontFamily = OutfitFontFamily,
              fontWeight = FontWeight.Bold,
              modifier =
                Modifier
                  .clip(RoundedCornerShape(50))
                  .then(
                    if (selected) {
                      Modifier.background(Color(0xFFFF5F57))
                    } else {
                      Modifier.frostedGlassControl(
                        RoundedCornerShape(50),
                        fallbackColor = Color.White.copy(alpha = 0.1f),
                      )
                    }
                  )
                  .clickable { styleIndex = index }
                  .padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
        }

        Spacer(Modifier.height(18.dp))

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
          IconButton(
            onClick = onPrevious,
            modifier = Modifier.frostedGlassControl(CircleShape, fallbackColor = Color.Transparent),
          ) {
            Icon(
              painter = painterResource(R.drawable.skip_previous),
              contentDescription = "Previous song",
              tint = Color.White,
              modifier = Modifier.size(26.dp),
            )
          }
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier
                .size(64.dp)
                .clip(CircleShape)
                .frostedGlassControl(CircleShape, fallbackColor = Color.White.copy(alpha = 0.18f))
                .clickable(onClick = onTogglePlayPause),
          ) {
            Icon(
              painter =
                painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
              contentDescription = if (isPlaying) "Pause" else "Play",
              tint = Color.White,
              modifier = Modifier.size(28.dp),
            )
          }
          IconButton(
            onClick = onNext,
            modifier = Modifier.frostedGlassControl(CircleShape, fallbackColor = Color.Transparent),
          ) {
            Icon(
              painter = painterResource(R.drawable.skip_next),
              contentDescription = "Next song",
              tint = Color.White,
              modifier = Modifier.size(26.dp),
            )
          }
        }
        Spacer(Modifier.height(8.dp))
      }
    }
  }
}

private fun DrawScope.drawBackdrop(
  t: Float,
  features: AudioFeatures,
  style: FullVisualizerStyle,
) {
  val glow =
    when (style) {
      FullVisualizerStyle.AURORA -> Color(0xFF1B2A6B)
      FullVisualizerStyle.RINGS -> Color(0xFF4A1236)
      FullVisualizerStyle.PARTICLES -> Color(0xFF123A3F)
      FullVisualizerStyle.THREE_D -> Color(0xFF102046)
      else -> Color(0xFF241235)
    }
  val pulse = 0.55f + features.energy * 0.45f + features.beat * 0.2f
  drawRect(
    brush =
      Brush.radialGradient(
        colors =
          listOf(
            glow.copy(alpha = min(0.85f, 0.35f * pulse)),
            Color(0xFF09070E),
            Color.Black,
          ),
        center = Offset(size.width * 0.5f, size.height * 0.45f),
        radius = max(size.width, size.height) * 0.85f,
      )
  )
  drawRect(color = Color.Black.copy(alpha = 0.18f + sin(t * 0.4f) * 0.03f))
}

private fun DrawScope.drawSpectrum(
  t: Float,
  features: AudioFeatures,
  spectrum: FloatArray,
) {
  val bars = spectrum.size.coerceAtLeast(1)
  val slot = size.width / bars
  val barWidth = (slot * 0.58f).coerceAtLeast(2f)
  val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
  val maxHeight = size.height * 0.46f
  val brush =
    Brush.verticalGradient(
      colors =
        listOf(
          Color(0xFFFF5F57),
          Color(0xFFB45CFF),
          Color(0xFF5CE1FF),
        ),
      startY = size.height,
      endY = size.height * 0.25f,
    )
  for (i in 0 until bars) {
    val level = spectrum[i].coerceIn(0.04f, 1f)
    val shimmer = 0.86f + 0.14f * sin(t * 3f + i * 0.37f)
    val barHeight = maxHeight * level * shimmer * (0.85f + features.energy * 0.4f)
    val x = i * slot + (slot - barWidth) / 2f
    drawRoundRect(
      brush = brush,
      topLeft = Offset(x, size.height - barHeight),
      size = Size(barWidth, barHeight),
      cornerRadius = corner,
    )
    drawRoundRect(
      brush = brush,
      topLeft = Offset(x, 0f),
      size = Size(barWidth, barHeight * 0.42f),
      cornerRadius = corner,
    )
  }
}

private fun DrawScope.drawRadialPulse(
  t: Float,
  features: AudioFeatures,
  spectrum: FloatArray,
) {
  val cx = size.width / 2f
  val cy = size.height * 0.46f
  val base = min(size.width, size.height) * 0.14f
  val radius = base * (1f + features.bass * 0.45f + features.kick * 0.35f + features.beat * 0.18f)
  drawCircle(
    brush =
      Brush.radialGradient(
        colors =
          listOf(
            Color(0xFFFF5F57).copy(alpha = 0.55f + features.energy * 0.35f),
            Color(0xFF7C3AED).copy(alpha = 0.22f),
            Color.Transparent,
          ),
        center = Offset(cx, cy),
        radius = radius * 1.8f,
      ),
    radius = radius * 1.8f,
    center = Offset(cx, cy),
  )
  drawCircle(
    color = Color.White.copy(alpha = 0.16f + features.beat * 0.4f),
    radius = radius,
    center = Offset(cx, cy),
    style = Stroke(width = 3.dp.toPx()),
  )

  val count = spectrum.size.coerceAtLeast(8)
  for (i in 0 until count) {
    val angle = (i.toFloat() / count) * 2f * Math.PI.toFloat() + t * 0.12f
    val level = spectrum[i].coerceIn(0.04f, 1f)
    val inner = radius * 1.12f
    val outer = inner + min(size.width, size.height) * 0.16f * level
    drawLine(
      color =
        if (i % 3 == 0) Color(0xFFFF5F57) else Color(0xFF8B5CF6),
      start = Offset(cx + cos(angle) * inner, cy + sin(angle) * inner),
      end = Offset(cx + cos(angle) * outer, cy + sin(angle) * outer),
      strokeWidth = (2f + level * 5f).coerceAtMost(8f),
      cap = StrokeCap.Round,
    )
  }
}

private fun DrawScope.drawWaveFlow(
  t: Float,
  features: AudioFeatures,
  spectrum: FloatArray,
) {
  val lines = 4
  val step = size.width / 90f
  for (line in 0 until lines) {
    val color =
      when (line % 4) {
        0 -> Color(0xFFFF5F57)
        1 -> Color(0xFFB45CFF)
        2 -> Color(0xFF5CE1FF)
        else -> Color(0xFFF9C74F)
      }
    val baseline =
      size.height * (0.3f + line * 0.12f) +
        sin(t * (0.7f + line * 0.13f) + line) * size.height * 0.03f
    val amplitude = size.height * (0.035f + features.mid * 0.1f + features.treble * 0.06f)
    var x = 0f
    while (x < size.width) {
      val bin = ((x / size.width) * spectrum.size).toInt().coerceIn(0, spectrum.size - 1)
      val spectrumLift = (spectrum.getOrElse(bin) { 0.04f } - 0.04f) * size.height * 0.12f
      val y1 =
        baseline +
          sin(x / 90f + t * (1.4f + line * 0.2f)) * amplitude -
          spectrumLift * 0.5f
      val x2 = (x + step).coerceAtMost(size.width)
      val bin2 = ((x2 / size.width) * spectrum.size).toInt().coerceIn(0, spectrum.size - 1)
      val spectrumLift2 = (spectrum.getOrElse(bin2) { 0.04f } - 0.04f) * size.height * 0.12f
      val y2 =
        baseline +
          sin(x2 / 90f + t * (1.4f + line * 0.2f)) * amplitude -
          spectrumLift2 * 0.5f
      drawLine(
        color = color.copy(alpha = 0.55f + features.energy * 0.35f),
        start = Offset(x, y1),
        end = Offset(x2, y2),
        strokeWidth = (2f + features.beat * 3f),
        cap = StrokeCap.Round,
      )
      x = x2
    }
  }
}

private fun DrawScope.drawParticles(
  t: Float,
  features: AudioFeatures,
  spectrum: FloatArray,
) {
  val count = 72
  val cx = size.width / 2f
  val cy = size.height * 0.46f
  val reach = min(size.width, size.height) * 0.42f
  for (i in 0 until count) {
    val seed = (i * 37) % 101 / 101f
    val angle = seed * 2f * Math.PI.toFloat() + t * (0.15f + seed * 0.25f)
    val orbit = reach * (0.2f + 0.8f * abs(sin(seed * 9f + t * 0.35f)))
    val bin = i % spectrum.size.coerceAtLeast(1)
    val level = spectrum.getOrElse(bin) { 0.04f }.coerceIn(0.04f, 1f)
    val radius = (1.5f + level * 7f + features.beat * 4f)
    val color =
      when (i % 3) {
        0 -> Color(0xFFFF5F57)
        1 -> Color(0xFF5CE1FF)
        else -> Color(0xFFB45CFF)
      }
    drawCircle(
      color = color.copy(alpha = 0.3f + level * 0.6f),
      radius = radius,
      center =
        Offset(
          cx + cos(angle) * orbit,
          cy + sin(angle) * orbit * 0.72f,
        ),
    )
  }
}

private fun DrawScope.drawBeatRings(
  t: Float,
  features: AudioFeatures,
) {
  val cx = size.width / 2f
  val cy = size.height * 0.46f
  val maxRadius = max(size.width, size.height) * 0.62f
  for (i in 0 until 7) {
    val phase = (t * (0.34f + features.energy * 0.25f) + i / 7f + features.beat * 0.08f) % 1f
    val radius = phase * maxRadius
    val alpha = (1f - phase) * (0.16f + features.beat * 0.42f + features.kick * 0.2f)
    val color =
      if (i % 2 == 0) Color(0xFFFF5F57) else Color(0xFF8B5CF6)
    drawCircle(
      color = color.copy(alpha = alpha.coerceIn(0f, 0.9f)),
      radius = radius,
      center = Offset(cx, cy),
      style = Stroke(width = (2f + (1f - phase) * 5f).coerceAtMost(8f)),
    )
  }
  drawCircle(
    color = Color.White.copy(alpha = 0.2f + features.beat * 0.5f),
    radius = min(size.width, size.height) * 0.06f * (1f + features.bass * 0.5f),
    center = Offset(cx, cy),
  )
}

/**
 * Rotating wireframe tunnel: a perspective-projected 3D lattice whose rings
 * swell with the FFT bins and whose yaw/pitch drift is nudged by mid/treble.
 * The depth cue comes from a real camera projection, not a flat scale trick.
 */
private fun DrawScope.drawThreeDPattern(
  t: Float,
  features: AudioFeatures,
  spectrum: FloatArray,
) {
  val cx = size.width / 2f
  val cy = size.height * 0.46f
  val focal = min(size.width, size.height) * 0.62f
  val yaw = t * 0.26f + features.mid * 0.4f
  val pitch = 0.2f + sin(t * 0.33f) * 0.14f + features.treble * 0.16f
  val cyaw = cos(yaw)
  val syaw = sin(yaw)
  val cpitch = cos(pitch)
  val spitch = sin(pitch)

  fun project(x: Float, y: Float, z: Float): Offset {
    val rx = x * cyaw - z * syaw
    val rz = x * syaw + z * cyaw
    val ry = y * cpitch - rz * spitch
    val dz = (y * spitch + rz * cpitch) + 3.4f
    val scale = focal / dz.coerceAtLeast(0.6f)
    return Offset(cx + rx * scale, cy + ry * scale)
  }

  val bins = spectrum.size.coerceAtLeast(1)
  val ringCount = 12
  val palette =
    listOf(
      Color(0xFFFF5F57),
      Color(0xFFB45CFF),
      Color(0xFF5CE1FF),
      Color(0xFF3DDC97),
    )
  val rings =
    List(ringCount) { i ->
      val depth = i / (ringCount - 1f)
      val z = -1.2f + depth * 3.6f
      val bin = (depth * (bins - 1)).toInt().coerceIn(0, bins - 1)
      val level = spectrum.getOrElse(bin) { VisualizerSpectrum.IDLE_LEVEL }.coerceIn(0.04f, 1f)
      val half = 0.74f + level * 0.5f + features.bass * 0.2f + features.beat * 0.12f
      val color = palette[i % palette.size]
      val alpha = (0.22f + depth * 0.58f) * (0.7f + features.energy * 0.4f)
      val corners =
        listOf(
          project(-half, -half, z),
          project(half, -half, z),
          project(half, half, z),
          project(-half, half, z),
        )
      Triple(corners, color, alpha.coerceIn(0f, 1f))
    }

  // Longitudinal rails sell the depth before the near rings are painted.
  for (corner in 0..3) {
    for (i in 0 until ringCount - 1) {
      val near = rings[i].first
      val far = rings[i + 1].first
      drawLine(
        color = rings[i].second.copy(alpha = rings[i].third * 0.6f),
        start = near[corner],
        end = far[corner],
        strokeWidth = (1.2f + features.beat * 1.8f).coerceAtMost(3.5f),
      )
    }
  }

  // Paint far-to-near so the closest lattice sits on top.
  for (i in ringCount - 1 downTo 0) {
    val (corners, color, alpha) = rings[i]
    for (edge in 0..3) {
      drawLine(
        color = color.copy(alpha = alpha),
        start = corners[edge],
        end = corners[(edge + 1) % 4],
        strokeWidth = (1.4f + (1f - i / ringCount.toFloat()) * 2.6f + features.kick * 2f)
          .coerceAtMost(6f),
        cap = StrokeCap.Round,
      )
    }
  }

  drawCircle(
    brush =
      Brush.radialGradient(
        colors =
          listOf(
            Color(0xFFFF5F57).copy(alpha = 0.5f + features.beat * 0.4f),
            Color(0xFF5CE1FF).copy(alpha = 0.18f),
            Color.Transparent,
          ),
        center = Offset(cx, cy),
        radius = focal * 0.22f * (1f + features.bass * 0.45f + features.beat * 0.25f),
      ),
    center = Offset(cx, cy),
    radius = focal * 0.22f * (1f + features.bass * 0.45f + features.beat * 0.25f),
  )
}

private fun DrawScope.drawAurora(
  t: Float,
  features: AudioFeatures,
) {
  val ribbons = 5
  val step = size.width / 80f
  val colors =
    listOf(
      Color(0xFFFF5F57),
      Color(0xFFB45CFF),
      Color(0xFF5CE1FF),
      Color(0xFF3DDC97),
      Color(0xFFF9C74F),
    )
  for (r in 0 until ribbons) {
    var x = 0f
    while (x < size.width) {
      val wave1 = sin(x / 150f + t * (0.6f + r * 0.08f) + r) * size.height * 0.08f
      val wave2 = sin(x / 70f - t * (0.35f + r * 0.05f)) * size.height * 0.025f
      val y1 = size.height * (0.32f + r * 0.08f) + wave1 + wave2
      val x2 = (x + step).coerceAtMost(size.width)
      val wave1b = sin(x2 / 150f + t * (0.6f + r * 0.08f) + r) * size.height * 0.08f
      val wave2b = sin(x2 / 70f - t * (0.35f + r * 0.05f)) * size.height * 0.025f
      val y2 = size.height * (0.32f + r * 0.08f) + wave1b + wave2b
      drawLine(
        color =
          colors[r].copy(alpha = 0.28f + features.energy * 0.35f + features.beat * 0.15f),
        start = Offset(x, y1),
        end = Offset(x2, y2),
        strokeWidth = 10f + features.bass * 18f,
        cap = StrokeCap.Round,
      )
      x = x2
    }
  }
}
