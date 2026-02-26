package com.klim.tennisrefapp.data.timer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.klim.tennisrefapp.MainActivity

internal const val COURT_TIMER_CHANNEL_ID = "court_timer_channel_v2"
internal const val COURT_TIMER_CHANNEL_NAME = "Court Timer"
private const val COURT_TIMER_NOTIFICATION_BASE_ID = 10000

internal object CourtTimerAlerts {
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            COURT_TIMER_CHANNEL_ID,
            COURT_TIMER_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Match timer alerts"
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 180L, 120L, 180L)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyFinished(
        context: Context,
        courtId: Long,
        courtName: String,
        timerType: CourtTimerType,
    ) {
        ensureChannel(context)
        vibrate(context, if (timerType == CourtTimerType.WARMUP) 2 else 1)
        notify(context, courtId, courtName, timerType)
    }

    private fun notify(
        context: Context,
        courtId: Long,
        courtName: String,
        timerType: CourtTimerType,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val pattern = if (timerType == CourtTimerType.WARMUP) {
            longArrayOf(0L, 220L, 140L, 220L)
        } else {
            longArrayOf(0L, 220L)
        }
        val openCourtIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_COURT
            putExtra(MainActivity.EXTRA_OPEN_COURT_ID, courtId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openCourtPendingIntent = PendingIntent.getActivity(
            context,
            courtId.toInt(),
            openCourtIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, COURT_TIMER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$courtName timer finished")
            .setContentText(timerType.label)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(pattern)
            .setContentIntent(openCourtPendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(COURT_TIMER_NOTIFICATION_BASE_ID + courtId.toInt(), notification)
    }

    private fun vibrate(context: Context, pulses: Int) {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return

        val timings = LongArray(pulses * 2 + 1).also { array ->
            array[0] = 0L
            repeat(pulses) { index ->
                array[index * 2 + 1] = 180L
                array[index * 2 + 2] = if (index == pulses - 1) 0L else 120L
            }
        }
        val amplitudes = IntArray(timings.size) { index -> if (index % 2 == 1) 255 else 0 }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }
}
