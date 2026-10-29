package com.padraik99.grindhaus.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.padraik99.grindhaus.domain.BrewMethod
import com.padraik99.grindhaus.domain.FilterOption
import kotlinx.coroutines.flow.Flow

@Dao
interface GrinderDao {
    @Insert
    suspend fun insert(grinder: Grinder): Long

    @Query("SELECT * FROM grinders WHERE archived = 0 ORDER BY id")
    suspend fun active(): List<Grinder>

    @Query("SELECT * FROM grinders WHERE archived = 0 ORDER BY id")
    fun observeActive(): Flow<List<Grinder>>
}

@Dao
interface BrewDao {

    @Query(
        "SELECT * FROM dial_presets WHERE bean_id = :beanId AND method = :method " +
            "AND grinder_id = :grinderId AND filter = :filter",
    )
    fun observePreset(beanId: Long, method: BrewMethod, grinderId: Long, filter: FilterOption): Flow<DialPreset?>

    @Query(
        "SELECT * FROM dial_presets WHERE bean_id = :beanId AND method = :method " +
            "AND grinder_id = :grinderId AND filter = :filter",
    )
    suspend fun preset(beanId: Long, method: BrewMethod, grinderId: Long, filter: FilterOption): DialPreset?

    @Upsert
    suspend fun upsertPreset(preset: DialPreset)

    /** Saves new settings for a combination, replacing the old ones (the unique key keeps one row). */
    @Transaction
    suspend fun savePreset(preset: DialPreset) {
        val existing = preset(preset.beanId, preset.method, preset.grinderId, preset.filter)
        upsertPreset(preset.copy(id = existing?.id ?: 0))
    }

    @Insert
    suspend fun insertLog(log: BrewLog): Long

    @Query(
        "SELECT * FROM brew_logs WHERE bean_id = :beanId AND method = :method " +
            "AND grinder_id = :grinderId AND filter = :filter ORDER BY brewed_at DESC, id DESC LIMIT 1",
    )
    fun observeLastLog(beanId: Long, method: BrewMethod, grinderId: Long, filter: FilterOption): Flow<BrewLog?>

    @Query(
        "SELECT * FROM brew_logs WHERE bean_id = :beanId AND method = :method " +
            "AND grinder_id = :grinderId AND filter = :filter ORDER BY brewed_at DESC, id DESC LIMIT 1",
    )
    suspend fun lastLog(beanId: Long, method: BrewMethod, grinderId: Long, filter: FilterOption): BrewLog?

    /** The latest brew among methods that share one dial-in memory (AeroPress and freddo). */
    @Query(
        "SELECT * FROM brew_logs WHERE bean_id = :beanId AND method IN (:methods) " +
            "AND grinder_id = :grinderId AND filter = :filter ORDER BY brewed_at DESC, id DESC LIMIT 1",
    )
    suspend fun lastLogIn(beanId: Long, methods: List<BrewMethod>, grinderId: Long, filter: FilterOption): BrewLog?

    @Query(
        "SELECT * FROM brew_logs WHERE bean_id = :beanId AND method IN (:methods) " +
            "AND grinder_id = :grinderId AND filter = :filter ORDER BY brewed_at DESC, id DESC LIMIT 1",
    )
    fun observeLastLogIn(beanId: Long, methods: List<BrewMethod>, grinderId: Long, filter: FilterOption): Flow<BrewLog?>

    /** The most recent brews of any coffee, newest first, for Home. */
    @Query("SELECT * FROM brew_logs ORDER BY brewed_at DESC, id DESC LIMIT :limit")
    fun observeRecentLogs(limit: Int): Flow<List<BrewLog>>

    /** When each bag was last brewed, so the shelf can put the recent ones first. */
    @Query("SELECT bean_id, MAX(brewed_at) AS last_brewed FROM brew_logs GROUP BY bean_id")
    fun observeLastBrewed(): Flow<List<BeanLastBrewed>>

    @Query("SELECT * FROM brew_logs WHERE bean_id = :beanId ORDER BY brewed_at DESC")
    fun observeLogsForBean(beanId: Long): Flow<List<BrewLog>>

    @Query("SELECT * FROM brew_logs WHERE id = :id")
    suspend fun log(id: Long): BrewLog?

    @Insert
    suspend fun insertIntake(event: IntakeEvent): Long

    @Query("SELECT * FROM intake_events WHERE taken_at >= :sinceMillis ORDER BY taken_at")
    fun observeIntakeSince(sinceMillis: Long): Flow<List<IntakeEvent>>

    /** Only for tea and other drinks logged on their own; brews go with their log. */
    @Query("DELETE FROM intake_events WHERE id = :id AND brew_log_id IS NULL")
    suspend fun deleteStandaloneIntake(id: Long)

    @Query("DELETE FROM intake_events WHERE brew_log_id = :brewLogId")
    suspend fun deleteIntakeForLog(brewLogId: Long)

    @Delete
    suspend fun deleteLog(log: BrewLog)

    @Update
    suspend fun updateLog(log: BrewLog)

    @Query("UPDATE intake_events SET mg = :mg, taken_at = :takenAt, label = :label WHERE brew_log_id = :brewLogId")
    suspend fun updateIntakeForLog(brewLogId: Long, mg: Double, takenAt: Long, label: String)

    /** Every rated brew, for the shot-stars tie-breaker in the Favorites sort. */
    @Query("SELECT bean_id, rating FROM brew_logs WHERE rating IS NOT NULL")
    fun observeRatings(): Flow<List<BeanRating>>

    @Query("SELECT COALESCE(SUM(dose_g), 0) FROM brew_logs WHERE bean_id = :beanId")
    fun observeGramsUsed(beanId: Long): Flow<Double>
}

data class BeanLastBrewed(
    @androidx.room.ColumnInfo(name = "bean_id") val beanId: Long,
    @androidx.room.ColumnInfo(name = "last_brewed") val lastBrewed: Long,
)

/** Saved teas, most-used first. */
@Dao
interface TeaDao {
    @Query("SELECT * FROM saved_teas ORDER BY times_logged DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<SavedTea>>

    @Insert
    suspend fun insert(tea: SavedTea): Long

    @Update
    suspend fun update(tea: SavedTea)

    @Delete
    suspend fun delete(tea: SavedTea)

    @Query("UPDATE saved_teas SET times_logged = times_logged + 1 WHERE id = :id")
    suspend fun countUse(id: Long)
}

data class BeanRating(
    @androidx.room.ColumnInfo(name = "bean_id") val beanId: Long,
    val rating: Int,
)
