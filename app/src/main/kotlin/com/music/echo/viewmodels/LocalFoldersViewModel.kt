package echo.music.iad1tya.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import echo.music.iad1tya.db.MusicDatabase
import echo.music.iad1tya.localmedia.LocalFolder
import echo.music.iad1tya.localmedia.LocalFolderBrowser
import echo.music.iad1tya.utils.reportException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the on-device Folders browser. Playback reuses the app's local songs. */
@HiltViewModel
class LocalFoldersViewModel
@Inject
constructor(
  private val folderBrowser: LocalFolderBrowser,
  database: MusicDatabase,
) : ViewModel() {
  private val _folders = MutableStateFlow<List<LocalFolder>>(emptyList())
  val folders = _folders.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading = _isLoading.asStateFlow()

  /** Local songs from Room, matched by MediaStore content-URI id for playback. */
  val localSongs =
    database
      .localSongs()
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
      )

  fun load() {
    if (_isLoading.value) return
    viewModelScope.launch(Dispatchers.IO) {
      _isLoading.value = true
      runCatching { folderBrowser.load() }
        .onSuccess { _folders.value = it }
        .onFailure { error -> reportException(error) }
      _isLoading.value = false
    }
  }
}
