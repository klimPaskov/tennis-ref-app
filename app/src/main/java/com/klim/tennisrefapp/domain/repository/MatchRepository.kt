package com.klim.tennisrefapp.domain.repository

sealed class StartMatchResult {
    data object Success : StartMatchResult()
    data object MatchNotFound : StartMatchResult()
    data object MissingResolvedParticipants : StartMatchResult()
    data object InvalidInitialServer : StartMatchResult()
    data object InvalidMatchState : StartMatchResult()
}

interface MatchRepository {
    suspend fun swapSides(matchId: Long)
    suspend fun startMatch(
        matchId: Long,
        initialServerPlayerId: Long,
        timestamp: Long = System.currentTimeMillis(),
    ): StartMatchResult
}
