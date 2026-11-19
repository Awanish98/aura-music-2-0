package com.music.echo.insta

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import echo.music.iad1tya.R
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Audio-reaction values from real analysis. They never change lyric selection or timing. */
data class InstaReactionInput(
  val beat: Float = 0f,
  val bass: Float = 0f,
  val energy: Float = 0f,
  val silence: Boolean = false,
)

/**
 * Draws one INSTA LRC frame onto any Android [Canvas]. The same renderer is used
 * by the live Compose surface and the settings preview, so what users preview is
 * exactly what they see during playback.
 */
object InstaFrameRenderer {
  fun render(
    canvas: Canvas,
    width: Int,
    height: Int,
    timeMs: Long,
    spec: InstaRenderSpec,
    reaction: InstaReactionInput = InstaReactionInput(),
    backgroundBitmap: Bitmap? = null,
    drawBackground: Boolean = true,
    context: Context? = null,
  ) {
    if (width <= 0 || height <= 0) return
    canvas.save()
    if (drawBackground) {
      drawBackground(canvas, width, height, spec, timeMs, backgroundBitmap)
    } else {
      drawScrim(canvas, width, height, spec)
    }

    val words = spec.words
    val phrases = InstaLrcPolicy.groupWords(words)
    val phrase = InstaLrcPolicy.phraseAt(phrases, timeMs)
    if (phrase.isEmpty()) {
      drawInstrumental(canvas, width, height, spec, context)
    } else {
      val frames =
        InstaLrcPolicy.framesForPhrase(
          phrase = phrase,
          positionMs = timeMs,
          preset = spec.preset,
          style = spec.effectiveStyle,
          energy = if (reaction.silence) 0f else reaction.energy.coerceIn(0f, 1f),
          beatImpact = if (reaction.silence) 0f else reaction.beat.coerceIn(0f, 1f),
          bass = if (reaction.silence) 0f else reaction.bass.coerceIn(0f, 1f),
          language = spec.language,
        )
      frames
        .sortedBy { if (it.active) 1 else 0 }
        .forEach { frame -> drawWord(canvas, width, height, frame, spec, context) }
      if (!InstaLrcPolicy.isVerified(phrase)) {
        drawTimingHint(canvas, width, height, spec.preset, context)
      }
    }
    drawWatermark(canvas, width, height, spec, context)
    canvas.restore()
  }

  private fun drawBackground(
    canvas: Canvas,
    width: Int,
    height: Int,
    spec: InstaRenderSpec,
    timeMs: Long,
    bitmap: Bitmap?,
  ) {
    val preset = spec.preset
    val style = spec.effectiveStyle
    val intensity = preset.backgroundIntensity.coerceIn(0f, 1f)
    val accent = colorFromLong(preset.highlightColor)
    canvas.drawColor(Color.BLACK)

    if (bitmap != null && spec.preset.background in setOf(
        InstaBackground.ALBUM_ARTWORK,
        InstaBackground.CUSTOM_IMAGE,
        InstaBackground.CUSTOM_VIDEO,
      )
    ) {
      val source = Rect(0, 0, bitmap.width, bitmap.height)
      val scale = maxOf(width / bitmap.width.toFloat(), height / bitmap.height.toFloat())
      val drawW = bitmap.width * scale
      val drawH = bitmap.height * scale
      val left = (width - drawW) / 2f
      val top = (height - drawH) / 2f
      val paint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
          alpha = (0.55f * intensity * 255).toInt().coerceIn(0, 255)
        }
      canvas.drawBitmap(bitmap, source, RectF(left, top, left + drawW, top + drawH), paint)
      drawScrim(canvas, width, height, spec)
      return
    }

    when (spec.preset.background) {
      InstaBackground.PURE_BLACK -> Unit
      InstaBackground.GRADIENT -> {
        val paint =
          Paint().apply {
            shader =
              LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                Color.argb((150 * intensity).toInt(), Color.red(accent) / 3, Color.green(accent) / 4, Color.blue(accent) / 3),
                Color.BLACK,
                Shader.TileMode.CLAMP,
              )
          }
        canvas.drawPaint(paint)
      }
      InstaBackground.VINTAGE_PAINTING,
      InstaBackground.FLORAL_TEXTURE,
      InstaBackground.CINEMATIC_SCENERY,
      InstaBackground.VINTAGE_FILM,
      -> {
        val warm =
          when (spec.preset.background) {
            InstaBackground.VINTAGE_FILM -> Color.argb((120 * intensity).toInt(), 78, 96, 82)
            InstaBackground.CINEMATIC_SCENERY -> Color.argb((130 * intensity).toInt(), 86, 66, 48)
            else -> Color.argb((140 * intensity).toInt(), 116, 82, 48)
          }
        val paint =
          Paint().apply {
            shader =
              RadialGradient(
                width * 0.42f,
                height * 0.5f,
                min(width, height) * 0.95f,
                intArrayOf(warm, Color.BLACK),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
              )
          }
        canvas.drawPaint(paint)
        drawDecorations(canvas, width, height, spec, timeMs)
      }
      InstaBackground.ALBUM_ARTWORK,
      InstaBackground.CUSTOM_IMAGE,
      InstaBackground.CUSTOM_VIDEO,
      -> drawScrim(canvas, width, height, spec)
    }

    if (!preset.lowPower && preset.grainIntensity > 0f) {
      drawGrain(canvas, width, height, spec, timeMs)
    }
    drawVignette(canvas, width, height, intensity)
  }

  private fun drawDecorations(
    canvas: Canvas,
    width: Int,
    height: Int,
    spec: InstaRenderSpec,
    timeMs: Long,
  ) {
    if (spec.preset.lowPower) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val accent = colorFromLong(spec.preset.highlightColor)
    val (dx, dy) = InstaLrcPolicy.driftFor(spec.effectiveStyle, timeMs, spec.preset.animationIntensity)
    val random = Random(17L)
    repeat(if (spec.preset.background == InstaBackground.FLORAL_TEXTURE) 18 else 7) { index ->
      val cx = width * (0.08f + random.nextFloat() * 0.84f) + dx * (index % 3 + 1)
      val cy = height * (0.08f + random.nextFloat() * 0.84f) + dy * (index % 2 + 1)
      val radius = min(width, height) * (0.035f + random.nextFloat() * 0.08f)
      paint.color = Color.argb(14, Color.red(accent), Color.green(accent), Color.blue(accent))
      paint.style = Paint.Style.STROKE
      paint.strokeWidth = 2f
      canvas.save()
      canvas.translate(cx, cy)
      canvas.rotate(random.nextFloat() * 360f)
      canvas.drawOval(-radius, -radius * 0.42f, radius, radius * 0.42f, paint)
      canvas.restore()
    }
    if (spec.effectiveStyle == InstaVisualStyle.VINTAGE_FILM) {
      paint.style = Paint.Style.STROKE
      paint.strokeWidth = 1f
      repeat(6) { index ->
        val x = width * ((index + 1) / 7f) + sin(timeMs / 2500f + index) * 3f
        paint.color = Color.argb(22, 210, 218, 202)
        canvas.drawLine(x, height * 0.04f, x + 2f, height * 0.96f, paint)
      }
    }
  }

  private fun drawScrim(canvas: Canvas, width: Int, height: Int, spec: InstaRenderSpec) {
    val alpha = (0.42f * spec.preset.backgroundIntensity.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
    val paint =
      Paint().apply {
        shader =
          LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            Color.argb(alpha, 0, 0, 0),
            Color.argb((alpha * 0.72f).toInt(), 0, 0, 0),
            Shader.TileMode.CLAMP,
          )
      }
    canvas.drawPaint(paint)
  }

  private fun drawGrain(canvas: Canvas, width: Int, height: Int, spec: InstaRenderSpec, timeMs: Long) {
    val amount = spec.preset.grainIntensity.coerceIn(0f, 1f)
    val random = Random(timeMs / 120L + spec.title.hashCode())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    repeat((44 * amount).toInt().coerceAtLeast(6)) {
      val x = random.nextFloat() * width
      val y = random.nextFloat() * height
      val radius = 0.6f + random.nextFloat() * 1.6f
      paint.color = Color.argb((random.nextFloat() * 46 * amount).toInt(), 236, 226, 204)
      canvas.drawCircle(x, y, radius, paint)
    }
  }

  private fun drawVignette(canvas: Canvas, width: Int, height: Int, intensity: Float) {
    val paint =
      Paint().apply {
        shader =
          RadialGradient(
            width / 2f,
            height / 2f,
            maxOf(width, height) * 0.72f,
            intArrayOf(
              Color.TRANSPARENT,
              Color.argb((150 * intensity).toInt(), 0, 0, 0),
            ),
            floatArrayOf(0.28f, 1f),
            Shader.TileMode.CLAMP,
          )
      }
    canvas.drawPaint(paint)
  }

  private fun drawWord(
    canvas: Canvas,
    width: Int,
    height: Int,
    frame: InstaWordFrame,
    spec: InstaRenderSpec,
    context: Context?,
  ) {
    val preset = spec.preset
    val accent = colorFromLong(preset.highlightColor)
    val baseText =
      if (frame.important) accent
      else if (spec.effectiveStyle == InstaVisualStyle.VINTAGE_FILM) Color.argb(235, 218, 224, 212)
      else Color.WHITE
    val visible = frame.word.text.take(frame.revealChars)
    if (visible.isBlank()) return
    val alpha = (frame.alpha * 255).toInt().coerceIn(0, 255)
    val textColor = Color.argb(alpha, Color.red(baseText), Color.green(baseText), Color.blue(baseText))

    val paint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        typeface = typefaceFor(preset.font, context)
        // sizeScale already contains the preset font-size multiplier; applying it
        // again here made small settings explode and large settings clip.
        textSize =
          (min(width, height) * baseSizeFactor(spec.effectiveStyle) * frame.sizeScale)
            .coerceAtMost(min(width, height) * 0.2f)
        isFakeBoldText = preset.font != InstaFont.DISPLAY && preset.fontWeight >= 700
        textAlign =
          when (preset.alignment) {
            InstaTextAlign.START -> Paint.Align.LEFT
            InstaTextAlign.CENTER -> Paint.Align.CENTER
            InstaTextAlign.END -> Paint.Align.RIGHT
          }
        if (frame.glow > 0f && !preset.lowPower) {
          setShadowLayer(18f * frame.glow, 0f, 0f, accent)
        }
      }

    val anchorX = width * frame.x
    val anchorY = height * frame.y
    val lines = wrapText(visible, paint, width * 0.86f)
    val lineHeight = paint.fontSpacing * preset.spacing.coerceIn(0.7f, 1.8f)

    canvas.save()
    canvas.translate(anchorX, anchorY)
    canvas.rotate(frame.rotationDegrees)
    var y = -((lines.size - 1) * lineHeight) / 2f - paint.descent()
    repeat(frame.blurPasses) { pass ->
      val ghostAlpha = (alpha * (0.18f - pass * 0.04f)).toInt().coerceAtLeast(0)
      paint.color = Color.argb(ghostAlpha, Color.red(accent), Color.green(accent), Color.blue(accent))
      val offset = (pass + 1) * 5f * preset.blur.coerceIn(0f, 2f)
      lines.forEachIndexed { index, line ->
        canvas.drawText(line, offset, y + index * lineHeight + offset * 0.35f, paint)
      }
      paint.color = textColor
    }
    lines.forEachIndexed { index, line ->
      if (frame.pattern == InstaAnimationPattern.SPLIT_TEXT && line.length > 2) {
        val split = line.length / 2
        paint.color = textColor
        canvas.drawText(line.substring(0, split), -6f, y + index * lineHeight, paint)
        paint.color =
          Color.argb(alpha, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawText(line.substring(split), 6f, y + index * lineHeight, paint)
        paint.color = textColor
      } else if (frame.pattern == InstaAnimationPattern.VERTICAL_REVEAL) {
        drawVertical(canvas, line, paint, y + index * lineHeight)
      } else {
        canvas.drawText(line, 0f, y + index * lineHeight, paint)
      }
    }
    canvas.restore()
  }

  private fun drawVertical(canvas: Canvas, text: String, paint: Paint, startY: Float) {
    val step = paint.fontSpacing * 0.92f
    text.forEachIndexed { index, char ->
      canvas.drawText(char.toString(), 0f, startY + index * step, paint)
    }
  }

  private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
    if (text.length <= 1 || paint.measureText(text) <= maxWidth) return listOf(text)
    val lines = mutableListOf<String>()
    var current = ""
    text.forEach { char ->
      val candidate = current + char
      if (current.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
        lines.add(current.trimEnd())
        current = char.toString()
      } else {
        current = candidate
      }
    }
    if (current.isNotBlank()) lines.add(current)
    return lines.ifEmpty { listOf(text) }
  }

  private fun baseSizeFactor(style: InstaVisualStyle): Float =
    when (style) {
      InstaVisualStyle.BOLD_TYPOGRAPHY -> 0.118f
      // Legacy enum values are kept for stored presets, but they now inherit the
      // same oversized treatment so no saved look renders tiny text.
      InstaVisualStyle.VINTAGE_CINEMATIC -> 0.112f
      InstaVisualStyle.MINIMAL_KINETIC -> 0.112f
      InstaVisualStyle.VINTAGE_FILM -> 0.112f
      InstaVisualStyle.SMART_HYBRID -> 0.112f
    }

  private val typefaceCache = ConcurrentHashMap<String, Typeface>()

  private fun typefaceFor(font: InstaFont, context: Context?): Typeface =
    typefaceCache.getOrPut(font.name) {
      val resId =
        when (font) {
          InstaFont.SERIF -> R.font.cormorant_garamond_italic
          InstaFont.SANS -> R.font.outfit
          InstaFont.DISPLAY -> R.font.bbh_bartle_regular
          InstaFont.MONO -> return@getOrPut Typeface.MONOSPACE
          InstaFont.HANDWRITTEN -> R.font.cormorant_garamond_italic
          InstaFont.GENZ -> R.font.outfit
        }
      context
        ?.let { ResourcesCompat.getFont(it.applicationContext, resId) }
        ?: fallbackTypeface(font)
    }

  private fun fallbackTypeface(font: InstaFont): Typeface =
    when (font) {
      InstaFont.SERIF -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
      InstaFont.SANS -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
      InstaFont.DISPLAY -> Typeface.DEFAULT_BOLD
      InstaFont.MONO -> Typeface.MONOSPACE
      InstaFont.HANDWRITTEN -> Typeface.create(Typeface.SERIF, Typeface.ITALIC)
      InstaFont.GENZ -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

  private fun drawInstrumental(
    canvas: Canvas,
    width: Int,
    height: Int,
    spec: InstaRenderSpec,
    context: Context?,
  ) {
    val paint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 235, 229, 216)
        typeface = typefaceFor(InstaFont.SERIF, context)
        textSize = min(width, height) * 0.052f
        textAlign = Paint.Align.CENTER
      }
    val label = "Instrumental · ${spec.title}"
    canvas.drawText(label, width / 2f, height / 2f, paint)
  }

  private fun drawTimingHint(
    canvas: Canvas,
    width: Int,
    height: Int,
    preset: InstaPreset,
    context: Context?,
  ) {
    val paint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255)
        typeface = typefaceFor(InstaFont.SANS, context)
        textSize = min(width, height) * 0.024f
        textAlign = Paint.Align.RIGHT
      }
    canvas.drawText("Estimated word timing", width - preset.safeMargin * width, height - preset.safeMargin * height, paint)
  }

  private fun drawWatermark(
    canvas: Canvas,
    width: Int,
    height: Int,
    spec: InstaRenderSpec,
    context: Context?,
  ) {
    if (spec.preset.watermark.isBlank()) return
    val paint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
        typeface = typefaceFor(InstaFont.SANS, context)
        textSize = min(width, height) * 0.024f
        textAlign = Paint.Align.LEFT
      }
    canvas.drawText(
      spec.preset.watermark,
      spec.preset.safeMargin * width,
      height - spec.preset.safeMargin * height,
      paint,
    )
  }

  private fun colorFromLong(value: Long): Int =
    Color.argb(
      Color.alpha(value),
      Color.red(value),
      Color.green(value),
      Color.blue(value),
    )
}
