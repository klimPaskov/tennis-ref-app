package com.klim.tennisrefapp.domain.repository

interface TournamentRepository {
    suspend fun seedIfEmpty()
    suspend fun reloadOrderOfPlay()
    suspend fun reloadOrderOfPlayFromFile(uriString: String): Boolean
    suspend fun loadPreloadedOrderOfPlay(): Boolean
}
