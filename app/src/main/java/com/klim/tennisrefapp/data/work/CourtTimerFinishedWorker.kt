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

        if (courtId <= 0L || matchId <= 0L || courtName.isBlank()) {
            return Result.success()
        }

        val timerType = runCatching { CourtTimerType.valueOf(timerTypeName) }.getOrNull()
            ?: return Result.success()

        CourtTimerAlerts.notifyFinished(
            context = applicationContext,
            courtId = courtId,
            courtName = courtName,
            timerType = timerType,
        )
        eventLogRepository.logEvent(
            matchId = matchId,
            courtId = courtId,
            kind = MatchEventKind.TIMER,
            offenderPlayerId = null,
            message = "TIMER|${timerType.name}|DONE",
        )
        if (timerType == CourtTimerType.WARMUP) {
            matchRepository.autoStartMatchFromWarmup(matchId)
        }
        CourtTimerPersistence.remove(applicationContext, courtId)
        return Result.success()
    }

    companion object {
        const val KEY_COURT_ID = "court_id"
        const val KEY_MATCH_ID = "match_id"
        const val KEY_COURT_NAME = "court_name"
        const val KEY_TIMER_TYPE = "timer_type"

        fun workName(courtId: Long): String = "court_timer_finish_$courtId"
    }
}
