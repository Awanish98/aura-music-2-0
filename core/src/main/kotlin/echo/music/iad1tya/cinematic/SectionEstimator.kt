package echo.music.iad1tya.cinematic

enum class SongSection { INTRO, VERSE, PRE_CHORUS, CHORUS, BRIDGE, INSTRUMENTAL, OUTRO }

data class SongSectionSpan(val section: SongSection, val startMs: Long, val endMs: Long)

data class LyricPoint(val timeMs: Long, val text: String)

data class EnergyPoint(val timeMs: Long, val energy: Float)

/**
 * Derives song sections from lyric gaps and observed audio energy.
 *
 * Real structural metadata is used by callers when a provider supplies it;
 * this estimator is the honest fallback and never invents lyrics for an
 * instrumental region.
 */
object SectionEstimator {
  private const val WINDOW_MS = 4_000L
  private const val INSTRUMENTAL_GAP_MS = 7_000L

  fun estimate(
    lyrics: List<LyricPoint>,
    energies: List<EnergyPoint>,
    durationMs: Long,
  ): List<SongSectionSpan> {
    val points = lyrics.filter { it.text.isNotBlank() }.sortedBy { it.timeMs }
    if (points.isEmpty()) {
      return listOf(SongSectionSpan(SongSection.INSTRUMENTAL, 0L, durationMs.coerceAtLeast(1L)))
    }

    val firstLyric = points.first().timeMs.coerceAtLeast(0L)
    val lastLyric = points.last().timeMs.coerceAtLeast(firstLyric)
    val spans = mutableListOf<SongSectionSpan>()

    if (firstLyric > 3_000L) {
      spans += SongSectionSpan(SongSection.INTRO, 0L, firstLyric)
    }

    var regionStart = firstLyric
    for (index in 1 until points.size) {
      val gap = points[index].timeMs - points[index - 1].timeMs
      if (gap > INSTRUMENTAL_GAP_MS) {
        spans += vocalSpans(regionStart, points[index - 1].timeMs + 2_000L, energies)
        spans +=
          SongSectionSpan(
            SongSection.INSTRUMENTAL,
            points[index - 1].timeMs + 2_000L,
            points[index].timeMs,
          )
        regionStart = points[index].timeMs
      }
    }
    val lyricEnd = (lastLyric + 3_000L).coerceAtMost(durationMs)
    spans += vocalSpans(regionStart, lyricEnd, energies)
    if (durationMs > lyricEnd + 2_000L) {
      spans += SongSectionSpan(SongSection.OUTRO, lyricEnd, durationMs)
    }

    return merge(refine(spans.sortedBy { it.startMs }))
  }

  fun sectionAt(spans: List<SongSectionSpan>, positionMs: Long): SongSection =
    spans.firstOrNull { positionMs in it.startMs..it.endMs }?.section ?: SongSection.VERSE

  /** Maps a provider/AI section label to our vocabulary, ignoring unknowns. */
  fun sectionFromName(name: String): SongSection? =
    SongSection.entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

  /**
   * Overlays real structural metadata (when a provider or AI plan supplies it)
   * on top of the estimated spans. Known spans win; the fallback is untouched
   * wherever no trusted structure exists.
   */
  fun withKnownSections(
    estimated: List<SongSectionSpan>,
    known: List<SongSectionSpan>,
    durationMs: Long,
  ): List<SongSectionSpan> {
    if (known.isEmpty()) return estimated
    val bound = durationMs.coerceAtLeast(1L)
    val trusted =
      known
        .asSequence()
        .map { it.copy(startMs = it.startMs.coerceIn(0L, bound), endMs = it.endMs.coerceIn(0L, bound)) }
        .filter { it.endMs > it.startMs }
        .sortedBy { it.startMs }
        .toList()
    if (trusted.isEmpty()) return estimated
    val output = estimated.toMutableList()
    trusted.forEach { span ->
      val overlapping = output.indices.filter { index ->
        val current = output[index]
        current.endMs > span.startMs && current.startMs < span.endMs
      }
      overlapping.forEach { output[it] = output[it].copy(endMs = span.startMs) }
      output.add(span)
    }
    return merge(output.filter { it.endMs > it.startMs }.sortedBy { it.startMs })
  }

  private fun vocalSpans(
    startMs: Long,
    endMs: Long,
    energies: List<EnergyPoint>,
  ): List<SongSectionSpan> {
    if (endMs <= startMs) return emptyList()
    val windows = mutableListOf<SongSectionSpan>()
    var cursor = startMs
    while (cursor < endMs) {
      val next = (cursor + WINDOW_MS).coerceAtMost(endMs)
      val average =
        energies
          .filter { it.timeMs in cursor..next }
          .map { it.energy }
          .takeIf { it.isNotEmpty() }
          ?.average()
          ?.toFloat()
      val section =
        when {
          average == null -> SongSection.VERSE
          average > 0.62f -> SongSection.CHORUS
          average < 0.28f -> SongSection.BRIDGE
          else -> SongSection.VERSE
        }
      windows += SongSectionSpan(section, cursor, next)
      cursor = next
    }
    return windows
  }

  private fun refine(spans: List<SongSectionSpan>): List<SongSectionSpan> {
    val output = spans.toMutableList()
    for (index in 1 until output.size) {
      if (output[index].section != SongSection.CHORUS) continue
      val previous = output[index - 1]
      if (previous.section == SongSection.VERSE && output[index].startMs - previous.startMs > WINDOW_MS) {
        val split = output[index].startMs - 2_000L
        if (split > previous.startMs) {
          output[index - 1] = previous.copy(endMs = split)
          output.add(index, SongSectionSpan(SongSection.PRE_CHORUS, split, output[index].startMs))
        }
      }
    }
    val choruses = output.filter { it.section == SongSection.CHORUS }
    if (choruses.size >= 2) {
      for (index in output.indices) {
        val span = output[index]
        if (
          span.section == SongSection.BRIDGE &&
            span.startMs > choruses.first().startMs &&
            span.endMs < choruses.last().endMs
        ) {
          output[index] = span.copy(section = SongSection.BRIDGE)
        }
      }
    }
    return output.sortedBy { it.startMs }
  }

  private fun merge(spans: List<SongSectionSpan>): List<SongSectionSpan> {
    if (spans.isEmpty()) return spans
    val output = mutableListOf<SongSectionSpan>()
    var current = spans.first()
    for (span in spans.drop(1)) {
      if (span.section == current.section && span.startMs <= current.endMs + 1L) {
        current = current.copy(endMs = maxOf(current.endMs, span.endMs))
      } else {
        output += current
        current = span
      }
    }
    output += current
    return output
  }
}
