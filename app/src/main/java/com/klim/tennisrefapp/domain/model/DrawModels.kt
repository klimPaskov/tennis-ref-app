package com.klim.tennisrefapp.domain.model

data class DrawBoard(
    val eventCode: String,
    val rounds: List<DrawRound>,
)

data class DrawRound(
    val depth: Int,
    val label: String,
    val matches: List<DrawMatchNode>,
)

data class DrawMatchNode(
    val matchId: Long,
    val order: Int,
    val courtName: String?,
    val leftLabel: String,
    val rightLabel: String,
    val winnerLabel: String?,
    val scoreLabel: String?,
    val status: CourtStatus,
)
