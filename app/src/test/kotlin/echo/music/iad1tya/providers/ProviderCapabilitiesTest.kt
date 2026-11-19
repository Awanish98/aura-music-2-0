package echo.music.iad1tya.providers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCapabilitiesTest {
  @Test
  fun streamingSourceDeclaresPlaybackButNotDownload() {
    assertTrue(ProviderCapabilityRegistry.can("innertube", ProviderCapability.FULL_PLAYBACK))
    assertTrue(ProviderCapabilityRegistry.can("innertube", ProviderCapability.SEARCH))
    assertFalse(ProviderCapabilityRegistry.can("innertube", ProviderCapability.DOWNLOAD))
  }

  @Test
  fun lyricsProvidersOnlyDeclareLyrics() {
    listOf("kugou", "lrclib", "betterlyrics", "simpmusic", "youlyplus", "paxsenixlyrics")
      .forEach { id ->
        assertTrue(ProviderCapabilityRegistry.can(id, ProviderCapability.LYRICS))
        assertFalse(ProviderCapabilityRegistry.can(id, ProviderCapability.FULL_PLAYBACK))
        assertFalse(ProviderCapabilityRegistry.can(id, ProviderCapability.DOWNLOAD))
      }
  }

  @Test
  fun localLibraryIsBrowseableAndPlayable() {
    assertTrue(ProviderCapabilityRegistry.can("local", ProviderCapability.BROWSE))
    assertTrue(ProviderCapabilityRegistry.can("local", ProviderCapability.FULL_PLAYBACK))
    assertFalse(ProviderCapabilityRegistry.can("local", ProviderCapability.LYRICS))
  }

  @Test
  fun spotifyImportIsMetadataOnly() {
    assertTrue(ProviderCapabilityRegistry.can("spotify_import", ProviderCapability.BROWSE))
    assertFalse(ProviderCapabilityRegistry.can("spotify_import", ProviderCapability.FULL_PLAYBACK))
  }

  @Test
  fun unknownProviderHasNoCapabilities() {
    assertFalse(ProviderCapabilityRegistry.can("unknown", ProviderCapability.SEARCH))
    assertFalse(ProviderCapabilityRegistry.can("", ProviderCapability.FULL_PLAYBACK))
  }

  @Test
  fun capabilityObjectExposesDerivedFlags() {
    val provider = ProviderCapabilityRegistry.providers.first { it.providerId == "innertube" }
    assertTrue(provider.canPlayFullTracks)
    assertFalse(provider.canDownload)
  }
}
