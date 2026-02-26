package com.klim.tennisrefapp.domain.model

data class RestPrompt(
    val matchId: Long,
    val matchDurationMinutes: Int,
    val players: List<RestPromptPlayer>,
)

data class RestPromptPlayer(
    val playerId: Long,
    val playerName: String,
    val countryCode: String?,
    val suggestedRestMinutes: Int,
    val upcomingMatchLabel: String,
    val upcomingCourtName: String?,
)
