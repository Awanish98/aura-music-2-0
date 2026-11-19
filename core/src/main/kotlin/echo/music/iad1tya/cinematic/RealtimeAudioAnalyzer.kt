package echo.music.iad1tya.cinematic

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/** One frame of audio-derived features. All values are 0..1 unless noted. */
data class AudioFeatures(
  val bass: Float = 0f,
  val mid: Float = 0f,
  val treble: Float = 0f,
  val energy: Float = 0f,
  val bpm: Float = 0f,
  val beat: Float = 0f,
  val kick: Float = 0f,
  val snare: Float = 0f,
  val drop: Float = 0f,
  val onset: Float = 0f,
  val silence: Boolean = true,
  val intensity: Float = 0f,
) {
  companion object {
    val IDLE = AudioFeatures()
  }
}

/**
 * Pure-Kotlin analysis of the interleaved FFT frames produced by Android's
 * [android.media.audiofx.Visualizer]. No Android types are used here, so the
 * detection logic is fully unit-testable and never touches the audio thread.
 *
 * It deliberately combines adaptive baselines (so a loud song does not flash
 * on every bin) with spectral flux onsets (so real transients still register).
 */
class RealtimeAudioAnalyzer(initialSensitivity: Float = 1f) {
  private var sensitivity = initialSensitivity.coerceIn(0.25f, 2.5f)

  private var scratch = FloatArray(0)
  private var previous = FloatArray(0)
  private var lastUpdateMs = Long.MIN_VALUE

  private var smoothBass = 0f
  private var smoothMid = 0f
  private var smoothTreble = 0f
  private var smoothEnergy = 0f
  private var slowEnergy = 0f
  private var baselineBass = 0f
  private var baselineMid = 0f
  private var baselineTreble = 0f
  private var previousBass = 0f
  private var previousEnergy = 0f

  private val fluxHistory = ArrayDeque<Float>()
  private val onsetIntervals = ArrayDeque<Long>()
  private var lastOnsetMs = -1L
  private var bpm = 0f

  private var beatPulse = 0f
  private var kickPulse = 0f
  private var snarePulse = 0f
  private var dropPulse = 0f
  private var lastDropMs = -1L
  private var quietSinceMs = -1L

  fun setSensitivity(value: Float) {
    sensitivity = value.coerceIn(0.25f, 2.5f)
  }

  fun reset() {
    lastUpdateMs = Long.MIN_VALUE
    smoothBass = 0f
    smoothMid = 0f
    smoothTreble = 0f
    smoothEnergy = 0f
    slowEnergy = 0f
    baselineBass = 0f
    baselineMid = 0f
    baselineTreble = 0f
    previousBass = 0f
    previousEnergy = 0f
    fluxHistory.clear()
    onsetIntervals.clear()
    lastOnsetMs = -1L
    bpm = 0f
    lastDropMs = -1L
    quietSinceMs = -1L
    beatPulse = 0f
    kickPulse = 0f
    snarePulse = 0f
    dropPulse = 0f
  }

  fun accept(fft: ByteArray?, samplingRateHz: Int, nowMs: Long): AudioFeatures {
    val dt =
      if (lastUpdateMs == Long.MIN_VALUE) 32L
      else (nowMs - lastUpdateMs).coerceIn(8L, 200L)
    lastUpdateMs = nowMs

    if (fft == null || fft.size < 16) {
      decay(dt, silent = true)
      return AudioFeatures.IDLE
    }

    val bins = fft.size / 2
    if (scratch.size != bins) scratch = FloatArray(bins)
    if (previous.size != bins) previous = FloatArray(bins)

    var peak = 1f
    for (bin in 1 until bins) {
      val real = fft[bin * 2].toInt()
      val imaginary = fft[bin * 2 + 1].toInt()
      val magnitude = sqrt((real * real + imaginary * imaginary).toFloat())
      scratch[bin] = magnitude
      if (magnitude > peak) peak = magnitude
    }
    scratch[0] = 0f // discard DC offset
    val divisor = max(80f, peak * 0.65f)

    val hzPerBin = (samplingRateHz.coerceAtLeast(8000) / 2f) / bins
    val bassEnd = max(2, (250f / hzPerBin).toInt()).coerceAtMost(bins - 2)
    val midEnd = max(bassEnd + 2, (4000f / hzPerBin).toInt()).coerceAtMost(bins - 1)

    var bassSum = 0f
    for (bin in 1..bassEnd) bassSum += scratch[bin] / divisor
    val bassRaw = (bassSum / bassEnd).coerceIn(0f, 1f)

    var midSum = 0f
    for (bin in (bassEnd + 1)..midEnd) midSum += scratch[bin] / divisor
    val midRaw = (midSum / (midEnd - bassEnd)).coerceIn(0f, 1f)

    var trebleSum = 0f
    for (bin in (midEnd + 1) until bins) trebleSum += scratch[bin] / divisor
    val trebleRaw = (trebleSum / max(1, bins - midEnd - 1)).coerceIn(0f, 1f)

    var flux = 0f
    var bassDelta = 0f
    var midDelta = 0f
    for (bin in 1 until bins) {
      val current = scratch[bin] / divisor
      val was = previous[bin] / divisor
      val positive = current - was
      if (positive > 0f) flux += positive
      if (bin <= bassEnd) bassDelta += max(0f, positive)
      else if (bin <= midEnd) midDelta += max(0f, positive)
    }
    flux /= bins

    // Rotate buffers: the frame just analysed becomes the next baseline.
    val old = previous
    previous = scratch
    scratch = old

    smoothBass = follow(smoothBass, bassRaw, dt)
    smoothMid = follow(smoothMid, midRaw, dt)
    smoothTreble = follow(smoothTreble, trebleRaw, dt)
    baselineBass += (bassRaw - baselineBass) * 0.012f
    baselineMid += (midRaw - baselineMid) * 0.012f
    baselineTreble += (trebleRaw - baselineTreble) * 0.012f

    val reactiveBass = (smoothBass - baselineBass * 0.78f).coerceIn(0f, 1f)
    val reactiveMid = (smoothMid - baselineMid * 0.78f).coerceIn(0f, 1f)
    val reactiveTreble = (smoothTreble - baselineTreble * 0.78f).coerceIn(0f, 1f)
    val energy =
      (reactiveBass * 0.5f + reactiveMid * 0.35f + reactiveTreble * 0.15f).coerceIn(0f, 1f)
    smoothEnergy = follow(smoothEnergy, energy, dt)
    slowEnergy += (energy - slowEnergy) * 0.02f

    fluxHistory.addLast(flux)
    if (fluxHistory.size > 48) fluxHistory.removeFirst()
    var mean = 0f
    for (value in fluxHistory) mean += value
    mean /= fluxHistory.size
    var variance = 0f
    for (value in fluxHistory) {
      val delta = value - mean
      variance += delta * delta
    }
    variance /= fluxHistory.size
    val threshold = mean + 1.25f * sensitivity * sqrt(variance)
    val isOnset =
      fluxHistory.size >= 8 &&
        flux > threshold &&
        flux > 0.006f &&
        nowMs - lastOnsetMs > 110L

    if (isOnset) {
      // Keep the previous onset before updating the clock. Onset intervals must
      // always be relative durations; mixing an absolute timestamp into this
      // queue made every later interval absurd and left BPM at zero.
      val previousOnsetMs = lastOnsetMs
      lastOnsetMs = nowMs
      beatPulse = 1f
      if (bassDelta * 2f >= midDelta) kickPulse = 1f else snarePulse = 1f

      if (previousOnsetMs >= 0L) {
        val interval = nowMs - previousOnsetMs
        if (interval in 250L..1000L) {
          onsetIntervals.addLast(interval)
          if (onsetIntervals.size > 16) onsetIntervals.removeFirst()
          bpm = estimateBpm()
        } else {
          // A long pause is not a tempo change; start the interval history over.
          onsetIntervals.clear()
        }
      }
    }

    val strongBassRise = reactiveBass > 0.68f && previousBass < 0.42f
    val strongEnergyRise = energy > 0.82f && previousEnergy < 0.45f
    if (
      (strongBassRise || strongEnergyRise) &&
        nowMs - lastDropMs > 1800L &&
        !silent
    ) {
      dropPulse = 1f
      lastDropMs = nowMs
    }
    previousBass = reactiveBass
    previousEnergy = energy

    if (smoothEnergy < 0.055f) {
      if (quietSinceMs < 0L) quietSinceMs = nowMs
    } else {
      quietSinceMs = -1L
    }
    val isSilent = quietSinceMs >= 0L && nowMs - quietSinceMs > 450L

    decay(dt, isSilent)
    val intensity = (smoothEnergy * 0.72f + slowEnergy * 0.28f).coerceIn(0f, 1f)
    return AudioFeatures(
      bass = reactiveBass,
      mid = reactiveMid,
      treble = reactiveTreble,
      energy = smoothEnergy,
      bpm = bpm,
      beat = beatPulse,
      kick = kickPulse,
      snare = snarePulse,
      drop = dropPulse,
      onset = if (isOnset) 1f else 0f,
      silence = isSilent,
      intensity = intensity,
    )
  }

  private val silent: Boolean
    get() = smoothEnergy < 0.055f

  private fun follow(current: Float, target: Float, dt: Long): Float {
    val rate = if (target > current) 0.42f else 0.1f
    val weight = 1f - exp(-dt / 90f * rate * 4f)
    return current + (target - current) * weight.coerceIn(0f, 1f)
  }

  private fun estimateBpm(): Float {
    if (onsetIntervals.size < 3) return bpm
    val sorted = onsetIntervals.sorted()
    val median = sorted[sorted.size / 2].toFloat()
    var estimated = 60_000f / median.coerceAtLeast(1f)
    while (estimated < 80f) estimated *= 2f
    while (estimated > 175f) estimated /= 2f
    return if (bpm == 0f) estimated else bpm * 0.8f + estimated * 0.2f
  }

  private fun decay(dt: Long, silent: Boolean) {
    beatPulse *= exp(-dt / 150f)
    kickPulse *= exp(-dt / 120f)
    snarePulse *= exp(-dt / 95f)
    dropPulse *= exp(-dt / 430f)
    if (silent) {
      beatPulse *= 0.8f
      kickPulse *= 0.8f
      snarePulse *= 0.8f
    }
  }
}
