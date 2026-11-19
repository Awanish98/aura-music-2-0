package com.music.echo.moodauto

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import echo.music.iad1tya.ui.player.CinematicAudioState
import echo.music.iad1tya.ui.player.FullVisualizerStyle
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Mood-matched visual bed drawn behind the premium lyric canvas. It reads the
 * same audio state as every other surface; it never creates a second player or
 * changes lyric timing. Motion is scaled by [MoodAutoExperience] so calm songs
 * breathe slowly while energetic songs move with the beat.
 */
@Composable
fun MoodAutoVisual(
  audio: CinematicAudioState,
  experience: MoodAutoExperience,
  isPlaying: Boolean,
  modifier: Modifier = Modifier,
) {
  var frameTimeMs by remember { mutableLongStateOf(0L) }
  val playing = rememberUpdatedState(isPlaying)

  LaunchedEffect(Unit) {
    var lastFrame = 0L
    var lastPublish = 0L
    while (true) {
      withFrameMillis { now ->
        val delta = if (lastFrame == 0L) 0L else (now - lastFrame).coerceAtMost(80L)
        lastFrame = now
        if (playing.value && now - lastPublish >= 24L) {
          lastPublish = now
          frameTimeMs += delta
        }
      }
    }
  }

  val palette = experience.palette()
  val features = audio.value
  val spectrum = audio.spectrum
  val t = frameTimeMs / 1000f * experience.motionScale

  Canvas(modifier.background(Color.Black)) {
    drawMoodBackdrop(t, experience, palette)
    when (experience.visualStyle) {
      FullVisualizerStyle.AUTO,
      FullVisualizerStyle.SPECTRUM -> drawMoodSpectrum(t, spectrum, experience, palette)
      FullVisualizerStyle.RADIAL -> drawMoodRadial(t, spectrum, experience, palette)
      FullVisualizerStyle.WAVE -> drawMoodWaves(t, features.energy, experience, palette)
      FullVisualizerStyle.PARTICLES -> drawMoodParticles(t, spectrum, experience, palette)
      FullVisualizerStyle.RINGS -> drawMoodRings(t, features.beat, experience, palette)
      FullVisualizerStyle.AURORA -> drawMoodAurora(t, features.energy, experience, palette)
      FullVisualizerStyle.THREE_D -> drawMoodThreeD(t, spectrum, experience, palette)
    }
  }
}

private data class MoodPalette(
  val primary: Color,
  val accent: Color,
  val glow: Color,
)

private fun MoodAutoExperience.palette(): MoodPalette {
  val theme = theme
  return if (theme != null) {
    MoodPalette(
      primary = Color(theme.primary.toInt()),
      accent = Color(theme.secondary.toInt()),
      glow = Color(theme.accent.toInt()),
    )
  } else {
    MoodPalette(
      primary = Color(0xFFEAF2FF),
      accent = Color(0xFF6CE5FF),
      glow = Color(0xFFB388FF),
    )
  }
}

private fun DrawScope.drawMoodBackdrop(
  t: Float,
  experience: MoodAutoExperience,
  palette: MoodPalette,
) {
  val background = experience.theme?.background?.toInt()?.let { Color(it) } ?: Color(0xFF03030A)
  drawRect(brush = Brush.verticalGradient(listOf(background, Color.Black)))
  val radius = maxOf(size.width, size.height) * 0.72f
  drawRect(
    brush =
      Brush.radialGradient(
        colors =
          listOf(
            palette.glow.copy(alpha = 0.1f * experience.glowScale),
            palette.accent.copy(alpha = 0.04f * experience.glowScale),
            Color.Transparent,
          ),
        center = Offset(size.width / 2f, size.height * 0.45f),
        radius = radius,
      )
  )
  drawRect(
    brush =
      Brush.linearGradient(
        colors =
          listOf(
            Color.Transparent,
            palette.primary.copy(alpha = 0.03f * experience.glowScale),
            Color.Transparent,
          ),
        start = Offset(0f, size.height * (0.5f + sin(t * 0.3f) * 0.1f)),
        end = Offset(size.width, size.height * (0.35f - sin(t * 0.3f) * 0.1f)),
      )
  )
}

private fun DrawScope.drawMoodSpectrum(
  t: Float,
  spectrum: FloatArray,
  experience: MoodAutoExperience,
  palette: MoodPalette,
) {
  val bars = spectrum.size.coerceAtLeast(8)
  val slot = size.width / bars
  val width = (slot * 0.56f).coerceAtLeast(2f)
  for (i in 0 until bars) {
    val level = spectrum[i].coerceIn(0.04f, 1f)
    val shimmer = 0.88f + 0.12f * sin(t * 2f + i * 0.31f)
    val height = size.height * 0.34f * level * shimmer * experience.glowScale
    drawRect(
      brush = Brush.verticalGradient(listOf(palette.accent, palette.glow)),
      topLeft = Offset(i * slot + (slot - width) / 2f, size.height - height),
      size = androidx.compose.ui.geometry.Size(width, height),
      alpha = 0.5f,
    )
  }
}

private fun DrawScope.drawMoodRadial(
  t: Float,
  spectrum: FloatArray,
  experience: MoodAutoExperience,
  palette: MoodPalette,
) {
  val cx = size.width / 2f
  val cy = size.height * 0.44f
  val base = min(size.width, size.height) * 0.16f
  val count = spectrum.size.coerceAtLeast(16)
  for (i in 0 until count) {
    val angle = i.toFloat() / count * 2f * Math.PI.toFloat() + t * 0.16f
    val level = spectrum[i].coerceIn(0.04f, 1f)
    val outer = base + level * min(size.width, size.height) * 0.22f * experience.glowScale
    drawLine(
      color = if (i % 3 == 0) palette.accent else palette.glow,
      start = Offset(cx + cos(angle) * base, cy + sin(angle) * base),
      end = Offset(cx + cos(angle) * outer, cy + sin(angle) * outer),
      strokeWidth = 2f + level * 3f,
      alpha = 0.55f,
    )
  }
}

private fun DrawScope.drawMoodWaves(
  t: Float,
  energy: Float,
  experience: MoodAutoExperience,
  palette: MoodPalette,
) {
  for (layer in 0..3) {
    val path = androidx.compose.ui.graphics.Path()
    for (x in 0..size.width.toInt() step 8) {
      val p = x / size.width
      val y =
        size.height * (0.34f + layer * 0.1f) +
          sin(p * 7f + t * (0.7f + layer * 0.12f)) * (18f + energy * 34f) +
          sin(p * 15f - t) * 8f
      if (x == 0) path.moveTo(x.toFloat(), y) else path.lineTo(x.toFloat(), y)
    }
    drawPath(
      path = path,
      color = if (layer % 2 == 0) palette.accent else palette.glow,
      alpha = 0.13f * experience.glowScale,
      style = Stroke(width = 2f),
    )
  }
}

private fun DrawScope.drawMoodParticles(
  t: Float,
  spectrum: FloatArray,
  experience: MoodAutoExperience,
  palette: MoodPalette,
) {
  val count = (70 * experience.particleScale).toInt().coerceIn(16, 120)
  for (i in 0 until count) {
    val seed = i.toFloat() / count
    val angle = seed * 6.283f * 3f + t * 0.22f
    val radius =
      min(size.width, size.height) * (0.16f + 0.16f * sin(seed * 11f + t * 0.4f))
    val x = size.width / 2f + cos(angle) * radius * 1.5f
    val y = size.height * 0.45f + sin(angle) * radius * 0.7f
    val level = spectrum.getOrNull(i % spectrum.size)?.coerceIn(0.04f, 1f) ?: 0.2f
    drawCircle(
      color = if (i % 4 == 0) palette.accent else palette.glow,
      radius = 1.5f + level * 3f,
      center = Offset(x, y),
      alpha = 0.35f * experience.glowScale,
    )
  }
}

private fun DrawScope.drawMoodRings(
  t: Float,
  beat: Float,
  experience: MoodAutoExperience,
  palette: MoodPalette,
) {
  val cx = size.width / 2f
  val cy = size.height * 0.44f
  for (ring in 0 until 6) {
    val radius =
      min(size.width, size.height) * (0.1f + ring * 0.055f) +
        beat * 22f * experience.glowScale
    drawCircle(
      color = if (ring % 2 == 0) palette.accent else palette.glow,
      radius = radius,
      center = Offset(cx, cy),
      alpha = (0.28f - ring * 0.03f) * experience.glowScale,
      style = Stroke(width = 2f),
    )
  }
}

private fun DrawScope.drawMoodAurora(
  t: Float,
  energy: Float,
  experience: MoodAutoExperience,
  palette: MoodPalette,
) {
  for (band in 0 until 5) {
    val path = androidx.compose.ui.graphics.Path()
    for (x in 0..size.width.toInt() step 10) {
      val p = x / size.width
      val y =
        size.height * (0.24f + band * 0.09f) +
          sin(p * 5f + t * (0.35f + band * 0.08f)) * (26f + energy * 44f)
      if (x == 0) path.moveTo(x.toFloat(), y) else path.lineTo(x.toFloat(), y)
    }
    path.lineTo(size.width, size.height)
    path.lineTo(0f, size.height)
    path.close()
    drawPath(
      path = path,
      brush = Brush.verticalGradient(listOf(palette.accent, Color.Transparent)),
      alpha = 0.06f * experience.glowScale,
    )
  }
}

private fun DrawScope.drawMoodThreeD(
  t: Float,
  spectrum: FloatArray,
  experience: MoodAutoExperience,
  palette: MoodPalette,
) {
  val rotation = t * 0.4f
  val count = 56
  for (i in 0 until count) {
    val angle = i.toFloat() / count * 6.283f + rotation
    val level = spectrum.getOrNull(i % spectrum.size)?.coerceIn(0.04f, 1f) ?: 0.2f
    val x = size.width / 2f + cos(angle) * size.width * 0.3f
    val height = (12f + level * size.height * 0.2f) * experience.glowScale
    val baseY = size.height * 0.6f + sin(angle) * 18f
    drawLine(
      color = if (i % 2 == 0) palette.accent else palette.glow,
      start = Offset(x, baseY),
      end = Offset(x, baseY - height),
      strokeWidth = 3f,
      alpha = 0.62f,
    )
  }
}
