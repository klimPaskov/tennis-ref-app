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
        if (intent.action != ACTION_TIMER_FINISH) return
        val courtId = intent.getLongExtra(EXTRA_COURT_ID, -1L)
        val matchId = intent.getLongExtra(EXTRA_MATCH_ID, -1L)
        val courtName = intent.getStringExtra(EXTRA_COURT_NAME).orEmpty()
        val timerTypeName = intent.getStringExtra(EXTRA_TIMER_TYPE).orEmpty()
        val timerType = runCatching { CourtTimerType.valueOf(timerTypeName) }.getOrNull() ?: return
        if (courtId <= 0L || matchId <= 0L || courtName.isBlank()) return

        CourtTimerAlerts.notifyFinished(
            context = context,
            courtId = courtId,
            courtName = courtName,
            timerType = timerType,
        )

        val request = OneTimeWorkRequestBuilder<CourtTimerFinishedWorker>()
            .setInputData(
                Data.Builder()
                    .putLong(CourtTimerFinishedWorker.KEY_COURT_ID, courtId)
                    .putLong(CourtTimerFinishedWorker.KEY_MATCH_ID, matchId)
                    .putString(CourtTimerFinishedWorker.KEY_COURT_NAME, courtName)
                    .putString(CourtTimerFinishedWorker.KEY_TIMER_TYPE, timerType.name)
                    .putBoolean(CourtTimerFinishedWorker.KEY_SKIP_ALERT, true)
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
        const val EXTRA_COURT_ID = "extra_court_id"
        const val EXTRA_MATCH_ID = "extra_match_id"
        const val EXTRA_COURT_NAME = "extra_court_name"
        const val EXTRA_TIMER_TYPE = "extra_timer_type"
    }
}
