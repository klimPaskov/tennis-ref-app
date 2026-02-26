package com.klim.tennisrefapp.data.timer

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.klim.tennisrefapp.data.work.CourtTimerFinishedWorker
import com.klim.tennisrefapp.domain.model.MatchEventKind
import com.klim.tennisrefapp.domain.repository.EventLogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class CourtTimerType(
    val label: String,
    val durationMillis: Long,
) {
    WARMUP("Warm-up", 5 * 60 * 1000L),
    SIDE_SWITCH("Side switch", 90 * 1000L),
    SET_BREAK("Set break", 120 * 1000L),
    TOILET_BREAK("Toilet break", 3 * 60 * 1000L),
    OFF_COURT("Off-court", 5 * 60 * 1000L),
    MEDICAL_TIMEOUT("Medical timeout", 3 * 60 * 1000L),
    EQUIPMENT_FIX("Equipment fix", 2 * 60 * 1000L),
}

data class CourtTimerState(
    val courtId: Long,
    val matchId: Long,
    val courtName: String,
    val timerType: CourtTimerType,
    val remainingMillis: Long,
    val isRunning: Boolean,
)

@Singleton
class CourtTimerManager @Inject constructor(
    @ApplicationContext
    private val appContext: Context,
    private val eventLogRepository: EventLogRepository,
    private val workManager: WorkManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val state = MutableStateFlow<Map<Long, CourtTimerState>>(emptyMap())
    private val timerJobs = mutableMapOf<Long, Job>()

    init {
        CourtTimerAlerts.ensureChannel(appContext)
        restorePersistedTimers()
    }

    fun observeTimer(courtId: Long): Flow<CourtTimerState?> = state.map { it[courtId] }

    fun observeAllTimers(): Flow<Map<Long, CourtTimerState>> = state

    fun startOrRestart(
        courtId: Long,
        matchId: Long,
        courtName: String,
        timerType: CourtTimerType,
    ) {
        stop(courtId)
        CourtTimerAlerts.ensureChannel(appContext)
        val endAtMillis = System.currentTimeMillis() + timerType.durationMillis
        val timer = PersistedCourtTimer(
            courtId = courtId,
            matchId = matchId,
            courtName = courtName,
            timerType = timerType,
            endAtMillis = endAtMillis,
        )

        CourtTimerPersistence.save(appContext, timer)
        scheduleFinishWorker(timer)
        logTimerEvent(courtId, matchId, timerType, "START")
        launchTicker(timer)
    }

    fun stop(courtId: Long) {
        val current = state.value[courtId]
        timerJobs.remove(courtId)?.cancel()
        cancelFinishWorker(courtId)
        CourtTimerPersistence.remove(appContext, courtId)
        state.value = state.value.toMutableMap().also { map -> map.remove(courtId) }
        if (current != null && current.isRunning) {
            logTimerEvent(courtId, current.matchId, current.timerType, "STOP")
        }
    }

    private fun logTimerEvent(
        courtId: Long,
        matchId: Long,
        timerType: CourtTimerType,
        action: String,
    ) {
        scope.launch {
            eventLogRepository.logEvent(
                matchId = matchId,
                courtId = courtId,
                kind = MatchEventKind.TIMER,
                offenderPlayerId = null,
                message = "TIMER|${timerType.name}|$action",
            )
        }
    }

    private fun launchTicker(timer: PersistedCourtTimer) {
        timerJobs.remove(timer.courtId)?.cancel()
        val job = scope.launch {
            while (true) {
                val remaining = max(0L, timer.endAtMillis - System.currentTimeMillis())
                state.value = state.value.toMutableMap().also { map ->
                    map[timer.courtId] = CourtTimerState(
                        courtId = timer.courtId,
                        matchId = timer.matchId,
                        courtName = timer.courtName,
                        timerType = timer.timerType,
                        remainingMillis = remaining,
                        isRunning = remaining > 0L,
                    )
                }

                if (remaining <= 0L) {
                    timerJobs.remove(timer.courtId)
                    break
                }
                delay(250L)
            }
        }
        timerJobs[timer.courtId] = job
    }

    private fun scheduleFinishWorker(timer: PersistedCourtTimer) {
        val delay = max(0L, timer.endAtMillis - System.currentTimeMillis())
        val request = OneTimeWorkRequestBuilder<CourtTimerFinishedWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putLong(CourtTimerFinishedWorker.KEY_COURT_ID, timer.courtId)
                    .putLong(CourtTimerFinishedWorker.KEY_MATCH_ID, timer.matchId)
                    .putString(CourtTimerFinishedWorker.KEY_COURT_NAME, timer.courtName)
                    .putString(CourtTimerFinishedWorker.KEY_TIMER_TYPE, timer.timerType.name)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(
            CourtTimerFinishedWorker.workName(timer.courtId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun cancelFinishWorker(courtId: Long) {
        workManager.cancelUniqueWork(CourtTimerFinishedWorker.workName(courtId))
    }

    private fun restorePersistedTimers() {
        val now = System.currentTimeMillis()
        CourtTimerPersistence.loadAll(appContext).forEach { timer ->
            if (timer.endAtMillis <= now) {
                scheduleFinishWorker(timer.copy(endAtMillis = now))
                state.value = state.value.toMutableMap().also { map ->
                    map[timer.courtId] = CourtTimerState(
                        courtId = timer.courtId,
                        matchId = timer.matchId,
                        courtName = timer.courtName,
                        timerType = timer.timerType,
                        remainingMillis = 0L,
                        isRunning = false,
                    )
                }
            } else {
                launchTicker(timer)
            }
        }
    }
}
