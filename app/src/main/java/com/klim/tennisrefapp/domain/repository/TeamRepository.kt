package com.klim.tennisrefapp.domain.repository

import com.klim.tennisrefapp.domain.model.TeamSummary
import kotlinx.coroutines.flow.Flow

interface TeamRepository {
    fun observeTeams(): Flow<List<TeamSummary>>
}
