package com.music.echo.ui.player

import echo.music.iad1tya.ui.player.VisualizerFallback
import echo.music.iad1tya.ui.player.VisualizerSpectrum
import echo.music.iad1tya.ui.player.simulatedVisualFeatures
import echo.music.iad1tya.ui.player.simulatedVisualSpectrum
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerSpectrumTest {
  private fun toneFrame(bin: Int, magnitude: Int, bins: Int = 128): ByteArray {
    val fft = ByteArray(bins * 2)
    fft[bin * 2] = magnitude.toByte()
    fft[bin * 2 + 1] = 0
    return fft
  }

  @Test
  fun `missing or short frames fall back to idle bars`() {
    assertEquals(
      List(8) { VisualizerSpectrum.IDLE_LEVEL },
      VisualizerSpectrum.map(null, 8).asList(),
    )
    assertEquals(
      List(8) { VisualizerSpectrum.IDLE_LEVEL },
      VisualizerSpectrum.map(byteArrayOf(0, 0), 8).asList(),
    )
    assertTrue(VisualizerSpectrum.map(toneFrame(4, 100), 0).isEmpty())
  }

  @Test
  fun `silence stays at idle level`() {
    val result = VisualizerSpectrum.map(ByteArray(256), 16)
    assertEquals(16, result.size)
    assertEquals(List(16) { VisualizerSpectrum.IDLE_LEVEL }, result.asList())
  }

  @Test
  fun `a tone lights the matching bar more than distant ones`() {
    val bins = 128
    val toneBin = 40
    val result = VisualizerSpectrum.map(toneFrame(toneBin, 120, bins), 32)

    val expectedBar = (sqrt(toneBin / (bins - 1).toFloat()) * 32).toInt().coerceIn(0, 31)
    assertTrue(result[expectedBar] > result[0])
    assertTrue(result[expectedBar] > result[31])
    assertTrue(result[expectedBar] > 0.5f)
  }

  @Test
  fun `levels stay in display range`() {
    val fft = ByteArray(256)
    for (i in 1 until 128) {
      fft[i * 2] = (i % 40).toByte()
      fft[i * 2 + 1] = ((i * 7) % 30).toByte()
    }
    val result = VisualizerSpectrum.map(fft, 24)
    assertEquals(24, result.size)
    assertTrue(result.all { it >= VisualizerSpectrum.IDLE_LEVEL && it <= 1f })
  }

  @Test
  fun `canvas fallback animates when capture is unavailable`() {
    val first = VisualizerFallback.levels(1_000L, 24)
    val later = VisualizerFallback.levels(1_400L, 24)
    assertEquals(24, first.size)
    assertTrue(first.any { it > VisualizerSpectrum.IDLE_LEVEL })
    assertTrue(!first.contentEquals(later))
    assertTrue(later.all { it in VisualizerSpectrum.IDLE_LEVEL..1f })
  }

  @Test
  fun `fullscreen simulated frame stays in display range`() {
    val features = simulatedVisualFeatures(2.4f)
    val spectrum = simulatedVisualSpectrum(2.4f, 64)
    assertEquals(64, spectrum.size)
    assertTrue(features.energy in 0f..1f)
    assertTrue(features.beat in 0f..1f)
    assertTrue(spectrum.all { it in 0.04f..1f })
    assertTrue(simulatedVisualSpectrum(2.8f, 64).asList() != spectrum.asList())
  }
}
