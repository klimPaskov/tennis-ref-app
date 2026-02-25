package com.klim.tennisrefapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.klim.tennisrefapp.data.local.entity.TeamPlayerCrossRefEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamPlayerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(crossRefs: List<TeamPlayerCrossRefEntity>)

    @Query("SELECT * FROM team_player_cross_ref")
    fun observeAll(): Flow<List<TeamPlayerCrossRefEntity>>

    @Query("SELECT * FROM team_player_cross_ref")
    suspend fun getAll(): List<TeamPlayerCrossRefEntity>
}
