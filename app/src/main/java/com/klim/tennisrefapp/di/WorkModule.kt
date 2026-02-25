package com.klim.tennisrefapp.di

import android.content.Context
import androidx.work.WorkManager
import com.klim.tennisrefapp.data.work.WorkManagerReminderScheduler
import com.klim.tennisrefapp.domain.repository.ReminderScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkModule {
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideReminderScheduler(scheduler: WorkManagerReminderScheduler): ReminderScheduler = scheduler
}
