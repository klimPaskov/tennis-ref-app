package com.klim.tennisrefapp.domain.repository

import com.klim.tennisrefapp.domain.model.CompletedCourtMatch
import com.klim.tennisrefapp.domain.model.MatchHistoryDetail
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeCompletedMatchesByCourt(): Flow<List<CompletedCourtMatch>>
    fun observeMatchHistory(matchId: Long): Flow<MatchHistoryDetail?>
}
