package echo.music.iad1tya.ui.player

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.EntryPointAccessors
import echo.music.iad1tya.LocalDatabase
import echo.music.iad1tya.LocalPlayerConnection
import echo.music.iad1tya.cinematic.AudioFeatures
import echo.music.iad1tya.cinematic.CinematicMood
import echo.music.iad1tya.cinematic.CinematicTheme
import echo.music.iad1tya.cinematic.EnergyPoint
import echo.music.iad1tya.cinematic.LyricPoint
import echo.music.iad1tya.cinematic.MoodEstimator
import echo.music.iad1tya.cinematic.MoodResult
import echo.music.iad1tya.cinematic.SectionEstimator
import echo.music.iad1tya.cinematic.SmartCinematicAdvisor
import echo.music.iad1tya.cinematic.SmartSongSignals
import echo.music.iad1tya.cinematic.SongSection
import echo.music.iad1tya.cinematic.SongSectionSpan
import echo.music.iad1tya.cinematic.ThemeMode
import echo.music.iad1tya.cinematic.ThemeSelector
import echo.music.iad1tya.constants.CinematicLyricCorrectionsKey
import echo.music.iad1tya.db.entities.LyricsEntity
import echo.music.iad1tya.di.CinematicEntryPoint
import echo.music.iad1tya.di.LyricsHelperEntryPoint
import echo.music.iad1tya.lyrics.LyricsUtils.parseLyrics
import echo.music.iad1tya.models.MediaMetadata
import echo.music.iad1tya.smart.AiAnalysis
import echo.music.iad1tya.smart.GroqLyricsClient
import echo.music.iad1tya.ui.component.KaraokeTiming
import echo.music.iad1tya.ui.component.KaraokeWord
import echo.music.iad1tya.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

data class CinematicEngineState(
  val theme: CinematicTheme,
  val features: AudioFeatures,
  val section: SongSection,
  val positionMs: Long,
  val words: List<KaraokeWord>,
  val visual: CinematicVisualState,
  val mood: CinematicMood,
  val moodConfidence: Float,
  val ai: AiAnalysis?,
  val timingVerified: Boolean,
  val onSelectTheme: (CinematicTheme) -> Unit,
  val onAdjustTiming: (Long) -> Unit,
  val onRefreshAi: () -> Unit,
)

/**
 * One shared cinematic brain for the phone overlay, wired Presentation and
 * desktop receiver. It consumes real lyrics timestamps and real FFT features;
 * the words themselves are never generated from the beat detector.
 */
@Composable
fun rememberCinematicEngine(
  mediaMetadata: MediaMetadata?,
  currentLyrics: LyricsEntity?,
  audioFeatures: State<AudioFeatures>,
  settings: CinematicSettingsState,
  lyricsOffsetMs: Long,
  active: Boolean,
  frameIntervalMs: Long = 32L,
): CinematicEngineState {
  val context = LocalContext.current
  val playerConnection = LocalPlayerConnection.current
  val database = LocalDatabase.current
  val scope = rememberCoroutineScope()

  val selector = remember { ThemeSelector() }
  val advisor = remember { SmartCinematicAdvisor() }
  val aiClient = remember { GroqLyricsClient() }

  var positionMs by remember { mutableLongStateOf(0L) }
  var theme by remember { mutableStateOf(CinematicTheme.GOLDEN_CINEMA) }
  var moodResult by remember { mutableStateOf(MoodEstimator.estimate(AudioFeatures.IDLE, emptyList())) }
  var ai by remember { mutableStateOf<AiAnalysis?>(null) }
  var genreTags by remember { mutableStateOf(emptyList<String>()) }
  var signals by remember { mutableStateOf(SmartSongSignals(mediaMetadata?.id.orEmpty())) }
  var genresLoadedFor by remember { mutableStateOf<String?>(null) }
  var energyPoints by remember { mutableStateOf(listOf(EnergyPoint(0L, 0f))) }
  var correctionMs by remember { mutableLongStateOf(0L) }
  var forceAiRefresh by remember { mutableLongStateOf(0L) }
  var pinnedTheme by remember { mutableStateOf<CinematicTheme?>(null) }
  var pinnedTrack by remember { mutableStateOf<String?>(null) }

  var correctionsJson by rememberPreference(CinematicLyricCorrectionsKey, "{}")

  val trackKey = mediaMetadata?.id.orEmpty()
  val artist = mediaMetadata?.artists?.firstOrNull()?.name.orEmpty()
  val title = mediaMetadata?.title.orEmpty()

  val lowEnd =
    remember {
      (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    }

  // Fetch lyrics on demand, exactly as the previous dedicated overlay did.
  LaunchedEffect(trackKey, currentLyrics) {
    if (trackKey.isBlank() || currentLyrics != null) return@LaunchedEffect
    delay(400)
    runCatching {
      if (database.lyrics(trackKey).firstOrNull() != null) return@runCatching
      val lyricsEntryPoint =
        EntryPointAccessors.fromApplication(
          context.applicationContext,
          LyricsHelperEntryPoint::class.java,
        )
      val fetched = lyricsEntryPoint.lyricsHelper().getLyrics(mediaMetadata ?: return@runCatching)
      database.query {
        upsert(
          LyricsEntity(
            id = trackKey,
            lyrics = fetched.lyrics ?: "",
            provider = fetched.providerName,
          )
        )
      }
    }
  }

  val lines =
    remember(currentLyrics?.lyrics) {
      val raw = currentLyrics?.lyrics?.trim()
      if (raw.isNullOrEmpty()) emptyList() else parseLyrics(raw).filter { it.text.isNotBlank() }
    }

  val words =
    remember(lines, correctionMs) {
      lines.flatMapIndexed { index, entry ->
        KaraokeTiming.wordsFor(entry, lines.getOrNull(index + 1)?.time).map { word ->
          if (correctionMs == 0L) {
            word
          } else {
            word.copy(startMs = word.startMs + correctionMs, endMs = word.endMs + correctionMs)
          }
        }
      }
    }

  // Real structural metadata from a trusted AI plan overrides the estimate.
  val knownSections =
    remember(ai?.sections) {
      ai?.sections?.mapNotNull { section ->
        SectionEstimator.sectionFromName(section.name)?.let {
          SongSectionSpan(it, section.startMs, section.endMs)
        }
      }.orEmpty()
    }
  val sections =
    remember(lines, energyPoints, mediaMetadata?.duration, knownSections) {
      val durationMs = ((mediaMetadata?.duration ?: 180) * 1000L).coerceAtLeast(1L)
      SectionEstimator.withKnownSections(
        SectionEstimator.estimate(
          lyrics = lines.map { LyricPoint(timeMs = it.time, text = it.text) },
          energies = energyPoints,
          durationMs = durationMs,
        ),
        knownSections,
        durationMs,
      )
    }
  val section = SectionEstimator.sectionAt(sections, positionMs)
  val analysisEnergy = ai?.takeIf { !it.degraded && it.confidence > 0.3f }?.energyHint
  val effectiveEnergy = analysisEnergy ?: signals.energy

  // Direct player sampling keeps word highlighting close to the vocal.
  LaunchedEffect(playerConnection, active) {
    val player = playerConnection?.player ?: return@LaunchedEffect
    positionMs = player.currentPosition
    var lastEnergyMs = 0L
    while (isActive) {
      positionMs = player.currentPosition
      val now = System.currentTimeMillis()
      if (active && now - lastEnergyMs >= 2_000L) {
        lastEnergyMs = now
        energyPoints =
          (energyPoints + EnergyPoint(positionMs, audioFeatures.value.energy))
            .takeLast(400)
      }
      delay(if (active) frameIntervalMs.coerceIn(16L, 250L) else 200L)
    }
  }

  // Load private SMART signals and real genre tags for this song.
  LaunchedEffect(trackKey, settings.learningEnabled.value, settings.historyTracking.value) {
    if (trackKey.isBlank()) return@LaunchedEffect
    val entryPoint =
      EntryPointAccessors.fromApplication(
        context.applicationContext,
        CinematicEntryPoint::class.java,
      )
    val repository = entryPoint.smartCinematicRepository()
    val localMood =
      MoodEstimator.estimate(
        audio = audioFeatures.value,
        tags = genreTags,
        title = title,
      )
    signals =
      repository.signals(
        trackKey = trackKey,
        mood = localMood.mood,
        energy = audioFeatures.value.energy,
        genreTags = genreTags,
        lyricsAvailable = words.isNotEmpty(),
        favorites = settings.favorites.value,
      )
    if (genresLoadedFor != trackKey) {
      genresLoadedFor = trackKey
      runCatching {
        genreTags =
          entryPoint.genresRepository().genresForTrack("$trackKey|$artist").take(12)
      }
    }
  }

  // Optional Groq intelligence via the secure backend. Cached/offline first.
  LaunchedEffect(
    trackKey,
    currentLyrics?.lyrics,
    settings.localAnalysis.value,
    settings.backendBaseUrl.value,
    settings.backendToken.value,
    settings.aiModel.value,
    forceAiRefresh,
  ) {
    if (trackKey.isBlank() || !settings.localAnalysis.value) return@LaunchedEffect
    val entryPoint =
      EntryPointAccessors.fromApplication(
        context.applicationContext,
        CinematicEntryPoint::class.java,
      )
    val repository = entryPoint.smartCinematicRepository()
    val cached = repository.cachedPlan(trackKey, settings.aiCacheTtl.value)
    if (cached != null && forceAiRefresh == 0L) {
      ai = aiClient.parse(cached)
    } else if (settings.backendBaseUrl.value.isNotBlank()) {
      val rawLyrics = currentLyrics?.lyrics.orEmpty()
      if (rawLyrics.isNotBlank() || title.isNotBlank()) {
        val result =
          aiClient.analyze(
            baseUrl = settings.backendBaseUrl.value,
            clientToken = settings.backendToken.value,
            model = settings.aiModel.value.ifBlank { "llama-3.3-70b-versatile" },
            title = title,
            artist = artist,
            lyrics = rawLyrics,
            genreTags = genreTags,
          )
        ai = result
        runCatching { repository.saveAiPlan(trackKey, result.mood, result.rawJson) }
      }
    }
  }

  LaunchedEffect(ai?.mood, genreTags, audioFeatures.value.energy) {
    moodResult =
      if (ai != null && !ai!!.degraded && ai!!.confidence > 0.3f) {
        MoodResult(ai!!.mood, ai!!.confidence)
      } else {
        MoodEstimator.estimate(audioFeatures.value, genreTags, title)
      }
  }

  LoadCinematicCorrection(trackKey, correctionsJson) { correctionMs = it }

  // Theme is stable per song; manual changes and section transitions are explicit.
  LaunchedEffect(
    trackKey,
    settings.mode.value,
    settings.manualTheme.value,
    settings.shuffle.value,
    settings.favorites.value,
    moodResult.mood,
    genreTags,
    signals,
    effectiveEnergy,
    ai?.themeHint,
  ) {
    if (trackKey.isBlank()) return@LaunchedEffect
    val pinned = if (pinnedTrack == trackKey) pinnedTheme else null
    val smartSuggestion =
      if (settings.mode.value == ThemeMode.SMART && settings.learningEnabled.value) {
        pinned
          ?: ai?.takeIf { !it.degraded && it.confidence > 0.3f }?.themeHint
          ?: advisor.recommend(signals)
      } else {
        null
      }
    val selected =
      selector.selectForSong(
        songId = trackKey,
        mode = settings.mode.value,
        manualTheme = settings.manualTheme.value,
        favorites = settings.favorites.value,
        mood = moodResult.mood,
        genreTags = genreTags,
        energy = effectiveEnergy,
        shuffle = settings.shuffle.value,
        smartSuggestion = smartSuggestion,
      )
    theme = selected
    if (settings.learningEnabled.value && settings.historyTracking.value) {
      val entryPoint =
        EntryPointAccessors.fromApplication(
          context.applicationContext,
          CinematicEntryPoint::class.java,
        )
      entryPoint.smartCinematicRepository()
        .recordPlay(trackKey, title, artist, selected, moodResult.mood)
    }
  }

  LaunchedEffect(section) {
    val next =
      selector.selectForSection(
        section = section,
        current = theme,
        enabled = settings.autoSectionSwitch.value,
        mode = settings.mode.value,
        favorites = settings.favorites.value,
        mood = moodResult.mood,
        genreTags = genreTags,
        energy = effectiveEnergy,
      )
    if (next != null) theme = next
  }

  fun selectTheme(next: CinematicTheme) {
    theme = next
    if (settings.mode.value == ThemeMode.SMART && settings.smartManualOverride.value) {
      // Keep SMART learning active but honour this explicit choice for the song.
      pinnedTrack = trackKey
      pinnedTheme = next
    } else {
      settings.mode.value = ThemeMode.MANUAL
      settings.manualTheme.value = next
    }
    if (settings.learningEnabled.value) {
      scope.launch(Dispatchers.IO) {
        runCatching {
          val entryPoint =
            EntryPointAccessors.fromApplication(
              context.applicationContext,
              CinematicEntryPoint::class.java,
            )
          entryPoint.smartCinematicRepository().recordManualTheme(trackKey, title, artist, next)
        }
      }
    }
  }

  fun adjustTiming(deltaMs: Long) {
    val next = (correctionMs + deltaMs).coerceIn(-5_000L, 5_000L)
    correctionMs = next
    correctionsJson =
      runCatching {
        val obj = JSONObject(correctionsJson.ifBlank { "{}" })
        if (next == 0L) obj.remove(trackKey) else obj.put(trackKey, next)
        obj.toString()
      }.getOrDefault(correctionsJson)
  }

  val visualSettings = settings.snapshot()
  val visual =
    CinematicVisualState(
      theme = theme,
      features = audioFeatures.value,
      section = section,
      positionMs = positionMs + lyricsOffsetMs,
      settings = visualSettings,
      lowEnd = lowEnd,
    )

  return CinematicEngineState(
    theme = theme,
    features = audioFeatures.value,
    section = section,
    positionMs = positionMs + lyricsOffsetMs,
    words = words,
    visual = visual,
    mood = moodResult.mood,
    moodConfidence = moodResult.confidence,
    ai = ai,
    timingVerified = words.isEmpty() || words.all { it.verified },
    onSelectTheme = ::selectTheme,
    onAdjustTiming = ::adjustTiming,
    onRefreshAi = { forceAiRefresh = System.currentTimeMillis() },
  )
}

@Composable
private fun LoadCinematicCorrection(
  trackKey: String,
  json: String,
  onLoaded: (Long) -> Unit,
) {
  LaunchedEffect(trackKey, json) {
    val value =
      runCatching { JSONObject(json.ifBlank { "{}" }).optLong(trackKey, 0L) }.getOrDefault(0L)
    onLoaded(value)
  }
}
