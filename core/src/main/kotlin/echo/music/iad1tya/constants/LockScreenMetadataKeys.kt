package echo.music.iad1tya.constants

/**
 * Custom MediaMetadata keys used to carry the active lyric line from the
 * playback service to the lock-screen notification and the optional in-app
 * lock-screen renderer. They are intentionally namespaced so other Media3
 * controllers simply ignore them.
 */
object LockScreenMetadataKeys {
  const val LINE = "aura.lock_screen.line"
  const val NEXT_LINE = "aura.lock_screen.next_line"
  const val WORD = "aura.lock_screen.word"
  const val UPCOMING_WORD = "aura.lock_screen.upcoming_word"
  const val WORD_PROGRESS = "aura.lock_screen.word_progress"
  const val VERIFIED = "aura.lock_screen.verified"
  const val UPDATED_AT = "aura.lock_screen.updated_at"
}
