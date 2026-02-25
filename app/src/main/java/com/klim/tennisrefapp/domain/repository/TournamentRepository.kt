package com.klim.tennisrefapp.domain.repository

interface TournamentRepository {
    suspend fun seedIfEmpty()
}
