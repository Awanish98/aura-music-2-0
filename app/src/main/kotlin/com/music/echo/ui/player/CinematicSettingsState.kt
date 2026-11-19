package echo.music.iad1tya.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import echo.music.iad1tya.cinematic.CinematicTheme
import echo.music.iad1tya.cinematic.ThemeMode
import echo.music.iad1tya.constants.AmoledLyricsKeepAwakeKey
import echo.music.iad1tya.constants.AmoledLyricsLowPowerKey
import echo.music.iad1tya.constants.AmoledLyricsModeKey
import echo.music.iad1tya.constants.CinematicAnimationSpeedKey
import echo.music.iad1tya.constants.CinematicAutoSectionSwitchKey
import echo.music.iad1tya.constants.CinematicBackgroundEffectsKey
import echo.music.iad1tya.constants.CinematicBassReactionKey
import echo.music.iad1tya.constants.CinematicBeatSensitivityKey
import echo.music.iad1tya.constants.CinematicFavoriteThemesKey
import echo.music.iad1tya.constants.CinematicGlowKey
import echo.music.iad1tya.constants.CinematicManualThemeKey
import echo.music.iad1tya.constants.CinematicMotionKey
import echo.music.iad1tya.constants.CinematicParticlesKey
import echo.music.iad1tya.constants.CinematicReducedMotionKey
import echo.music.iad1tya.constants.CinematicShuffleKey
import echo.music.iad1tya.constants.CinematicThemeModeKey
import echo.music.iad1tya.constants.CinematicVocalHighlightKey
import echo.music.iad1tya.constants.SmartAiCacheTtlKey
import echo.music.iad1tya.constants.SmartAiModelKey
import echo.music.iad1tya.constants.SmartBackendBaseUrlKey
import echo.music.iad1tya.constants.SmartBackendTokenKey
import echo.music.iad1tya.constants.SmartHistoryTrackingKey
import echo.music.iad1tya.constants.SmartLearningEnabledKey
import echo.music.iad1tya.constants.SmartLocalAnalysisKey
import echo.music.iad1tya.constants.SmartManualOverrideKey
import echo.music.iad1tya.utils.rememberEnumPreference
import echo.music.iad1tya.utils.rememberPreference

/** All persisted cinematic/SMART controls in one place. */
data class CinematicSettingsState(
  val mode: MutableState<ThemeMode>,
  val manualTheme: MutableState<CinematicTheme>,
  val favorites: MutableState<Set<CinematicTheme>>,
  val shuffle: MutableState<Boolean>,
  val beatSensitivity: MutableState<Float>,
  val bassReaction: MutableState<Float>,
  val vocalHighlight: MutableState<Float>,
  val animationSpeed: MutableState<Float>,
  val glow: MutableState<Float>,
  val particles: MutableState<Float>,
  val backgroundEffects: MutableState<Boolean>,
  val motion: MutableState<Float>,
  val autoSectionSwitch: MutableState<Boolean>,
  val reducedMotion: MutableState<Boolean>,
  val learningEnabled: MutableState<Boolean>,
  val historyTracking: MutableState<Boolean>,
  val localAnalysis: MutableState<Boolean>,
  val smartManualOverride: MutableState<Boolean>,
  val backendBaseUrl: MutableState<String>,
  val backendToken: MutableState<String>,
  val aiModel: MutableState<String>,
  val aiCacheTtl: MutableState<Long>,
  val amoledMode: MutableState<Boolean>,
  val amoledLowPower: MutableState<Boolean>,
  val amoledKeepAwake: MutableState<Boolean>,
)

@Composable
fun rememberCinematicSettings(): CinematicSettingsState {
  val mode = rememberEnumPreference(CinematicThemeModeKey, ThemeMode.AUTO)
  val manualTheme =
    rememberEnumPreference(CinematicManualThemeKey, CinematicTheme.GOLDEN_CINEMA)
  val favoriteNames = rememberPreference(CinematicFavoriteThemesKey, emptySet<String>())
  val favorites =
    remember(favoriteNames) {
      object : MutableState<Set<CinematicTheme>> {
        override var value: Set<CinematicTheme>
          get() =
            favoriteNames.value.mapNotNull { name ->
              runCatching { CinematicTheme.valueOf(name) }.getOrNull()
            }.toSet()
          set(value) { favoriteNames.value = value.map { it.name }.toSet() }

        override fun component1() = value

        override fun component2(): (Set<CinematicTheme>) -> Unit = { value = it }
      }
    }

  return CinematicSettingsState(
    mode = mode,
    manualTheme = manualTheme,
    favorites = favorites,
    shuffle = rememberPreference(CinematicShuffleKey, false),
    beatSensitivity = rememberPreference(CinematicBeatSensitivityKey, 1f),
    bassReaction = rememberPreference(CinematicBassReactionKey, 1f),
    vocalHighlight = rememberPreference(CinematicVocalHighlightKey, 1f),
    animationSpeed = rememberPreference(CinematicAnimationSpeedKey, 1f),
    glow = rememberPreference(CinematicGlowKey, 1f),
    particles = rememberPreference(CinematicParticlesKey, 1f),
    backgroundEffects = rememberPreference(CinematicBackgroundEffectsKey, true),
    motion = rememberPreference(CinematicMotionKey, 1f),
    autoSectionSwitch = rememberPreference(CinematicAutoSectionSwitchKey, false),
    reducedMotion = rememberPreference(CinematicReducedMotionKey, false),
    learningEnabled = rememberPreference(SmartLearningEnabledKey, true),
    historyTracking = rememberPreference(SmartHistoryTrackingKey, true),
    localAnalysis = rememberPreference(SmartLocalAnalysisKey, true),
    smartManualOverride = rememberPreference(SmartManualOverrideKey, false),
    backendBaseUrl = rememberPreference(SmartBackendBaseUrlKey, ""),
    backendToken = rememberPreference(SmartBackendTokenKey, ""),
    aiModel = rememberPreference(SmartAiModelKey, ""),
    aiCacheTtl = rememberPreference(SmartAiCacheTtlKey, 6L * 60L * 60L * 1000L),
    amoledMode = rememberPreference(AmoledLyricsModeKey, false),
    amoledLowPower = rememberPreference(AmoledLyricsLowPowerKey, true),
    amoledKeepAwake = rememberPreference(AmoledLyricsKeepAwakeKey, true),
  )
}
