package echo.music.iad1tya.ui.screens.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.music.echo.codepoetry.CodeComposition
import com.music.echo.codepoetry.CodePalette
import com.music.echo.codepoetry.CodePoetryPolicy
import com.music.echo.codepoetry.CodePoetryRenderer
import com.music.echo.codepoetry.rememberCodePoetrySettings
import echo.music.iad1tya.R
import echo.music.iad1tya.ui.component.KaraokeWord
import echo.music.iad1tya.ui.theme.OutfitFontFamily
import echo.music.iad1tya.ui.theme.bbhBartle

/** Durable CODE POETRY customisation, backed by the player's own DataStore. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodePoetrySettingsScreen(navController: NavController) {
  val settings = rememberCodePoetrySettings()

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            "CODE POETRY",
            fontSize = 24.sp,
            fontFamily = bbhBartle,
            fontWeight = FontWeight.Bold,
          )
        },
        navigationIcon = {
          IconButton(onClick = { navController.navigateUp() }) {
            Icon(
              painter = painterResource(R.drawable.arrow_back),
              contentDescription = "Back",
            )
          }
        },
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        CodeCard {
          SectionTitle("CODE POETRY")
          Text(
            "Lyrics become live code in a dark editor. Typing, highlights, camera and effects all follow the real player position — no video, no second player.",
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.78f),
          )
          Text(
            "Word timestamps are used when the provider supplies them. Estimated timing is labelled and can be corrected with −/+ in the overlay.",
            fontSize = 14.sp,
            color = Color(0xFFFF8A7A),
          )
        }
      }

      item {
        SectionTitle("LIVE PREVIEW")
        CodePreview(settings.palette.value, settings.fontSize.value)
      }

      item {
        CodeCard {
          SectionTitle("TYPING & TYPE")
          CodeSlider(
            "Typing speed",
            settings.typingSpeed.value,
            0.25f..3f,
          ) { settings.typingSpeed.value = it }
          CodeSlider("Font size", settings.fontSize.value, 0.7f..1.8f) {
            settings.fontSize.value = it
          }
          Text("Palette", color = Color(0xFFFF5F57), fontSize = 15.sp, fontFamily = bbhBartle)
          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            CodePalette.entries.forEach { palette ->
              FilterChip(
                selected = settings.palette.value == palette,
                onClick = { settings.palette.value = palette },
                label = { Text(palette.label, fontSize = 14.sp, fontFamily = OutfitFontFamily) },
                leadingIcon = {
                  Box(
                    Modifier
                      .padding(4.dp)
                      .clip(RoundedCornerShape(4.dp))
                      .background(Color(palette.keyword.toInt()))
                      .height(14.dp)
                      .fillMaxWidth(0.06f),
                  )
                },
              )
            }
          }
          var cursorText by remember { mutableStateOf(settings.cursorColor.value) }
          OutlinedTextField(
            value = cursorText,
            onValueChange = {
              cursorText = it
              settings.cursorColor.value = it
            },
            label = { Text("Cursor colour (#RRGGBB, blank = palette)", fontSize = 14.sp) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
              fontSize = 15.sp,
              fontFamily = OutfitFontFamily,
            ),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      item {
        CodeCard {
          SectionTitle("CINEMATIC CAMERA & EFFECTS")
          CodeSlider("Camera intensity", settings.cameraIntensity.value, 0f..1f) {
            settings.cameraIntensity.value = it
          }
          CodeSlider("Glitch intensity", settings.glitchIntensity.value, 0f..1f) {
            settings.glitchIntensity.value = it
          }
          CodeSlider("Film grain", settings.grain.value, 0f..1f) { settings.grain.value = it }
          CodeSlider("Popup frequency", settings.popupFrequency.value, 0f..1f) {
            settings.popupFrequency.value = it
          }
        }
      }

      item {
        CodeCard {
          SectionTitle("COMPOSITIONS & DISPLAY")
          CodeSwitch("Automatic composition switching", settings.autoComposition.value) {
            settings.autoComposition.value = it
          }
          if (!settings.autoComposition.value) {
            Text("Fixed composition", color = Color(0xFFFF5F57), fontSize = 15.sp, fontFamily = bbhBartle)
            Row(
              modifier = Modifier.horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              CodeComposition.entries.forEach { composition ->
                FilterChip(
                  selected = settings.composition.value == composition,
                  onClick = { settings.composition.value = composition },
                  label = {
                    Text(composition.label, fontSize = 14.sp, fontFamily = OutfitFontFamily)
                  },
                )
              }
            }
          }
          CodeSwitch("3D bug symbol at peaks", settings.bugEnabled.value) {
            settings.bugEnabled.value = it
          }
          CodeSwitch("Always-on AMOLED display", settings.alwaysOn.value) {
            settings.alwaysOn.value = it
          }
          Text(
            "Always-on uses a restrained, low-power renderer and slowly drifts the text to reduce burn-in.",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.7f),
          )
        }
      }
    }
  }
}

@Composable
private fun CodeCard(content: @Composable () -> Unit) {
  Surface(
    color = Color(0xFF12141A),
    shape = RoundedCornerShape(18.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      content()
    }
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(
    text,
    color = Color(0xFFFF5F57),
    fontSize = 16.sp,
    fontFamily = bbhBartle,
    fontWeight = FontWeight.Bold,
  )
}

@Composable
private fun CodeSlider(
  label: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  onChange: (Float) -> Unit,
) {
  Text(
    "$label · ${String.format("%.2f", value)}",
    fontSize = 15.sp,
    fontFamily = OutfitFontFamily,
    fontWeight = FontWeight.Medium,
  )
  Slider(value = value, valueRange = range, onValueChange = onChange)
}

@Composable
private fun CodeSwitch(
  label: String,
  value: Boolean,
  onChange: (Boolean) -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      label,
      fontSize = 15.sp,
      fontFamily = OutfitFontFamily,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.weight(1f),
    )
    Switch(checked = value, onCheckedChange = onChange)
  }
}

@Composable
private fun CodePreview(
  palette: CodePalette,
  fontScale: Float,
) {
  val config =
    com.music.echo.codepoetry.CodePoetryConfig(
      typingSpeed = 1f,
      fontSize = fontScale,
      palette = palette,
      cursorArgb = palette.cursor.toInt(),
      cameraIntensity = 0.35f,
      glitchIntensity = 0.25f,
      grain = 0.15f,
      popupFrequency = 0.35f,
      bugEnabled = true,
      autoComposition = false,
      composition = CodeComposition.FULL_EDITOR,
      alwaysOn = false,
      lowPower = false,
    )
  val phrases =
    CodePoetryPolicy.groupPhrases(
      listOf(
        KaraokeWord("we", 0L, 320L),
        KaraokeWord("were", 320L, 760L),
        KaraokeWord("never", 760L, 1_340L),
        KaraokeWord("really", 1_340L, 1_900L),
        KaraokeWord("alone", 1_900L, 2_800L),
        KaraokeWord("in", 2_900L, 3_100L),
        KaraokeWord("the", 3_100L, 3_300L),
        KaraokeWord("silence", 3_300L, 4_600L),
      ),
    )
  val frame =
    CodePoetryPolicy.buildFrame(
      phrases = phrases,
      positionMs = 2_050L,
      config = config,
      isPlaying = true,
      energy = 0.55f,
      title = "Preview",
      artist = "CODE POETRY",
      frameIndex = 12,
    )
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(300.dp)
      .clip(RoundedCornerShape(18.dp))
      .background(Color.Black),
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawIntoCanvas { canvas ->
        CodePoetryRenderer.render(
          canvas = canvas.nativeCanvas,
          width = size.width.toInt(),
          height = size.height.toInt(),
          frame = frame,
        )
      }
    }
  }
}
