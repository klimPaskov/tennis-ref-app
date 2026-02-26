package com.klim.tennisrefapp.di

import com.klim.tennisrefapp.data.repository.AppRepository
import com.klim.tennisrefapp.domain.repository.CourtRepository
import com.klim.tennisrefapp.domain.repository.EventLogRepository
import com.klim.tennisrefapp.domain.repository.HistoryRepository
import com.klim.tennisrefapp.domain.repository.MatchRepository
import com.klim.tennisrefapp.domain.repository.PlayerRepository
import com.klim.tennisrefapp.domain.repository.PlayerInsightsRepository
import com.klim.tennisrefapp.domain.repository.SlotRepository
import com.klim.tennisrefapp.domain.repository.TeamRepository
import com.klim.tennisrefapp.domain.repository.TournamentRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    fun provideTournamentRepository(repository: AppRepository): TournamentRepository = repository

    @Provides
    fun provideCourtRepository(repository: AppRepository): CourtRepository = repository

    @Provides
    fun provideMatchRepository(repository: AppRepository): MatchRepository = repository

    @Provides
    fun provideEventLogRepository(repository: AppRepository): EventLogRepository = repository

    @Provides
    fun providePlayerRepository(repository: AppRepository): PlayerRepository = repository

    @Provides
    fun providePlayerInsightsRepository(repository: AppRepository): PlayerInsightsRepository = repository

    @Provides
    fun provideHistoryRepository(repository: AppRepository): HistoryRepository = repository

    @Provides
    fun provideTeamRepository(repository: AppRepository): TeamRepository = repository

    @Provides
    fun provideSlotRepository(repository: AppRepository): SlotRepository = repository
}
