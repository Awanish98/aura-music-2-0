package com.music.echo.codepoetry

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.util.Random
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws one CODE POETRY frame on any Android [Canvas]. The same renderer is used
 * by the live full-screen overlay and the settings preview. It is a pure frame
 * function: no animation state is stored here, so seek/pause/resume cannot
 * desynchronise the typing from the shared player position.
 *
 * The shared policy deliberately hands the renderer a small window of real lyric
 * phrases (and their absolute line numbers). This renderer turns that window into
 * a complete, full-screen source file: every visible row is real, syntax-coloured
 * code with continuous line numbers, and the real lyric phrases are embedded at
 * their true absolute line numbers. Nothing here invents a lyric word or a
 * timestamp — scaffold rows are pure syntax used to fill the editor surface.
 */
object CodePoetryRenderer {
  private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG)

  private var noiseBitmap: Bitmap? = null
  private var noiseSize = 0
  private var scanlineBitmap: Bitmap? = null

  init {
    listOf(textPaint, measurePaint).forEach { it.typeface = Typeface.MONOSPACE }
  }

  private fun withAlpha(argb: Int, alpha: Int): Int =
    Color.argb(alpha.coerceIn(0, 255), Color.red(argb), Color.green(argb), Color.blue(argb))

  private fun mod(a: Int, b: Int): Int = ((a % b) + b) % b

  private fun measure(text: String, size: Float): Float {
    measurePaint.textSize = size.coerceAtLeast(8f)
    return measurePaint.measureText(text)
  }

  private fun configureText(paint: Paint, size: Float, color: Int, glow: Float) {
    paint.typeface = Typeface.MONOSPACE
    paint.textSize = size.coerceAtLeast(8f)
    paint.color = color
    paint.textAlign = Paint.Align.LEFT
    if (glow > 0f) {
      paint.setShadowLayer(glow, 0f, 0f, withAlpha(color, 110))
    } else {
      paint.setShadowLayer(0f, 0f, 0f, 0)
    }
  }

  fun render(
    canvas: Canvas,
    width: Int,
    height: Int,
    frame: CodeFrameSpec,
  ) {
    if (width <= 0 || height <= 0) return
    drawBackdrop(canvas, width, height, frame)

    // The editor is intentionally flat and screen-aligned. No perspective tilt,
    // zoom, rotation or vignette: every pixel belongs to the lyrics/code.
    drawEditor(canvas, width, height, frame)
    drawBug(canvas, width, height, frame)
    drawGlitch(canvas, width, height, frame)
    drawScanlines(canvas, width, height, frame)
    drawGrain(canvas, width, height, frame)
    drawPopup(canvas, width, height, frame)
  }

  private fun editorLines(frame: CodeFrameSpec): List<CodeLineFrame> =
    frame.editorLines.ifEmpty { frame.lines }

  // ---------------------------------------------------------------------------
  // Palette / syntax roles
  // ---------------------------------------------------------------------------

  private data class Syntax(
    val keyword: Int,
    val function: Int,
    val string: Int,
    val number: Int,
    val normal: Int,
    val comment: Int,
  )

  private fun syntaxOf(palette: CodePalette): Syntax =
    Syntax(
      keyword = palette.keyword.toInt(),
      function = palette.secondary.toInt(),
      string = palette.emphasis.toInt(),
      number = palette.important.toInt(),
      normal = palette.normal.toInt(),
      comment = palette.comment.toInt(),
    )

  private fun colorOf(
    kind: CodeTokenKind,
    frame: CodeFrameSpec,
  ): Int {
    val p = frame.config.palette
    return when (kind) {
      CodeTokenKind.NORMAL -> p.normal.toInt()
      CodeTokenKind.IMPORTANT -> p.important.toInt()
      CodeTokenKind.EMPHASIS -> p.emphasis.toInt()
      CodeTokenKind.KEYWORD -> p.keyword.toInt()
      CodeTokenKind.SECONDARY -> p.secondary.toInt()
      CodeTokenKind.COMMENT -> p.comment.toInt()
    }.let { if (frame.config.lowPower && kind == CodeTokenKind.SECONDARY) p.normal.toInt() else it }
  }

  /** One coloured run inside a rendered code row. */
  private data class Tok(
    val text: String,
    val color: Int,
    val word: CodeWordFrame? = null,
  )

  /** One wrapped, drawable row of the visible editor. */
  private data class VisualRow(
    val number: Int?,
    val tokens: List<Tok>,
    val isActive: Boolean,
    val isLyric: Boolean,
  )

  /** Geometry for one frame. Recomputed from scratch so previews scale correctly. */
  private data class Layout(
    val panelL: Float,
    val panelT: Float,
    val panelR: Float,
    val panelB: Float,
    val pad: Float,
    val tabH: Float,
    val crumbH: Float,
    val statusH: Float,
    val gutterX: Float,
    val codeL: Float,
    val codeR: Float,
    val codeT: Float,
    val codeB: Float,
    val textSize: Float,
    val rowH: Float,
    val visibleRows: Int,
    val desiredRow: Int,
    val minimapL: Float,
    val minimapW: Float,
  )

  private fun layout(
    w: Int,
    h: Int,
    frame: CodeFrameSpec,
  ): Layout {
    val minSide = minOf(w, h)
    val pad = (minSide * 0.012f).coerceIn(8f, 22f)
    // True edge-to-edge editor: no inset card, no rounded panel, no shadow.
    val panelL = 0f
    val panelT = 0f
    val panelR = w.toFloat()
    val panelB = h.toFloat()
    val tabH = (minSide * 0.048f).coerceIn(34f, 72f)
    val crumbH = (minSide * 0.034f).coerceIn(24f, 52f)
    val statusH = (minSide * 0.038f).coerceIn(28f, 54f)
    val gutterX = pad + (minSide * 0.052f).coerceIn(34f, 78f)
    val minimapW = (w * 0.022f).coerceIn(12f, 36f)
    val minimapL = panelR - pad - minimapW
    val codeL = gutterX + pad * 0.6f
    val codeR = max(codeL + minSide * 0.2f, minimapL - pad * 0.8f)
    val codeT = tabH + crumbH
    val codeB = panelB - statusH

    var textSize =
      (minSide * 0.026f * frame.config.fontSize).coerceIn(12f, minSide * 0.052f)
    // A single quoted lyric word must never be wider than the code column.
    val longestWord =
      editorLines(frame).maxOfOrNull { line ->
        line.words.maxOfOrNull { word -> measure("\"${word.text}\",", textSize) } ?: 0f
      } ?: 0f
    val availableWidth = (codeR - codeL).coerceAtLeast(minSide * 0.25f)
    if (longestWord > availableWidth * 0.92f && longestWord > 0f) {
      textSize = (textSize * availableWidth * 0.92f / longestWord).coerceAtLeast(10f)
    }

    val rowH = textSize * 1.42f
    val availableH = (codeB - codeT).coerceAtLeast(rowH * 5f)
    val visibleRows = max(5, floor(availableH / rowH).toInt())
    // Keep the typing line low enough that finished lines visibly stack above it.
    val desiredRow = (visibleRows * 0.62f).roundToInt().coerceIn(1, visibleRows - 1)
    return Layout(
      panelL,
      panelT,
      panelR,
      panelB,
      pad,
      tabH,
      crumbH,
      statusH,
      gutterX,
      codeL,
      codeR,
      codeT,
      codeB,
      textSize,
      rowH,
      visibleRows,
      desiredRow,
      minimapL,
      minimapW,
    )
  }

  // ---------------------------------------------------------------------------
  // Source rows: real lyric phrases + deterministic code scaffold
  // ---------------------------------------------------------------------------

  private fun scaffoldTokens(
    lineNo: Int,
    s: Syntax,
  ): List<Tok> {
    fun t(text: String, color: Int) = Tok(text, color)
    val kw = s.keyword
    val fn = s.function
    val str = s.string
    val num = s.number
    val txt = s.normal
    val com = s.comment
    val dim = withAlpha(com, 145)
    return when (mod(lineNo - 1, 30)) {
      0 -> listOf(t("import ", kw), t("{ ", txt), t("renderVerse", fn), t(", ", txt), t("composeFrame", fn), t(" } ", txt), t("from ", kw), t("\"@aura/core\"", str), t(";", txt))
      1 -> listOf(t("import ", kw), t("type ", kw), t("FrameSpec", fn), t(", ", txt), t("TokenKind", fn), t(" from ", kw), t("\"@aura/types\"", str), t(";", txt))
      2 -> listOf(t("import ", kw), t("{ palette }", txt), t(" from ", kw), t("\"./theme\"", str), t(";", txt))
      3 -> emptyList()
      4 -> listOf(t("// theme resolved from the shared palette", com))
      5 -> listOf(t("const ", kw), t("theme", txt), t(" = ", txt), t("resolveTheme", fn), t("(\"midnight\")", str), t(";", txt))
      6 -> listOf(t("const ", kw), t("clock", txt), t(" = ", txt), t("createPlayerClock", fn), t("();", txt))
      7 -> listOf(t("const ", kw), t("GUTTER", txt), t(" = ", txt), t("64", num), t("; // px", com))
      8 -> emptyList()
      9 -> listOf(t("export ", kw), t("function ", kw), t("renderVerse", fn), t("(spec: ", txt), t("FrameSpec", fn), t(") {", txt))
      10 -> listOf(t("  ", dim), t("const ", kw), t("ctx", txt), t(" = ", txt), t("createContext", fn), t("(theme, spec.viewport);", txt))
      11 -> listOf(t("  ", dim), t("const ", kw), t("rows: ", txt), t("Token", fn), t("[] = [];", txt))
      12 -> emptyList()
      13 -> listOf(t("  ", dim), t("for ", kw), t("(const ", kw), t("line", txt), t(" of ", kw), t("spec.lines", txt), t(") {", txt))
      14 -> listOf(t("    ", dim), t("if ", kw), t("(!line.visible) ", txt), t("continue", kw), t(";", txt))
      15 -> listOf(t("    ", dim), t("const ", kw), t("active", txt), t(" = ", txt), t("line.index", txt), t(" === ", txt), t("spec.cursor", txt), t(";", txt))
      16 -> listOf(t("    ", dim), t("const ", kw), t("tokens", txt), t(" = ", txt), t("tokenize", fn), t("(line.words, active);", txt))
      17 -> listOf(t("    ", dim), t("rows.push", fn), t("(...tokens);", txt))
      18 -> listOf(t("  ", dim), t("}", txt))
      19 -> emptyList()
      20 -> listOf(t("  ", dim), t("const ", kw), t("frame", txt), t(" = ", txt), t("composeFrame", fn), t("(rows, {", txt))
      21 -> listOf(t("    ", dim), t("cursor: ", txt), t("spec.cursor", txt), t(",", txt))
      22 -> listOf(t("    ", dim), t("energy: ", txt), t("spec.energy", txt), t(",", txt))
      23 -> listOf(t("    ", dim), t("timing: ", txt), t("\"synced\"", str), t(",", txt))
      24 -> listOf(t("    ", dim), t("theme: ", txt), t("theme.id", txt), t(",", txt))
      25 -> listOf(t("  ", dim), t("});", txt))
      26 -> emptyList()
      27 -> listOf(t("  ", dim), t("ctx.draw", fn), t("(frame);", txt))
      28 -> listOf(t("  ", dim), t("return ", kw), t("frame", txt), t(";", txt))
      else -> listOf(t("}", txt))
    }
  }

  private fun lyricTokens(
    line: CodeLineFrame,
    frame: CodeFrameSpec,
    s: Syntax,
  ): List<Tok> {
    val tokens = mutableListOf<Tok>()
    tokens += Tok("  ", withAlpha(s.comment, 150))
    tokens += Tok("const ", s.keyword)
    tokens += Tok("line${line.number.toString().padStart(2, '0')}", s.function)
    tokens += Tok(" = ", s.normal)
    tokens += Tok("await ", s.keyword)
    tokens += Tok("sing", s.function)
    tokens += Tok("(", s.normal)
    line.words.forEachIndexed { index, word ->
      // Never invent a word: the non-current words are the real engine words,
      // shown dimmed; only the current word uses its progressive reveal.
      val shown = if (word.isCurrent) word.typed else word.text
      val literal = "\"$shown\"" + if (index < line.words.lastIndex) "," else ""
      val color =
        when {
          word.isCurrent -> s.number
          line.isActive -> colorOf(word.kind, frame)
          else -> withAlpha(colorOf(word.kind, frame), 125)
        }
      tokens += Tok(literal, color, word)
    }
    tokens += Tok(");", s.normal)
    if (!line.verified) {
      tokens += Tok(" // timing ~", withAlpha(s.comment, 140))
    }
    return tokens
  }

  private fun sourceRow(
    lineNo: Int,
    lines: List<CodeLineFrame>,
    frame: CodeFrameSpec,
    s: Syntax,
  ): VisualRow {
    val lyric = lines.firstOrNull { it.number == lineNo }
    return if (lyric != null) {
      VisualRow(lineNo, lyricTokens(lyric, frame, s), lyric.isActive, true)
    } else {
      VisualRow(lineNo, scaffoldTokens(lineNo, s), false, false)
    }
  }

  private fun wrap(
    source: VisualRow,
    maxWidth: Float,
    textSize: Float,
    commentColor: Int,
  ): List<VisualRow> {
    val total = source.tokens.sumOf { measure(it.text, textSize).toDouble() }.toFloat()
    if (total <= maxWidth || source.tokens.size <= 1) {
      return listOf(source)
    }
    val out = mutableListOf<VisualRow>()
    var current = mutableListOf<Tok>()
    var width = 0f
    val hangWidth = measure("    ", textSize)
    source.tokens.forEachIndexed { index, token ->
      val tokenWidth = measure(token.text, textSize)
      if (width + tokenWidth > maxWidth && current.isNotEmpty() && index > 0) {
        out += VisualRow(
          if (out.isEmpty()) source.number else null,
          current,
          source.isActive,
          source.isLyric,
        )
        current = mutableListOf(Tok("    ", withAlpha(commentColor, 130)))
        width = hangWidth
      }
      current += token
      width += tokenWidth
    }
    if (current.isNotEmpty()) {
      out += VisualRow(
        if (out.isEmpty()) source.number else null,
        current,
        source.isActive,
        source.isLyric,
      )
    }
    return out
  }

  private fun buildRows(
    layout: Layout,
    frame: CodeFrameSpec,
    s: Syntax,
  ): List<VisualRow> {
    val lines = editorLines(frame)
    if (lines.isEmpty()) return emptyList()
    val availableWidth = (layout.codeR - layout.codeL).coerceAtLeast(10f)
    val all = mutableListOf<VisualRow>()
    lines.forEach { line ->
      all += wrap(sourceRow(line.number, lines, frame, s), availableWidth, layout.textSize, s.comment)
    }

    val activeIndex = all.indexOfFirst { it.isActive }
    val start =
      if (activeIndex >= 0) maxOf(0, activeIndex - layout.desiredRow) else 0
    val window = all.drop(start).toMutableList()
    // Extend past the final lyric with neutral source rows so the editor always
    // fills the display without inventing a single lyric word.
    var lineNo = (lines.lastOrNull()?.number ?: 0) + 1
    val lastLine = lineNo + layout.visibleRows
    while (window.size < layout.visibleRows && lineNo <= lastLine) {
      window += wrap(
        sourceRow(lineNo++, lines, frame, s),
        availableWidth,
        layout.textSize,
        s.comment,
      )
    }
    return window.take(layout.visibleRows)
  }

  // ---------------------------------------------------------------------------
  // Editor surface
  // ---------------------------------------------------------------------------

  private fun drawEditor(
    canvas: Canvas,
    width: Int,
    height: Int,
    frame: CodeFrameSpec,
  ) {
    val s = syntaxOf(frame.config.palette)
    val l = layout(width, height, frame)
    drawPanel(canvas, l, s, frame)
    drawTabs(canvas, l, s)
    drawBreadcrumb(canvas, l, s, frame)
    val rows = buildRows(l, frame, s)
    drawCode(canvas, l, rows, s, frame)
    drawMinimap(canvas, l, rows, s, frame)
    drawStatusBar(canvas, l, s, frame, rows)
  }

  private fun drawBackdrop(
    canvas: Canvas,
    width: Int,
    height: Int,
    frame: CodeFrameSpec,
  ) {
    // Flat black everywhere. Corner glows read as edge blur on AMOLED phones.
    canvas.drawColor(Color.BLACK)
  }

  private fun drawPanel(
    canvas: Canvas,
    l: Layout,
    s: Syntax,
    frame: CodeFrameSpec,
  ) {
    // Full-bleed editor surface: no rounded card, border glow or drop shadow.
    surfacePaint.color = Color.BLACK
    canvas.drawRect(l.panelL, l.panelT, l.panelR, l.panelB, surfacePaint)
    surfacePaint.color = withAlpha(s.comment, if (frame.config.lowPower) 28 else 46)
    canvas.drawLine(l.panelL, l.tabH, l.panelR, l.tabH, surfacePaint)
    surfacePaint.color = withAlpha(s.comment, if (frame.config.lowPower) 22 else 38)
    canvas.drawLine(l.panelL, l.panelB - l.statusH, l.panelR, l.panelB - l.statusH, surfacePaint)
  }

  private fun drawTabs(
    canvas: Canvas,
    l: Layout,
    s: Syntax,
  ) {
    val gap = l.pad * 0.45f
    val available = l.panelR - l.panelL - l.pad * 2f - gap * 3f
    val tabW = available / 3f
    val names = listOf("echo.ts", "verse.ts", "theme.ts")
    val top = l.panelT + l.pad * 0.35f
    val bottom = l.panelT + l.tabH - l.pad * 0.25f
    names.forEachIndexed { index, name ->
      val left = l.panelL + l.pad + index * (tabW + gap)
      val right = left + tabW
      surfacePaint.color =
        if (index == 0) withAlpha(s.keyword, 30) else withAlpha(s.comment, 16)
      canvas.drawRoundRect(
        RectF(left, top, right, bottom),
        l.pad * 0.32f,
        l.pad * 0.32f,
        surfacePaint,
      )
      configureText(
        textPaint,
        l.textSize * 0.62f,
        if (index == 0) withAlpha(s.normal, 235) else withAlpha(s.comment, 175),
        0f,
      )
      textPaint.textAlign = Paint.Align.CENTER
      canvas.drawText(name, (left + right) / 2f, (top + bottom) / 2f + l.textSize * 0.22f, textPaint)
      textPaint.textAlign = Paint.Align.LEFT
      if (index == 0) {
        surfacePaint.color = withAlpha(s.keyword, 230)
        canvas.drawRoundRect(
          RectF(left + tabW * 0.18f, bottom - 2.5f, right - tabW * 0.18f, bottom),
          2f,
          2f,
          surfacePaint,
        )
      }
    }
    configureText(textPaint, l.textSize * 0.72f, withAlpha(s.comment, 160), 0f)
    canvas.drawText(
      "+",
      l.panelR - l.pad - l.textSize * 0.2f,
      (top + bottom) / 2f + l.textSize * 0.25f,
      textPaint,
    )
  }

  private fun drawBreadcrumb(
    canvas: Canvas,
    l: Layout,
    s: Syntax,
    frame: CodeFrameSpec,
  ) {
    val prefix = "src / lyrics / echo.ts / "
    val tail = if (frame.lines.isEmpty()) "awaiting lyrics" else "renderVerse()"
    val size = l.textSize * 0.55f
    val baseline = l.panelT + l.tabH + l.crumbH * 0.68f
    configureText(textPaint, size, withAlpha(s.comment, 180), 0f)
    canvas.drawText(prefix, l.panelL + l.pad, baseline, textPaint)
    val tailX = l.panelL + l.pad + measure(prefix, size)
    configureText(textPaint, size, withAlpha(s.function, 220), 0f)
    canvas.drawText(tail, tailX, baseline, textPaint)

    surfacePaint.color = withAlpha(s.comment, 45)
    canvas.drawLine(
      l.panelL + l.pad,
      l.codeT - l.pad * 0.25f,
      l.panelR - l.pad,
      l.codeT - l.pad * 0.25f,
      surfacePaint,
    )
  }

  private fun drawCode(
    canvas: Canvas,
    l: Layout,
    rows: List<VisualRow>,
    s: Syntax,
    frame: CodeFrameSpec,
  ) {
    val activeFirst = rows.indexOfFirst { it.isActive }
    val activeLast = rows.indexOfLast { it.isActive }
    if (activeFirst >= 0 && !frame.config.lowPower) {
      val top = l.codeT + activeFirst * l.rowH - l.textSize * 0.88f
      val bottom = l.codeT + (activeLast + 1) * l.rowH - l.textSize * 0.06f
      surfacePaint.setShadowLayer(l.textSize * 0.6f, 0f, 0f, withAlpha(s.keyword, 55))
      surfacePaint.color = withAlpha(s.keyword, 22)
      canvas.drawRoundRect(
        RectF(l.codeL - l.pad * 0.65f, top, l.codeR + l.pad * 0.25f, bottom),
        l.textSize * 0.24f,
        l.textSize * 0.24f,
        surfacePaint,
      )
      surfacePaint.setShadowLayer(0f, 0f, 0f, 0)
      surfacePaint.color = withAlpha(s.keyword, 235)
      canvas.drawRoundRect(
        RectF(l.gutterX + l.pad * 0.38f, top, l.gutterX + l.pad * 0.38f + 3f, bottom),
        2f,
        2f,
        surfacePaint,
      )
    }

    if (!frame.config.lowPower) {
      surfacePaint.color = withAlpha(s.comment, 38)
      canvas.drawLine(
        l.codeL + l.textSize * 0.55f,
        l.codeT,
        l.codeL + l.textSize * 0.55f,
        l.codeB,
        surfacePaint,
      )
    }

    rows.forEachIndexed { index, row ->
      drawRow(canvas, l, row, index, s, frame)
    }
  }

  private fun drawRow(
    canvas: Canvas,
    l: Layout,
    row: VisualRow,
    index: Int,
    s: Syntax,
    frame: CodeFrameSpec,
  ) {
    val baseline = l.codeT + l.textSize * 0.82f + index * l.rowH

    row.number?.let { number ->
      configureText(
        textPaint,
        l.textSize * 0.52f,
        if (row.isActive) withAlpha(s.keyword, 240) else withAlpha(s.comment, 175),
        0f,
      )
      textPaint.textAlign = Paint.Align.RIGHT
      canvas.drawText(number.toString().padStart(2, '0'), l.gutterX, baseline, textPaint)
      textPaint.textAlign = Paint.Align.LEFT
    }

    var x = l.codeL
    val positions = ArrayList<Float>(row.tokens.size)
    row.tokens.forEach { token ->
      positions += x
      x += measure(token.text, l.textSize)
    }

    if (!frame.config.lowPower) {
      row.tokens.forEachIndexed { tokenIndex, token ->
        if (token.word?.isCurrent == true) {
          val width = measure(token.text, l.textSize)
          surfacePaint.color = withAlpha(s.number, 46)
          canvas.drawRoundRect(
            RectF(
              positions[tokenIndex] - l.textSize * 0.12f,
              baseline - l.textSize * 0.86f,
              positions[tokenIndex] + width + l.textSize * 0.12f,
              baseline + l.textSize * 0.2f,
            ),
            l.textSize * 0.2f,
            l.textSize * 0.2f,
            surfacePaint,
          )
        }
      }
    }

    row.tokens.forEachIndexed { tokenIndex, token ->
      val isCurrent = token.word?.isCurrent == true
      configureText(
        textPaint,
        l.textSize,
        token.color,
        if (isCurrent && !frame.config.lowPower) l.textSize * 0.6f else 0f,
      )
      canvas.drawText(token.text, positions[tokenIndex], baseline, textPaint)
      if (isCurrent && !frame.config.lowPower) {
        surfacePaint.color = withAlpha(s.number, 200)
        canvas.drawRect(
          positions[tokenIndex],
          baseline + l.textSize * 0.17f,
          positions[tokenIndex] + measure(token.text, l.textSize),
          baseline + l.textSize * 0.24f,
          surfacePaint,
        )
      }
    }

    if (
      row.isActive &&
      frame.isPlaying &&
      !frame.config.lowPower &&
      frame.frameIndex / 6 % 2 == 0
    ) {
      val cursorX = (positions.lastOrNull() ?: l.codeL) + l.textSize * 0.08f
      surfacePaint.color = frame.config.cursorArgb
      canvas.drawRect(
        cursorX,
        baseline - l.textSize * 0.78f,
        cursorX + l.textSize * 0.5f,
        baseline + l.textSize * 0.14f,
        surfacePaint,
      )
    }
  }

  private fun drawMinimap(
    canvas: Canvas,
    l: Layout,
    rows: List<VisualRow>,
    s: Syntax,
    frame: CodeFrameSpec,
  ) {
    if (frame.config.lowPower) return
    val top = l.codeT
    val bottom = l.codeB
    surfacePaint.color = withAlpha(s.comment, 26)
    canvas.drawRoundRect(
      RectF(l.minimapL, top, l.minimapL + l.minimapW, bottom),
      l.pad * 0.2f,
      l.pad * 0.2f,
      surfacePaint,
    )
    rows.forEachIndexed { index, row ->
      val y = top + (index + 0.5f) * l.rowH
      val seed = mod((row.number ?: index) * 37 + 11, 97)
      val barW = l.minimapW * (0.28f + 0.6f * (seed / 96f))
      surfacePaint.color =
        if (row.isActive) withAlpha(s.keyword, 235)
        else withAlpha(if (row.isLyric) s.function else s.comment, 105)
      canvas.drawRect(l.minimapL + 2f, y - 1f, l.minimapL + 2f + barW, y + 1f, surfacePaint)
    }
    val active = rows.indexOfFirst { it.isActive }
    if (active >= 0) {
      val viewTop = top + maxOf(0, active - 5) * l.rowH
      val viewBottom = minOf(bottom, top + (active + 6) * l.rowH)
      surfacePaint.style = Paint.Style.STROKE
      surfacePaint.strokeWidth = 1.2f
      surfacePaint.color = withAlpha(s.keyword, 130)
      canvas.drawRoundRect(
        RectF(l.minimapL - 1f, viewTop, l.minimapL + l.minimapW + 1f, viewBottom),
        3f,
        3f,
        surfacePaint,
      )
      surfacePaint.style = Paint.Style.FILL
    }
  }

  private fun drawStatusBar(
    canvas: Canvas,
    l: Layout,
    s: Syntax,
    frame: CodeFrameSpec,
    rows: List<VisualRow>,
  ) {
    val top = l.panelB - l.statusH
    surfacePaint.color = withAlpha(s.comment, 40)
    canvas.drawLine(l.panelL + l.pad, top, l.panelR - l.pad, top, surfacePaint)
    val baseline = top + l.statusH * 0.62f
    val size = l.textSize * 0.5f

    configureText(textPaint, size, withAlpha(s.function, 220), 0f)
    canvas.drawText("main*", l.panelL + l.pad + l.textSize * 0.7f, baseline, textPaint)
    surfacePaint.color = withAlpha(s.function, 200)
    canvas.drawLine(l.panelL + l.pad + l.textSize * 0.15f, baseline - size * 0.55f, l.panelL + l.pad + l.textSize * 0.55f, baseline - size * 0.15f, surfacePaint)
    canvas.drawLine(l.panelL + l.pad + l.textSize * 0.55f, baseline - size * 0.15f, l.panelL + l.pad + l.textSize * 0.15f, baseline + size * 0.45f, surfacePaint)

    val timing =
      if (frame.timingVerified) "● synced to player clock" else "~ estimated · tap to correct"
    configureText(textPaint, size, withAlpha(s.comment, 190), 0f)
    val timingX = l.panelL + (l.panelR - l.panelL) * 0.32f
    canvas.drawText(timing, timingX, baseline, textPaint)

    val activeNumber =
      frame.lines.firstOrNull { it.isActive }?.number
        ?: frame.lines.firstOrNull()?.number
        ?: 0
    val activeRow = rows.firstOrNull { it.isActive }
    val col = activeRow?.tokens?.indexOfFirst { it.word?.isCurrent == true }?.plus(1) ?: 1
    val right =
      "Ln $activeNumber, Col $col   UTF-8   TypeScript   AURA"
    configureText(textPaint, size, withAlpha(s.normal, 175), 0f)
    textPaint.textAlign = Paint.Align.RIGHT
    canvas.drawText(right, l.panelR - l.pad, baseline, textPaint)
    textPaint.textAlign = Paint.Align.LEFT
  }

  // ---------------------------------------------------------------------------
  // Optional effects (bug, popup and film grain). Kept subtle for legibility.
  // ---------------------------------------------------------------------------

  private fun drawBug(
    canvas: Canvas,
    width: Int,
    height: Int,
    frame: CodeFrameSpec,
  ) {
    if (!frame.showBug) return
    val r = minOf(width, height) * 0.03f
    val bx = width * 0.84f + frame.driftX
    val by = height * 0.22f + sin(frame.frameIndex / 14f) * r * 0.5f + frame.driftY
    val accent = frame.config.palette.important.toInt()
    surfacePaint.style = Paint.Style.STROKE
    surfacePaint.color = accent
    surfacePaint.strokeWidth = r * 0.12f
    surfacePaint.setShadowLayer(r * 0.5f, 0f, 0f, accent)
    canvas.drawCircle(bx, by, r * 1.35f, surfacePaint)
    surfacePaint.setShadowLayer(0f, 0f, 0f, 0)
    surfacePaint.style = Paint.Style.FILL

    canvas.save()
    canvas.translate(bx, by)
    canvas.rotate((frame.frameIndex * 3f) % 360f)
    surfacePaint.color = 0xFF2A2D36.toInt()
    canvas.drawOval(RectF(-r * 0.45f, -r * 0.62f, r * 0.45f, r * 0.62f), surfacePaint)
    surfacePaint.style = Paint.Style.STROKE
    surfacePaint.color = accent
    surfacePaint.strokeWidth = r * 0.09f
    for (side in -1..1 step 2) {
      for (i in -1..1) {
        val y = i * r * 0.34f
        canvas.drawLine(side * r * 0.35f, y, side * r * 0.92f, y + r * 0.28f, surfacePaint)
      }
      canvas.drawLine(side * r * 0.18f, -r * 0.55f, side * r * 0.55f, -r * 0.95f, surfacePaint)
    }
    surfacePaint.style = Paint.Style.FILL
    canvas.restore()
  }

  private fun ensureNoise(size: Int) {
    if (noiseBitmap != null && noiseSize == size) return
    noiseSize = size
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val random = Random(1337)
    val pixels = IntArray(size * size)
    for (i in pixels.indices) {
      val value = random.nextInt(256)
      pixels[i] = Color.argb(random.nextInt(80), value, value, value)
    }
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    noiseBitmap?.recycle()
    noiseBitmap = bitmap
  }

  private fun ensureScanlines() {
    if (scanlineBitmap != null) return
    val bitmap = Bitmap.createBitmap(1, 6, Bitmap.Config.ARGB_8888)
    bitmap.setPixel(0, 0, Color.argb(30, 0, 0, 0))
    (1..5).forEach { bitmap.setPixel(0, it, Color.TRANSPARENT) }
    scanlineBitmap = bitmap
  }

  private fun drawScanlines(
    canvas: Canvas,
    width: Int,
    height: Int,
    frame: CodeFrameSpec,
  ) {
    if (frame.config.lowPower) return
    ensureScanlines()
    val bitmap = scanlineBitmap ?: return
    val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    surfacePaint.shader = shader
    surfacePaint.alpha = (26 + frame.config.grain * 36).toInt().coerceIn(0, 255)
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), surfacePaint)
    surfacePaint.shader = null
    surfacePaint.alpha = 255
  }

  private fun drawGrain(
    canvas: Canvas,
    width: Int,
    height: Int,
    frame: CodeFrameSpec,
  ) {
    val grain = frame.config.grain
    if (grain <= 0.01f) return
    ensureNoise(128)
    val bitmap = noiseBitmap ?: return
    val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    val matrix = Matrix()
    matrix.setTranslate(
      ((frame.frameIndex * 37) % 128).toFloat(),
      ((frame.frameIndex * 61) % 128).toFloat(),
    )
    shader.setLocalMatrix(matrix)
    surfacePaint.shader = shader
    surfacePaint.alpha = (grain * 48f).toInt().coerceIn(0, 80)
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), surfacePaint)
    surfacePaint.shader = null
    surfacePaint.alpha = 255
  }

  private fun drawGlitch(
    canvas: Canvas,
    width: Int,
    height: Int,
    frame: CodeFrameSpec,
  ) {
    val glitch = frame.config.glitchIntensity
    if (glitch <= 0.05f || frame.config.lowPower || frame.frameIndex % 23 >= 2) return
    val colors =
      listOf(
        0xFFFF5C8A.toInt(),
        0xFF5DE1FF.toInt(),
        frame.config.palette.keyword.toInt(),
      )
    colors.forEachIndexed { i, color ->
      val y = ((frame.frameIndex * (i + 3) * 97) % height).toFloat()
      surfacePaint.color = color
      surfacePaint.alpha = (glitch * 45f).toInt().coerceIn(0, 80)
      canvas.drawRect(0f, y, width.toFloat(), y + 2f + glitch * 6f, surfacePaint)
    }
    surfacePaint.alpha = 255
  }

  private fun drawPopup(
    canvas: Canvas,
    width: Int,
    height: Int,
    frame: CodeFrameSpec,
  ) {
    if (frame.popupText.isBlank()) return
    val progress = (frame.popupAgeMs.coerceAtLeast(0L) / 260f).coerceIn(0f, 1f)
    val fade = (1f - (frame.popupAgeMs - 2_200f) / 500f).coerceIn(0f, 1f)
    if (fade <= 0f) return
    val left = width * 0.18f + (1f - progress) * width * 0.05f
    val top = height * 0.34f
    val right = width * 0.82f + (1f - progress) * width * 0.05f
    val bottom = height * 0.52f
    val accent = frame.config.palette.important.toInt()

    surfacePaint.setShadowLayer(28f, 0f, 10f, Color.BLACK)
    surfacePaint.color = Color.argb((238 * fade).toInt(), 12, 17, 28)
    canvas.drawRoundRect(RectF(left, top, right, bottom), width * 0.014f, width * 0.014f, surfacePaint)
    surfacePaint.setShadowLayer(0f, 0f, 0f, 0)
    surfacePaint.style = Paint.Style.STROKE
    surfacePaint.strokeWidth = 1.6f
    surfacePaint.color = withAlpha(accent, (170 * fade).toInt().coerceIn(0, 255))
    canvas.drawRoundRect(RectF(left, top, right, bottom), width * 0.014f, width * 0.014f, surfacePaint)
    surfacePaint.style = Paint.Style.FILL

    configureText(
      textPaint,
      minOf(width, height) * 0.026f,
      Color.argb((245 * fade).toInt(), 235, 240, 250),
      0f,
    )
    canvas.drawText(frame.popupText, left + width * 0.04f, top + height * 0.06f, textPaint)
    configureText(
      textPaint,
      minOf(width, height) * 0.02f,
      withAlpha(frame.config.palette.comment.toInt(), (210 * fade).toInt().coerceIn(0, 255)),
      0f,
    )
    canvas.drawText(
      "// the player clock is still right — continue when ready",
      left + width * 0.04f,
      bottom - height * 0.035f,
      textPaint,
    )
  }
}
