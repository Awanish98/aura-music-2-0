package com.music.echo.codepoetry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import echo.music.iad1tya.constants.CodePoetryAlwaysOnKey
import echo.music.iad1tya.constants.CodePoetryAutoCompositionKey
import echo.music.iad1tya.constants.CodePoetryBugEnabledKey
import echo.music.iad1tya.constants.CodePoetryCameraIntensityKey
import echo.music.iad1tya.constants.CodePoetryCompositionKey
import echo.music.iad1tya.constants.CodePoetryCursorColorKey
import echo.music.iad1tya.constants.CodePoetryFontSizeKey
import echo.music.iad1tya.constants.CodePoetryGlitchIntensityKey
import echo.music.iad1tya.constants.CodePoetryGrainKey
import echo.music.iad1tya.constants.CodePoetryPaletteKey
import echo.music.iad1tya.constants.CodePoetryPopupFrequencyKey
import echo.music.iad1tya.constants.CodePoetryTypingSpeedKey
import echo.music.iad1tya.utils.rememberEnumPreference
import echo.music.iad1tya.utils.rememberPreference
import kotlinx.serialization.Serializable

/** Syntax colours for the fake IDE. Values are ARGB longs so they work on Canvas and in Compose. */
@Serializable
enum class CodePalette(
  val label: String,
  val normal: Long,
  val important: Long,
  val emphasis: Long,
  val keyword: Long,
  val secondary: Long,
  val comment: Long,
  val cursor: Long,
) {
  MIDNIGHT(
    "Midnight Aurora",
    0xFFE7EDF8, // normal - frost white
    0xFFFF6B9A, // important - current lyric word / numbers
    0xFFFFC17A, // emphasis - string literals
    0xFF5DE1FF, // keyword - electric cyan
    0xFFB79CFF, // secondary - functions and types
    0xFF6C7C99, // comment - muted slate
    0xFF66E6FF, // cursor - cyan
  ),
  MATRIX(
    "Matrix",
    0xFFC8FFD8,
    0xFFFF4D5E,
    0xFFFFB347,
    0xFF39FF88,
    0xFF8CFFB0,
    0xFF3F6B4F,
    0xFFFF3B30,
  ),
  AMBER(
    "Amber",
    0xFFF2E7D5,
    0xFFFF4D5E,
    0xFFFFB020,
    0xFFFFD166,
    0xFFC9A7FF,
    0xFF6B6255,
    0xFFFF3B30,
  ),
  AMOLED(
    "AMOLED",
    0xFFF5F5F5,
    0xFFFF2D42,
    0xFFFF8A00,
    0xFF4D8DFF,
    0xFFB06CFF,
    0xFF4A4A4A,
    0xFFFF0000,
  );

  companion object {
    fun fromName(value: String?): CodePalette =
      entries.find { it.name.equals(value, ignoreCase = true) } ?: MIDNIGHT
  }
}

/** The six cinematic compositions requested by the feature. */
@Serializable
enum class CodeComposition(val label: String) {
  FULL_EDITOR("Full code editor"),
  MACRO_WORD("Macro word"),
  TYPING("Typing"),
  ERROR_POPUP("Error popup"),
  BUG_SYMBOL("Bug symbol"),
  TRANSITION("Cinematic transition");

  companion object {
    fun fromName(value: String?): CodeComposition =
      entries.find { it.name.equals(value, ignoreCase = true) } ?: FULL_EDITOR
  }
}

enum class CodeTokenKind { NORMAL, IMPORTANT, EMPHASIS, KEYWORD, SECONDARY, COMMENT }

/** Flat, immutable render input. All timing comes from the shared cinematic engine. */
/** Optional Groq-derived direction. Never contains lyrics or timestamps. */
data class CodeAiDirection(
  val energyHint: Float = 0.5f,
  val confidence: Float = 0f,
  val themeHint: String? = null,
  val sectionName: String? = null,
  val mood: String? = null,
  val available: Boolean = false,
)

data class CodePoetryConfig(
  val typingSpeed: Float,
  val fontSize: Float,
  val palette: CodePalette,
  val cursorArgb: Int,
  val cameraIntensity: Float,
  val glitchIntensity: Float,
  val grain: Float,
  val popupFrequency: Float,
  val bugEnabled: Boolean,
  val autoComposition: Boolean,
  val composition: CodeComposition,
  val alwaysOn: Boolean,
  val lowPower: Boolean,
  val ai: CodeAiDirection = CodeAiDirection(),
)

data class CodeWordFrame(
  val text: String,
  val typed: String,
  val startMs: Long,
  val endMs: Long,
  val verified: Boolean,
  val isCurrent: Boolean,
  val progress: Float,
  val kind: CodeTokenKind,
)

data class CodeLineFrame(
  val number: Int,
  val words: List<CodeWordFrame>,
  val isActive: Boolean,
  val verified: Boolean,
)

data class CodeCameraFrame(
  val zoom: Float,
  val panX: Float,
  val panY: Float,
  val rotation: Float,
  val perspectiveX: Float,
  val perspectiveY: Float,
  val blur: Float,
)

data class CodeFrameSpec(
  val lines: List<CodeLineFrame>,
  /**
   * Complete lyric history for the editor surface. [lines] stays the small,
   * unchanged policy window; the renderer uses this history so passed lines
   * scroll upward like a real source file while the active line types in place.
   */
  val editorLines: List<CodeLineFrame> = lines,
  val composition: CodeComposition,
  val camera: CodeCameraFrame,
  val config: CodePoetryConfig,
  val positionMs: Long,
  val isPlaying: Boolean,
  val timingVerified: Boolean,
  val title: String,
  val artist: String,
  val frameIndex: Int,
  val popupText: String,
  val popupAgeMs: Long,
  val showBug: Boolean,
  val driftX: Float,
  val driftY: Float,
  val aiLabel: String = "",
)

/** Durable controls for CODE POETRY, backed by the existing DataStore. */
data class CodePoetrySettingsState(
  val typingSpeed: MutableState<Float>,
  val fontSize: MutableState<Float>,
  val palette: MutableState<CodePalette>,
  val cursorColor: MutableState<String>,
  val cameraIntensity: MutableState<Float>,
  val glitchIntensity: MutableState<Float>,
  val grain: MutableState<Float>,
  val popupFrequency: MutableState<Float>,
  val bugEnabled: MutableState<Boolean>,
  val autoComposition: MutableState<Boolean>,
  val composition: MutableState<CodeComposition>,
  val alwaysOn: MutableState<Boolean>,
)

@Composable
fun rememberCodePoetrySettings(): CodePoetrySettingsState {
  val typingSpeed = rememberPreference(CodePoetryTypingSpeedKey, 1f)
  val fontSize = rememberPreference(CodePoetryFontSizeKey, 1f)
  val palette = rememberEnumPreference(CodePoetryPaletteKey, CodePalette.MIDNIGHT)
  val cursorColor = rememberPreference(CodePoetryCursorColorKey, "")
  val cameraIntensity = rememberPreference(CodePoetryCameraIntensityKey, 0.45f)
  val glitchIntensity = rememberPreference(CodePoetryGlitchIntensityKey, 0.3f)
  val grain = rememberPreference(CodePoetryGrainKey, 0.18f)
  val popupFrequency = rememberPreference(CodePoetryPopupFrequencyKey, 0.35f)
  val bugEnabled = rememberPreference(CodePoetryBugEnabledKey, true)
  val autoComposition = rememberPreference(CodePoetryAutoCompositionKey, true)
  val composition = rememberEnumPreference(CodePoetryCompositionKey, CodeComposition.FULL_EDITOR)
  val alwaysOn = rememberPreference(CodePoetryAlwaysOnKey, false)

  return remember(
    typingSpeed,
    fontSize,
    palette,
    cursorColor,
    cameraIntensity,
    glitchIntensity,
    grain,
    popupFrequency,
    bugEnabled,
    autoComposition,
    composition,
    alwaysOn,
  ) {
    CodePoetrySettingsState(
      typingSpeed = typingSpeed,
      fontSize = fontSize,
      palette = palette,
      cursorColor = cursorColor,
      cameraIntensity = cameraIntensity,
      glitchIntensity = glitchIntensity,
      grain = grain,
      popupFrequency = popupFrequency,
      bugEnabled = bugEnabled,
      autoComposition = autoComposition,
      composition = composition,
      alwaysOn = alwaysOn,
    )
  }
}
