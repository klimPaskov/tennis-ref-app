package com.klim.tennisrefapp.domain.repository

import com.klim.tennisrefapp.domain.model.PlayerSummary
import kotlinx.coroutines.flow.Flow

interface PlayerRepository {
    fun observePlayers(): Flow<List<PlayerSummary>>
}
