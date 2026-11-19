package echo.music.iad1tya.localmedia

import java.util.Locale

/**
 * A single on-device audio file as seen by the folder browser.
 *
 * [id] is the MediaStore content URI, which is the same stable id the app's
 * [LocalSongScanner] stores for local songs. That lets the folder browser
 * delegate playback to the existing player queue without a second engine.
 */
data class LocalFolderTrack(
  val id: String,
  val title: String,
  val artist: String,
  val folderPath: String,
)

/** A folder group containing the supported audio files found inside it. */
data class LocalFolder(
  val path: String,
  val name: String,
  val tracks: List<LocalFolderTrack>,
) {
  val songCount: Int
    get() = tracks.size
}

/**
 * Pure, JVM-testable logic for normalizing MediaStore paths and grouping
 * local tracks into folders. No Android or database dependency.
 */
object LocalFolderGrouping {
  private val RepeatedSlash = Regex("/+")
  private val StorageRoot = Regex("^storage/emulated/\\d+/")

  /** Normalize a folder path to a lowercase, slash-separated, trimmed key. */
  fun normalizeFolderPath(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val normalized =
      raw.replace('\\', '/').replace(RepeatedSlash, "/").trim().trim('/')
    return StorageRoot.replace(normalized, "").lowercase(Locale.ROOT)
  }

  /**
   * Resolve the folder key for a MediaStore row. `RELATIVE_PATH` is preferred
   * on API 29+; the absolute DATA path is used as a fallback on older devices.
   */
  fun resolveFolderPath(relativePath: String?, absolutePath: String?): String {
    val relative = normalizeFolderPath(relativePath)
    if (relative.isNotEmpty()) return relative

    val parent =
      absolutePath
        ?.replace('\\', '/')
        ?.substringBeforeLast('/', missingDelimiterValue = "")
    return normalizeFolderPath(parent)
  }

  /**
   * Human readable folder name from the original path: its last segment, or
   * "Root". Case is preserved for display; only grouping keys are lowercased.
   */
  fun displayName(folderPath: String): String {
    val displayPath = folderPath.replace('\\', '/').trim().trim('/')
    if (displayPath.isEmpty()) return "Root"
    return displayPath.substringAfterLast('/', displayPath)
  }

  /** Group tracks by normalized folder, sorting folders and tracks by name. */
  fun group(tracks: List<LocalFolderTrack>): List<LocalFolder> =
    tracks
      .filter { it.folderPath.isNotEmpty() }
      .groupBy { normalizeFolderPath(it.folderPath) }
      .map { (path, items) ->
        LocalFolder(
          path = path,
          name = displayName(items.first().folderPath),
          tracks = items.sortedBy { it.title.lowercase(Locale.ROOT) },
        )
      }
      .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}
