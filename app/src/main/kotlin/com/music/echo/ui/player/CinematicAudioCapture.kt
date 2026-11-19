package echo.music.iad1tya.ui.player

import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import echo.music.iad1tya.cinematic.AudioFeatures
import echo.music.iad1tya.cinematic.RealtimeAudioAnalyzer
import kotlin.math.max

/**
 * One capture stream, two views of the same audio frame.
 *
 * [value] stays the plain [AudioFeatures] contract used by every cinematic
 * lyric engine. [spectrum] additionally exposes the smoothed FFT bars captured
 * in the very same callback, so the full-screen visualizer never needs a second
 * Visualizer instance or a second playback clock.
 */
class CinematicAudioState internal constructor() : State<AudioFeatures> {
  private val featuresState = mutableStateOf<AudioFeatures>(AudioFeatures.IDLE.copy(silence = true))
  private val spectrumState =
    mutableStateOf(FloatArray(DEFAULT_SPECTRUM_BARS) { VisualizerSpectrum.IDLE_LEVEL })

  override val value: AudioFeatures
    get() = featuresState.value

  val spectrum: FloatArray
    get() = spectrumState.value

  internal fun publish(features: AudioFeatures, spectrum: FloatArray) {
    featuresState.value = features
    spectrumState.value = spectrum
  }

  companion object {
    const val DEFAULT_SPECTRUM_BARS = 64
  }
}

/**
 * Bridges the public Visualizer capture callback to the pure-Kotlin analyser.
 * FFT decoding and onset detection happen off composition; UI state is
 * throttled to ~30 Hz so visual work never blocks playback.
 */
@Composable
fun rememberCinematicAudio(
  audioSessionId: Int,
  active: Boolean,
  sensitivity: Float,
  spectrumBarCount: Int = CinematicAudioState.DEFAULT_SPECTRUM_BARS,
): CinematicAudioState {
  val analyzer = remember { RealtimeAudioAnalyzer(sensitivity) }
  val state = remember { CinematicAudioState() }
  val bars = spectrumBarCount.coerceAtLeast(1)

  LaunchedEffect(sensitivity) { analyzer.setSensitivity(sensitivity) }

  DisposableEffect(audioSessionId, active, bars) {
    if (!active || audioSessionId <= 0) {
      state.publish(
        AudioFeatures.IDLE.copy(silence = true),
        FloatArray(bars) { VisualizerSpectrum.IDLE_LEVEL },
      )
      onDispose { }
    } else {
      var visualizer: Visualizer? = null
      var lastPublishMs = 0L
      val smoother = FloatArray(bars) { VisualizerSpectrum.IDLE_LEVEL }
      try {
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
                  val now = SystemClock.elapsedRealtime()
                  val analysed = analyzer.accept(fft, samplingRate, now)
                  val target = VisualizerSpectrum.map(fft, bars)
                  for (i in 0 until bars) {
                    val level = target[i]
                    smoother[i] =
                      if (level > smoother[i]) {
                        smoother[i] + (level - smoother[i]) * 0.5f
                      } else {
                        max(VisualizerSpectrum.IDLE_LEVEL, smoother[i] * 0.88f)
                      }
                  }
                  if (now - lastPublishMs >= 32L) {
                    lastPublishMs = now
                    state.publish(analysed, smoother.copyOf())
                  }
                }
              },
              rate,
              false,
              true,
            )
            enabled = true
          }
      } catch (e: Exception) {
        // Capture is optional: protected content, casts and some ROMs refuse it.
        Log.w("CinematicAudio", "Unable to attach visualizer to session $audioSessionId", e)
      }

      onDispose {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        analyzer.reset()
        state.publish(
          AudioFeatures.IDLE.copy(silence = true),
          FloatArray(bars) { VisualizerSpectrum.IDLE_LEVEL },
        )
      }
    }
  }
  return state
}
