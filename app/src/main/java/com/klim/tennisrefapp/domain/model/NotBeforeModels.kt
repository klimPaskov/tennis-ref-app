package com.klim.tennisrefapp.domain.model

data class PlayerAvailability(
    val playerId: Long,
    val restMinutes: Int,
    val lastEndedAtMillis: Long?,
)

data class NotBeforeInput(
    val manualNotBeforeMillis: Long?,
    val participants: List<PlayerAvailability>,
)
