package echo.music.iad1tya.ui.bigscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.music.iad1tya.ui.player.AmoledLyricsPolicy
import echo.music.iad1tya.ui.player.CinematicBackdrop
import echo.music.iad1tya.ui.player.CinematicInstrumentalScene
import echo.music.iad1tya.ui.player.CinematicLyricsLayer
import echo.music.iad1tya.ui.player.CinematicParticles

/**
 * Dedicated large-screen renderer. It deliberately contains no mobile controls,
 * navigation or status bars: the external display only shows the experience.
 */
@Composable
fun AuraBigScreenRenderer() {
  val connection by BigScreenSession.connection.collectAsState()
  val state by BigScreenSession.visual.collectAsState()

  BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
    if (state == null) {
      Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          "AURA MUSIC",
          color = Color(0xFFE8C77B),
          fontFamily = FontFamily.Serif,
          fontWeight = FontWeight.Bold,
          fontSize = 34.sp,
          letterSpacing = 6.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
          connection?.name ?: "Waiting for your phone…",
          color = Color(0xFF9AA0AA),
          fontSize = 15.sp,
          textAlign = TextAlign.Center,
        )
      }
      return@BoxWithConstraints
    }

    val current = state!!
    val visual = current.visual
    val renderVisual =
      if (visual.settings.pureBlack) AmoledLyricsPolicy.sanitize(visual, visual.settings.lowPower)
      else visual
    if (!renderVisual.settings.pureBlack) {
      CinematicBackdrop(renderVisual, Modifier.fillMaxSize())
      CinematicParticles(renderVisual, Modifier.fillMaxSize())
    }

    if (current.lyricsVisible && current.words.isNotEmpty()) {
      CinematicLyricsLayer(
        visual = visual,
        words = current.words,
        screenWidth = maxWidth,
        screenHeight = maxHeight,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      CinematicInstrumentalScene(visual, Modifier.fillMaxSize())
    }

    Column(
      Modifier
        .align(Alignment.BottomStart)
        .padding(24.dp),
    ) {
      if (current.title.isNotBlank()) {
        Text(
          current.title,
          color = Color(visual.theme.text.toInt()).copy(alpha = 0.85f),
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
        )
      }
      if (current.artist.isNotBlank()) {
        Text(
          current.artist,
          color = Color(visual.theme.muted.toInt()),
          fontSize = 11.sp,
          maxLines = 1,
        )
      }
    }
  }
}
