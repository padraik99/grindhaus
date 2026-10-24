package com.padraik99.grindhaus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BeanDao {

    @Insert
    suspend fun insert(bean: Bean): Long

    @Query("SELECT * FROM beans WHERE archived = 0 ORDER BY roast_date DESC")
    fun observeActive(): Flow<List<Bean>>

    @Query("SELECT * FROM beans WHERE archived = 0 ORDER BY roast_date DESC")
    suspend fun active(): List<Bean>

    /** Beans are archived, never deleted, so brew history survives a finished bag. */
    @Query("UPDATE beans SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)
}
