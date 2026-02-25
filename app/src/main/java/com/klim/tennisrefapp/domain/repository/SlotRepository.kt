package com.klim.tennisrefapp.domain.repository

import com.klim.tennisrefapp.domain.model.SlotSummary
import kotlinx.coroutines.flow.Flow

interface SlotRepository {
    fun observeSlotsForMatch(matchId: Long): Flow<List<SlotSummary>>
}
