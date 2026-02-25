package com.klim.tennisrefapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.klim.tennisrefapp.data.local.entity.CourtEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourtDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(courts: List<CourtEntity>)

    @Query("SELECT * FROM courts WHERE isActive = 1 ORDER BY sortOrder ASC")
    fun observeActiveCourts(): Flow<List<CourtEntity>>

    @Query("SELECT * FROM courts WHERE id = :courtId LIMIT 1")
    fun observeById(courtId: Long): Flow<CourtEntity?>
}
