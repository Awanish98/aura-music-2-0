package echo.music.iad1tya.localmedia

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Read-only MediaStore browser used by the Folders screen.
 *
 * It never modifies the media store and never persists a new track table:
 * folder paths are derived at query time, while playback still goes through
 * the app's existing local songs (matched by the MediaStore content-URI id).
 */
class LocalFolderBrowser
@Inject
constructor(
  @ApplicationContext private val context: Context,
) {
  fun load(): List<LocalFolder> {
    val projection =
      buildList {
          add(MediaStore.Audio.Media._ID)
          add(MediaStore.Audio.Media.TITLE)
          add(MediaStore.Audio.Media.DISPLAY_NAME)
          add(MediaStore.Audio.Media.ARTIST)
          add(MediaStore.Audio.Media.MIME_TYPE)
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.MediaColumns.RELATIVE_PATH)
          } else {
            add(MediaStore.MediaColumns.DATA)
          }
        }
        .toTypedArray()

    val selection =
      buildList {
          add("${MediaStore.Audio.Media.SIZE} > 0")
          add("${MediaStore.Audio.Media.DURATION} > 0")
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add("${MediaStore.MediaColumns.IS_PENDING} = 0")
          }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add("is_trashed = 0")
          }
        }
        .joinToString(" AND ")

    val unknownArtist = "Unknown artist"
    val tracks = mutableListOf<LocalFolderTrack>()

    context.contentResolver
      .query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        null,
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
      )
      ?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val displayNameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val artistIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
        val mimeTypeIndex = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
        val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
        val dataPathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

        while (cursor.moveToNext()) {
          val displayName = cursor.getString(displayNameIndex).orEmpty()
          val mimeType = if (mimeTypeIndex >= 0) cursor.getString(mimeTypeIndex) else null
          if (!SupportedLocalAudio.isSupported(displayName, mimeType)) continue

          val mediaId = cursor.getLong(idIndex)
          val contentUri =
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
          val folderPath =
            LocalFolderGrouping.resolveFolderPath(
              relativePath = if (relativePathIndex >= 0) cursor.getString(relativePathIndex) else null,
              absolutePath = if (dataPathIndex >= 0) cursor.getString(dataPathIndex) else null,
            )
          val rawTitle = cursor.getString(titleIndex)?.trim().orEmpty()
          val title =
            rawTitle.ifEmpty { displayName.substringBeforeLast('.', displayName) }
          val artist =
            if (artistIndex >= 0) {
              cursor.getString(artistIndex)?.trim()?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
            } else {
              null
            } ?: unknownArtist

          tracks +=
            LocalFolderTrack(
              id = contentUri.toString(),
              title = title,
              artist = artist,
              folderPath = folderPath,
            )
        }
      }

    return LocalFolderGrouping.group(tracks)
  }
}
