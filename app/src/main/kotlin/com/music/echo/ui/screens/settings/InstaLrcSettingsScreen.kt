package echo.music.iad1tya.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.music.echo.insta.InstaAnimationPattern
import com.music.echo.insta.InstaBackground
import com.music.echo.insta.InstaFont
import com.music.echo.insta.InstaFrameRenderer
import com.music.echo.insta.InstaLrcMode
import com.music.echo.insta.InstaLrcPolicy
import com.music.echo.insta.InstaLrcSettingsState
import com.music.echo.insta.InstaPreset
import com.music.echo.insta.InstaRenderSpec
import com.music.echo.insta.InstaTextAlign
import com.music.echo.insta.InstaTextPosition
import com.music.echo.insta.InstaVisualStyle
import com.music.echo.insta.InstaWord
import com.music.echo.insta.rememberInstaLrcSettings
import echo.music.iad1tya.R
import echo.music.iad1tya.ui.player.CinematicSettingsState
import echo.music.iad1tya.ui.player.rememberCinematicSettings
import echo.music.iad1tya.ui.theme.OutfitFontFamily
import echo.music.iad1tya.ui.theme.bbhBartle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Durable INSTA LRC customization. It deliberately reuses the player's DataStore state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstaLrcSettingsScreen(navController: NavController) {
  val context = LocalContext.current
  val settings = rememberInstaLrcSettings()
  val cinematicSettings: CinematicSettingsState = rememberCinematicSettings()
  val preset = settings.preset
  var customName by remember { mutableStateOf("") }
  var showAdvanced by remember { mutableStateOf(false) }
  var backgroundBitmap by remember { mutableStateOf<Bitmap?>(null) }

  val imagePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      if (uri != null) {
        persistUri(context, uri)
        settings.imageUri.value = uri.toString()
      }
    }
  val videoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      if (uri != null) {
        persistUri(context, uri)
        settings.videoUri.value = uri.toString()
      }
    }

  val previewUri =
    when (preset.background) {
      InstaBackground.CUSTOM_IMAGE -> settings.imageUri.value
      InstaBackground.CUSTOM_VIDEO -> settings.videoUri.value
      else -> ""
    }
  LaunchedEffect(preset.background, previewUri) {
    backgroundBitmap = null
    if (previewUri.isBlank()) return@LaunchedEffect
    if (preset.background == InstaBackground.CUSTOM_VIDEO) {
      val retriever = MediaMetadataRetriever()
      val frame =
        runCatching {
          withContext(Dispatchers.IO) {
            retriever.setDataSource(context, Uri.parse(previewUri))
            retriever.getFrameAtTime(1_400_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
          }
        }.getOrNull()
      runCatching { withContext(Dispatchers.IO) { retriever.release() } }
      backgroundBitmap = frame
    } else {
      val request =
        ImageRequest.Builder(context).data(previewUri).size(480, 854).allowHardware(false).build()
      backgroundBitmap =
        runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull()
    }
  }

  val micGranted =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED
  val aiConfigured = cinematicSettings.backendBaseUrl.value.isNotBlank()

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            "INSTA LRC PLAY",
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
        InstaCard {
          SectionTitle("INSTA LRC PLAY")
          Text(
            "Lyrics move with the real player position. No extra player, no fake sync.",
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.78f),
          )
          Text(
            "AUTO picks the theme, typography and animation from the song. Choose MANUAL only when you want full control.",
            fontSize = 15.sp,
            color = Color(0xFFE8C77B),
            fontWeight = FontWeight.Medium,
          )
        }
      }

      item {
        SectionTitle("LIVE PREVIEW")
        InstaPresetPreview(preset = preset, backgroundBitmap = backgroundBitmap)
      }

      item {
        InstaCard {
          SectionTitle("HOW IT PLAYS")
          EnumChips(
            items = InstaLrcMode.entries.toList(),
            selected = settings.mode.value,
            label = { it.label },
            onSelect = { settings.mode.value = it },
          )
          Text(
            when (settings.mode.value) {
              InstaLrcMode.MANUAL -> "MANUAL uses your selected preset exactly."
              InstaLrcMode.AUTO ->
                "AUTO chooses style, fonts and motion from lyrics, mood, section and energy on this device."
              InstaLrcMode.SMART_AI ->
                if (!aiConfigured) {
                  "SMART AI has no secure backend configured. The player will label its local fallback; no AI result is claimed."
                } else {
                  "SMART AI uses your configured backend when it responds; offline or low-confidence results fall back to local selection and are labelled in the player."
                }
            },
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.78f),
          )
          Text(
            if (micGranted) {
              "Audio reactivity: real FFT/beat/bass analysis modulates decoration only; silence calms visuals."
            } else {
              "Audio reactivity unavailable until microphone permission is granted. Word timing is unaffected."
            },
            fontSize = 15.sp,
            color = if (micGranted) Color(0xFF9AD9B0) else Color(0xFFFFB4A2),
          )
        }
      }

      item {
        InstaCard {
          SectionTitle("QUICK LOOK")
          EnumChips(
            items = InstaPresetsLocal.builtIn,
            selected = preset,
            label = { it.name },
            onSelect = { settings.applyPreset(it) },
          )
          Text(
            preset.style.description,
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.7f),
          )
          Text(
            "AUTO keeps the oversized bold type and adapts motion to the song.",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.72f),
          )
        }
      }

      item {
        Button(
          onClick = { showAdvanced = !showAdvanced },
          modifier = Modifier.fillMaxWidth(),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = Color(0xFF1C1E25),
              contentColor = Color(0xFFE8C77B),
            ),
        ) {
          Text(
            if (showAdvanced) "HIDE EXTRA OPTIONS" else "MORE OPTIONS  ·  PRESETS, BACKGROUND, TYPOGRAPHY, TIMING",
            fontSize = 16.sp,
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      if (showAdvanced) {
        item {
          InstaCard {
            SectionTitle("MY PRESETS")
            OutlinedTextField(
              value = customName,
              onValueChange = { customName = it },
              label = {
                Text(
                  "Preset name",
                  fontSize = 15.sp,
                  fontFamily = OutfitFontFamily,
                )
              },
              textStyle =
                androidx.compose.ui.text.TextStyle(
                  fontSize = 16.sp,
                  fontFamily = OutfitFontFamily,
                ),
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(onClick = { customName = settings.saveCustomPreset(customName) }) {
                Text("SAVE CURRENT", fontSize = 15.sp)
              }
              if (settings.customPresets.any { it.name.equals(preset.name, true) }) {
                Button(
                  onClick = { settings.deleteCustomPreset(preset.name) },
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7A2E2E)),
                ) {
                  Text("DELETE", fontSize = 15.sp)
                }
              }
            }
            if (settings.customPresets.isNotEmpty()) {
              EnumChips(
                items = settings.customPresets,
                selected = preset,
                label = { it.name },
                onSelect = { settings.applyPreset(it) },
              )
            }
          }
        }

        item {
          InstaCard {
            SectionTitle("BACKGROUND")
            EnumChips(
              items = InstaBackground.entries.toList(),
              selected = preset.background,
              label = { it.label },
              onSelect = { value -> settings.updatePreset { it.copy(background = value) } },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(onClick = { imagePicker.launch("image/*") }) {
                Text("PICK IMAGE", fontSize = 15.sp)
              }
              Button(onClick = { videoPicker.launch("video/*") }) {
                Text("PICK VIDEO", fontSize = 15.sp)
              }
            }
            Text(
              "Files stay on your device. Custom video supplies position-matched frames without a second player.",
              fontSize = 16.sp,
              color = Color.White.copy(alpha = 0.72f),
            )
            InstaSlider("Background intensity", preset.backgroundIntensity, 0f..1f) {
              settings.updatePreset { p -> p.copy(backgroundIntensity = it) }
            }
            InstaSlider("Grain", preset.grainIntensity, 0f..1f) {
              settings.updatePreset { p -> p.copy(grainIntensity = it) }
            }
            InstaSlider("Blur", preset.blur, 0f..2f) {
              settings.updatePreset { p -> p.copy(blur = it) }
            }
          }
        }

        item {
          InstaCard {
            SectionTitle("TYPOGRAPHY")
            EnumChips(
              items = InstaFont.entries.toList(),
              selected = preset.font,
              label = { it.label },
              onSelect = { value -> settings.updatePreset { it.copy(font = value) } },
            )
            EnumChips(
              items = InstaTextAlign.entries.toList(),
              selected = preset.alignment,
              label = { it.label },
              onSelect = { value -> settings.updatePreset { it.copy(alignment = value) } },
            )
            EnumChips(
              items = InstaTextPosition.entries.toList(),
              selected = preset.position,
              label = { it.label },
              onSelect = { value -> settings.updatePreset { it.copy(position = value) } },
            )
            InstaSlider("Font size", preset.fontSize, 0.6f..2f) {
              settings.updatePreset { p -> p.copy(fontSize = it) }
            }
            InstaSlider("Font weight", preset.fontWeight.toFloat(), 300f..900f) {
              settings.updatePreset { p -> p.copy(fontWeight = it.roundToIntValue()) }
            }
            InstaSlider("Line spacing", preset.spacing, 0.6f..1.8f) {
              settings.updatePreset { p -> p.copy(spacing = it) }
            }
            InstaSlider("Word spacing", preset.wordSpacing, 0.6f..1.8f) {
              settings.updatePreset { p -> p.copy(wordSpacing = it) }
            }
            InstaSlider("Safe margin", preset.safeMargin, 0.03f..0.2f) {
              settings.updatePreset { p -> p.copy(safeMargin = it) }
            }
            OutlinedTextField(
              value = preset.watermark,
              onValueChange = { value -> settings.updatePreset { it.copy(watermark = value.take(32)) } },
              label = { Text("Watermark (blank to remove)", fontSize = 15.sp) },
              textStyle =
                androidx.compose.ui.text.TextStyle(
                  fontSize = 16.sp,
                  fontFamily = OutfitFontFamily,
                ),
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }

        item {
          InstaCard {
            SectionTitle("ANIMATION")
            EnumChips(
              items = InstaAnimationPattern.entries.toList(),
              selected = preset.pattern,
              label = { it.label },
              onSelect = { value -> settings.updatePreset { it.copy(pattern = value) } },
            )
            InstaSlider("Animation speed", preset.animationSpeed, 0.4f..2f) {
              settings.updatePreset { p -> p.copy(animationSpeed = it) }
            }
            InstaSlider("Transition duration (ms)", preset.transitionDurationMs.toFloat(), 90f..900f) {
              settings.updatePreset { p -> p.copy(transitionDurationMs = it.toLong()) }
            }
            InstaSlider("Animation intensity", preset.animationIntensity, 0f..1f) {
              settings.updatePreset { p -> p.copy(animationIntensity = it) }
            }
            InstaSlider("Glow", preset.glow, 0f..1f) {
              settings.updatePreset { p -> p.copy(glow = it) }
            }
            EnumChips<Pair<Long, String>>(
              items = highlightColors,
              selected =
                highlightColors.firstOrNull { it.first == preset.highlightColor }
                  ?: highlightColors.first(),
              label = { option -> option.second },
              onSelect = { option -> settings.updatePreset { it.copy(highlightColor = option.first) } },
            )
          }
        }

        item {
          InstaCard {
            SectionTitle("TIMING & POWER")
            InstaSlider(
              "INSTA timing offset (ms)",
              settings.timingOffsetMs.value.toFloat(),
              -2_000f..2_000f,
            ) { settings.timingOffsetMs.value = it.toLong() }
            Text(
              "Applied only to INSTA LRC PLAY. Existing lyrics offsets and saved corrections stay untouched.",
              fontSize = 16.sp,
              color = Color.White.copy(alpha = 0.72f),
            )
            SwitchRow("Low power", preset.lowPower) {
              settings.updatePreset { p -> p.copy(lowPower = it) }
            }
            SwitchRow("Safe mode (AMOLED, no reactive effects)", settings.safeMode.value) {
              settings.safeMode.value = it
            }
            SwitchRow("Mark estimated timings", settings.showEstimatedHint.value) {
              settings.showEstimatedHint.value = it
            }
          }
        }
      }
    }
  }
}

private val highlightColors: List<Pair<Long, String>> =
  listOf(
    0xFFE8C77BL to "Gold",
    0xFFFFFFFFL to "White",
    0xFF7ED5FFL to "Ice",
    0xFFFF8FB1L to "Rose",
    0xFFFF6B57L to "Ember",
  )

private object InstaPresetsLocal {
  val builtIn: List<InstaPreset> =
    listOf(
      com.music.echo.insta.InstaPresets.BUILT_IN[0],
      com.music.echo.insta.InstaPresets.BUILT_IN[1],
      com.music.echo.insta.InstaPresets.BUILT_IN[2],
      com.music.echo.insta.InstaPresets.BUILT_IN[3],
      com.music.echo.insta.InstaPresets.BUILT_IN[4],
    )
}

private fun persistUri(context: android.content.Context, uri: Uri) {
  runCatching {
    context.contentResolver.takePersistableUriPermission(
      uri,
      Intent.FLAG_GRANT_READ_URI_PERMISSION,
    )
  }
}

private fun Float.roundToIntValue(): Int = (this / 100f).toInt() * 100

@Composable
private fun InstaCard(content: @Composable () -> Unit) {
  Surface(
    color = Color(0xFF121318),
    shape = RoundedCornerShape(18.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
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
    color = Color(0xFFE8C77B),
    fontSize = 16.sp,
    fontFamily = bbhBartle,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.2.sp,
  )
}

@Composable
private fun <T> EnumChips(
  items: List<T>,
  selected: T,
  label: (T) -> String,
  onSelect: (T) -> Unit,
) {
  Row(
    modifier = Modifier.horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    items.forEach { item ->
      val active = item == selected
      Button(
        onClick = { onSelect(item) },
        colors =
          ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xFFE8C77B) else Color(0xFF24262D),
            contentColor = if (active) Color.Black else Color.White,
          ),
      ) {
        Text(
          label(item),
          fontSize = 15.sp,
          fontFamily = OutfitFontFamily,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}

@Composable
private fun SwitchRow(
  label: String,
  checked: Boolean,
  onChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      label,
      fontSize = 16.sp,
      fontFamily = OutfitFontFamily,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.weight(1f),
    )
    Switch(checked = checked, onCheckedChange = onChange)
  }
}

@Composable
private fun InstaSlider(
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
  Slider(value = value.coerceIn(range), valueRange = range, onValueChange = onChange)
}

@Composable
private fun InstaPresetPreview(
  preset: InstaPreset,
  backgroundBitmap: Bitmap?,
) {
  val context = LocalContext.current
  val effectiveStyle =
    if (preset.style == InstaVisualStyle.SMART_HYBRID) {
      InstaLrcPolicy.chooseStyle(
        requested = InstaVisualStyle.SMART_HYBRID,
        mood = "ROMANTIC",
        energy = 0.55f,
        section = "VERSE",
        language = "en",
        preferred = null,
      )
    } else {
      preset.style
    }
  val spec =
    InstaRenderSpec(
      words =
        listOf(
          InstaWord("Aura", 0L, 900L),
          InstaWord("in", 900L, 1_300L),
          InstaWord("the", 1_300L, 1_700L),
          InstaWord("silence", 1_700L, 3_200L, verified = false),
        ),
      title = "Preview",
      artist = "INSTA LRC",
      preset = preset,
      effectiveStyle = effectiveStyle,
      mood = "ROMANTIC",
      section = "VERSE",
      energy = 0.55f,
      language = "en",
    )
  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(320.dp)
        .clip(RoundedCornerShape(20.dp))
        .background(Color.Black)
        .clickable { },
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawIntoCanvas { canvas ->
        InstaFrameRenderer.render(
          canvas = canvas.nativeCanvas,
          width = size.width.toInt(),
          height = size.height.toInt(),
          timeMs = 1_950L,
          spec = spec,
          backgroundBitmap = backgroundBitmap,
          context = context,
        )
      }
    }
  }
}
