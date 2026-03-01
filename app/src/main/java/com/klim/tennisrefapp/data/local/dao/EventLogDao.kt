package com.klim.tennisrefapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.klim.tennisrefapp.data.local.entity.EventLogEntity
import com.klim.tennisrefapp.data.local.entity.EventType
import kotlinx.coroutines.flow.Flow

@Dao
interface EventLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventLogEntity>)

    @Query("SELECT * FROM event_logs WHERE matchId = :matchId ORDER BY timestamp ASC")
    fun observeByMatchId(matchId: Long): Flow<List<EventLogEntity>>

    @Query("SELECT * FROM event_logs WHERE matchId = :matchId AND eventType = :eventType ORDER BY timestamp ASC")
    suspend fun getByMatchAndType(matchId: Long, eventType: EventType): List<EventLogEntity>

    @Query("SELECT * FROM event_logs WHERE matchId = :matchId AND eventType = :eventType ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestByMatchAndType(matchId: Long, eventType: EventType): EventLogEntity?

    @Query("UPDATE event_logs SET timestamp = :timestamp, message = :message WHERE id = :eventId")
    suspend fun updateEvent(eventId: Long, timestamp: Long, message: String?): Int

    @Query("DELETE FROM event_logs WHERE id = :eventId")
    suspend fun deleteById(eventId: Long): Int
}
