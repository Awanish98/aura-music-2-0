package echo.music.iad1tya.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.EntryPointAccessors
import echo.music.iad1tya.LocalDatabase
import echo.music.iad1tya.LocalPlayerConnection
import echo.music.iad1tya.R
import echo.music.iad1tya.db.entities.LyricsEntity
import echo.music.iad1tya.lyrics.LyricsUtils.parseLyrics
import echo.music.iad1tya.models.MediaMetadata
import echo.music.iad1tya.ui.component.ImmersiveOverlayEffect
import echo.music.iad1tya.ui.component.frostedGlassControl
import echo.music.iad1tya.ui.component.safeTopInset
import echo.music.iad1tya.ui.theme.OutfitFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Distraction-free, full-screen lyrics page.
 *
 * The screen holds nothing but the synced lyrics in the middle and a small,
 * translucent transport strip at the bottom (previous / play-pause / next /
 * fullscreen). It deliberately reuses the single player clock passed in as
 * [positionMs]; it never starts another player or lyric engine. Word/line
 * timing comes straight from the same parsed LRC used everywhere else.
 */
@Composable
fun CleanLyricsOverlay(
  mediaMetadata: MediaMetadata?,
  positionMs: Long,
  isPlaying: Boolean,
  isFullscreen: Boolean,
  onTogglePlayPause: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onToggleFullscreen: () -> Unit,
  onDismiss: () -> Unit,
) {
  val playerConnection = LocalPlayerConnection.current ?: return
  val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
  val context = LocalContext.current
  val database = LocalDatabase.current
  val coroutineScope = rememberCoroutineScope()

  // Fetch lyrics on demand so this surface works without the inline lyrics view.
  LaunchedEffect(mediaMetadata?.id, currentLyrics) {
    if (mediaMetadata != null && currentLyrics == null) {
      delay(400)
      coroutineScope.launch(Dispatchers.IO) {
        try {
          val existing = database.lyrics(mediaMetadata.id).firstOrNull()
          if (existing != null) return@launch
          val entryPoint =
            EntryPointAccessors.fromApplication(
              context.applicationContext,
              echo.music.iad1tya.di.LyricsHelperEntryPoint::class.java,
            )
          val fetched = entryPoint.lyricsHelper().getLyrics(mediaMetadata)
          database.query {
            upsert(
              LyricsEntity(
                mediaMetadata.id,
                fetched.lyrics ?: "",
                fetched.providerName,
              )
            )
          }
        } catch (_: Exception) {
          // Timing stays on the player clock; a failure just leaves this empty.
        }
      }
    }
  }

  val lines =
    remember(currentLyrics) {
      val text = currentLyrics?.lyrics?.trim()
      if (text.isNullOrEmpty() || !text.startsWith("[")) emptyList()
      else parseLyrics(text).filter { it.text.isNotBlank() }
    }

  val currentIndex =
    remember(positionMs, lines) {
      if (lines.isEmpty()) -1 else lines.indexOfLast { it.time <= positionMs }.coerceAtLeast(0)
    }

  BackHandler(onBack = onDismiss)
  ImmersiveOverlayEffect()

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            listOf(
              Color(0xFF0A0A0D),
              Color(0xFF050506),
              Color(0xFF000000),
            )
          )
        ),
  ) {
    // Tap anywhere to reveal the controls again after they auto-hide.
    Box(modifier = Modifier.fillMaxSize().clickable { /* controls stay visible */ })

    Column(
      modifier =
        Modifier
          .align(Alignment.Center)
          .fillMaxWidth()
          .padding(horizontal = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      when {
        currentLyrics == null -> {
          Text(
            text = "Loading lyrics…",
            color = Color.White.copy(alpha = 0.4f),
            fontFamily = OutfitFontFamily,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
          )
        }
        lines.isEmpty() -> {
          Text(
            text = mediaMetadata?.title?.let { "♪  $it" } ?: "Instrumental",
            color = Color.White.copy(alpha = 0.5f),
            fontFamily = OutfitFontFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
          )
        }
        else -> {
          // A tight window of lines around the active one keeps the page clean
          // while still showing where the song is going.
          val first = (currentIndex - 2).coerceAtLeast(0)
          val last = (currentIndex + 2).coerceAtMost(lines.lastIndex)
          for (index in first..last) {
            val line = lines[index]
            val distance = kotlin.math.abs(index - currentIndex)
            val isCurrent = distance == 0
            val alpha =
              when (distance) {
                0 -> 1f
                1 -> 0.5f
                else -> 0.26f
              }
            val textColor by
              animateColorAsState(
                targetValue = Color.White.copy(alpha = alpha),
                animationSpec = tween(220),
                label = "cleanLyricColor",
              )
            Text(
              text = line.text,
              color = textColor,
              fontFamily = OutfitFontFamily,
              fontSize = if (isCurrent) 30.sp else 21.sp,
              fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
              textAlign = TextAlign.Center,
              lineHeight = if (isCurrent) 38.sp else 28.sp,
              modifier = Modifier.padding(vertical = 10.dp),
            )
          }
        }
      }
    }

    // Minimal, translucent transport strip. Only the four requested controls.
    Row(
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 28.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Spacer(Modifier.width(16.dp))
      CleanTransportButton(onClick = onPrevious, icon = R.drawable.skip_previous, description = "Previous song")
      Spacer(Modifier.width(12.dp))
      CleanTransportButton(
        onClick = onTogglePlayPause,
        icon = if (isPlaying) R.drawable.pause else R.drawable.play,
        description = if (isPlaying) "Pause" else "Play",
        accent = true,
      )
      Spacer(Modifier.width(12.dp))
      CleanTransportButton(onClick = onNext, icon = R.drawable.skip_next, description = "Next song")
      Spacer(Modifier.width(12.dp))
      CleanTransportButton(
        onClick = onToggleFullscreen,
        icon = if (isFullscreen) R.drawable.fullscreen_exit else R.drawable.fullscreen,
        description = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
      )
      Spacer(Modifier.width(16.dp))
    }

    // Small, unobtrusive exit control so the page can be closed without a gesture.
    IconButton(
      onClick = onDismiss,
      modifier =
        Modifier
          .align(Alignment.TopEnd)
          .safeTopInset()
          .padding(12.dp)
          .size(40.dp)
          .clip(CircleShape)
          .frostedGlassControl(CircleShape, fallbackColor = Color.White.copy(alpha = 0.08f)),
    ) {
      Icon(
        painter = painterResource(R.drawable.close),
        contentDescription = "Close clean lyrics",
        tint = Color.White.copy(alpha = 0.7f),
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
private fun CleanTransportButton(
  onClick: () -> Unit,
  icon: Int,
  description: String,
  accent: Boolean = false,
) {
  val container =
    if (accent) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f)
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      Modifier
        .size(if (accent) 62.dp else 52.dp)
        .clip(RoundedCornerShape(if (accent) 31.dp else 26.dp))
        .frostedGlassControl(
          RoundedCornerShape(if (accent) 31.dp else 26.dp),
          fallbackColor = container,
        )
        .clickable(onClick = onClick),
  ) {
    Icon(
      painter = painterResource(icon),
      contentDescription = description,
      tint = Color.White,
      modifier = Modifier.size(if (accent) 28.dp else 24.dp),
    )
  }
}
