package com.klim.tennisrefapp.domain.model

data class CompletedCourtMatch(
    val matchId: Long,
    val courtId: Long?,
    val courtName: String?,
    val matchLabel: String,
    val scoreLabel: String?,
    val startedAtMillis: Long?,
    val endedAtMillis: Long?,
)

data class MatchHistoryDetail(
    val matchId: Long,
    val courtName: String?,
    val matchLabel: String,
    val scoreLabel: String?,
    val startedAtMillis: Long?,
    val endedAtMillis: Long?,
    val events: List<MatchEvent>,
)
