package echo.music.iad1tya.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import echo.music.iad1tya.constants.AmoledLyricsKeepAwakeKey
import echo.music.iad1tya.constants.AmoledLyricsLowPowerKey
import echo.music.iad1tya.constants.AmoledLyricsModeKey
import echo.music.iad1tya.utils.dataStore
import echo.music.iad1tya.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmoledLyricsSettingsScreen(navController: NavController) {
  val context = LocalContext.current
  val writeScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

  var enabled by rememberPreference(AmoledLyricsModeKey, false)
  var lowPower by rememberPreference(AmoledLyricsLowPowerKey, true)
  var keepAwake by rememberPreference(AmoledLyricsKeepAwakeKey, true)

  fun persist(key: Preferences.Key<Boolean>, value: Boolean) {
    writeScope.launch { context.dataStore.edit { it[key] = value } }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("AMOLED Lyrics") },
        navigationIcon = {
          TextButton(onClick = { navController.popBackStack() }) { Text("Back") }
        },
      )
    }
  ) { padding ->
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
      item {
        AmoledSwitchRow(
          title = "AMOLED lyrics mode",
          description =
            "The player's lyrics button opens the true-black renderer instead of the themed cinematic overlay. Big Screen also renders on pure black.",
          checked = enabled,
          onCheckedChange = {
            enabled = it
            persist(AmoledLyricsModeKey, it)
          },
        )
        HorizontalDivider()
        AmoledSwitchRow(
          title = "Low-power rendering",
          description =
            "Skips audio-reactive work, glow and particles, and updates word timing at 10 Hz. Lyric accuracy is unchanged.",
          checked = lowPower,
          onCheckedChange = {
            lowPower = it
            persist(AmoledLyricsLowPowerKey, it)
          },
        )
        HorizontalDivider()
        AmoledSwitchRow(
          title = "Keep screen on",
          description = "Prevents the display from sleeping while the AMOLED lyrics view is open.",
          checked = keepAwake,
          onCheckedChange = {
            keepAwake = it
            persist(AmoledLyricsKeepAwakeKey, it)
          },
        )
        HorizontalDivider()
      }

      item {
        Text(
          text =
            "Timing honesty: word highlighting uses provider word timestamps when available. " +
              "Plain line-synced LRC is labelled approximate and can be corrected with the +/− controls " +
              "inside the lyrics view. Corrections are cached per song.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
        )
      }
    }
  }
}

@Composable
private fun AmoledSwitchRow(
  title: String,
  description: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  ListItem(
    headlineContent = { Text(title) },
    supportingContent = { Text(description) },
    trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    modifier = Modifier.fillMaxWidth(),
  )
}
