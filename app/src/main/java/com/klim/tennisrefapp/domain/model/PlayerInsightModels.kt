package com.klim.tennisrefapp.domain.model

data class PlayerOverview(
    val playerId: Long,
    val name: String,
    val countryCode: String?,
    val completedCount: Int,
    val nextMatchLabel: String?,
    val nextCourtName: String?,
    val nextNotBeforeMillis: Long?,
)

data class PlayerDetailInsight(
    val playerId: Long,
    val name: String,
    val countryCode: String?,
    val currentRestMinutes: Int,
    val completedMatches: List<PlayerMatchSnapshot>,
    val nextMatch: PlayerMatchSnapshot?,
    val canRequestRest: Boolean,
)

data class PlayerMatchSnapshot(
    val matchId: Long,
    val courtName: String?,
    val matchLabel: String,
    val status: CourtStatus,
    val scoreLabel: String?,
    val notBeforeMillis: Long?,
    val startedAtMillis: Long?,
    val endedAtMillis: Long?,
)
