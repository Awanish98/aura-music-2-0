package echo.music.iad1tya.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.EntryPointAccessors
import echo.music.iad1tya.R
import echo.music.iad1tya.cinematic.CinematicTheme
import echo.music.iad1tya.cinematic.ThemeMode
import echo.music.iad1tya.di.CinematicEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Full-screen panel: mode selection, 15 live previews, favorites and all controls. */
@Composable
fun CinematicSettingsPanel(
  settings: CinematicSettingsState,
  engine: CinematicEngineState,
  onDismiss: () -> Unit,
  onOpenInstaLrc: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val repository =
    remember {
      EntryPointAccessors.fromApplication(
        context.applicationContext,
        CinematicEntryPoint::class.java,
      ).smartCinematicRepository()
    }
  val profiles by repository.observeProfiles().collectAsState(initial = emptyList())
  var confirmClear by remember { mutableStateOf(false) }

  val surface = Color(0xFF07080B)
  val text = Color(0xFFF3EDE2)
  val subtle = Color(0xFF9AA0AA)
  val currentTheme = engine.theme
  Box(Modifier.fillMaxSize().background(surface)) {
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          "CINEMATIC STUDIO",
          color = text,
          fontSize = 22.sp,
          fontFamily = FontFamily.Serif,
          fontWeight = FontWeight.Bold,
          letterSpacing = 2.sp,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
          Icon(
            painter = painterResource(R.drawable.close),
            contentDescription = "Close settings",
            tint = text,
          )
        }
      }

      Button(
        onClick = onOpenInstaLrc,
        modifier = Modifier.fillMaxWidth(),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE8C77B),
            contentColor = Color.Black,
          ),
      ) {
        Text("OPEN INSTA LRC PLAY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
      }

      Text("PLAYBACK MODE", color = subtle, fontSize = 11.sp, letterSpacing = 1.6.sp)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeButton("AUTO", settings.mode.value == ThemeMode.AUTO, text) {
          settings.mode.value = ThemeMode.AUTO
        }
        ModeButton("MANUAL", settings.mode.value == ThemeMode.MANUAL, text) {
          settings.mode.value = ThemeMode.MANUAL
        }
        ModeButton("SMART AI", settings.mode.value == ThemeMode.SMART, text) {
          settings.mode.value = ThemeMode.SMART
        }
      }

      Text("THEMES", color = subtle, fontSize = 11.sp, letterSpacing = 1.6.sp)
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = Modifier.height(238.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        items(CinematicTheme.entries) { theme ->
          ThemeCard(
            theme = theme,
            active = theme == currentTheme,
            favorite = theme in settings.favorites.value,
            onSelect = { engine.onSelectTheme(theme) },
            onFavorite = {
              val current = settings.favorites.value.toMutableSet()
              if (theme in current) current.remove(theme) else current.add(theme)
              settings.favorites.value = current
            },
          )
        }
      }

      CinematicSwitch("Shuffle theme", settings.shuffle.value) { settings.shuffle.value = it }
      CinematicSwitch(
        "Auto theme at song sections",
        settings.autoSectionSwitch.value,
      ) { settings.autoSectionSwitch.value = it }
      CinematicSwitch("Reduced motion", settings.reducedMotion.value) {
        settings.reducedMotion.value = it
      }
      CinematicSwitch("AMOLED lyrics mode", settings.amoledMode.value) {
        settings.amoledMode.value = it
      }
      CinematicSwitch("AMOLED low power", settings.amoledLowPower.value) {
        settings.amoledLowPower.value = it
      }
      CinematicSwitch("Background effects", settings.backgroundEffects.value) {
        settings.backgroundEffects.value = it
      }
      CinematicSwitch("Smart learning", settings.learningEnabled.value) {
        settings.learningEnabled.value = it
      }
      CinematicSwitch("Remember listening history", settings.historyTracking.value) {
        settings.historyTracking.value = it
      }
      CinematicSwitch("Manual picks pin SMART", settings.smartManualOverride.value) {
        settings.smartManualOverride.value = it
      }

      CinematicSlider("Beat sensitivity", settings.beatSensitivity.value, 0.25f..2.5f) {
        settings.beatSensitivity.value = it
      }
      CinematicSlider("Bass reaction", settings.bassReaction.value, 0f..2f) {
        settings.bassReaction.value = it
      }
      CinematicSlider("Vocal highlight", settings.vocalHighlight.value, 0f..1f) {
        settings.vocalHighlight.value = it
      }
      CinematicSlider("Animation speed", settings.animationSpeed.value, 0.5f..2f) {
        settings.animationSpeed.value = it
      }
      CinematicSlider("Glow", settings.glow.value, 0f..2f) { settings.glow.value = it }
      CinematicSlider("Particles", settings.particles.value, 0f..2f) {
        settings.particles.value = it
      }
      CinematicSlider("Motion intensity", settings.motion.value, 0f..2f) {
        settings.motion.value = it
      }
      Text("SMART AI & GROQ", color = subtle, fontSize = 11.sp, letterSpacing = 1.6.sp)
      Text(
        "The Groq key stays on your secure backend. This app never stores it. " +
          "SMART is private preference learning, not model retraining.",
        color = subtle,
        fontSize = 11.sp,
        lineHeight = 15.sp,
      )
      OutlinedTextField(
        value = settings.backendBaseUrl.value,
        onValueChange = { settings.backendBaseUrl.value = it },
        label = { Text("Secure backend URL") },
        placeholder = { Text("https://your-backend.example") },
        singleLine = true,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = settings.backendToken.value,
        onValueChange = { settings.backendToken.value = it },
        label = { Text("Backend token") },
        placeholder = { Text("Not a Groq API key") },
        singleLine = true,
        maxLines = 1,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = settings.aiModel.value,
        onValueChange = { settings.aiModel.value = it },
        label = { Text("Groq model (optional)") },
        placeholder = { Text("llama-3.3-70b-versatile") },
        singleLine = true,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth(),
      )
      Text(
        "Cached analysis TTL: ${settings.aiCacheTtl.value / 3_600_000L} h",
        color = subtle,
        fontSize = 11.sp,
      )
      val ai = engine.ai
      val aiStatus =
        when {
          ai?.degraded == true ->
            "Offline / degraded: ${ai.explanation.ifBlank { "Local intelligence is active." }}"
          ai == null && settings.backendBaseUrl.value.isBlank() ->
            "No backend configured · local mood and cached themes remain active"
          ai == null -> "No AI plan cached for this song yet"
          else ->
            "Cached/online plan · ${ai.mood.name} · confidence " +
              "${String.format("%.2f", ai.confidence)} · ${ai.sections.size} sections"
        }
      Text(aiStatus, color = if (ai?.degraded == true) Color(0xFFFFB4A2) else text, fontSize = 12.sp)
      ai?.explanation?.takeIf { it.isNotBlank() && !ai.degraded }?.let {
        Text(it, color = subtle, fontSize = 11.sp, lineHeight = 15.sp)
      }
      Button(
        onClick = engine.onRefreshAi,
        enabled = settings.backendBaseUrl.value.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8C77B), contentColor = Color.Black),
      ) {
        Text("REANALYSE SONG", fontSize = 11.sp)
      }
      Text(
        "Private SMART profiles: ${profiles.size}. Play count, skips and manual choices stay on this device.",
        color = subtle,
        fontSize = 11.sp,
      )
      OutlinedButton(
        onClick = {
          if (confirmClear) {
            scope.launch(Dispatchers.IO) { repository.clear() }
            confirmClear = false
          } else {
            confirmClear = true
          }
        },
      ) {
        Text(
          if (confirmClear) "CONFIRM CLEAR" else "CLEAR SMART MEMORY",
          color = Color(0xFFFF8A80),
          fontSize = 11.sp,
        )
      }
    }
  }
}

@Composable
private fun ModeButton(label: String, active: Boolean, text: Color, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    modifier = Modifier.height(38.dp),
    colors =
      ButtonDefaults.buttonColors(
        containerColor = if (active) Color(0xFFE8C77B) else Color(0xFF1A1C22),
        contentColor = if (active) Color.Black else text,
      ),
  ) {
    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun ThemeCard(
  theme: CinematicTheme,
  active: Boolean,
  favorite: Boolean,
  onSelect: () -> Unit,
  onFavorite: () -> Unit,
) {
  Box(
    modifier =
      Modifier
        .height(106.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(Color(theme.background.toInt()))
        .clickable(onClick = onSelect)
        .then(if (active) Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x22E8C77B)) else Modifier),
  ) {
    Text(
      "Aa",
      color = Color(theme.primary.toInt()),
      fontSize = 34.sp,
      fontFamily = FontFamily.Serif,
      fontStyle = if (theme.typeface == echo.music.iad1tya.cinematic.CinematicTypeface.SERIF_ITALIC)
        FontStyle.Italic
      else FontStyle.Normal,
      modifier = Modifier.align(Alignment.Center),
    )
    Text(
      theme.displayName,
      color = Color(theme.text.toInt()),
      fontSize = 9.sp,
      textAlign = TextAlign.Center,
      modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
    )
    Text(
      if (favorite) "★" else "☆",
      color = Color(theme.accent.toInt()),
      fontSize = 16.sp,
      modifier =
        Modifier
          .align(Alignment.TopEnd)
          .padding(4.dp)
          .clickable(onClick = onFavorite),
    )
  }
}

@Composable
private fun CinematicSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(label, color = Color(0xFFD8D8D8), fontSize = 13.sp, modifier = Modifier.weight(1f))
    Switch(checked = value, onCheckedChange = onChange)
  }
}

@Composable
private fun CinematicSlider(
  label: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  onChange: (Float) -> Unit,
) {
  Column {
    Text("$label  ${String.format("%.1f", value)}", color = Color(0xFFD8D8D8), fontSize = 12.sp)
    Slider(value = value, onValueChange = onChange, valueRange = range)
  }
}
