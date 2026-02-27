package com.klim.tennisrefapp.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.klim.tennisrefapp.data.local.dao.MatchDao
import com.klim.tennisrefapp.data.local.entity.MatchStatus
import com.klim.tennisrefapp.data.settings.AppSettingsStore
import com.klim.tennisrefapp.data.timer.CourtTimerAlerts
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class CourtWarmupPendingReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val matchDao: MatchDao,
    private val appSettingsStore: AppSettingsStore,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val courtId = inputData.getLong(KEY_COURT_ID, -1L)
        val matchId = inputData.getLong(KEY_MATCH_ID, -1L)
        val courtName = inputData.getString(KEY_COURT_NAME).orEmpty()
        if (courtId <= 0L || matchId <= 0L || courtName.isBlank()) return Result.success()
        if (!appSettingsStore.currentSettings().warmupPendingReminder) {
            CourtTimerAlerts.cancelWarmupPending(applicationContext, courtId)
            return Result.success()
        }

        val match = matchDao.getById(matchId) ?: return Result.success()
        val stillPending = match.courtId == courtId &&
            match.status == MatchStatus.SCHEDULED &&
            match.startedAt == null &&
            match.endedAt == null
        if (!stillPending) return Result.success()

        CourtTimerAlerts.notifyWarmupPending(applicationContext, courtId, courtName)
        scheduleNext(applicationContext, courtId, matchId, courtName)
        return Result.success()
    }

    companion object {
        const val KEY_COURT_ID = "court_id"
        const val KEY_MATCH_ID = "match_id"
        const val KEY_COURT_NAME = "court_name"
        private const val REMINDER_INTERVAL_MINUTES = 2L

        fun workName(courtId: Long): String = "court_warmup_pending_reminder_$courtId"

        fun scheduleNext(
            context: Context,
            courtId: Long,
            matchId: Long,
            courtName: String,
        ) {
            val request = OneTimeWorkRequestBuilder<CourtWarmupPendingReminderWorker>()
                .setInitialDelay(REMINDER_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setInputData(
                    Data.Builder()
                        .putLong(KEY_COURT_ID, courtId)
                        .putLong(KEY_MATCH_ID, matchId)
                        .putString(KEY_COURT_NAME, courtName)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(courtId),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
