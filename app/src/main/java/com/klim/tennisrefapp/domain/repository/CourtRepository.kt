package com.klim.tennisrefapp.domain.repository

import com.klim.tennisrefapp.domain.model.CourtCard
import com.klim.tennisrefapp.domain.model.CourtDetail
import kotlinx.coroutines.flow.Flow

interface CourtRepository {
    fun observeCourtCards(): Flow<List<CourtCard>>
    fun observeCourtDetail(courtId: Long): Flow<CourtDetail?>
}
