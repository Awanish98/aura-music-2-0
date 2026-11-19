package echo.music.iad1tya.ui.player

import kotlin.math.roundToLong

/**
 * Pure policy for the AMOLED renderer. It contains no Android or Compose state so
 * the same rules can be unit-tested and reused by the phone overlay, the wired
 * Presentation and the desktop receiver payload.
 */
object AmoledLyricsPolicy {
  /**
   * Returns visual settings that render on true black. Low power additionally
   * removes glow, particles, background effects and motion so only timestamped
   * typography and the seek bar keep updating.
   */
  fun settings(
    base: CinematicVisualSettings,
    lowPower: Boolean,
  ): CinematicVisualSettings =
    base.copy(
      pureBlack = true,
      glow = if (lowPower) 0f else base.glow.coerceAtMost(0.35f),
      particles = if (lowPower) 0f else base.particles.coerceAtMost(0.15f),
      bassReaction = if (lowPower) 0f else base.bassReaction.coerceAtMost(0.25f),
      backgroundEffects = false,
      motion = if (lowPower) 0f else base.motion.coerceAtMost(0.35f),
      reducedMotion = lowPower || base.reducedMotion,
      lowPower = lowPower,
    )

  fun sanitize(
    visual: CinematicVisualState,
    lowPower: Boolean,
  ): CinematicVisualState = visual.copy(settings = settings(visual.settings, lowPower))

  /** Maps a tap inside the progress bar to a clamped playback position. */
  fun seekPosition(
    offsetX: Float,
    width: Int,
    durationMs: Long,
  ): Long {
    if (width <= 0 || durationMs <= 0L) return 0L
    val fraction = (offsetX / width.toFloat()).coerceIn(0f, 1f)
    return (durationMs.toFloat() * fraction).roundToLong().coerceIn(0L, durationMs)
  }
}
