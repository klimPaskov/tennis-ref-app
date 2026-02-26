package com.klim.tennisrefapp.domain.model

enum class CourtStatus {
    EMPTY,
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
}

data class PlayerRef(
    val playerId: Long,
    val name: String,
    val countryCode: String?,
)

data class SideParticipant(
    val slotId: Long,
    val label: String,
    val players: List<PlayerRef>,
    val resolvedTeamId: Long?,
    val isResolved: Boolean,
)

data class CourtCard(
    val courtId: Long,
    val courtName: String,
    val status: CourtStatus,
    val currentMatchLabel: String,
    val computedNotBeforeMillis: Long?,
    val matchId: Long?,
)

data class CourtDetail(
    val courtId: Long,
    val courtName: String,
    val status: CourtStatus,
    val matchId: Long?,
    val leftSide: SideParticipant?,
    val rightSide: SideParticipant?,
    val computedNotBeforeMillis: Long?,
    val startedAtMillis: Long?,
    val endedAtMillis: Long?,
    val initialServerPlayerId: Long?,
    val winnerTeamId: Long?,
    val canSwapSides: Boolean,
    val canStartMatch: Boolean,
    val scoreText: String?,
    val resultType: String?,
)

enum class MatchEventKind {
    MATCH_STARTED,
    MATCH_ENDED,
    VIOLATION,
    TIMER,
}

data class MatchEvent(
    val id: Long,
    val matchId: Long?,
    val courtId: Long?,
    val timestamp: Long,
    val kind: MatchEventKind,
    val offenderPlayerId: Long?,
    val message: String?,
)
