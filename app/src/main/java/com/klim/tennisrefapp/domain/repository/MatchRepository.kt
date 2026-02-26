package com.klim.tennisrefapp.domain.repository

import com.klim.tennisrefapp.domain.model.ViolationCategory
import com.klim.tennisrefapp.domain.model.ViolationGuidance
import com.klim.tennisrefapp.domain.model.RestPrompt

sealed class StartMatchResult {
    data object Success : StartMatchResult()
    data object MatchNotFound : StartMatchResult()
    data object MissingResolvedParticipants : StartMatchResult()
    data object InvalidInitialServer : StartMatchResult()
    data object InvalidMatchState : StartMatchResult()
}

enum class MatchWinnerSide {
    LEFT,
    RIGHT,
}

enum class MatchResultType {
    NORMAL,
    RETIREMENT,
    WALKOVER,
    DEFAULT,
}

data class ApplyViolationResult(
    val guidance: ViolationGuidance,
    val summary: String,
)

sealed class EndMatchResult {
    data object Success : EndMatchResult()
    data object MatchNotFound : EndMatchResult()
    data object InvalidMatchState : EndMatchResult()
    data object MissingResolvedParticipants : EndMatchResult()
}

interface MatchRepository {
    suspend fun swapSides(matchId: Long)

    suspend fun setScheduledInitialServer(matchId: Long, playerId: Long): Boolean

    suspend fun startMatch(
        matchId: Long,
        initialServerPlayerId: Long,
        timestamp: Long = System.currentTimeMillis(),
    ): StartMatchResult

    suspend fun endMatch(
        matchId: Long,
        winnerSide: MatchWinnerSide,
        resultType: MatchResultType,
        scoreText: String,
        timestamp: Long = System.currentTimeMillis(),
    ): EndMatchResult

    suspend fun applyViolation(
        matchId: Long,
        offenderPlayerId: Long,
        category: ViolationCategory,
        violationName: String,
        timestamp: Long = System.currentTimeMillis(),
    ): ApplyViolationResult

    suspend fun undoLastViolation(matchId: Long): Boolean

    suspend fun clearCourt(courtId: Long)
    suspend fun assignFirstUnassignedMatch(courtId: Long): Boolean
    suspend fun swapCourtMatches(courtIdA: Long, courtIdB: Long): Boolean
    suspend fun moveMatchToCourt(matchId: Long, courtId: Long?): Boolean
    suspend fun autoStartMatchFromWarmup(matchId: Long): StartMatchResult
    suspend fun getRestPrompt(matchId: Long): RestPrompt?
}
