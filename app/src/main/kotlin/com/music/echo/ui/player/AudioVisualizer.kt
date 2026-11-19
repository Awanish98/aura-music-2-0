package echo.music.iad1tya.ui.player

import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Turns an FFT capture frame into a small set of display bars.
 *
 * Kept free of Android/Compose types so the mapping can be unit tested and so
 * the renderer degrades gracefully when a device refuses to give us capture
 * data (some output routes, casts and protected content).
 */
internal object VisualizerSpectrum {
  const val IDLE_LEVEL = 0.04f

  fun map(fft: ByteArray?, barCount: Int): FloatArray {
    val output = FloatArray(barCount) { IDLE_LEVEL }
    if (fft == null || barCount <= 0) return output

    val bins = fft.size / 2
    if (bins < 2) return output

    // FFT data arrives as interleaved (real, imaginary) bytes. Bin 0 is DC.
    val magnitudes = FloatArray(bins)
    for (bin in 1 until bins) {
      val real = fft[bin * 2].toInt()
      val imaginary = fft[bin * 2 + 1].toInt()
      magnitudes[bin] = sqrt((real * real + imaginary * imaginary).toFloat())
    }

    val usable = (bins - 1).coerceAtMost(255)
    var peak = 1f
    for (bin in 1..usable) peak = max(peak, magnitudes[bin])

    for (bar in 0 until barCount) {
      // Quadratic bin mapping gives bass more room without hiding the highs.
      val from = (1 + usable * (bar.toFloat() / barCount).pow(2)).toInt()
      val to =
        (1 + usable * ((bar + 1).toFloat() / barCount).pow(2))
          .toInt()
          .coerceAtLeast(from)
          .coerceAtMost(usable)
      // A bucket maximum keeps narrow musical peaks visible; averaging them
      // away makes a real spectrum display look flat.
      var loudest = 0f
      for (bin in from..to) loudest = max(loudest, magnitudes[bin])
      output[bar] = (loudest / peak).pow(0.65f).coerceIn(IDLE_LEVEL, 1f)
    }
    return output
  }
}

/**
 * Motion fallback for devices/routes that refuse Visualizer capture. This is a
 * visual-only demo beat; it is never presented as audio analysis.
 */
internal object VisualizerFallback {
  fun levels(frameMs: Long, barCount: Int): FloatArray {
    val t = frameMs / 1000f
    val beat = 0.5f + 0.5f * kotlin.math.sin(t * 5.2f)
    return FloatArray(barCount.coerceAtLeast(0)) { i ->
      val fall = kotlin.math.exp(-i / 9f)
      val ripple = 0.7f + 0.3f * kotlin.math.sin(i * 0.42f + t * 2.1f)
      (0.08f + 0.5f * fall * ripple + 0.38f * beat * fall)
        .coerceIn(VisualizerSpectrum.IDLE_LEVEL, 1f)
    }
  }
}

/**
 * Real-time spectrum bars driven by the active playback audio session.
 *
 * This is a clean-room implementation using only the public Android
 * [Visualizer] API; no third-party rendering or captured assets are involved.
 */
@Composable
fun AudioVisualizerStrip(
  audioSessionId: Int,
  isPlaying: Boolean,
  color: Color,
  modifier: Modifier = Modifier,
  barCount: Int = 40,
) {
  var levels by remember(barCount) { mutableStateOf(FloatArray(barCount) { VisualizerSpectrum.IDLE_LEVEL }) }
  var lastRealDataMs by remember { mutableLongStateOf(0L) }
  var frameTimeMs by remember { mutableLongStateOf(0L) }
  val playing = rememberUpdatedState(isPlaying)

  LaunchedEffect(Unit) {
    var lastFrame = 0L
    var lastPublish = 0L
    while (true) {
      withFrameMillis { now ->
        val delta = if (lastFrame == 0L) 0L else (now - lastFrame).coerceAtMost(80L)
        lastFrame = now
        if (playing.value && now - lastPublish >= 32L) {
          lastPublish = now
          frameTimeMs += delta
        }
      }
    }
  }

  DisposableEffect(audioSessionId, barCount) {
    var visualizer: Visualizer? = null
    if (audioSessionId > 0) {
      try {
        val smoother = FloatArray(barCount) { VisualizerSpectrum.IDLE_LEVEL }
        visualizer =
          Visualizer(audioSessionId).apply {
            val maxRate = Visualizer.getMaxCaptureRate().coerceAtLeast(1_000)
            val rate = (maxRate / 2).coerceIn(1_000, maxRate)
            setDataCaptureListener(
              object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(
                  visualizer: Visualizer?,
                  waveform: ByteArray?,
                  samplingRate: Int,
                ) = Unit

                override fun onFftDataCapture(
                  visualizer: Visualizer?,
                  fft: ByteArray?,
                  samplingRate: Int,
                ) {
                  val target = VisualizerSpectrum.map(fft, barCount)
                  lastRealDataMs = SystemClock.elapsedRealtime()
                  for (i in 0 until barCount) {
                    val value = target[i]
                    smoother[i] =
                      if (value > smoother[i]) {
                        smoother[i] + (value - smoother[i]) * 0.55f
                      } else {
                        max(VisualizerSpectrum.IDLE_LEVEL, smoother[i] * 0.86f)
                      }
                  }
                  levels = smoother.copyOf()
                }
              },
              rate,
              false,
              true,
            )
            enabled = true
          }
      } catch (e: Exception) {
        // Visualizer is optional: output effects may be disabled on some
        // devices/routes. Keep the idle bars instead of breaking playback.
        Log.w("AudioVisualizer", "Unable to attach visualizer to session $audioSessionId", e)
      }
    }

    onDispose {
      runCatching { visualizer?.enabled = false }
      runCatching { visualizer?.release() }
    }
  }

  val hasFreshCapture = SystemClock.elapsedRealtime() - lastRealDataMs < 700L
  val displayed =
    when {
      !isPlaying -> FloatArray(barCount) { VisualizerSpectrum.IDLE_LEVEL }
      hasFreshCapture -> levels
      else -> VisualizerFallback.levels(frameTimeMs, barCount)
    }
  Canvas(modifier) {
    val slot = size.width / barCount
    val barWidth = (slot * 0.55f).coerceAtLeast(1f)
    val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
    val brush =
      Brush.verticalGradient(
        colors = listOf(color.copy(alpha = 0.45f), color),
      )
    for (bar in 0 until barCount) {
      val level = displayed[bar].coerceIn(VisualizerSpectrum.IDLE_LEVEL, 1f)
      val barHeight = size.height * level
      drawRoundRect(
        brush = brush,
        topLeft = Offset(bar * slot + (slot - barWidth) / 2f, size.height - barHeight),
        size = Size(barWidth, barHeight),
        cornerRadius = corner,
      )
    }
  }
}
