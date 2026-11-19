package echo.music.iad1tya.db.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import echo.music.iad1tya.db.entities.CinematicLearningEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CinematicLearningDao {
  @Query("SELECT * FROM cinematic_learning WHERE trackKey = :trackKey")
  suspend fun find(trackKey: String): CinematicLearningEntity?

  @Query("SELECT * FROM cinematic_learning ORDER BY playCount DESC LIMIT :limit")
  suspend fun mostPlayed(limit: Int = 20): List<CinematicLearningEntity>

  @Query("SELECT * FROM cinematic_learning ORDER BY updatedAt DESC")
  fun observeAll(): Flow<List<CinematicLearningEntity>>

  @Query("SELECT COUNT(*) FROM cinematic_learning")
  fun count(): Flow<Int>

  @Upsert
  suspend fun upsert(entity: CinematicLearningEntity)

  @Query("DELETE FROM cinematic_learning")
  suspend fun clear()
}
