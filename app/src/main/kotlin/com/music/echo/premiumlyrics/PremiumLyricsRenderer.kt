package com.music.echo.premiumlyrics

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.PorterDuff
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure Android-Canvas renderer for PREMIUM LYRICS. Every glyph comes from
 * [PremiumFrame.lines]; audio only changes how those glyphs move and glow.
 *
 * All fifteen looks share one editorial baseline — large auto-fit lines, a
 * heavier current word, tight tracking and a progressive karaoke fill — but
 * each look owns a distinct palette, typeface and signature effect so switching
 * looks is a real change of scene, not a recolour.
 */
object PremiumLyricsRenderer {
  private data class PlacedWord(
    val word: PremiumWordFrame,
    val x: Float,
    val baseline: Float,
    val size: Float,
    val width: Float,
  )

  private data class PlacedLine(
    val words: List<PlacedWord>,
    val baseline: Float,
    val size: Float,
    val centerX: Float,
    val prominence: Float,
    val isActive: Boolean,
  )

  private data class Faces(val regular: Typeface?, val bold: Typeface?)

  private typealias WordDrawer = (PlacedLine, PlacedWord, Float, Float) -> Unit

  fun render(
    canvas: Canvas,
    width: Int,
    height: Int,
    frame: PremiumFrame,
    typeface: Typeface? = null,
    transparentBackground: Boolean = false,
  ) {
    if (width <= 0 || height <= 0) return
    if (transparentBackground) {
      canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    } else {
      canvas.drawColor(Color.BLACK)
    }

    val style = frame.style
    val primary = style.primaryArgb.toInt()
    val accent = style.accentArgb.toInt()
    val glow = style.glowArgb.toInt()
    val time = frame.frameTimeMs / 1000f
    val look = style.look
    val faces = Faces(typeface, typeface?.let { Typeface.create(it, Typeface.BOLD) })

    val lines = frame.lines
    if (lines.isEmpty()) {
      drawIdle(canvas, width, height, frame, faces.regular, primary)
      drawChrome(canvas, width, height, frame, faces.regular, style, primary, accent)
      return
    }

    val activeIndex =
      lines.indexOfFirst { it.isActive }.takeIf { it >= 0 }
        ?: lines.indices.minByOrNull { index ->
          val line = lines[index]
          val first = line.words.firstOrNull()?.startMs ?: 0L
          val last = line.words.lastOrNull()?.endMs ?: first
          abs(first + (last - first) / 2 - frame.positionMs)
        } ?: 0

    val baseSize = (minOf(width / 9.5f, height / 13.5f)).coerceIn(32f, 108f)
    val lineGap = baseSize * 1.92f
    val activeBaseline = height * 0.44f
    val window =
      (-2..2).mapNotNull { offset ->
        val index = activeIndex + offset
        lines.getOrNull(index)?.let { it to index }
      }

    val base =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        if (faces.regular != null) this.typeface = faces.regular
        textAlign = Paint.Align.LEFT
        isLinearText = true
      }

    val placed =
      window.map { (line, index) ->
        val distance = index - activeIndex
        val isActive = distance == 0
        val prominence =
          when (abs(distance)) {
            0 -> 1f
            1 -> 0.5f
            else -> 0.26f
          }
        val lineSize =
          baseSize *
            when {
              isActive -> 1.06f
              abs(distance) == 1 -> 0.78f
              else -> 0.6f
            }
        val currentWordIndex = line.words.indexOfFirst { it.isCurrent }
        var sizes =
          line.words.indices.map { i ->
            if (!isActive) {
              lineSize
            } else if (i == currentWordIndex) {
              lineSize * 1.16f
            } else if (currentWordIndex >= 0 && abs(i - currentWordIndex) == 1) {
              lineSize
            } else {
              lineSize * 0.92f
            }
          }
        var gap = lineSize * 0.25f
        var widths =
          sizes.mapIndexed { i, size ->
            val word = line.words[i]
            val useBold = isActive && (i == currentWordIndex || word.isPast)
            measureWord(base, faces, word.text, size, useBold)
          }
        var total = widths.sum() + gap * (line.words.size - 1).coerceAtLeast(0)
        val available = width * 0.88f
        if (total > available) {
          val fit = available / total
          sizes = sizes.map { it * fit }
          widths = widths.map { it * fit }
          gap *= fit
          total = available
        }
        var cursor = (width - total) / 2f
        val baseline = activeBaseline + distance * lineGap
        val words =
          line.words.mapIndexed { i, word ->
            val item =
              PlacedWord(
                word = word,
                x = cursor,
                baseline = baseline,
                size = sizes[i],
                width = widths[i],
              )
            cursor += widths[i] + gap
            item
          }
        PlacedLine(words, baseline, lineSize, width / 2f, prominence, isActive)
      }

    // A soft pool of light behind the active line; the rest of the canvas stays black.
    if (!frame.lowPower && look != PremiumLook.NOIR && look != PremiumLook.TERMINAL) {
      val haloRadius = baseSize * (if (look == PremiumLook.GOLDEN) 6f else 5.2f)
      val halo =
        RadialGradient(
          width / 2f,
          activeBaseline - baseSize * 0.2f,
          haloRadius,
          withAlpha(glow, (if (look == PremiumLook.GOLDEN) 0.16f else 0.1f) * frame.glow),
          Color.TRANSPARENT,
          Shader.TileMode.CLAMP,
        )
      canvas.drawRect(
        width / 2f - haloRadius,
        activeBaseline - haloRadius,
        width / 2f + haloRadius,
        activeBaseline + haloRadius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = halo },
      )
    }

    val paintWord: WordDrawer = { line, word, dx, dy ->
      drawKaraokeWord(canvas, word, line, base, faces, frame, primary, accent, glow, dx, dy)
    }

    when (look) {
      PremiumLook.AURORA -> drawAurora(canvas, placed, frame, time, accent, glow, paintWord)
      PremiumLook.NOIR -> drawNoir(canvas, placed, width, accent, paintWord)
      PremiumLook.GOLDEN -> drawGolden(canvas, placed, width, height, frame, time, accent, paintWord)
      PremiumLook.NEON -> drawNeon(canvas, placed, frame, time, base, glow, paintWord)
      PremiumLook.EMBER -> drawEmber(canvas, placed, frame, time, width, height, accent, glow, paintWord)
      PremiumLook.PRISM -> drawPrism(canvas, placed, frame, base, accent, glow, paintWord)
      PremiumLook.CHROME -> drawChromeLook(canvas, placed, frame, base, accent, glow, paintWord)
      PremiumLook.TERMINAL -> drawTerminal(canvas, placed, frame, time, width, height, base, accent, paintWord)
      PremiumLook.HOLOGRAM -> drawHologram(canvas, placed, frame, time, width, height, base, accent, glow, paintWord)
      PremiumLook.ORBIT -> drawOrbit(canvas, placed, frame, time, accent, paintWord)
      PremiumLook.TIDAL -> drawTidal(canvas, placed, frame, time, accent, paintWord)
      PremiumLook.DEPTH -> drawDepth(canvas, placed, frame, base, glow, paintWord)
      PremiumLook.ECHO -> drawEcho(canvas, placed, frame, base, accent, glow, paintWord)
      PremiumLook.POSTER -> drawPoster(canvas, placed, frame, base, accent, glow, paintWord)
      PremiumLook.STARDUST -> drawStardust(canvas, placed, frame, time, accent, glow, paintWord)
    }

    drawChrome(canvas, width, height, frame, faces.regular, style, primary, accent)
  }

  private fun measureWord(base: Paint, faces: Faces, text: String, size: Float, bold: Boolean): Float =
    Paint(base).apply {
      textSize = size
      letterSpacing = -size * 0.018f
      if (bold && faces.bold != null) typeface = faces.bold
    }.measureText(text)

  /** One word: quiet base, real weight on the current word, progressive fill. */
  private fun drawKaraokeWord(
    canvas: Canvas,
    word: PlacedWord,
    line: PlacedLine,
    base: Paint,
    faces: Faces,
    frame: PremiumFrame,
    primary: Int,
    accent: Int,
    glow: Int,
    dx: Float,
    dy: Float,
  ) {
    val w = word.word
    val isCurrent = w.isCurrent
    val x = word.x + dx
    val y = word.baseline + dy
    val paint =
      Paint(base).apply {
        textSize = word.size
        letterSpacing = -word.size * 0.018f
        if (faces.bold != null && (isCurrent || line.isActive && w.isPast)) this.typeface = faces.bold
        color =
          when {
            !line.isActive -> withAlpha(primary, line.prominence * 0.9f)
            isCurrent -> withAlpha(Color.WHITE, 0.32f)
            w.isPast -> withAlpha(Color.WHITE, 0.9f)
            else -> withAlpha(primary, 0.6f)
          }
        if (isCurrent && frame.style.look != PremiumLook.NOIR) {
          setShadowLayer(12f * frame.glow, 0f, 0f, withAlpha(glow, 0.6f))
        }
      }
    canvas.drawText(w.text, x, y, paint)
    paint.clearShadowLayer()

    if (line.isActive && w.progress > 0.01f) {
      val fillWidth = word.width * w.progress.coerceIn(0f, 1f)
      canvas.save()
      canvas.clipRect(x - 2f, y - word.size * 1.15f, x + fillWidth, y + word.size * 0.35f)
      val gradient =
        LinearGradient(
          x,
          y - word.size,
          x + word.width,
          y,
          intArrayOf(withAlpha(accent, 1f), withAlpha(glow, 1f), withAlpha(Color.WHITE, 0.96f)),
          floatArrayOf(0f, 0.55f, 1f),
          Shader.TileMode.CLAMP,
        )
      canvas.drawText(w.text, x, y, Paint(paint).apply { shader = gradient })
      canvas.restore()
    }

    if (line.isActive && w.progress > 0.02f && frame.style.look != PremiumLook.NOIR) {
      val bar =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = withAlpha(accent, 0.85f)
          setShadowLayer(6f * frame.glow, 0f, 0f, withAlpha(glow, 0.5f))
        }
      val barY = y + word.size * 0.24f
      canvas.drawRoundRect(
        x,
        barY,
        x + word.width * w.progress.coerceIn(0f, 1f),
        barY + 2.5f,
        1.25f,
        1.25f,
        bar,
      )
      bar.clearShadowLayer()
    }
  }

  private fun drawSoftText(
    canvas: Canvas,
    word: PlacedWord,
    base: Paint,
    color: Int,
    alpha: Float,
    radius: Float,
    dx: Float = 0f,
    dy: Float = 0f,
  ) {
    val paint =
      Paint(base).apply {
        textSize = word.size
        letterSpacing = -word.size * 0.018f
        this.color = withAlpha(color, alpha)
        setShadowLayer(radius, 0f, 0f, withAlpha(color, alpha * 0.7f))
      }
    canvas.drawText(word.word.text, word.x + dx, word.baseline + dy, paint)
    paint.clearShadowLayer()
  }

  private fun drawIdle(
    canvas: Canvas,
    width: Int,
    height: Int,
    frame: PremiumFrame,
    typeface: Typeface?,
    primary: Int,
  ) {
    val paint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        if (typeface != null) this.typeface = typeface
        color = withAlpha(primary, 0.55f)
        textSize = (minOf(width, height) * 0.035f).coerceIn(15f, 26f)
        letterSpacing = 1.5f
        textAlign = Paint.Align.CENTER
      }
    val text = if (frame.title.isBlank()) "Instrumental" else "Instrumental · ${frame.title}"
    canvas.drawText(text, width / 2f, height * 0.48f, paint)
  }

  // ── 01 AURORA ─────────────────────────────────────────────────────────────
  private fun drawAurora(
    canvas: Canvas,
    lines: List<PlacedLine>,
    frame: PremiumFrame,
    time: Float,
    accent: Int,
    glow: Int,
    paintWord: WordDrawer,
  ) {
    lines.forEach { line ->
      val drift = { index: Int -> sin(time * frame.speed * 0.9f + index * 0.6f) * line.size * 0.04f }
      line.words.forEach { word -> paintWord(line, word, 0f, drift(word.word.wordIndex)) }
      if (line.isActive && line.words.size > 1) {
        val first = line.words.first()
        val last = line.words.last()
        val ribbon =
          Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader =
              LinearGradient(
                first.x,
                0f,
                last.x + last.width,
                0f,
                Color.TRANSPARENT,
                withAlpha(accent, 0.5f),
                Shader.TileMode.CLAMP,
              )
            strokeWidth = 1.5f
            setShadowLayer(8f * frame.glow, 0f, 0f, withAlpha(glow, 0.4f))
          }
        canvas.drawLine(
          first.x,
          line.baseline + line.size * 0.36f,
          last.x + last.width,
          line.baseline + line.size * 0.36f,
          ribbon,
        )
        ribbon.clearShadowLayer()
      }
    }
  }

  // ── 02 NOIR — quiet luxury, nothing moves ─────────────────────────────────
  private fun drawNoir(
    canvas: Canvas,
    lines: List<PlacedLine>,
    width: Int,
    accent: Int,
    paintWord: WordDrawer,
  ) {
    lines.forEach { line ->
      line.words.forEach { word -> paintWord(line, word, 0f, 0f) }
      if (line.isActive) {
        val first = line.words.first()
        val last = line.words.last()
        val rule =
          Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(accent, 0.7f)
            strokeWidth = 1f
          }
        val y = line.baseline + line.size * 0.42f
        canvas.drawLine(first.x, y, last.x + last.width, y, rule)
        canvas.drawCircle(width / 2f, y, 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
      }
    }
  }

  // ── 03 GOLDEN — warm halo + letterbox ─────────────────────────────────────
  private fun drawGolden(
    canvas: Canvas,
    lines: List<PlacedLine>,
    width: Int,
    height: Int,
    frame: PremiumFrame,
    time: Float,
    accent: Int,
    paintWord: WordDrawer,
  ) {
    val barH = height * 0.055f
    val bar =
      Paint().apply { color = withAlpha(Color.BLACK, 0.92f) }
    canvas.drawRect(0f, 0f, width.toFloat(), barH, bar)
    canvas.drawRect(0f, height - barH, width.toFloat(), height.toFloat(), bar)
    val warm =
      Paint().apply {
        shader =
          RadialGradient(
            width / 2f,
            height * 0.46f,
            minOf(width, height) * 0.6f,
            withAlpha(accent, 0.12f * frame.glow),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
          )
      }
    canvas.drawRect(0f, barH, width.toFloat(), height - barH, warm)
    lines.forEach { line ->
      val drift = sin(time * frame.speed * 0.7f) * line.size * 0.025f
      line.words.forEach { word -> paintWord(line, word, 0f, drift) }
    }
  }

  // ── 04 NEON ───────────────────────────────────────────────────────────────
  private fun drawNeon(
    canvas: Canvas,
    lines: List<PlacedLine>,
    frame: PremiumFrame,
    time: Float,
    base: Paint,
    glow: Int,
    paintWord: WordDrawer,
  ) {
    lines.forEach { line ->
      val pulse = if (line.isActive) 0.85f + 0.15f * sin(time * 5f) else 0.6f
      line.words.forEach { word ->
        for (layer in 3 downTo 1) {
          drawSoftText(canvas, word, base, glow, 0.07f * line.prominence * pulse, layer * 7f * frame.glow)
        }
        paintWord(line, word, 0f, 0f)
      }
    }
  }

  // ── 05 EMBER ──────────────────────────────────────────────────────────────
  private fun drawEmber(
    canvas: Canvas,
    lines: List<PlacedLine>,
    frame: PremiumFrame,
    time: Float,
    width: Int,
    height: Int,
    accent: Int,
    glow: Int,
    paintWord: WordDrawer,
  ) {
    if (!frame.lowPower) {
      val haze =
        Paint().apply {
          shader =
            LinearGradient(
              0f,
              height.toFloat(),
              0f,
              height * 0.35f,
              withAlpha(glow, 0.1f),
              Color.TRANSPARENT,
              Shader.TileMode.CLAMP,
            )
        }
      canvas.drawRect(0f, height * 0.35f, width.toFloat(), height.toFloat(), haze)
    }
    val spark = Paint(Paint.ANTI_ALIAS_FLAG)
    lines.forEach { line ->
      line.words.forEach { word ->
        paintWord(line, word, 0f, 0f)
        if (!line.isActive || frame.lowPower) return@forEach
        for (i in 0 until 14) {
          val seed = (word.word.wordIndex * 19 + i * 7).toFloat()
          val rise = ((time * frame.speed * 55f + seed * 13f) % 320f) / 320f
          val px = word.x + (seed % word.width.coerceAtLeast(1f))
          val py = word.baseline + word.size * 0.5f - rise * line.size * 3f
          spark.color = withAlpha(if (i % 3 == 0) accent else glow, (1f - rise) * 0.55f)
          canvas.drawCircle(px, py, 1f + (seed % 2f), spark)
        }
      }
    }
  }

  // ── 06 PRISM — chromatic split ────────────────────────────────────────────
  private fun drawPrism(
    canvas: Canvas,
    lines: List<PlacedLine>,
    frame: PremiumFrame,
    base: Paint,
    accent: Int,
    glow: Int,
    paintWord: WordDrawer,
  ) {
    val split = if (frame.lowPower) 0f else baseSizeSplit(frame) * 0.045f
    lines.forEach { line ->
      line.words.forEach { word ->
        if (split > 0f) {
          drawSoftText(canvas, word, base, accent, 0.42f * line.prominence, 0f, -split, 0f)
          drawSoftText(canvas, word, base, glow, 0.42f * line.prominence, 0f, split, 0f)
        }
        paintWord(line, word, 0f, 0f)
      }
    }
  }

  private fun baseSizeSplit(frame: PremiumFrame): Float = 60f * frame.speed

  // ── 07 CHROME — metallic gradient + extrusion ─────────────────────────────
  private fun drawChromeLook(
    canvas: Canvas,
    lines: List<PlacedLine>,
    frame: PremiumFrame,
    base: Paint,
    accent: Int,
    glow: Int,
    paintWord: WordDrawer,
  ) {
    lines.forEach { line ->
      line.words.forEach { word ->
        if (line.isActive && !frame.lowPower) {
          for (layer in 4 downTo 1) {
            drawSoftText(
              canvas,
              word,
              base,
              glow,
              0.09f,
              2f,
              layer * 1.6f,
              layer * 1.6f,
            )
          }
          val x = word.x
          val y = word.baseline
          val metal =
            Paint(base).apply {
              textSize = word.size
              letterSpacing = -word.size * 0.018f
              shader =
                LinearGradient(
                  x,
                  y - word.size,
                  x,
                  y,
                  intArrayOf(
                    withAlpha(accent, 0.9f),
                    Color.WHITE,
                    withAlpha(accent, 0.55f),
                    withAlpha(glow, 0.8f),
                  ),
                  floatArrayOf(0f, 0.35f, 0.7f, 1f),
                  Shader.TileMode.CLAMP,
                )
            }
          canvas.drawText(word.word.text, x, y, metal)
        }
        paintWord(line, word, 0f, 0f)
      }
    }
  }

  // ── 08 TERMINAL ───────────────────────────────────────────────────────────
  private fun drawTerminal(
    canvas: Canvas,
    lines: List<PlacedLine>,
    frame: PremiumFrame,
    time: Float,
    width: Int,
    height: Int,
    base: Paint,
    accent: Int,
    paintWord: WordDrawer,
  ) {
    if (!frame.lowPower) {
      val scan = Paint().apply { color = withAlpha(accent, 0.03f) }
      var y = 0f
      while (y < height) {
        canvas.drawRect(0f, y, width.toFloat(), y + 1f, scan)
        y += 5f
      }
    }
    lines.forEach { line ->
      line.words.forEach { word ->
        paintWord(line, word, 0f, 0f)
        if (line.isActive && word.word.isCurrent) {
          val blink = (time * 1.6f).toInt() % 2 == 0
          if (blink) {
            canvas.drawRect(
              word.x + word.width + 3f,
              word.baseline - word.size * 0.72f,
              word.x + word.width + 3f + word.size * 0.09f,
              word.baseline + word.size * 0.06f,
              Paint().apply { color = withAlpha(accent, 0.9f) },
            )
          }
        }
      }
    }
  }

  // ── 09 HOLOGRAM ───────────────────────────────────────────────────────────
  private fun drawHologram(
    canvas: Canvas,
    lines: List<PlacedLine>,
    frame: PremiumFrame,
    time: Float,
    width: Int,
    height: Int,
    base: Paint,
    accent: Int,
    glow: Int,
    paintWord: WordDrawer,
  ) {
    if (!frame.lowPower) {
      val barY = (time * frame.speed * 120f) % (height + 160f) - 80f
      canvas.drawRect(
        0f,
        barY - 40f,
        width.toFloat(),
        barY + 40f,
        Paint().apply {
          shader =
            LinearGradient(
              0f,
              barY - 40f,
              0f,
              barY + 40f,
              intArrayOf(Color.TRANSPARENT, withAlpha(accent, 0.1f), Color.TRANSPARENT),
              floatArrayOf(0f, 0.5f, 1f),
              Shader.TileMode.CLAMP,
            )
        },
      )
    }
    lines.forEach { line ->
      line.words.forEach { word ->
        if (line.isActive && !frame.lowPower) {
          drawSoftText(canvas, word, base, glow, 0.22f * line.prominence, 4f, 0f, -line.size * 0.05f)
          drawSoftText(canvas, word, base, accent, 0.14f * line.prominence, 2f, 0f, line.size * 0.05f)
        }
        paintWord(line, word, 0f, 0f)
      }
    }
  }

  // ── 10 ORBIT ──────────────────────────────────────────────────────────────
  private fun drawOrbit(
    canvas: Canvas,
    lines: List<PlacedLine>,
    frame: PremiumFrame,
    time: Float,
    accent: Int,
    paintWord: WordDrawer,
  ) {
    lines.forEach { line ->
      if (line.isActive) {
        val rect =
          RectF(
            line.centerX - line.size * 3f,
            line.baseline - line.size * 1.5f,
            line.centerX + line.size * 3f,
            line.baseline + line.size * 0.95f,
          )
        val ring =
          Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            color = withAlpha(accent, 0.3f)
          }
        val angle = (time * frame.speed * 28f) % 360f
        canvas.drawArc(rect, angle, 96f, false, ring)
        canvas.drawArc(rect, angle + 180f, 48f, false, ring)
      }
      line.words.forEach { word ->
        val wobble = if (word.word.isCurrent) sin(time * frame.speed * 2.4f) * line.size * 0.05f else 0f
        paintWord(line, word, 0f, wobble)
        if (word.word.isCurrent) {
          canvas.drawCircle(
            word.x + word.width / 2f + cos(time * 1.8f) * line.size,
            word.baseline - line.size * 0.65f + sin(time * 1.8f) * line.size * 0.32f,
            3f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, 0.85f) },
          )
        }
      }
    }
  }

  // ── 11 TIDAL ──────────────────────────────────────────────────────────────
  private fun drawTidal(
    canvas: Canvas,
    lines: List<PlacedLine>,
    frame: PremiumFrame,
    time: Float,
    accent: Int,
    paintWord: WordDrawer,
  ) {
    lines.forEach { line ->
      val offset = { index: Int ->
        sin(time * frame.speed * 1.6f + index * 0.7f) * line.size * 0.08f * (1f + frame.features.bass)
      }
      line.words.forEach { word -> paintWord(line, word, 0f, offset(word.word.wordIndex)) }
      if (line.isActive && line.words.size > 1) {
        val first = line.words.first()
        val last = line.words.last()
        canvas.drawLine(
          first.x,
          line.baseline + line.size * 0.36f + offset(first.word.wordIndex),
          last.x + last.width,
          line.baseline + line.size * 0.36f + offset(last.word.wordIndex),
          Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(accent, 0.25f)
            strokeWidth = 1.4f
          },
        )
      }
    }
  }

  // ── 12 DEPTH ──────────────────────────────────────────────────────────────
  private fun drawDepth(
    canvas: Canvas,
    lines: List<PlacedLine>,
    frame: PremiumFrame,
    base: Paint,
    glow: Int,
    paintWord: WordDrawer,
  ) {
    lines.forEach { line ->
      line.words.forEach { word ->
        val parallax = (word.word.wordIndex % 3 - 1) * line.size * 0.02f * frame.speed
        if (!frame.lowPower) {
          for (layer in 4 downTo 1) {
            val offset = layer * (1.2f + frame.features.beat * 1.4f)
            drawSoftText(canvas, word, base, glow, 0.07f * line.prominence, 3f, offset + parallax, offset)
          }
        }
        paintWord(line, word, 0f, 0f)
      }
    }
  }

  // ── 13 ECHO ───────────────────────────────────────────────────────────────
  private fun drawEcho(
    canvas: Canvas,
    lines: List<PlacedLine>,
    frame: PremiumFrame,
    base: Paint,
    accent: Int,
    glow: Int,
    paintWord: WordDrawer,
  ) {
    lines.forEach { line ->
      line.words.forEach { word ->
        val w = word.word
        if (!frame.lowPower && (w.isCurrent || line.isActive)) {
          for (t in 3 downTo 1) {
            val shift = t * line.size * 0.07f
            drawSoftText(canvas, word, base, glow, line.prominence * 0.1f / t, 4f, -shift, shift * 0.4f)
          }
          if (line.isActive) {
            canvas.save()
            canvas.scale(1f, -1f, word.x, word.baseline + line.size * 1.4f)
            drawSoftText(canvas, word, base, accent, line.prominence * 0.12f, 3f)
            canvas.restore()
          }
        }
        paintWord(line, word, 0f, 0f)
      }
    }
  }

  // ── 14 POSTER ─────────────────────────────────────────────────────────────
  private fun drawPoster(
    canvas: Canvas,
    lines: List<PlacedLine>,
    frame: PremiumFrame,
    base: Paint,
    accent: Int,
    glow: Int,
    paintWord: WordDrawer,
  ) {
    lines.forEach { line ->
      line.words.forEach { word ->
        if (line.isActive && !frame.lowPower) {
          val off = line.size * 0.045f
          val shadow =
            Paint(base).apply {
              textSize = word.size
              letterSpacing = -word.size * 0.018f
              color = withAlpha(glow, 0.55f)
              style = Paint.Style.FILL
            }
          canvas.drawText(word.word.text, word.x + off, word.baseline + off, shadow)
          val outline =
            Paint(base).apply {
              textSize = word.size
              letterSpacing = -word.size * 0.018f
              style = Paint.Style.STROKE
              strokeWidth = 1.2f
              color = withAlpha(accent, 0.65f)
              pathEffect = DashPathEffect(floatArrayOf(12f, 9f), 0f)
            }
          canvas.drawText(word.word.text, word.x, word.baseline, outline)
        }
        paintWord(line, word, 0f, 0f)
      }
    }
  }

  // ── 15 STARDUST ───────────────────────────────────────────────────────────
  private fun drawStardust(
    canvas: Canvas,
    lines: List<PlacedLine>,
    frame: PremiumFrame,
    time: Float,
    accent: Int,
    glow: Int,
    paintWord: WordDrawer,
  ) {
    val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    lines.forEach { line ->
      line.words.forEach { word ->
        paintWord(line, word, 0f, 0f)
        if (!line.isActive || frame.lowPower) return@forEach
        for (i in 0 until 16) {
          val seed = (word.word.wordIndex * 23 + i * 11).toFloat()
          val angle = seed * 0.41f + time * frame.speed * 0.25f
          val radius = (12f + seed % 26f) * (1f + frame.features.beat * 0.5f)
          val px = word.x + word.width / 2f + cos(angle) * radius
          val py = word.baseline - word.size * 0.3f + sin(angle) * radius * 0.55f
          dot.color = withAlpha(if (i % 2 == 0) accent else glow, line.prominence * 0.5f)
          canvas.drawCircle(px, py, 0.8f + (i % 3) * 0.5f, dot)
        }
      }
    }
  }

  private fun drawChrome(
    canvas: Canvas,
    width: Int,
    height: Int,
    frame: PremiumFrame,
    typeface: Typeface?,
    style: PremiumLyricStyle,
    primary: Int,
    accent: Int,
  ) {
    val label =
      if (style == PremiumLyricStyle.AUTO) "PREMIUM · AUTO" else "PREMIUM · ${style.label.uppercase()}"
    val paint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        if (typeface != null) this.typeface = typeface
        color = withAlpha(primary, 0.42f)
        textSize = 11f
        letterSpacing = 2.4f
      }
    canvas.drawText(label, 24f, height - 28f, paint)
    val artist = frame.artist
    if (artist.isNotBlank()) {
      canvas.drawText(
        artist,
        width - 24f,
        height - 28f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          if (typeface != null) this.typeface = typeface
          color = withAlpha(primary, 0.34f)
          textSize = 11f
          letterSpacing = 0.8f
          textAlign = Paint.Align.RIGHT
        },
      )
    }
    if (frame.durationMs > 0L) {
      val progress = frame.positionMs.toFloat() / frame.durationMs.toFloat()
      canvas.drawRect(
        0f,
        0f,
        width * progress.coerceIn(0f, 1f),
        2f,
        Paint().apply { color = withAlpha(accent, 0.7f) },
      )
    }
  }

  private fun withAlpha(argb: Int, alpha: Float): Int {
    val a = (alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24
    return a or (argb and 0x00FFFFFF)
  }
}
