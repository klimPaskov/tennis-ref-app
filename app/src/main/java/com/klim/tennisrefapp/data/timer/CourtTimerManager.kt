package com.klim.tennisrefapp.data.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.klim.tennisrefapp.data.work.CourtTimerFinishedWorker
import com.klim.tennisrefapp.data.work.CourtTimerWarningWorker
import com.klim.tennisrefapp.data.work.CourtWarmupPendingReminderWorker
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
        val running = state.value[courtId]
        if (running != null &&
            running.isRunning &&
            running.matchId == matchId &&
            running.timerType == timerType
        ) {
            return
        }
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
        scheduleFinishAlarm(timer)
        scheduleWarningAlarm(timer)
        scheduleWarningWorker(timer)
        logTimerEvent(courtId, matchId, timerType, "START")
        launchTicker(timer)
    }

    fun stop(courtId: Long) {
        val current = state.value[courtId]
        timerJobs.remove(courtId)?.cancel()
        cancelFinishWorker(courtId)
        cancelFinishAlarm(courtId)
        cancelWarningAlarm(courtId)
        cancelWarningWorker(courtId)
        cancelWarmupPendingReminder(courtId)
        CourtTimerAlerts.cancelWarning(appContext, courtId)
        CourtTimerAlerts.cancelWarmupPending(appContext, courtId)
        CourtTimerPersistence.remove(appContext, courtId)
        state.value = state.value.toMutableMap().also { map -> map.remove(courtId) }
        if (current != null && current.isRunning && current.timerType != CourtTimerType.WARMUP) {
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

    private fun scheduleFinishWorker(timer: PersistedCourtTimer, skipAlert: Boolean = false) {
        val delay = max(0L, timer.endAtMillis - System.currentTimeMillis())
        val request = OneTimeWorkRequestBuilder<CourtTimerFinishedWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putLong(CourtTimerFinishedWorker.KEY_COURT_ID, timer.courtId)
                    .putLong(CourtTimerFinishedWorker.KEY_MATCH_ID, timer.matchId)
                    .putString(CourtTimerFinishedWorker.KEY_COURT_NAME, timer.courtName)
                    .putString(CourtTimerFinishedWorker.KEY_TIMER_TYPE, timer.timerType.name)
                    .putBoolean(CourtTimerFinishedWorker.KEY_SKIP_ALERT, skipAlert)
                    .putLong(CourtTimerFinishedWorker.KEY_END_AT, timer.endAtMillis)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(
            CourtTimerFinishedWorker.workName(timer.courtId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun scheduleFinishAlarm(timer: PersistedCourtTimer) {
        val triggerAtMillis = timer.endAtMillis.coerceAtLeast(System.currentTimeMillis())
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = timerAlarmPendingIntent(
            action = CourtTimerAlarmReceiver.ACTION_TIMER_FINISH,
            courtId = timer.courtId,
            matchId = timer.matchId,
            courtName = timer.courtName,
            timerType = timer.timerType,
            endAtMillis = timer.endAtMillis,
            updateCurrent = true,
            warning = false,
        ) ?: return
        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms() ->
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                else ->
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }.getOrElse {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
    }

    private fun scheduleWarningAlarm(timer: PersistedCourtTimer) {
        if (timer.timerType != CourtTimerType.WARMUP) return
        val warningAtMillis = timer.endAtMillis - 60_000L
        if (warningAtMillis <= System.currentTimeMillis()) return
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = timerAlarmPendingIntent(
            action = CourtTimerAlarmReceiver.ACTION_TIMER_WARNING,
            courtId = timer.courtId,
            matchId = timer.matchId,
            courtName = timer.courtName,
            timerType = timer.timerType,
            endAtMillis = timer.endAtMillis,
            updateCurrent = true,
            warning = true,
        ) ?: return
        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms() ->
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, warningAtMillis, pendingIntent)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, warningAtMillis, pendingIntent)
                else ->
                    alarmManager.set(AlarmManager.RTC_WAKEUP, warningAtMillis, pendingIntent)
            }
        }.getOrElse {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, warningAtMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, warningAtMillis, pendingIntent)
            }
        }
    }

    private fun cancelFinishWorker(courtId: Long) {
        workManager.cancelUniqueWork(CourtTimerFinishedWorker.workName(courtId))
    }

    private fun scheduleWarningWorker(timer: PersistedCourtTimer) {
        if (timer.timerType != CourtTimerType.WARMUP) return
        val delay = (timer.endAtMillis - System.currentTimeMillis() - 60_000L).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<CourtTimerWarningWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putLong(CourtTimerWarningWorker.KEY_COURT_ID, timer.courtId)
                    .putLong(CourtTimerWarningWorker.KEY_MATCH_ID, timer.matchId)
                    .putString(CourtTimerWarningWorker.KEY_COURT_NAME, timer.courtName)
                    .putString(CourtTimerWarningWorker.KEY_TIMER_TYPE, timer.timerType.name)
                    .putLong(CourtTimerWarningWorker.KEY_END_AT, timer.endAtMillis)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(
            CourtTimerWarningWorker.workName(timer.courtId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun cancelWarningWorker(courtId: Long) {
        workManager.cancelUniqueWork(CourtTimerWarningWorker.workName(courtId))
    }

    private fun cancelWarmupPendingReminder(courtId: Long) {
        workManager.cancelUniqueWork(CourtWarmupPendingReminderWorker.workName(courtId))
    }

    private fun cancelFinishAlarm(courtId: Long) {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = timerAlarmPendingIntent(
                action = CourtTimerAlarmReceiver.ACTION_TIMER_FINISH,
                courtId = courtId,
                matchId = 0L,
                courtName = "",
                timerType = CourtTimerType.WARMUP,
                endAtMillis = 0L,
                updateCurrent = false,
                warning = false,
            ) ?: return
        alarmManager.cancel(pendingIntent)
    }

    private fun cancelWarningAlarm(courtId: Long) {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = timerAlarmPendingIntent(
            action = CourtTimerAlarmReceiver.ACTION_TIMER_WARNING,
            courtId = courtId,
            matchId = 0L,
            courtName = "",
            timerType = CourtTimerType.WARMUP,
            endAtMillis = 0L,
            updateCurrent = false,
            warning = true,
        ) ?: return
        alarmManager.cancel(pendingIntent)
    }

    private fun timerAlarmPendingIntent(
        action: String,
        courtId: Long,
        matchId: Long,
        courtName: String,
        timerType: CourtTimerType,
        endAtMillis: Long,
        updateCurrent: Boolean,
        warning: Boolean,
    ): PendingIntent? {
        val intent = Intent(appContext, CourtTimerAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(CourtTimerAlarmReceiver.EXTRA_COURT_ID, courtId)
            putExtra(CourtTimerAlarmReceiver.EXTRA_MATCH_ID, matchId)
            putExtra(CourtTimerAlarmReceiver.EXTRA_COURT_NAME, courtName)
            putExtra(CourtTimerAlarmReceiver.EXTRA_TIMER_TYPE, timerType.name)
            putExtra(CourtTimerAlarmReceiver.EXTRA_END_AT, endAtMillis)
        }
        val flags = (if (updateCurrent) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or
            PendingIntent.FLAG_IMMUTABLE
        val requestCodeOffset = if (warning) 50_000 else 10_000
        return PendingIntent.getBroadcast(
            appContext,
            courtId.toInt() + requestCodeOffset,
            intent,
            flags,
        )
    }

    private fun restorePersistedTimers() {
        val now = System.currentTimeMillis()
        CourtTimerPersistence.loadAll(appContext).forEach { timer ->
            if (timer.endAtMillis <= now) {
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
                scheduleFinishWorker(timer, skipAlert = true)
            } else {
                scheduleFinishAlarm(timer)
                scheduleWarningAlarm(timer)
                scheduleWarningWorker(timer)
                launchTicker(timer)
            }
        }
    }
}
