package echo.music.iad1tya.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.datastore.preferences.core.edit
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.navigation.NavController
import echo.music.iad1tya.constants.LockScreenArtworkEnabledKey
import echo.music.iad1tya.constants.LockScreenLyricsEnabledKey
import echo.music.iad1tya.constants.LockScreenLyricsVisibleKey
import echo.music.iad1tya.constants.LockScreenOverlayEnabledKey
import echo.music.iad1tya.constants.LockScreenThemeKey
import echo.music.iad1tya.lockscreen.LockScreenLyricsActivity
import echo.music.iad1tya.lockscreen.LockScreenTheme
import echo.music.iad1tya.utils.dataStore
import echo.music.iad1tya.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockScreenSettingsScreen(navController: NavController) {
  val context = LocalContext.current
  // A durable write scope keeps a preference edit alive even if the user leaves
  // this screen immediately after tapping a chip or switch.
  val writeScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
  var enabled by rememberPreference(LockScreenLyricsEnabledKey, false)
  var lyricsVisible by rememberPreference(LockScreenLyricsVisibleKey, true)
  var artworkEnabled by rememberPreference(LockScreenArtworkEnabledKey, true)
  var overlayEnabled by rememberPreference(LockScreenOverlayEnabledKey, false)
  var themeName by rememberPreference(LockScreenThemeKey, LockScreenTheme.CINEMATIC.name)

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Dynamic Lock Screen") },
        navigationIcon = {
          TextButton(onClick = { navController.popBackStack() }) { Text("Back") }
        },
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
    ) {
      item {
        ListItem(
          headlineContent = { Text("Lock-screen lyrics") },
          supportingContent = {
            Text("Publish the active lyric line to the system media notification.")
          },
          trailingContent = {
            Switch(
              checked = enabled,
              onCheckedChange = {
                enabled = it
                writeScope.launch {
                  context.dataStore.edit { prefs -> prefs[LockScreenLyricsEnabledKey] = it }
                }
              },
            )
          },
          modifier = Modifier.clickable { enabled = !enabled }.fillMaxWidth(),
        )
        HorizontalDivider()
        ListItem(
          headlineContent = { Text("Show active lyric and word") },
          supportingContent = {
            Text("Word highlighting uses real timestamps only; estimates are labelled.")
          },
          trailingContent = {
            Switch(
              checked = lyricsVisible,
              onCheckedChange = {
                lyricsVisible = it
                writeScope.launch {
                  context.dataStore.edit { prefs -> prefs[LockScreenLyricsVisibleKey] = it }
                }
              },
            )
          },
          modifier = Modifier.clickable { lyricsVisible = !lyricsVisible }.fillMaxWidth(),
        )
        HorizontalDivider()
        ListItem(
          headlineContent = { Text("Show artwork in overlay") },
          supportingContent = { Text("Applies to the optional full-screen overlay only.") },
          trailingContent = {
            Switch(
              checked = artworkEnabled,
              onCheckedChange = {
                artworkEnabled = it
                writeScope.launch {
                  context.dataStore.edit { prefs -> prefs[LockScreenArtworkEnabledKey] = it }
                }
              },
            )
          },
          modifier = Modifier.clickable { artworkEnabled = !artworkEnabled }.fillMaxWidth(),
        )
        HorizontalDivider()
        ListItem(
          headlineContent = { Text("Allow over-lock-screen overlay") },
          supportingContent = {
            Text("Opt-in only. Secure and limited lock screens still use the media notification.")
          },
          trailingContent = {
            Switch(
              checked = overlayEnabled,
              onCheckedChange = {
                overlayEnabled = it
                writeScope.launch {
                  context.dataStore.edit { prefs -> prefs[LockScreenOverlayEnabledKey] = it }
                }
              },
            )
          },
          modifier = Modifier.clickable { overlayEnabled = !overlayEnabled }.fillMaxWidth(),
        )
        HorizontalDivider()
      }

      item {
        Text(
          "Lock-screen theme",
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp),
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
          LockScreenTheme.entries.forEach { theme ->
            FilterChip(
              selected = LockScreenTheme.fromStored(themeName) == theme,
              onClick = {
                themeName = theme.name
                writeScope.launch {
                  context.dataStore.edit { it[LockScreenThemeKey] = theme.name }
                }
              },
              label = { Text(theme.label) },
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
        HorizontalDivider(Modifier.padding(vertical = 18.dp))
      }

      item {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            "Preview",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
          )
          Text(
            "The preview uses the same MediaSession as the player. It does not start a second playback engine.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 12.dp),
          )
          Button(
            onClick = {
              runCatching {
                context.startActivity(Intent(context, LockScreenLyricsActivity::class.java))
              }
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Open lock-screen preview")
          }
        }
      }
    }
  }
}
