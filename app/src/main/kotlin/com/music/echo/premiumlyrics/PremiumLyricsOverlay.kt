package com.music.echo.premiumlyrics

import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.compose.runtime.withFrameMillis
import echo.music.iad1tya.R
import echo.music.iad1tya.constants.PremiumLyricStyleKey
import echo.music.iad1tya.models.MediaMetadata
import echo.music.iad1tya.ui.component.ImmersiveOverlayEffect
import echo.music.iad1tya.ui.component.frostedGlassControl
import echo.music.iad1tya.ui.component.isGlassSupported
import echo.music.iad1tya.ui.component.safeTopInset
import echo.music.iad1tya.ui.player.CinematicEngineState
import echo.music.iad1tya.ui.theme.PlusJakartaSansFontFamily
import echo.music.iad1tya.utils.makeTimeString
import echo.music.iad1tya.utils.rememberEnumPreference
import kotlin.math.abs

/**
 * 15 handcrafted premium looks on one true-black surface. The AUTO preference
 * reads the shared beat/BPM/energy features and switches looks with hysteresis;
 * lyrics and timestamps always come from [engine].
 */
@Composable
fun PremiumLyricsOverlay(
  mediaMetadata: MediaMetadata?,
  engine: CinematicEngineState,
  positionMs: Long,
  durationMs: Long,
  isPlaying: Boolean,
  isFullscreen: Boolean,
  audioReactiveAvailable: Boolean,
  onTogglePlayPause: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onSeek: (Long) -> Unit,
  onDismiss: () -> Unit,
  onToggleFullscreen: () -> Unit,
  onRequestAudio: () -> Unit,
  /** When set, this overlay becomes a mood-matched AUTO surface: no manual style picker. */
  forcedStyle: PremiumLyricStyle? = null,
  motionScale: Float = 1f,
  glowScale: Float = 1f,
  visual: (@Composable () -> Unit)? = null,
  moodBadge: String? = null,
) {
  val context = LocalContext.current
  val (preferredStyle, onPreferredStyleChange) =
    rememberEnumPreference(PremiumLyricStyleKey, PremiumLyricStyle.AUTO)

  var showControls by remember { mutableStateOf(false) }
  var showStylePicker by remember { mutableStateOf(false) }
  var frameTime by remember { mutableLongStateOf(0L) }
  val autoState = remember { AutoStyleState() }
  val currentOnSeek = rememberUpdatedState(onSeek)

  LaunchedEffect(Unit) {
    var lastPublished = 0L
    while (true) {
      withFrameMillis { now ->
        if (now - lastPublished >= 32L) {
          lastPublished = now
          frameTime = now
        }
      }
    }
  }

  val style =
    forcedStyle ?: if (preferredStyle == PremiumLyricStyle.AUTO) {
      val input =
        PremiumAutoInput(
          features = engine.features,
          section = engine.section,
          mood = engine.mood,
          positionMs = engine.visual.positionMs,
        )
      val chosen = PremiumLyricsPolicy.chooseAutoStyle(input, autoState.style, frameTime - autoState.changedAt)
      if (chosen != autoState.style) {
        autoState.style = chosen
        autoState.changedAt = frameTime
      }
      autoState.style
    } else {
      preferredStyle
    }

  // Each look has its own typographic voice; the renderer keeps the shared
  // editorial rhythm so switching looks never changes the lyric content.
  val typeface =
    remember(style.look) {
      when (style.look.font) {
        PremiumLyricFont.MONO -> Typeface.MONOSPACE
        PremiumLyricFont.POSTER -> ResourcesCompat.getFont(context, R.font.bbh_bartle_regular)
        PremiumLyricFont.SERIF -> ResourcesCompat.getFont(context, R.font.cormorant_garamond_italic)
        PremiumLyricFont.DISPLAY -> ResourcesCompat.getFont(context, R.font.outfit)
        PremiumLyricFont.SANS -> ResourcesCompat.getFont(context, R.font.plus_jakarta_sans)
      }
    }

  val frame =
    PremiumLyricsPolicy.buildFrame(
      words = engine.words,
      positionMs = engine.visual.positionMs,
      durationMs = durationMs,
      style = style,
      features = engine.features,
      frameTimeMs = frameTime,
      title = mediaMetadata?.title.orEmpty(),
      artist = mediaMetadata?.artists?.firstOrNull()?.name.orEmpty(),
      timingVerified = engine.timingVerified,
      lowPower = !audioReactiveAvailable,
      motionScale = motionScale,
      glowScale = glowScale,
    )

  BackHandler {
    when {
      showStylePicker -> showStylePicker = false
      showControls -> showControls = false
      else -> onDismiss()
    }
  }
  ImmersiveOverlayEffect()

  BoxWithConstraints(
    modifier =
      Modifier
        .fillMaxSize()
        .then(if (visual == null) Modifier.background(Color.Black) else Modifier),
  ) {
    visual?.invoke()
    Canvas(
      modifier =
        Modifier
          .fillMaxSize()
          .clickable {
            if (showStylePicker) showStylePicker = false else showControls = !showControls
          }
    ) {
      drawIntoCanvas { canvas ->
        PremiumLyricsRenderer.render(
          canvas = canvas.nativeCanvas,
          width = size.width.toInt(),
          height = size.height.toInt(),
          frame = frame,
          typeface = typeface,
          transparentBackground = visual != null,
        )
      }
    }

    if (moodBadge != null && !showControls && !showStylePicker) {
      Text(
        text = moodBadge,
        color = Color.White.copy(alpha = 0.72f),
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier =
          Modifier
            .align(Alignment.TopStart)
            .safeTopInset()
            .padding(horizontal = 18.dp, vertical = 20.dp),
      )
    }

    AnimatedVisibility(
      visible = showControls,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.TopStart).safeTopInset().padding(12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDismiss) {
          Icon(
            painter = painterResource(R.drawable.close),
            contentDescription = "Close Premium Lyrics",
            tint = Color.White,
            modifier = Modifier.size(22.dp),
          )
        }
        Spacer(Modifier.width(4.dp))
        TextButtonGlass(text = "Hide") { showControls = false }
        if (forcedStyle == null) {
          Spacer(Modifier.width(4.dp))
          TextButtonGlass(text = "Style") { showStylePicker = true }
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onToggleFullscreen) {
          Text(
            if (isFullscreen) "⤡" else "⤢",
            color = Color.White,
            fontSize = 20.sp,
          )
        }
      }
    }

    if (!showControls && !showStylePicker && !engine.timingVerified) {
      Text(
        text = "Approx. word timing · tap for styles",
        color = Color.White.copy(alpha = 0.3f),
        fontSize = 10.sp,
        modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 18.dp, vertical = 92.dp),
      )
    }

    Column(
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
      if (showControls) {
        Text(
          text = mediaMetadata?.title.orEmpty(),
          color = Color.White.copy(alpha = 0.9f),
          fontFamily = PlusJakartaSansFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          maxLines = 1,
        )
        val artist = mediaMetadata?.artists?.firstOrNull()?.name.orEmpty()
        if (!artist.isNullOrBlank()) {
          Text(
            text = artist,
            color = Color.White.copy(alpha = 0.5f),
            fontFamily = PlusJakartaSansFontFamily,
            fontSize = 12.sp,
            maxLines = 1,
          )
        }
        Spacer(Modifier.height(10.dp))
      }

      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .pointerInput(durationMs) {
              awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                val up = waitForUpOrCancellation()
                if (up != null) {
                  up.consume()
                  val stayedOnBar =
                    abs(up.position.x - down.position.x) < 64f &&
                      abs(up.position.y - down.position.y) < 64f
                  if (stayedOnBar && durationMs > 0L) {
                    currentOnSeek.value((up.position.x / size.width * durationMs).toLong())
                  }
                }
              }
            },
      ) {
        if (durationMs > 0L) {
          Box(
            modifier =
              Modifier
                .fillMaxWidth((positionMs.coerceIn(0L, durationMs).toFloat() / durationMs.toFloat()))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(style.accentArgb.toInt())),
          )
        }
      }

      if (showControls) {
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(makeTimeString(positionMs), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) {
              Text("⏮", color = Color.White, fontSize = 18.sp)
            }
            IconButton(onClick = onTogglePlayPause) {
              Text(if (isPlaying) "⏸" else "▶", color = Color.White, fontSize = 20.sp)
            }
            IconButton(onClick = onNext) {
              Text("⏭", color = Color.White, fontSize = 18.sp)
            }
          }
          Text(
            if (forcedStyle != null) {
              moodBadge ?: style.label
            } else if (preferredStyle == PremiumLyricStyle.AUTO) {
              "AUTO · ${style.label}"
            } else {
              style.label
            },
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 10.sp,
          )
        }
        if (!audioReactiveAvailable) {
          TextButtonGlass(text = "Enable beat detection") { onRequestAudio() }
        }
      }
    }

    AnimatedVisibility(
      visible = showStylePicker,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .background(Color(0xD9000000))
            .clickable { showStylePicker = false },
      ) {
        Surface(
          color = Color.Transparent,
          shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
          modifier =
            Modifier
              .align(Alignment.BottomCenter)
              .fillMaxWidth()
              .padding(top = 80.dp)
              .then(
                if (isGlassSupported()) {
                  Modifier.frostedGlassControl(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                } else {
                  Modifier.background(Color(0xF2101116))
                }
              ),
        ) {
          Column(
            modifier =
              Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .verticalScroll(rememberScrollState()),
          ) {
            Text(
              "15 PREMIUM LYRIC LOOKS",
              color = Color(0xFFFF5F57),
              fontFamily = PlusJakartaSansFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 18.sp,
            )
            Text(
              "AUTO listens to BPM, beat and energy and switches styles by itself.",
              color = Color.White.copy(alpha = 0.65f),
              fontFamily = PlusJakartaSansFontFamily,
              fontSize = 12.sp,
              modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            StyleChipRow(
              title = "Smart",
              selected = preferredStyle,
              items = listOf(PremiumLyricStyle.AUTO),
              onSelect = { onPreferredStyleChange(it) },
            )
            lookGroups.forEach { (title, font) ->
              StyleChipRow(
                title = title,
                selected = preferredStyle,
                items = PremiumLyricStyle.SELECTABLE.filter { it.look.font == font },
                onSelect = { onPreferredStyleChange(it) },
              )
            }
            Spacer(Modifier.height(12.dp))
          }
        }
      }
    }
  }
}

private val lookGroups =
  listOf(
    "Signature Sans" to PremiumLyricFont.SANS,
    "Geometric Display" to PremiumLyricFont.DISPLAY,
    "Cinematic Serif" to PremiumLyricFont.SERIF,
    "Graphic Poster" to PremiumLyricFont.POSTER,
    "Terminal Mono" to PremiumLyricFont.MONO,
  )

private class AutoStyleState {
  var style: PremiumLyricStyle = PremiumLyricStyle.AUTO
  var changedAt: Long = 0L
}

@Composable
private fun TextButtonGlass(text: String, onClick: () -> Unit) {
  val shape = RoundedCornerShape(14.dp)
  Text(
    text = text,
    color = Color.White.copy(alpha = 0.85f),
    fontFamily = PlusJakartaSansFontFamily,
    fontSize = 12.sp,
    modifier =
      Modifier
        .frostedGlassControl(shape)
        .clip(shape)
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 9.dp),
  )
}

@Composable
private fun StyleChipRow(
  title: String,
  selected: PremiumLyricStyle,
  items: List<PremiumLyricStyle>,
  onSelect: (PremiumLyricStyle) -> Unit,
) {
  Text(
    text = title.uppercase(),
    color = Color.White.copy(alpha = 0.5f),
    fontFamily = PlusJakartaSansFontFamily,
    fontSize = 11.sp,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
  )
  Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
  ) {
    items.forEach { item ->
      FilterChip(
        selected = selected == item,
        onClick = { onSelect(item) },
        colors =
          FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFFFF5F57),
            selectedLabelColor = Color.White,
            containerColor = Color.White.copy(alpha = 0.07f),
            labelColor = Color.White.copy(alpha = 0.8f),
          ),
        label = {
          Text(
            item.label,
            fontFamily = PlusJakartaSansFontFamily,
            fontSize = 11.sp,
            maxLines = 1,
          )
        },
      )
    }
  }
}
