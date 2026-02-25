package com.klim.tennisrefapp.di

import android.content.Context
import androidx.room.Room
import com.klim.tennisrefapp.data.local.AppDatabase
import com.klim.tennisrefapp.data.local.dao.CourtDao
import com.klim.tennisrefapp.data.local.dao.EventLogDao
import com.klim.tennisrefapp.data.local.dao.MatchDao
import com.klim.tennisrefapp.data.local.dao.PlayerDao
import com.klim.tennisrefapp.data.local.dao.SlotDao
import com.klim.tennisrefapp.data.local.dao.TeamDao
import com.klim.tennisrefapp.data.local.dao.TeamPlayerDao
import com.klim.tennisrefapp.data.local.dao.TournamentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "tennis_ref.db",
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideTournamentDao(db: AppDatabase): TournamentDao = db.tournamentDao()

    @Provides
    fun provideCourtDao(db: AppDatabase): CourtDao = db.courtDao()

    @Provides
    fun providePlayerDao(db: AppDatabase): PlayerDao = db.playerDao()

    @Provides
    fun provideTeamDao(db: AppDatabase): TeamDao = db.teamDao()

    @Provides
    fun provideTeamPlayerDao(db: AppDatabase): TeamPlayerDao = db.teamPlayerDao()

    @Provides
    fun provideMatchDao(db: AppDatabase): MatchDao = db.matchDao()

    @Provides
    fun provideSlotDao(db: AppDatabase): SlotDao = db.slotDao()

    @Provides
    fun provideEventLogDao(db: AppDatabase): EventLogDao = db.eventLogDao()
}
