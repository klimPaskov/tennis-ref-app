package com.klim.tennisrefapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.klim.tennisrefapp.data.local.entity.EventLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventLogEntity>)

    @Query("SELECT * FROM event_logs WHERE matchId = :matchId ORDER BY timestamp ASC")
    fun observeByMatchId(matchId: Long): Flow<List<EventLogEntity>>
}
