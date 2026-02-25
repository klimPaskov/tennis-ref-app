package com.klim.tennisrefapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.klim.tennisrefapp.data.local.entity.TournamentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TournamentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tournament: TournamentEntity)

    @Query("SELECT * FROM tournaments LIMIT 1")
    fun observeFirst(): Flow<TournamentEntity?>

    @Query("SELECT * FROM tournaments LIMIT 1")
    suspend fun getFirst(): TournamentEntity?

    @Query("SELECT COUNT(*) FROM tournaments")
    suspend fun count(): Int
}
