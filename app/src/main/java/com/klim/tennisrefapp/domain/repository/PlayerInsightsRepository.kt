package com.klim.tennisrefapp.domain.repository

import com.klim.tennisrefapp.domain.model.PlayerDetailInsight
import com.klim.tennisrefapp.domain.model.PlayerOverview
import kotlinx.coroutines.flow.Flow

interface PlayerInsightsRepository {
    fun observePlayerOverviews(): Flow<List<PlayerOverview>>
    fun observePlayerDetailInsight(playerId: Long): Flow<PlayerDetailInsight?>
}
