package echo.music.iad1tya.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.music.iad1tya.R
import echo.music.iad1tya.cinematic.AudioFeatures
import echo.music.iad1tya.cinematic.CinematicMotion
import echo.music.iad1tya.cinematic.CinematicReactions
import echo.music.iad1tya.cinematic.CinematicTheme
import echo.music.iad1tya.cinematic.CinematicTypeface
import echo.music.iad1tya.cinematic.SongSection
import echo.music.iad1tya.ui.component.KaraokeTiming
import echo.music.iad1tya.ui.theme.GoogleSansFontFamily
import echo.music.iad1tya.ui.theme.PlusJakartaSansFontFamily
import echo.music.iad1tya.ui.theme.bbhBartle
import echo.music.iad1tya.ui.component.KaraokeWord
import kotlin.math.cos
import kotlin.math.sin

private val CinematicSerif =
  FontFamily(
    Font(R.font.cormorant_garamond_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.cormorant_garamond_italic, FontWeight.Bold, FontStyle.Italic),
  )

private data class TypeSlot(
  val x: Float,
  val y: Float,
  val sizeSp: Float,
  val rotation: Float,
  val align: TextAlign,
  val widthFraction: Float,
  val letterSpacingSp: Float,
  val emphasis: Boolean,
)

private val TYPE_SLOTS =
  listOf(
    TypeSlot(0.07f, 0.16f, 56f, -5f, TextAlign.Start, 0.82f, 1.6f, true),
    TypeSlot(0.54f, 0.11f, 24f, 7f, TextAlign.End, 0.40f, 2.4f, false),
    TypeSlot(0.17f, 0.31f, 30f, 3f, TextAlign.Start, 0.62f, 0.8f, false),
    TypeSlot(0.50f, 0.27f, 66f, -4f, TextAlign.End, 0.45f, 0.4f, true),
    TypeSlot(0.09f, 0.49f, 22f, 6f, TextAlign.Start, 0.48f, 2.0f, false),
    TypeSlot(0.44f, 0.52f, 46f, -8f, TextAlign.Center, 0.54f, 1.0f, true),
    TypeSlot(0.14f, 0.69f, 34f, 2f, TextAlign.Start, 0.60f, 1.2f, false),
    TypeSlot(0.52f, 0.72f, 58f, -6f, TextAlign.End, 0.44f, 0.6f, true),
    TypeSlot(0.30f, 0.85f, 20f, 5f, TextAlign.Center, 0.50f, 2.6f, false),
  )

private const val LEAD_IN_MS = 950L
private const val FADE_OUT_MS = 1300L

/** Plain snapshot of controls, safe to send to a Presentation or web receiver. */
data class CinematicVisualSettings(
  val glow: Float = 1f,
  val particles: Float = 1f,
  val bassReaction: Float = 1f,
  val backgroundEffects: Boolean = true,
  val motion: Float = 1f,
  val animationSpeed: Float = 1f,
  val reducedMotion: Boolean = false,
  val vocalHighlight: Float = 1f,
  val pureBlack: Boolean = false,
  val lowPower: Boolean = false,
  val keepAwake: Boolean = false,
)

fun CinematicSettingsState.snapshot(): CinematicVisualSettings =
  CinematicVisualSettings(
    glow = glow.value,
    particles = particles.value,
    bassReaction = bassReaction.value,
    backgroundEffects = backgroundEffects.value,
    motion = motion.value,
    animationSpeed = animationSpeed.value,
    reducedMotion = reducedMotion.value,
    vocalHighlight = vocalHighlight.value,
    pureBlack = amoledMode.value,
    lowPower = amoledLowPower.value,
    keepAwake = amoledKeepAwake.value,
  )

/** Shared render inputs for all backdrops, particles and typography. */
data class CinematicVisualState(
  val theme: CinematicTheme,
  val features: AudioFeatures,
  val section: SongSection,
  val positionMs: Long,
  val settings: CinematicVisualSettings,
  val lowEnd: Boolean,
)

private fun color(value: Long) = Color(value.toInt())

private fun hash(a: Int, b: Int = 0): Float {
  var h = a * 374761393 + b * 668265263
  h = h xor (h ushr 13)
  h *= 1274126177
  return ((h xor (h ushr 16)) and 0x7fffffff).toFloat() / Int.MAX_VALUE
}

private fun typeface(theme: CinematicTheme): FontFamily =
  when (theme.typeface) {
    CinematicTypeface.SERIF_ITALIC -> CinematicSerif
    CinematicTypeface.SERIF -> FontFamily.Serif
    CinematicTypeface.SANS -> PlusJakartaSansFontFamily
    CinematicTypeface.SANS_CONDENSED -> GoogleSansFontFamily
    CinematicTypeface.MONO -> FontFamily.Monospace
    CinematicTypeface.DISPLAY -> bbhBartle
  }

/** Distinct full-screen backdrop per theme recipe. */
@Composable
fun CinematicBackdrop(visual: CinematicVisualState, modifier: Modifier = Modifier) {
  val theme = visual.theme
  val features = visual.features
  val settings = visual.settings
  val bg = color(theme.background)
  val primary = color(theme.primary)
  val secondary = color(theme.secondary)
  val accent = color(theme.accent)
  val t = visual.positionMs / 1000f * settings.animationSpeed
  val reaction = CinematicReactions.forFrame(features)
  val bassPulse = reaction.bass * settings.bassReaction
  val drop = reaction.drop
  val kick = reaction.kick
  val beatSpeed =
    if (reaction.bpm > 0f) (reaction.bpm / 120f).coerceIn(0.6f, 1.6f) else 1f
  val glow = settings.glow.coerceIn(0f, 2f) * (0.45f + 0.55f * reaction.liveliness)
  // A new theme fades in over the persistent black base; no audio/lyric state
  // is reset by the visual transition.
  val themeEnter = remember(theme) { Animatable(0f) }
  LaunchedEffect(themeEnter) {
    themeEnter.animateTo(1f, animationSpec = tween(650, easing = FastOutSlowInEasing))
  }

  Canvas(modifier.graphicsLayer { alpha = themeEnter.value }) {
    drawRect(brush = Brush.verticalGradient(listOf(bg, bg)), size = size)
    if (!settings.backgroundEffects) return@Canvas
    when (theme.backdrop) {
      echo.music.iad1tya.cinematic.CinematicBackdrop.AMBIENT -> {
        val radius = size.minDimension * (0.75f + bassPulse * 0.25f + drop * 0.3f + kick * 0.1f)
        drawCircle(
          brush =
            Brush.radialGradient(
              listOf(primary.copy(alpha = (0.13f + bassPulse * 0.1f) * glow), Color.Transparent),
              center = Offset(size.width * 0.75f, size.height * 0.25f),
              radius = radius,
            ),
          radius = radius,
          center = Offset(size.width * 0.75f, size.height * 0.25f),
        )
        drawCircle(
          brush =
            Brush.radialGradient(
              listOf(secondary.copy(alpha = 0.10f * glow), Color.Transparent),
              center = Offset(size.width * 0.2f, size.height * 0.8f),
              radius = size.minDimension * 0.8f,
            ),
          radius = size.minDimension * 0.8f,
          center = Offset(size.width * 0.2f, size.height * 0.8f),
        )
      }
      echo.music.iad1tya.cinematic.CinematicBackdrop.GRADIENT -> {
        drawRect(
          brush =
            Brush.verticalGradient(
              listOf(bg, Color(0xFF0A1A3A), secondary.copy(alpha = 0.35f * glow), bg)
            ),
          size = size,
        )
        for (i in 0 until 14) {
          val y = ((hash(i) * size.height + t * 8f) % size.height)
          drawLine(
            color = primary.copy(alpha = 0.05f + reaction.treble * 0.06f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
          )
        }
      }
      echo.music.iad1tya.cinematic.CinematicBackdrop.EDITORIAL -> {
        drawRect(color = bg, size = size)
        drawLine(
          color = primary.copy(alpha = 0.22f + reaction.onset * 0.05f),
          start = Offset(size.width * 0.08f, size.height * 0.12f),
          end = Offset(size.width * 0.92f, size.height * 0.12f),
          strokeWidth = 1.5f,
        )
        drawLine(
          color = primary.copy(alpha = 0.12f),
          start = Offset(size.width * 0.08f, size.height * 0.88f),
          end = Offset(size.width * 0.92f, size.height * 0.88f),
          strokeWidth = 1f,
        )
      }
      echo.music.iad1tya.cinematic.CinematicBackdrop.NEON -> {
        val r = size.minDimension * (0.55f + bassPulse * 0.2f + kick * 0.08f)
        drawCircle(
          brush =
            Brush.radialGradient(
              listOf(primary.copy(alpha = 0.24f * glow), Color.Transparent),
              center = Offset(size.width * 0.25f, size.height * 0.35f),
              radius = r,
            ),
          radius = r,
          center = Offset(size.width * 0.25f, size.height * 0.35f),
        )
        drawCircle(
          brush =
            Brush.radialGradient(
              listOf(secondary.copy(alpha = 0.26f * glow), Color.Transparent),
              center = Offset(size.width * 0.78f, size.height * 0.7f),
              radius = r,
            ),
          radius = r,
          center = Offset(size.width * 0.78f, size.height * 0.7f),
        )
        for (i in 0 until 30) {
          val y = hash(i) * size.height
          drawLine(
            color = accent.copy(alpha = 0.035f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
          )
        }
      }
      echo.music.iad1tya.cinematic.CinematicBackdrop.CRIMSON -> {
        val r = size.minDimension * (0.9f + bassPulse * 0.2f)
        drawCircle(
          brush =
            Brush.radialGradient(
              listOf(primary.copy(alpha = (0.22f + bassPulse * 0.12f) * glow), Color.Transparent),
              center = Offset(size.width * 0.5f, size.height * 0.1f),
              radius = r,
            ),
          radius = r,
          center = Offset(size.width * 0.5f, size.height * 0.1f),
        )
        drawRect(
          brush =
            Brush.verticalGradient(
              listOf(Color.Transparent, secondary.copy(alpha = 0.2f + reaction.mid * 0.08f))
            ),
          size = size,
        )
      }
      echo.music.iad1tya.cinematic.CinematicBackdrop.DREAM -> {
        for (i in 0 until 7) {
          val cx = (hash(i, 1) * 0.9f + 0.05f + sin(t * 0.4f + i) * 0.04f) * size.width
          val cy = (hash(i, 2) * 0.9f + 0.05f + cos(t * 0.3f + i) * 0.04f) * size.height
          val r = size.minDimension * (0.22f + hash(i, 3) * 0.25f)
          drawCircle(
            brush =
              Brush.radialGradient(
                listOf(
                  (if (i % 2 == 0) primary else secondary).copy(alpha = 0.10f * glow),
                  Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = r,
              ),
            radius = r,
            center = Offset(cx, cy),
          )
        }
      }
      echo.music.iad1tya.cinematic.CinematicBackdrop.FILM -> {
        drawRect(brush = Brush.radialGradient(listOf(secondary.copy(alpha = 0.12f), Color.Transparent)), size = size)
        val grainCount = if (visual.lowEnd) 40 else 110
        for (i in 0 until grainCount) {
          drawCircle(
            color = primary.copy(alpha = 0.05f + hash(i, 7) * 0.08f),
            radius = 0.6f + hash(i, 8) * 1.4f,
            center = Offset(hash(i, 1) * size.width, hash(i, 2) * size.height),
          )
        }
      }
      echo.music.iad1tya.cinematic.CinematicBackdrop.GLITCH -> {
        val step = (t * 9f * beatSpeed).toInt()
        for (i in 0 until 16) {
          val y = hash(i, step) * size.height
          val h = 3f + hash(i, step + 1) * 22f
          drawRect(
            color = (if (i % 3 == 0) accent else primary).copy(alpha = 0.05f + reaction.intensity * 0.08f),
            topLeft = Offset(0f, y),
            size = Size(size.width, h),
          )
        }
      }
      echo.music.iad1tya.cinematic.CinematicBackdrop.MOTION -> {
        for (i in 0 until 26) {
          val y = ((hash(i) * size.height + t * beatSpeed * (30f + hash(i, 1) * 90f)) % size.height)
          drawLine(
            brush =
              Brush.horizontalGradient(
                listOf(Color.Transparent, primary.copy(alpha = 0.10f + bassPulse * 0.1f), Color.Transparent)
              ),
            start = Offset(0f, y),
            end = Offset(size.width, y - 40f),
            strokeWidth = 1f + hash(i, 2) * 3f,
          )
        }
      }
      echo.music.iad1tya.cinematic.CinematicBackdrop.CHROME -> {
        for (i in 0 until 22) {
          val y = i * size.height / 22f
          drawRect(
            brush =
              Brush.verticalGradient(
                listOf(
                  Color.Transparent,
                  (if (i % 2 == 0) primary else secondary).copy(alpha = 0.10f + bassPulse * 0.06f),
                  Color.Transparent,
                )
              ),
            topLeft = Offset(0f, y),
            size = Size(size.width, size.height / 22f),
          )
        }
      }
      echo.music.iad1tya.cinematic.CinematicBackdrop.BLOOM -> {
        for (i in 0 until 9) {
          val cx = (hash(i, 1) + sin(t * 0.35f + i) * 0.06f) * size.width
          val cy = (hash(i, 2) + cos(t * 0.3f + i) * 0.06f) * size.height
          val r = size.minDimension * (0.15f + hash(i, 3) * 0.2f) * (1f + bassPulse * 0.15f)
          drawCircle(
            brush =
              Brush.radialGradient(
                listOf(primary.copy(alpha = 0.14f * glow), Color.Transparent),
                center = Offset(cx, cy),
                radius = r,
              ),
            radius = r,
            center = Offset(cx, cy),
          )
        }
      }
      echo.music.iad1tya.cinematic.CinematicBackdrop.RAIN -> {
        for (i in 0 until 48) {
          val x = hash(i, 1) * size.width
          val y = ((hash(i, 2) * size.height + t * beatSpeed * (140f + hash(i, 3) * 180f)) % size.height)
          drawLine(
            color = primary.copy(alpha = 0.10f + reaction.treble * 0.16f),
            start = Offset(x, y),
            end = Offset(x - 5f, y + 34f),
            strokeWidth = 1.2f,
          )
        }
        drawRect(
          brush = Brush.verticalGradient(listOf(Color.Transparent, secondary.copy(alpha = 0.16f))),
          size = size,
        )
      }
      echo.music.iad1tya.cinematic.CinematicBackdrop.DEPTH -> {
        for (i in 0 until 8) {
          val r = size.minDimension * (0.18f + i * 0.09f) * (1f + bassPulse * 0.12f)
          drawCircle(
            color = (if (i % 2 == 0) primary else secondary).copy(alpha = 0.13f - i * 0.01f),
            radius = r,
            center = Offset(size.width / 2f, size.height / 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f),
          )
        }
      }
      echo.music.iad1tya.cinematic.CinematicBackdrop.SUNSET -> {
        val r = size.minDimension * (1.1f + bassPulse * 0.2f)
        drawCircle(
          brush =
            Brush.radialGradient(
              listOf(
                primary.copy(alpha = (0.28f * glow + reaction.beat * 0.05f).coerceAtMost(1f)),
                secondary.copy(alpha = 0.12f),
                Color.Transparent,
              ),
              center = Offset(size.width * 0.5f, size.height),
              radius = r,
            ),
          radius = r,
          center = Offset(size.width * 0.5f, size.height),
        )
        for (i in 0 until 10) {
          val y = size.height * (0.45f + i * 0.055f)
          drawLine(
            color = accent.copy(alpha = 0.06f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 2f,
          )
        }
      }
      echo.music.iad1tya.cinematic.CinematicBackdrop.CHAOS -> {
        val step = (t * 5f * beatSpeed).toInt()
        for (i in 0 until 10) {
          val x = hash(i, step) * size.width
          val y = hash(i, step + 3) * size.height
          drawRect(
            color = (if (i % 3 == 0) secondary else primary).copy(alpha = 0.06f + reaction.snare * 0.12f),
            topLeft = Offset(x, y),
            size = Size(size.width * (0.05f + hash(i, 4) * 0.2f), size.height * 0.04f),
          )
        }
      }
    }
  }
}

/** High-frequency particles, capped and deterministic (no per-frame allocation storms). */
@Composable
fun CinematicParticles(visual: CinematicVisualState, modifier: Modifier = Modifier) {
  if (!visual.settings.backgroundEffects) return
  val reaction = CinematicReactions.forFrame(visual.features)
  val count =
    (10 + visual.theme.particles * 30f * visual.settings.particles *
      (0.4f + 0.6f * reaction.liveliness))
      .toInt()
      .coerceAtMost(if (visual.lowEnd) 18 else 60)
  val t = visual.positionMs / 1000f * visual.settings.animationSpeed
  val driftScale = 0.5f + 0.5f * reaction.liveliness
  val primary = color(visual.theme.primary)
  val accent = color(visual.theme.accent)
  Canvas(modifier) {
    for (i in 0 until count) {
      val seedX = hash(i, 11)
      val seedY = hash(i, 12)
      val drift = (12f + hash(i, 13) * 34f) * driftScale
      val x = ((seedX * size.width + t * drift) % size.width + size.width) % size.width
      val y = ((seedY * size.height - t * drift * 0.45f) % size.height + size.height) % size.height
      val alpha =
        (0.12f + reaction.treble * 0.5f + reaction.snare * 0.25f + reaction.kick * 0.15f) *
          visual.settings.particles.coerceIn(0f, 2f)
      drawCircle(
        color = if (i % 4 == 0) accent else primary,
        radius = 1f + hash(i, 14) * 2.2f,
        center = Offset(x, y),
        alpha = alpha.coerceIn(0f, 1f),
      )
    }
  }
}

/** Abstract, audio-reactive scene used for instrumentals and songs with no lyrics. */
@Composable
fun CinematicInstrumentalScene(visual: CinematicVisualState, modifier: Modifier = Modifier) {
  val t = visual.positionMs / 1000f * visual.settings.animationSpeed
  val reaction = CinematicReactions.forFrame(visual.features)
  val primary = color(visual.theme.primary)
  val secondary = color(visual.theme.secondary)
  val ambientGlow = visual.settings.glow.coerceIn(0f, 2f) * (0.4f + 0.6f * reaction.liveliness)
  Canvas(modifier) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val base = size.minDimension * 0.16f
    val pulse =
      1f + reaction.bass * 0.3f * visual.settings.bassReaction +
        reaction.drop * 0.45f + reaction.kick * 0.12f
    val radius = base * 3f * pulse
    drawCircle(
      brush =
        Brush.radialGradient(
          listOf(primary.copy(alpha = (0.18f * ambientGlow).coerceAtMost(1f)), Color.Transparent),
          center = Offset(cx, cy),
          radius = radius,
        ),
      radius = radius,
      center = Offset(cx, cy),
    )
    drawCircle(
      color = primary.copy(alpha = 0.45f),
      radius = base * pulse,
      center = Offset(cx, cy),
      style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
    )
    val bars = if (visual.lowEnd) 28 else 72
    for (i in 0 until bars) {
      val angle = (i.toFloat() / bars) * Math.PI.toFloat() * 2f + t * 0.08f
      val amplitude =
        base * (0.18f + reaction.energy * 0.85f + hash(i, 21) * 0.25f) *
          (0.5f + 0.5f * reaction.liveliness)
      val x1 = cx + cos(angle.toDouble()).toFloat() * base * pulse
      val y1 = cy + sin(angle.toDouble()).toFloat() * base * pulse
      val x2 = cx + cos(angle.toDouble()).toFloat() * (base * pulse + amplitude)
      val y2 = cy + sin(angle.toDouble()).toFloat() * (base * pulse + amplitude)
      drawLine(
        color = if (i % 3 == 0) secondary else primary,
        start = Offset(x1, y1),
        end = Offset(x2, y2),
        strokeWidth = 2f,
        alpha = 0.55f,
      )
    }
  }
}

private data class WordPose(
  val x: Float,
  val y: Float,
  val scale: Float,
  val rotation: Float,
  val alpha: Float,
  val blur: Float,
)

private fun sectionScale(section: SongSection): Float =
  when (section) {
    SongSection.CHORUS -> 1.18f
    SongSection.INTRO -> 0.84f
    SongSection.BRIDGE -> 0.92f
    SongSection.OUTRO -> 0.88f
    else -> 1f
  }

private fun poseFor(
  theme: CinematicTheme,
  slot: TypeSlot,
  index: Int,
  word: KaraokeWord,
  positionMs: Long,
  visual: CinematicVisualState,
): WordPose {
  val appear =
    ((positionMs - (word.startMs - LEAD_IN_MS)).toFloat() / LEAD_IN_MS).coerceIn(0f, 1f)
  val leave =
    ((word.endMs + FADE_OUT_MS - positionMs).toFloat() / FADE_OUT_MS).coerceIn(0f, 1f)
  val visibility = minOf(appear, leave)
  val t = positionMs / 1000f * visual.settings.animationSpeed
  val reaction = CinematicReactions.forFrame(visual.features)
  val motion =
    (if (visual.settings.reducedMotion) 0.08f else visual.settings.motion) *
      (0.4f + 0.6f * reaction.liveliness)
  var x = slot.x
  var y = slot.y
  var scale = 0.86f + 0.14f * appear
  var rotation = slot.rotation * (1.35f - 0.35f * appear)
  var blur = if (visual.settings.reducedMotion) 0f else (1f - appear) * 7f

  when (theme.motion) {
    CinematicMotion.FLOAT -> y += sin(t * 0.6f + index) * 0.02f * motion
    CinematicMotion.DRIFT -> {
      x += sin(t * 0.4f + index) * 0.03f * motion
      y += cos(t * 0.3f + index) * 0.015f * motion
    }
    CinematicMotion.SLIDE -> x += (1f - appear) * 0.06f * motion
    CinematicMotion.PULSE ->
      scale *= 1f + reaction.beat * 0.06f * visual.settings.bassReaction * motion
    CinematicMotion.GLITCH -> {
      val step = (t * 8f).toInt()
      x += (hash(index, step) - 0.5f) * 0.07f * motion
      rotation += (hash(index, step + 1) - 0.5f) * 14f * motion
    }
    CinematicMotion.BLUR -> {
      x += sin(t * 1.6f + index) * 0.05f * motion
      blur += (1f - appear) * 4f
    }
    CinematicMotion.CHROME -> {
      y += sin(t * 0.5f + index) * 0.012f * motion
      scale *= 1f + reaction.bass * 0.05f * motion
    }
    CinematicMotion.PETAL -> {
      x += sin(t * 0.45f + index) * 0.05f * motion
      y += cos(t * 0.35f + index) * 0.025f * motion
    }
    CinematicMotion.RAIN -> y += ((t * 0.05f + hash(index, 5)) % 1f - 0.5f) * 0.04f * motion
    CinematicMotion.PERSPECTIVE -> {
      scale *= 0.78f + (index % 3) * 0.13f
      rotation += sin(t * 0.5f + index) * 9f * motion
    }
    CinematicMotion.SCATTER -> {
      rotation *= 1.7f
      x += (hash(index, 9) - 0.5f) * 0.05f * motion
    }
    CinematicMotion.CHAOS -> {
      scale *= if (index % 3 == 0) 1.32f else 0.86f
      rotation += (hash(index, 10) - 0.5f) * 22f * motion
    }
  }
  // Short, intensity-scaled expansion on real kicks; never a hard flash.
  scale *= 1f + reaction.kick * 0.035f * motion
  return WordPose(x, y, scale, rotation, visibility, blur.coerceAtMost(10f))
}

/** Word-by-word kinetic typography driven by verified vocal timestamps. */
@Composable
fun CinematicLyricsLayer(
  visual: CinematicVisualState,
  words: List<KaraokeWord>,
  screenWidth: Dp,
  screenHeight: Dp,
  modifier: Modifier = Modifier,
) {
  val theme = visual.theme
  val family = typeface(theme)
  val primary = color(theme.primary)
  val accent = color(theme.accent)
  val muted = color(theme.muted)
  val textColor = color(theme.text)
  val isItalic = theme.typeface == CinematicTypeface.SERIF_ITALIC
  val reaction = CinematicReactions.forFrame(visual.features)
  val glow = visual.settings.glow.coerceIn(0f, 2f) * (0.5f + 0.5f * reaction.liveliness)
  val animatedSectionScale by
    animateFloatAsState(
      targetValue = sectionScale(visual.section),
      animationSpec = tween(700, easing = FastOutSlowInEasing),
      label = "cinematic-section",
    )

  Box(modifier) {
    words.forEachIndexed { index, word ->
      val slot = TYPE_SLOTS[index % TYPE_SLOTS.size]
      val pose = poseFor(theme, slot, index, word, visual.positionMs, visual)
      if (pose.alpha <= 0.01f) return@forEachIndexed

      val isCurrent = visual.positionMs in word.startMs..word.endMs
      val isPast = visual.positionMs > word.endMs
      val progress = KaraokeTiming.progressFor(word, visual.positionMs).coerceIn(0.02f, 0.98f)
      val baseAlpha =
        when {
          isCurrent -> visual.settings.vocalHighlight.coerceIn(0.2f, 2f).coerceAtMost(1f)
          isPast -> 0.24f
          else -> 0.16f
        }
      val brush =
        if (isCurrent) {
          Brush.linearGradient(
            0f to primary,
            progress to accent,
            progress to muted.copy(alpha = 0.35f),
            1f to muted.copy(alpha = 0.35f),
          )
        } else {
          SolidColor(if (isPast) textColor.copy(alpha = 0.4f) else muted)
        }
      val fontSize = slot.sizeSp * animatedSectionScale
      val style =
        TextStyle(
          fontFamily = family,
          fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
          fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
          fontSize = fontSize.sp,
          letterSpacing = slot.letterSpacingSp.sp,
          brush = brush,
          textAlign = slot.align,
          shadow =
            if (isCurrent && glow > 0f) {
              Shadow(color = primary.copy(alpha = (0.6f * glow).coerceAtMost(1f)), blurRadius = 20f * glow)
            } else {
              null
            },
        )
      val alignment =
        when (slot.align) {
          TextAlign.Center -> Alignment.Center
          TextAlign.End -> Alignment.CenterEnd
          else -> Alignment.CenterStart
        }

      Box(
        modifier =
          Modifier
            .offset(x = screenWidth * pose.x, y = screenHeight * pose.y)
            .widthIn(max = screenWidth * slot.widthFraction)
            .graphicsLayer {
              alpha = (pose.alpha * baseAlpha).coerceIn(0f, 1f)
              scaleX = pose.scale
              scaleY = pose.scale
              rotationZ = if (visual.settings.reducedMotion) 0f else pose.rotation
            }
            .then(if (pose.blur > 0.5f) Modifier.blur(pose.blur.dp) else Modifier),
        contentAlignment = alignment,
      ) {
        if (isCurrent && glow > 0.1f) {
          Text(
            text = word.text,
            style = style.copy(brush = SolidColor(primary), shadow = null),
            modifier = Modifier.blur(12.dp).alpha((0.35f * glow).coerceAtMost(0.8f)),
          )
        }
        Text(
          text = word.text,
          style = style,
          modifier =
            Modifier.drawBehind {
              if (slot.emphasis) {
                val stroke = 3.dp.toPx()
                val lineY = size.height + 6.dp.toPx()
                drawLine(
                  brush =
                    Brush.horizontalGradient(
                      colors = listOf(accent.copy(alpha = 0.95f), Color.Transparent)
                    ),
                  start = Offset(0f, lineY),
                  end = Offset(size.width * progress, lineY),
                  strokeWidth = stroke,
                )
              }
            },
        )
      }
    }
  }
}
