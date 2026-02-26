package com.klim.tennisrefapp.domain.model

enum class SlotTypeRef {
    DIRECT_TEAM,
    WINNER_OF_MATCH,
    BYE,
}

enum class SlotPositionRef {
    LEFT,
    RIGHT,
}

data class PlayerSummary(
    val id: Long,
    val displayName: String,
    val countryCode: String?,
    val restMinutes: Int?,
)

data class TeamSummary(
    val id: Long,
    val name: String?,
    val playerIds: List<Long>,
)

data class SlotSummary(
    val id: Long,
    val matchId: Long,
    val position: SlotPositionRef,
    val type: SlotTypeRef,
    val directTeamId: Long?,
    val winnerOfMatchId: Long?,
)
