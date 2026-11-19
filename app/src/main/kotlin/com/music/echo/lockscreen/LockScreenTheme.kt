package echo.music.iad1tya.lockscreen

/** Lock-screen visual presets. Values are persisted by name in DataStore. */
enum class LockScreenTheme(
  val label: String,
  val accentArgb: Long,
  val secondaryArgb: Long,
) {
  CINEMATIC("Cinematic", 0xFFE6B980, 0xFF3A2A1F),
  AMOLED("AMOLED", 0xFFFFFFFF, 0xFF000000),
  NEON("Neon", 0xFF8BE9FD, 0xFF442AD1),
  ROMANTIC("Romantic", 0xFFFF9DB5, 0xFF4A1D3A),
  EDITORIAL("Editorial", 0xFFF5F5F0, 0xFF1D1D1B);

  companion object {
    fun fromStored(value: String?): LockScreenTheme =
      entries.firstOrNull { it.name == value } ?: CINEMATIC
  }
}
