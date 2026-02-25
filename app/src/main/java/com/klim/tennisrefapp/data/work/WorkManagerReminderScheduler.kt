package com.klim.tennisrefapp.data.work

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.klim.tennisrefapp.domain.repository.ReminderScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    private val workManager: WorkManager,
) : ReminderScheduler {
    override fun scheduleNotBeforeReminder(matchId: Long, triggerAtMillis: Long, courtName: String?) {
        val delay = max(0L, triggerAtMillis - System.currentTimeMillis())
        val request = OneTimeWorkRequestBuilder<NotBeforeReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putLong(NotBeforeReminderWorker.KEY_MATCH_ID, matchId)
                    .putString(NotBeforeReminderWorker.KEY_COURT_NAME, courtName ?: "Unassigned")
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(
            NotBeforeReminderWorker.WORK_PREFIX + matchId,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun cancelNotBeforeReminder(matchId: Long) {
        workManager.cancelUniqueWork(NotBeforeReminderWorker.WORK_PREFIX + matchId)
    }
}
