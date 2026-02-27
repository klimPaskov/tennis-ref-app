package com.klim.tennisrefapp.data.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.klim.tennisrefapp.data.work.CourtTimerFinishedWorker

class CourtTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val courtId = intent.getLongExtra(EXTRA_COURT_ID, -1L)
        val matchId = intent.getLongExtra(EXTRA_MATCH_ID, -1L)
        val courtName = intent.getStringExtra(EXTRA_COURT_NAME).orEmpty()
        val timerTypeName = intent.getStringExtra(EXTRA_TIMER_TYPE).orEmpty()
        val expectedEndAtMillis = intent.getLongExtra(EXTRA_END_AT, -1L)
        val subjectPlayerId = intent.getLongExtra(EXTRA_SUBJECT_PLAYER_ID, -1L)
            .takeIf { it > 0L }
        val subjectPlayerName = intent.getStringExtra(EXTRA_SUBJECT_PLAYER_NAME)
            ?.trim()
            ?.ifBlank { null }
        val timerType = runCatching { CourtTimerType.valueOf(timerTypeName) }.getOrNull() ?: return
        if (courtId <= 0L || matchId <= 0L || courtName.isBlank()) return

        val persisted = CourtTimerPersistence.load(context, courtId) ?: return
        val timerMatches = persisted.matchId == matchId &&
            persisted.timerType == timerType &&
            (expectedEndAtMillis <= 0L || kotlin.math.abs(persisted.endAtMillis - expectedEndAtMillis) <= 1_500L)
        if (!timerMatches) return

        val now = System.currentTimeMillis()
        when (action) {
            ACTION_TIMER_WARNING -> {
                val remainingMillis = (persisted.endAtMillis - now).coerceAtLeast(0L)
                if (remainingMillis > 65_000L || remainingMillis < 0L) return
                if (timerType == CourtTimerType.WARMUP) {
                    CourtTimerAlerts.notifyWarning(
                        context = context,
                        courtId = courtId,
                        courtName = courtName,
                        timerType = timerType,
                        remainingLabel = "1 minute left",
                    )
                }
                return
            }

            ACTION_TIMER_FINISH -> {
                if (now + 1_500L < persisted.endAtMillis) return
                CourtTimerAlerts.cancelWarning(context, courtId)
                CourtTimerAlerts.notifyFinished(
                    context = context,
                    courtId = courtId,
                    courtName = courtName,
                    timerType = timerType,
                )
            }

            else -> return
        }

        val request = OneTimeWorkRequestBuilder<CourtTimerFinishedWorker>()
            .setInputData(
                Data.Builder()
                    .putLong(CourtTimerFinishedWorker.KEY_COURT_ID, courtId)
                    .putLong(CourtTimerFinishedWorker.KEY_MATCH_ID, matchId)
                    .putString(CourtTimerFinishedWorker.KEY_COURT_NAME, courtName)
                    .putString(CourtTimerFinishedWorker.KEY_TIMER_TYPE, timerType.name)
                    .putBoolean(CourtTimerFinishedWorker.KEY_SKIP_ALERT, true)
                    .putLong(CourtTimerFinishedWorker.KEY_END_AT, persisted.endAtMillis)
                    .putLong(
                        CourtTimerFinishedWorker.KEY_SUBJECT_PLAYER_ID,
                        subjectPlayerId ?: persisted.subjectPlayerId ?: -1L,
                    )
                    .putString(
                        CourtTimerFinishedWorker.KEY_SUBJECT_PLAYER_NAME,
                        subjectPlayerName ?: persisted.subjectPlayerName,
                    )
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CourtTimerFinishedWorker.workName(courtId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val ACTION_TIMER_FINISH = "com.klim.tennisrefapp.action.TIMER_FINISH"
        const val ACTION_TIMER_WARNING = "com.klim.tennisrefapp.action.TIMER_WARNING"
        const val EXTRA_COURT_ID = "extra_court_id"
        const val EXTRA_MATCH_ID = "extra_match_id"
        const val EXTRA_COURT_NAME = "extra_court_name"
        const val EXTRA_TIMER_TYPE = "extra_timer_type"
        const val EXTRA_END_AT = "extra_end_at"
        const val EXTRA_SUBJECT_PLAYER_ID = "extra_subject_player_id"
        const val EXTRA_SUBJECT_PLAYER_NAME = "extra_subject_player_name"
    }
}
