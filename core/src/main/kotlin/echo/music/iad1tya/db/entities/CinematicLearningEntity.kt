package echo.music.iad1tya.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Private, on-device SMART Cinematic AI profile. Never uploaded by default. */
@Entity(tableName = "cinematic_learning")
data class CinematicLearningEntity(
  @PrimaryKey val trackKey: String,
  val title: String = "",
  val artist: String = "",
  val playCount: Int = 0,
  val skipCount: Int = 0,
  val preferredTheme: String? = null,
  val themeCountsJson: String = "{}",
  val rejectedThemesJson: String = "[]",
  val lastMood: String? = null,
  val lastTheme: String? = null,
  val aiMood: String? = null,
  val aiPlanJson: String? = null,
  val aiUpdatedAt: Long = 0L,
  val updatedAt: Long = 0L,
)
