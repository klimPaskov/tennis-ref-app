package com.klim.tennisrefapp.domain.model

sealed interface SlotReference {
    data class DirectTeam(val teamId: Long) : SlotReference
    data class WinnerOfMatch(val matchId: Long) : SlotReference
    data object Bye : SlotReference
}

data class MatchDependency(
    val matchId: Long,
    val winnerTeamId: Long?,
)

data class SlotResolution(
    val resolvedTeamId: Long?,
    val isBye: Boolean,
    val dependencyMatchId: Long?,
) {
    val isResolved: Boolean
        get() = isBye || resolvedTeamId != null
}
