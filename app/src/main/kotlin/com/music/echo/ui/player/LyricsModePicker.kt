package echo.music.iad1tya.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import echo.music.iad1tya.cinematic.CinematicTheme
import echo.music.iad1tya.ui.component.frostedGlassControl
import echo.music.iad1tya.ui.component.isGlassSupported
import echo.music.iad1tya.ui.theme.OutfitFontFamily
import echo.music.iad1tya.ui.theme.bbhBartle

/** Every lyrics/visualizer surface and every cinematic/AMOLED theme in one chooser. */
enum class LyricsModeChoice(val label: String, val description: String) {
  AUTO(
    "AUTO · Mood Match",
    "Reads the song's mood — sad, love, party, dark and more — and matches lyrics + visuals",
  ),
  CLEAN_LYRICS("Clean Lyrics", "Full-screen lyrics with prev, play/pause, next and fullscreen"),
  CLASSIC("Classic Lyrics", "Karaoke lyrics under the player controls"),
  INSTAGRAM_LRC("Instagram LRC", "Cinematic full-screen LRC typography"),
  WORD_FLOW("Word Flow", "Word-by-word cinematic full-screen lyrics"),
  CODE_POETRY("CODE POETRY", "Live code-editor lyrics visualizer"),
  AMOLED("AMOLED Lyrics", "True-black, low-power reading surface"),
  PREMIUM_LYRICS(
    "Premium Lyrics",
    "15 handcrafted looks on true black · AUTO follows the beat and switches by itself",
  ),
  VISUALIZER("Audio Visualizer", "Live frequency spectrum behind the controls"),
  FULLSCREEN_VISUALIZER(
    "Full-screen Visualizer",
    "Beat-synced spectrum, waves, particles and rings across the whole screen",
  ),
}

@Composable
fun LyricsModePickerDialog(
  current: LyricsModeChoice?,
  currentTheme: CinematicTheme,
  onSelect: (LyricsModeChoice) -> Unit,
  onSelectTheme: (CinematicTheme) -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties =
      DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
      ),
  ) {
    val pickerShape = RoundedCornerShape(24.dp)
    val pickerGlass = isGlassSupported()
    Surface(
      color = if (pickerGlass) Color.Transparent else Color(0xF2101116),
      shape = pickerShape,
      modifier =
        Modifier.padding(24.dp).fillMaxWidth().then(
          if (pickerGlass) {
            Modifier.frostedGlassControl(pickerShape, fallbackColor = Color.Transparent)
          } else {
            Modifier
          }
        ),
    ) {
      Column(Modifier.padding(20.dp)) {
        Text(
          "LYRICS & VISUALS",
          color = Color(0xFFFF5F57),
          fontSize = 22.sp,
          fontFamily = bbhBartle,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
          "One button for every lyric look. Pick a mode, then a theme.",
          color = Color.White.copy(alpha = 0.72f),
          fontSize = 14.sp,
          fontFamily = OutfitFontFamily,
        )
        LazyColumn(
          modifier = Modifier.padding(top = 12.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          items(LyricsModeChoice.entries) { mode ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier =
                Modifier
                  .fillMaxWidth()
                  .frostedGlassControl(RoundedCornerShape(14.dp))
                  .clickable { onSelect(mode) }
                  .padding(vertical = 10.dp, horizontal = 4.dp),
            ) {
              RadioButton(selected = current == mode, onClick = { onSelect(mode) })
              Column(Modifier.weight(1f)) {
                Text(
                  mode.label,
                  color = Color.White,
                  fontSize = 16.sp,
                  fontFamily = OutfitFontFamily,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  mode.description,
                  color = Color.White.copy(alpha = 0.6f),
                  fontSize = 13.sp,
                  fontFamily = OutfitFontFamily,
                )
              }
            }
          }

          item {
            Column(Modifier.padding(top = 14.dp)) {
              Text(
                "CINEMATIC & AMOLED THEMES",
                color = Color(0xFFFF5F57),
                fontSize = 12.sp,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
              )
              Text(
                "Applies to Word Flow, Cinematic and AMOLED lyrics · Current: ${currentTheme.displayName}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontFamily = OutfitFontFamily,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
              )
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
              ) {
                CinematicTheme.entries.forEach { theme ->
                  FilterChip(
                    selected = theme == currentTheme,
                    onClick = { onSelectTheme(theme) },
                    modifier =
                      if (pickerGlass && theme != currentTheme) {
                        Modifier.frostedGlassControl(RoundedCornerShape(18.dp))
                      } else {
                        Modifier
                      },
                    label = {
                      Text(
                        theme.displayName,
                        fontSize = 12.sp,
                        fontFamily = OutfitFontFamily,
                        maxLines = 1,
                      )
                    },
                    colors =
                      FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF5F57),
                        selectedLabelColor = Color.White,
                        containerColor =
                          if (pickerGlass) Color.Transparent else Color.White.copy(alpha = 0.08f),
                        labelColor = Color.White.copy(alpha = 0.8f),
                      ),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
