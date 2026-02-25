package com.klim.tennisrefapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.klim.tennisrefapp.data.local.entity.MatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(matches: List<MatchEntity>)

    @Query("SELECT * FROM matches")
    fun observeAll(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches")
    suspend fun getAll(): List<MatchEntity>

    @Query("SELECT * FROM matches WHERE id = :matchId LIMIT 1")
    suspend fun getById(matchId: Long): MatchEntity?

    @Update
    suspend fun update(match: MatchEntity)
}
