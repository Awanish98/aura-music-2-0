package echo.music.iad1tya.ui.screens.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import echo.music.iad1tya.LocalPlayerConnection
import echo.music.iad1tya.R
import echo.music.iad1tya.constants.CompactLibraryLayoutKey
import echo.music.iad1tya.extensions.toMediaItem
import echo.music.iad1tya.playback.queues.ListQueue
import echo.music.iad1tya.utils.rememberPreference
import echo.music.iad1tya.viewmodels.LocalFoldersViewModel

/**
 * Independent folder browser for on-device music.
 *
 * It only reads MediaStore and reuses the app's player/song model; no second
 * playback engine, no copied proprietary UI, no Room schema change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalFolderScreen(
  navController: NavController,
  onBack: () -> Unit = { navController.navigateUp() },
  viewModel: LocalFoldersViewModel = hiltViewModel(),
) {
  val context =
    androidx.compose.ui.platform.LocalContext.current
  val playerConnection = LocalPlayerConnection.current ?: return
  val folders by viewModel.folders.collectAsState()
  val localSongs by viewModel.localSongs.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()
  val (compactLayout) = rememberPreference(CompactLibraryLayoutKey, defaultValue = false)
  val folderRowPadding = if (compactLayout) 8.dp else 16.dp
  val songRowVerticalPadding = if (compactLayout) 4.dp else 10.dp

  val permission =
    remember {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
      } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
      }
    }
  var hasPermission by
    rememberSaveable {
      mutableStateOf(
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
      )
    }
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasPermission = granted
    }

  LaunchedEffect(hasPermission) {
    if (hasPermission) viewModel.load()
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Folders") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              painter = painterResource(R.drawable.arrow_back),
              contentDescription = null,
            )
          }
        },
      )
    },
  ) { paddingValues ->
    if (!hasPermission) {
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = "Storage access is needed to browse local music folders.",
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { permissionLauncher.launch(permission) }) {
          Text("Grant access")
        }
      }
      return@Scaffold
    }

    when {
      isLoading && folders.isEmpty() -> {
        Box(
          modifier = Modifier.fillMaxSize().padding(paddingValues),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
      }
      folders.isEmpty() -> {
        Column(
          modifier =
            Modifier
              .fillMaxSize()
              .padding(paddingValues)
              .padding(24.dp),
          verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text = "No audio folders found on this device.",
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(12.dp))
          Button(onClick = { viewModel.load() }) {
            Text("Refresh")
          }
        }
      }
      else -> {
        LazyColumn(contentPadding = paddingValues) {
          items(folders, key = { it.path }) { folder ->
            var expanded by rememberSaveable(folder.path) { mutableStateOf(false) }
            Column {
              Row(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(folderRowPadding),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  painter = painterResource(R.drawable.folder_managed),
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = folder.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                  )
                  Text(
                    text = "${folder.songCount} ${if (folder.songCount == 1) "song" else "songs"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Text(text = if (expanded) "▲" else "▼")
              }

              if (expanded) {
                folder.tracks.forEach { track ->
                  Row(
                    modifier =
                      Modifier
                        .fillMaxWidth()
                        .clickable {
                          val queueItems =
                            folder.tracks.mapNotNull { item ->
                              localSongs.find { song -> song.id == item.id }?.toMediaItem()
                            }
                          if (queueItems.isNotEmpty()) {
                            val startIndex =
                              queueItems
                                .indexOfFirst { item -> item.mediaId == track.id }
                                .coerceAtLeast(0)
                            playerConnection.playQueue(
                              ListQueue(
                                title = folder.name,
                                items = queueItems,
                                startIndex = startIndex,
                              ),
                            )
                          }
                        }
                        .padding(
                          start = 48.dp,
                          end = 16.dp,
                          top = songRowVerticalPadding,
                          bottom = songRowVerticalPadding,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = track.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                      )
                      Text(
                        text = track.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                }
              }
            }
          }
          item {
            Spacer(modifier = Modifier.height(96.dp))
          }
        }
      }
    }
  }
}
