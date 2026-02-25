package com.klim.tennisrefapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.klim.tennisrefapp.data.local.entity.PlayerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(players: List<PlayerEntity>)

    @Query("SELECT * FROM players")
    fun observeAll(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players")
    suspend fun getAll(): List<PlayerEntity>
}
