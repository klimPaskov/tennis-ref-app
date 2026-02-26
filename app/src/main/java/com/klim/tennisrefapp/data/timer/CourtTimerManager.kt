package com.klim.tennisrefapp.data.timer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.klim.tennisrefapp.domain.model.MatchEventKind
import com.klim.tennisrefapp.domain.repository.EventLogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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

private const val CHANNEL_ID = "court_timer_channel"
private const val CHANNEL_NAME = "Court Timer"
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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val state = MutableStateFlow<Map<Long, CourtTimerState>>(emptyMap())
    private val timerJobs = mutableMapOf<Long, Job>()

    fun observeTimer(courtId: Long): Flow<CourtTimerState?> = state.map { it[courtId] }

    fun startOrRestart(
        courtId: Long,
        matchId: Long,
        courtName: String,
        timerType: CourtTimerType,
    ) {
        stop(courtId)
        ensureChannel()

        val totalDuration = timerType.durationMillis
        val startedAt = System.currentTimeMillis()
        logTimerEvent(courtId, matchId, timerType, "START")

        val job = scope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - startedAt
                val remaining = max(0L, totalDuration - elapsed)

                state.value = state.value.toMutableMap().also { map ->
                    map[courtId] = CourtTimerState(
                        courtId = courtId,
                        matchId = matchId,
                        courtName = courtName,
                        timerType = timerType,
                        remainingMillis = remaining,
                        isRunning = remaining > 0,
                    )
                }

                if (remaining <= 0L) {
                    onFinished(courtId, matchId, courtName, timerType)
                    break
                }

                delay(250L)
            }
        }

        timerJobs[courtId] = job
    }

    fun stop(courtId: Long) {
        val current = state.value[courtId]
        timerJobs.remove(courtId)?.cancel()
        state.value = state.value.toMutableMap().also { map -> map.remove(courtId) }
        if (current != null) {
            logTimerEvent(courtId, current.matchId, current.timerType, "STOP")
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
        manager.createNotificationChannel(channel)
    }

    private fun onFinished(
        courtId: Long,
        matchId: Long,
        courtName: String,
        timerType: CourtTimerType,
    ) {
        vibrate(if (timerType == CourtTimerType.WARMUP) 2 else 1)
        notify(courtId, courtName, "${timerType.label} ended")
        logTimerEvent(courtId, matchId, timerType, "DONE")
    }

    private fun notify(courtId: Long, courtName: String, milestoneLabel: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Court Timer")
            .setContentText("$courtName: $milestoneLabel")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(appContext).notify(10000 + courtId.toInt(), notification)
    }

    private fun vibrate(times: Int) {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) return
        val timings = LongArray(times * 2 + 1).also { array ->
            array[0] = 0L
            repeat(times) { index ->
                array[index * 2 + 1] = 150L
                array[index * 2 + 2] = if (index == times - 1) 0L else 120L
            }
        }
        val amplitudes = IntArray(timings.size) { i -> if (i % 2 == 1) 255 else 0 }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
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
}
