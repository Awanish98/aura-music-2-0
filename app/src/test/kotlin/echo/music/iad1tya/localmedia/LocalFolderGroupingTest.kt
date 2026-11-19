package echo.music.iad1tya.localmedia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFolderGroupingTest {
  @Test
  fun normalizeFolderPath_collapsesSlashesAndLowercases() {
    assertEquals(
      "music/bollywood",
      LocalFolderGrouping.normalizeFolderPath("  Music\\\\Bollywood//  "),
    )
  }

  @Test
  fun normalizeFolderPath_stripsLegacyStorageRoot() {
    assertEquals(
      "music/hip hop",
      LocalFolderGrouping.normalizeFolderPath("/storage/emulated/0/Music/Hip Hop/"),
    )
  }

  @Test
  fun resolveFolderPath_prefersRelativePath() {
    assertEquals(
      "music/rock",
      LocalFolderGrouping.resolveFolderPath(
        relativePath = "Music/Rock/",
        absolutePath = "/storage/emulated/0/Other/song.mp3",
      ),
    )
  }

  @Test
  fun resolveFolderPath_fallsBackToAbsoluteParent() {
    assertEquals(
      "music/jazz",
      LocalFolderGrouping.resolveFolderPath(
        relativePath = null,
        absolutePath = "/storage/emulated/0/Music/Jazz/song.mp3",
      ),
    )
  }

  @Test
  fun group_groupsByFolderAndSortsTracksAndFolders() {
    val tracks =
      listOf(
        LocalFolderTrack("content://1", "Zebra", "A", "Music/Rock"),
        LocalFolderTrack("content://2", "alpha", "B", "music/rock"),
        LocalFolderTrack("content://3", "Bhajan", "C", "Music/Devotional"),
      )

    val folders = LocalFolderGrouping.group(tracks)

    assertEquals(2, folders.size)
    assertEquals("Devotional", folders[0].name)
    assertEquals("Rock", folders[1].name)
    assertEquals(listOf("alpha", "Zebra"), folders[1].tracks.map { it.title })
    assertEquals(2, folders[1].songCount)
  }

  @Test
  fun group_ignoresTracksWithoutAFolder() {
    val tracks =
      listOf(
        LocalFolderTrack("content://1", "Loose", "A", ""),
        LocalFolderTrack("content://2", "Kept", "B", "Music/Pop"),
      )
    val folders = LocalFolderGrouping.group(tracks)
    assertEquals(1, folders.size)
    assertEquals(1, folders[0].songCount)
    assertTrue(folders[0].tracks.all { it.id == "content://2" })
  }

  @Test
  fun displayName_fallsBackToRootForEmptyPath() {
    assertEquals("Root", LocalFolderGrouping.displayName(""))
    assertEquals("Podcasts", LocalFolderGrouping.displayName("Music/Podcasts"))
  }
}
