package com.klim.tennisrefapp.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.klim.tennisrefapp.data.timer.CourtTimerAlerts
import com.klim.tennisrefapp.data.timer.CourtTimerPersistence
import com.klim.tennisrefapp.data.timer.CourtTimerType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.math.abs

@HiltWorker
class CourtTimerWarningWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val courtId = inputData.getLong(KEY_COURT_ID, -1L)
        val matchId = inputData.getLong(KEY_MATCH_ID, -1L)
        val courtName = inputData.getString(KEY_COURT_NAME).orEmpty()
        val timerTypeName = inputData.getString(KEY_TIMER_TYPE).orEmpty()
        val expectedEndAtMillis = inputData.getLong(KEY_END_AT, -1L)

        if (courtId <= 0L || matchId <= 0L || courtName.isBlank()) return Result.success()
        val timerType = runCatching { CourtTimerType.valueOf(timerTypeName) }.getOrNull() ?: return Result.success()
        if (timerType != CourtTimerType.WARMUP) return Result.success()

        val persisted = CourtTimerPersistence.load(applicationContext, courtId) ?: return Result.success()
        val timerMatches = persisted.matchId == matchId &&
            persisted.timerType == timerType &&
            (expectedEndAtMillis <= 0L || abs(persisted.endAtMillis - expectedEndAtMillis) <= 1_500L)
        if (!timerMatches) return Result.success()

        val remainingMillis = (persisted.endAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        if (remainingMillis in 1_000L..65_000L) {
            CourtTimerAlerts.notifyWarning(
                context = applicationContext,
                courtId = courtId,
                courtName = courtName,
                timerType = timerType,
                remainingLabel = "1 minute left",
            )
        }
        return Result.success()
    }

    companion object {
        const val KEY_COURT_ID = "court_id"
        const val KEY_MATCH_ID = "match_id"
        const val KEY_COURT_NAME = "court_name"
        const val KEY_TIMER_TYPE = "timer_type"
        const val KEY_END_AT = "end_at"

        fun workName(courtId: Long): String = "court_timer_warning_$courtId"
    }
}
