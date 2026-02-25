package com.klim.tennisrefapp.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class NotBeforeReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val courtName = inputData.getString(KEY_COURT_NAME) ?: "Unassigned"
        val matchId = inputData.getLong(KEY_MATCH_ID, -1L)
        Log.d(TAG, "Not-before reminder fired for match=$matchId court=$courtName")
        return Result.success()
    }

    companion object {
        const val WORK_PREFIX = "not_before_reminder_"
        const val KEY_MATCH_ID = "match_id"
        const val KEY_COURT_NAME = "court_name"
        private const val TAG = "NotBeforeReminder"
    }
}
