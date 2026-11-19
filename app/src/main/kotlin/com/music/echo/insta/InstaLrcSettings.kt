package com.music.echo.insta

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import echo.music.iad1tya.constants.InstaLrcCustomPresetsKey
import echo.music.iad1tya.constants.InstaLrcImageUriKey
import echo.music.iad1tya.constants.InstaLrcModeKey
import echo.music.iad1tya.constants.InstaLrcPresetKey
import echo.music.iad1tya.constants.InstaLrcSafeModeKey
import echo.music.iad1tya.constants.InstaLrcShowEstimatedKey
import echo.music.iad1tya.constants.InstaLrcTimingOffsetKey
import echo.music.iad1tya.constants.InstaLrcVideoUriKey
import echo.music.iad1tya.utils.rememberEnumPreference
import echo.music.iad1tya.utils.rememberPreference
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
enum class InstaLrcMode(val label: String) {
  AUTO("AUTO"),
  MANUAL("MANUAL"),
  SMART_AI("SMART AI"),
}

private val instaJson =
  Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

private val presetListSerializer = ListSerializer(InstaPreset.serializer())

private val legacyLookNames =
  mapOf(
    "vintage cinematic" to "Neon Pulse",
    "minimal kinetic" to "Chrome Pop",
    "vintage film" to "Rose Riot",
    "amoled hybrid" to "Afterglow",
  )

/**
 * Saved presets from the old small-type looks are transparently upgraded to the
 * oversized bold treatment. This keeps DataStore compatibility without showing
 * the weak themes to existing users.
 */
private fun upgradeLook(preset: InstaPreset): InstaPreset {
  legacyLookNames[preset.name.trim().lowercase()]?.let { name ->
    InstaPresets.find(name)?.let { return it }
  }
  val isLegacy =
    preset.style != InstaVisualStyle.BOLD_TYPOGRAPHY ||
      preset.fontSize < 1.3f ||
      preset.fontWeight < 900 ||
      preset.font == InstaFont.SERIF ||
      preset.font == InstaFont.HANDWRITTEN ||
      preset.background in
      setOf(
        InstaBackground.VINTAGE_PAINTING,
        InstaBackground.FLORAL_TEXTURE,
        InstaBackground.CINEMATIC_SCENERY,
        InstaBackground.VINTAGE_FILM,
      )
  if (!isLegacy) return preset
  return preset.copy(
    style = InstaVisualStyle.BOLD_TYPOGRAPHY,
    font = if (preset.font == InstaFont.SERIF || preset.font == InstaFont.HANDWRITTEN) InstaFont.DISPLAY else preset.font,
    fontSize = preset.fontSize.coerceAtLeast(1.35f),
    fontWeight = 900,
    spacing = preset.spacing.coerceAtMost(0.95f),
    background =
      if (
        preset.background in
        setOf(
          InstaBackground.VINTAGE_PAINTING,
          InstaBackground.FLORAL_TEXTURE,
          InstaBackground.CINEMATIC_SCENERY,
          InstaBackground.VINTAGE_FILM,
        )
      ) {
        InstaBackground.PURE_BLACK
      } else {
        preset.background
      },
    blur = 0f,
    safeMargin = preset.safeMargin.coerceAtLeast(0.1f),
    pattern =
      if (preset.pattern == InstaAnimationPattern.FADE_DISSOLVE || preset.pattern == InstaAnimationPattern.CINEMATIC_ZOOM) {
        InstaAnimationPattern.SCALE_IN
      } else {
        preset.pattern
      },
  )
}

/** Durable controls for INSTA LRC PLAY. All state is backed by the existing DataStore. */
data class InstaLrcSettingsState(
  val mode: MutableState<InstaLrcMode>,
  val presetJson: MutableState<String>,
  val customPresetsJson: MutableState<String>,
  val timingOffsetMs: MutableState<Long>,
  val showEstimatedHint: MutableState<Boolean>,
  val imageUri: MutableState<String>,
  val videoUri: MutableState<String>,
  val safeMode: MutableState<Boolean>,
) {
  val preset: InstaPreset
    get() =
      upgradeLook(
        runCatching { instaJson.decodeFromString(InstaPreset.serializer(), presetJson.value) }
          .getOrDefault(InstaPresets.BUILT_IN.first()),
      )

  val customPresets: List<InstaPreset>
    get() =
      runCatching { instaJson.decodeFromString(presetListSerializer, customPresetsJson.value) }
        .getOrDefault(emptyList())
        .map(::upgradeLook)
        .take(12)

  val allPresets: List<InstaPreset>
    get() = InstaPresets.BUILT_IN + customPresets

  fun applyPreset(value: InstaPreset) {
    presetJson.value = instaJson.encodeToString(InstaPreset.serializer(), value)
  }

  fun updatePreset(transform: (InstaPreset) -> InstaPreset) {
    applyPreset(transform(preset))
  }

  fun setCustomPresets(values: List<InstaPreset>) {
    customPresetsJson.value =
      instaJson.encodeToString(presetListSerializer, values.take(12))
  }

  fun saveCustomPreset(name: String): String {
    val cleanName = name.trim().ifBlank { "Custom preset" }.take(40)
    val value = preset.copy(name = cleanName)
    val existing = customPresets.filterNot { it.name.equals(cleanName, ignoreCase = true) }
    setCustomPresets(listOf(value) + existing)
    applyPreset(value)
    return cleanName
  }

  fun deleteCustomPreset(name: String) {
    setCustomPresets(customPresets.filterNot { it.name.equals(name, ignoreCase = true) })
  }
}

@Composable
fun rememberInstaLrcSettings(): InstaLrcSettingsState {
  val mode =
    rememberEnumPreference(InstaLrcModeKey, InstaLrcMode.AUTO)
  val presetJson =
    rememberPreference(
      InstaLrcPresetKey,
      instaJson.encodeToString(InstaPreset.serializer(), InstaPresets.BUILT_IN.first()),
    )
  val customPresetsJson =
    rememberPreference(InstaLrcCustomPresetsKey, instaJson.encodeToString(presetListSerializer, emptyList()))
  val timingOffsetMs = rememberPreference(InstaLrcTimingOffsetKey, 0L)
  val showEstimatedHint = rememberPreference(InstaLrcShowEstimatedKey, true)
  val imageUri = rememberPreference(InstaLrcImageUriKey, "")
  val videoUri = rememberPreference(InstaLrcVideoUriKey, "")
  val safeMode = rememberPreference(InstaLrcSafeModeKey, false)

  return remember(
    mode,
    presetJson,
    customPresetsJson,
    timingOffsetMs,
    showEstimatedHint,
    imageUri,
    videoUri,
    safeMode,
  ) {
    InstaLrcSettingsState(
      mode = mode,
      presetJson = presetJson,
      customPresetsJson = customPresetsJson,
      timingOffsetMs = timingOffsetMs,
      showEstimatedHint = showEstimatedHint,
      imageUri = imageUri,
      videoUri = videoUri,
      safeMode = safeMode,
    )
  }
}
