package com.klim.tennisrefapp.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.klim.tennisrefapp.data.timer.CourtTimerAlerts
import com.klim.tennisrefapp.data.timer.CourtTimerPersistence
import com.klim.tennisrefapp.data.timer.CourtTimerType
import com.klim.tennisrefapp.domain.model.MatchEventKind
import com.klim.tennisrefapp.domain.repository.EventLogRepository
import com.klim.tennisrefapp.domain.repository.MatchRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CourtTimerFinishedWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val eventLogRepository: EventLogRepository,
    private val matchRepository: MatchRepository,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val courtId = inputData.getLong(KEY_COURT_ID, -1L)
        val matchId = inputData.getLong(KEY_MATCH_ID, -1L)
        val courtName = inputData.getString(KEY_COURT_NAME).orEmpty()
        val timerTypeName = inputData.getString(KEY_TIMER_TYPE).orEmpty()
        val skipAlert = inputData.getBoolean(KEY_SKIP_ALERT, false)
        val expectedEndAtMillis = inputData.getLong(KEY_END_AT, -1L)

        if (courtId <= 0L || matchId <= 0L || courtName.isBlank()) {
            return Result.success()
        }

        val timerType = runCatching { CourtTimerType.valueOf(timerTypeName) }.getOrNull()
            ?: return Result.success()

        val persisted = CourtTimerPersistence.consumeIfMatches(
            context = applicationContext,
            courtId = courtId,
            matchId = matchId,
            timerType = timerType,
            expectedEndAtMillis = expectedEndAtMillis,
        ) ?: return Result.success()

        val now = System.currentTimeMillis()
        if (now + 1_500L < persisted.endAtMillis) {
            return Result.success()
        }

        CourtTimerAlerts.cancelWarning(applicationContext, courtId)
        if (!skipAlert) {
            CourtTimerAlerts.notifyFinished(
                context = applicationContext,
                courtId = courtId,
                courtName = courtName,
                timerType = timerType,
            )
        }

        if (timerType != CourtTimerType.WARMUP) {
            eventLogRepository.logEvent(
                matchId = matchId,
                courtId = courtId,
                kind = MatchEventKind.TIMER,
                offenderPlayerId = null,
                message = "TIMER|${timerType.name}|DONE",
            )
        }
        if (timerType == CourtTimerType.WARMUP) {
            val startResult = matchRepository.autoStartMatchFromWarmup(matchId)
            if (startResult != com.klim.tennisrefapp.domain.repository.StartMatchResult.Success) {
                CourtWarmupPendingReminderWorker.scheduleNext(
                    context = applicationContext,
                    courtId = courtId,
                    matchId = matchId,
                    courtName = courtName,
                )
            } else {
                CourtTimerAlerts.cancelWarmupPending(applicationContext, courtId)
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_COURT_ID = "court_id"
        const val KEY_MATCH_ID = "match_id"
        const val KEY_COURT_NAME = "court_name"
        const val KEY_TIMER_TYPE = "timer_type"
        const val KEY_SKIP_ALERT = "skip_alert"
        const val KEY_END_AT = "end_at"

        fun workName(courtId: Long): String = "court_timer_finish_$courtId"
    }
}
