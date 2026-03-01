package com.klim.tennisrefapp.domain.repository

interface TournamentRepository {
    suspend fun seedIfEmpty()
    suspend fun reloadOrderOfPlay(): Boolean
    suspend fun reloadOrderOfPlayFromFile(uriString: String): Boolean
    suspend fun listPreloadedOrderOfPlayOptions(): List<PreloadedOrderOfPlayOption>
    suspend fun loadPreloadedOrderOfPlay(preloadId: String): Boolean
    suspend fun deletePreloadedOrderOfPlay(preloadId: String): Boolean
    suspend fun saveCurrentOrderOfPlayAsPreset(name: String): Boolean
}

data class PreloadedOrderOfPlayOption(
    val id: String,
    val label: String,
    val isDeletable: Boolean,
)
