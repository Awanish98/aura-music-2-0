package echo.music.iad1tya.lockscreen

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.compose.AsyncImage
import com.google.common.util.concurrent.MoreExecutors
import echo.music.iad1tya.constants.LockScreenArtworkEnabledKey
import echo.music.iad1tya.constants.LockScreenMetadataKeys
import echo.music.iad1tya.constants.LockScreenThemeKey
import echo.music.iad1tya.constants.LockScreenLyricsVisibleKey
import echo.music.iad1tya.lockscreen.LockScreenTheme.Companion.fromStored
import echo.music.iad1tya.playback.MusicService
import echo.music.iad1tya.ui.theme.echomusicTheme
import echo.music.iad1tya.utils.dataStore
import echo.music.iad1tya.utils.get
import echo.music.iad1tya.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Optional, user-enabled lock-screen-style player.
 *
 * Android does not allow an app to replace the system lock screen. This Activity
 * only raises itself above the keyguard when the user explicitly opts in, and
 * the system media notification remains the fallback on secure/limited devices.
 */
@OptIn(UnstableApi::class)
class LockScreenLyricsActivity : ComponentActivity() {

  private var controller: MediaController? = null
  private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? =
    null

  private var title by mutableStateOf("")
  private var artist by mutableStateOf("")
  private var artworkUri by mutableStateOf<String?>(null)
  private var lyricLine by mutableStateOf("")
  private var nextLine by mutableStateOf("")
  private var activeWord by mutableStateOf("")
  private var upcomingWord by mutableStateOf("")
  private var wordProgress by mutableStateOf(0f)
  private var wordVerified by mutableStateOf(true)
  private var isPlaying by mutableStateOf(false)
  private var positionMs by mutableLongStateOf(0L)
  private var durationMs by mutableLongStateOf(0L)

  private val playerListener =
    object : Player.Listener {
      override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = syncMetadata(mediaMetadata)

      override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        positionMs = 0L
        controller?.let { syncMetadata(it.mediaMetadata) }
      }

      override fun onIsPlayingChanged(playing: Boolean) {
        isPlaying = playing
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val allowOverlay = dataStore.get(echo.music.iad1tya.constants.LockScreenOverlayEnabledKey, false)
    if (allowOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
      window.addFlags(
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
          WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
      )
    } else {
      setShowWhenLocked(false)
      setTurnScreenOn(false)
      window.clearFlags(
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
          WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
      )
    }

    val token = SessionToken(this, ComponentName(this, MusicService::class.java))
    controllerFuture =
      MediaController.Builder(this, token).buildAsync().also { future ->
        future.addListener(
          {
            runCatching { future.get() }
              .onSuccess { newController ->
                controller = newController
                newController.addListener(playerListener)
                syncMetadata(newController.mediaMetadata)
                isPlaying = newController.isPlaying
                positionMs = newController.currentPosition
                durationMs = newController.duration.takeIf { it > 0 } ?: 0L
              }
              .onFailure { title = "Player unavailable" }
          },
          MoreExecutors.directExecutor(),
        )
      }

    lifecycleScope.launch {
      while (isActive) {
        controller?.let {
          if (it.isConnected) {
            positionMs = it.currentPosition
            durationMs = it.duration.takeIf { d -> d > 0 } ?: 0L
            isPlaying = it.isPlaying
          }
        }
        delay(200L)
      }
    }

    setContent {
      echomusicTheme(darkTheme = true, pureBlack = true) {
        val showArtwork by rememberPreference(LockScreenArtworkEnabledKey, true)
        val showLyrics by rememberPreference(LockScreenLyricsVisibleKey, true)
        val themeName by rememberPreference(LockScreenThemeKey, LockScreenTheme.CINEMATIC.name)
        LockScreenStage(
          title = title,
          artist = artist,
          artworkUri = artworkUri.takeIf { showArtwork },
          lyricLine = lyricLine.takeIf { showLyrics }.orEmpty(),
          nextLine = nextLine.takeIf { showLyrics }.orEmpty(),
          activeWord = activeWord.takeIf { showLyrics }.orEmpty(),
          upcomingWord = upcomingWord.takeIf { showLyrics }.orEmpty(),
          wordProgress = wordProgress,
          wordVerified = wordVerified,
          theme = fromStored(themeName),
          isPlaying = isPlaying,
          positionMs = positionMs,
          durationMs = durationMs,
          onPrevious = { controller?.seekToPrevious() },
          onTogglePlay = {
            controller?.let { if (it.isPlaying) it.pause() else it.play() }
          },
          onNext = { controller?.seekToNext() },
          onSeek = { ms -> controller?.seekTo(ms) },
          onClose = { finish() },
          onOpenPlayer = {
            runCatching {
              startActivity(
                Intent(this@LockScreenLyricsActivity, echo.music.iad1tya.MainActivity::class.java)
                  .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
              )
            }
            finish()
          },
        )
      }
    }
  }

  private fun syncMetadata(metadata: MediaMetadata) {
    title = metadata.title?.toString().orEmpty()
    artist = metadata.artist?.toString().orEmpty()
    artworkUri = metadata.artworkUri?.toString()
    val extras = metadata.extras ?: return
    lyricLine = extras.getString(LockScreenMetadataKeys.LINE).orEmpty()
    nextLine = extras.getString(LockScreenMetadataKeys.NEXT_LINE).orEmpty()
    activeWord = extras.getString(LockScreenMetadataKeys.WORD).orEmpty()
    upcomingWord = extras.getString(LockScreenMetadataKeys.UPCOMING_WORD).orEmpty()
    wordProgress = extras.getFloat(LockScreenMetadataKeys.WORD_PROGRESS, 0f)
    wordVerified = extras.getBoolean(LockScreenMetadataKeys.VERIFIED, true)
  }

  override fun onDestroy() {
    controller?.let { runCatching { it.removeListener(playerListener) } }
    controllerFuture?.let { MediaController.releaseFuture(it) }
    controller = null
    super.onDestroy()
  }
}

@Composable
private fun LockScreenStage(
  title: String,
  artist: String,
  artworkUri: String?,
  lyricLine: String,
  nextLine: String,
  activeWord: String,
  upcomingWord: String,
  wordProgress: Float,
  wordVerified: Boolean,
  theme: LockScreenTheme,
  isPlaying: Boolean,
  positionMs: Long,
  durationMs: Long,
  onPrevious: () -> Unit,
  onTogglePlay: () -> Unit,
  onNext: () -> Unit,
  onSeek: (Long) -> Unit,
  onClose: () -> Unit,
  onOpenPlayer: () -> Unit,
) {
  val accent = Color(theme.accentArgb)
  val secondary = Color(theme.secondaryArgb)
  val driftTransition = rememberInfiniteTransition(label = "LockScreenDrift")
  val drift by driftTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec =
      infiniteRepeatable(animation = tween(9_000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
    label = "Drift",
  )

  Box(modifier = Modifier.fillMaxSize().background(Color(0xFF000000))) {
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .background(
            Brush.linearGradient(
              colors =
                listOf(
                  Color(0xFF000000),
                  secondary.copy(alpha = 0.55f),
                  accent.copy(alpha = 0.18f + drift * 0.08f),
                  Color(0xFF000000),
                ),
              start = Offset(0f, drift * 900f),
              end = Offset(1200f, 900f - drift * 600f),
            )
          )
    )

    if (artworkUri != null) {
      AsyncImage(
        model = artworkUri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
          Modifier
            .fillMaxSize()
            .blur(42.dp)
            .alpha(0.32f),
      )
    }

    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      RowActions(onClose = onClose, onOpenPlayer = onOpenPlayer, accent = accent)

      Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = title.ifBlank { "Nothing playing" },
          color = Color.White,
          fontSize = 22.sp,
          fontWeight = FontWeight.SemiBold,
          textAlign = TextAlign.Center,
          maxLines = 2,
        )
        if (artist.isNotBlank()) {
          Spacer(Modifier.height(4.dp))
          Text(artist, color = Color.White.copy(alpha = 0.72f), fontSize = 14.sp)
        }
      }

      Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (lyricLine.isNotBlank()) {
          Text(
            text = lyricLine,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp,
          )
          if (activeWord.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
              text = activeWord,
              color = accent,
              fontSize = 20.sp,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
            )
            LinearProgressIndicator(
              progress = { wordProgress.coerceIn(0f, 1f) },
              modifier = Modifier.fillMaxWidth(0.45f).height(3.dp).clip(RoundedCornerShape(2.dp)),
              color = accent,
              trackColor = Color.White.copy(alpha = 0.12f),
            )
            if (!wordVerified) {
              Spacer(Modifier.height(4.dp))
              Text(
                "Estimated word timing",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 10.sp,
              )
            }
          }
          if (upcomingWord.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
              "Next word: $upcomingWord",
              color = Color.White.copy(alpha = 0.45f),
              fontSize = 12.sp,
              textAlign = TextAlign.Center,
            )
          }
          if (nextLine.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
              nextLine,
              color = Color.White.copy(alpha = 0.35f),
              fontSize = 15.sp,
              textAlign = TextAlign.Center,
              maxLines = 2,
            )
          }
        } else {
          Text(
            "Instrumental or no synced lyrics",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
          )
        }
      }

      Column(modifier = Modifier.fillMaxWidth()) {
        val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
          progress = { fraction },
          modifier =
            Modifier
              .fillMaxWidth()
              .height(5.dp)
              .clip(RoundedCornerShape(3.dp))
              .pointerInput(durationMs) {
                detectTapGestures { offset ->
                  if (durationMs > 0 && size.width > 0) {
                    val tapped = (offset.x / size.width) * durationMs
                    onSeek(tapped.toLong().coerceIn(0L, durationMs))
                  }
                }
              },
          color = accent,
          trackColor = Color.White.copy(alpha = 0.16f),
        )
        Spacer(Modifier.height(18.dp))
        androidx.compose.foundation.layout.Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
          }
          IconButton(
            onClick = onTogglePlay,
            modifier =
              Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(accent.copy(alpha = 0.22f)),
          ) {
            Icon(
              imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
              contentDescription = if (isPlaying) "Pause" else "Play",
              tint = accent,
              modifier = Modifier.size(36.dp),
            )
          }
          IconButton(onClick = onNext) {
            Icon(Icons.Filled.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(36.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun RowActions(onClose: () -> Unit, onOpenPlayer: () -> Unit, accent: Color) {
  androidx.compose.foundation.layout.Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    TextButton(onClick = onClose) { Text("Close", color = Color.White.copy(alpha = 0.7f)) }
    TextButton(onClick = onOpenPlayer) { Text("Open player", color = accent) }
  }
}
