package com.klim.tennisrefapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.klim.tennisrefapp.data.local.entity.SlotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SlotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(slots: List<SlotEntity>)

    @Query("SELECT * FROM slots")
    fun observeAll(): Flow<List<SlotEntity>>

    @Query("SELECT * FROM slots WHERE matchId = :matchId")
    fun observeByMatchId(matchId: Long): Flow<List<SlotEntity>>

    @Query("SELECT * FROM slots")
    suspend fun getAll(): List<SlotEntity>

    @Query("SELECT * FROM slots WHERE matchId = :matchId")
    suspend fun getByMatchId(matchId: Long): List<SlotEntity>

    @Update
    suspend fun update(slot: SlotEntity)
}
