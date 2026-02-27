package com.klim.tennisrefapp.domain.repository

interface TournamentRepository {
    suspend fun seedIfEmpty()
    suspend fun reloadOrderOfPlay(): Boolean
    suspend fun reloadOrderOfPlayFromFile(uriString: String): Boolean
    suspend fun loadPreloadedOrderOfPlay(preloadId: String = "example"): Boolean
}
