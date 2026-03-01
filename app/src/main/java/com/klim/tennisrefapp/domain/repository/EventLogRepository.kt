package com.klim.tennisrefapp.domain.repository

import com.klim.tennisrefapp.domain.model.MatchEvent
import com.klim.tennisrefapp.domain.model.MatchEventKind
import kotlinx.coroutines.flow.Flow

interface EventLogRepository {
    suspend fun logEvent(
        matchId: Long?,
        courtId: Long?,
        kind: MatchEventKind,
        offenderPlayerId: Long?,
        message: String?,
        timestamp: Long = System.currentTimeMillis(),
    )

    fun observeEventsForMatch(matchId: Long): Flow<List<MatchEvent>>

    suspend fun updateEvent(
        eventId: Long,
        timestamp: Long,
        message: String?,
    ): Boolean

    suspend fun deleteEvent(eventId: Long): Boolean
}
