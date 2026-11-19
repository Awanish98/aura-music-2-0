package echo.music.iad1tya.providers

/**
 * Explicit, testable capability declarations for the sources AURA MUSIC already
 * ships. A source only advertises what it was actually observed to support;
 * unsupported capabilities are never inferred from a search result, preview
 * or artwork URL.
 */
enum class ProviderCapability {
  SEARCH,
  BROWSE,
  FULL_PLAYBACK,
  PREVIEW,
  DOWNLOAD,
  LYRICS,
  ARTWORK,
  RECOGNITION,
}

data class StreamingProviderCapability(
  val providerId: String,
  val displayName: String,
  val capabilities: Set<ProviderCapability>,
) {
  init {
    require(providerId.isNotBlank()) { "providerId must not be blank" }
  }

  val canPlayFullTracks: Boolean
    get() = ProviderCapability.FULL_PLAYBACK in capabilities

  val canDownload: Boolean
    get() = ProviderCapability.DOWNLOAD in capabilities
}

object ProviderCapabilityRegistry {
  /**
   * Conservative declarations based on the modules present at commit
   * cb398f13. DOWNLOAD is deliberately not granted to online streaming
   * sources because no source terms were audited in this workspace.
   */
  val providers: List<StreamingProviderCapability> =
    listOf(
      StreamingProviderCapability(
        providerId = "innertube",
        displayName = "YouTube Music (InnerTube)",
        capabilities =
          setOf(
            ProviderCapability.SEARCH,
            ProviderCapability.BROWSE,
            ProviderCapability.FULL_PLAYBACK,
            ProviderCapability.PREVIEW,
            ProviderCapability.ARTWORK,
          ),
      ),
      StreamingProviderCapability(
        providerId = "local",
        displayName = "On-device library",
        capabilities = setOf(ProviderCapability.BROWSE, ProviderCapability.FULL_PLAYBACK),
      ),
      StreamingProviderCapability(
        providerId = "kugou",
        displayName = "Kugou lyrics",
        capabilities = setOf(ProviderCapability.LYRICS),
      ),
      StreamingProviderCapability(
        providerId = "lrclib",
        displayName = "LRCLIB",
        capabilities = setOf(ProviderCapability.LYRICS),
      ),
      StreamingProviderCapability(
        providerId = "betterlyrics",
        displayName = "BetterLyrics",
        capabilities = setOf(ProviderCapability.LYRICS),
      ),
      StreamingProviderCapability(
        providerId = "simpmusic",
        displayName = "SimpMusic lyrics",
        capabilities = setOf(ProviderCapability.LYRICS),
      ),
      StreamingProviderCapability(
        providerId = "youlyplus",
        displayName = "YoulyPlus lyrics",
        capabilities = setOf(ProviderCapability.LYRICS),
      ),
      StreamingProviderCapability(
        providerId = "paxsenixlyrics",
        displayName = "Paxsenix lyrics",
        capabilities = setOf(ProviderCapability.LYRICS),
      ),
      StreamingProviderCapability(
        providerId = "shazamkit",
        displayName = "Recognition",
        capabilities = setOf(ProviderCapability.RECOGNITION),
      ),
      StreamingProviderCapability(
        providerId = "spotify_import",
        displayName = "Spotify playlist import",
        capabilities = setOf(ProviderCapability.BROWSE),
      ),
    )

  fun can(providerId: String, capability: ProviderCapability): Boolean =
    providers.firstOrNull { it.providerId == providerId }?.capabilities?.contains(capability) ?: false
}
