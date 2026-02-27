package com.klim.tennisrefapp

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.klim.tennisrefapp.ui.navigation.TennisRefNavGraph
import com.klim.tennisrefapp.ui.theme.TennisRefTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var launchCourtId by mutableStateOf<Long?>(null)
    private var notificationPrompt by mutableStateOf(NotificationPrompt.NONE)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        notificationPrompt = nextNotificationPrompt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchCourtId = extractCourtId(intent)
        requestNotificationsIfNeeded()
        setContent {
            TennisRefTheme {
                TennisRefNavGraph(
                    launchCourtId = launchCourtId,
                    onLaunchCourtConsumed = { launchCourtId = null },
                )
                NotificationPermissionDialog(
                    prompt = notificationPrompt,
                    onRequest = { launchNotificationPermissionRequest() },
                    onOpenSettings = { openAppNotificationSettings() },
                    onOpenExactAlarmSettings = { openExactAlarmSettings() },
                    onOpenBatteryOptimizationSettings = { openBatteryOptimizationSettings() },
                    onDismiss = { notificationPrompt = NotificationPrompt.NONE },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchCourtId = extractCourtId(intent)
    }

    override fun onResume() {
        super.onResume()
        notificationPrompt = nextNotificationPrompt()
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !isNotificationGranted()
        ) {
            if (!wasNotificationPermissionRequested()) {
                launchNotificationPermissionRequest()
                return
            }
        }
        notificationPrompt = nextNotificationPrompt()
    }

    private fun launchNotificationPermissionRequest() {
        markNotificationPermissionRequested()
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun nextNotificationPrompt(): NotificationPrompt {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            return NotificationPrompt.SETTINGS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationGranted()) {
            return if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                NotificationPrompt.REQUEST
            } else {
                NotificationPrompt.SETTINGS
            }
        }
        if (needsExactAlarmPermissionPrompt()) return NotificationPrompt.EXACT_ALARM
        if (needsBatteryOptimizationPrompt()) return NotificationPrompt.BATTERY_OPTIMIZATION
        return NotificationPrompt.NONE
    }

    private fun isNotificationGranted(): Boolean {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun wasNotificationPermissionRequested(): Boolean {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(KEY_NOTIFICATIONS_REQUESTED, false)
    }

    private fun markNotificationPermissionRequested() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFICATIONS_REQUESTED, true)
            .apply()
    }

    private fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        val opened = runCatching {
            startActivity(intent)
            true
        }.getOrDefault(false)
        if (opened) return
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ),
        )
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            }
            val opened = runCatching {
                startActivity(intent)
                true
            }.getOrDefault(false)
            if (opened) return
        }
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ),
        )
    }

    private fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            val opened = runCatching {
                startActivity(intent)
                true
            }.getOrDefault(false)
            if (opened) return
        }
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ),
        )
    }

    private fun needsExactAlarmPermissionPrompt(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val alarmManager = getSystemService(AlarmManager::class.java) ?: return false
        return !alarmManager.canScheduleExactAlarms()
    }

    private fun needsBatteryOptimizationPrompt(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val powerManager = getSystemService(PowerManager::class.java) ?: return false
        return !powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun extractCourtId(intent: Intent?): Long? {
        val source = intent ?: return null
        val courtId = source.getLongExtra(EXTRA_OPEN_COURT_ID, -1L)
        return courtId.takeIf { it > 0L }
    }

    companion object {
        const val ACTION_OPEN_COURT = "com.klim.tennisrefapp.action.OPEN_COURT"
        const val EXTRA_OPEN_COURT_ID = "extra_open_court_id"
        private const val PREFS_NAME = "permissions_state"
        private const val KEY_NOTIFICATIONS_REQUESTED = "notifications_requested"
    }
}

private enum class NotificationPrompt {
    NONE,
    REQUEST,
    SETTINGS,
    EXACT_ALARM,
    BATTERY_OPTIMIZATION,
}

@androidx.compose.runtime.Composable
private fun NotificationPermissionDialog(
    prompt: NotificationPrompt,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (prompt) {
        NotificationPrompt.NONE -> Unit
        NotificationPrompt.REQUEST -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Allow Notifications") },
                text = { Text("Enable notifications for timer alerts and vibration when a court timer ends.") },
                confirmButton = {
                    Button(onClick = onRequest) {
                        Text("Allow")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Later")
                    }
                },
            )
        }
        NotificationPrompt.SETTINGS -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Notifications Disabled") },
                text = { Text("Timer alerts are off. Open app notification settings to enable alerts and vibration.") },
                confirmButton = {
                    Button(onClick = onOpenSettings) {
                        Text("Open Settings")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Later")
                    }
                },
            )
        }
        NotificationPrompt.EXACT_ALARM -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Allow Exact Alarms") },
                text = { Text("Enable exact alarms so court timer notifications and vibration trigger on time.") },
                confirmButton = {
                    Button(onClick = onOpenExactAlarmSettings) {
                        Text("Open Settings")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Later")
                    }
                },
            )
        }
        NotificationPrompt.BATTERY_OPTIMIZATION -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Allow Unrestricted Battery") },
                text = { Text("Allow unrestricted battery usage so timer alerts trigger while the phone is locked or in the background.") },
                confirmButton = {
                    Button(onClick = onOpenBatteryOptimizationSettings) {
                        Text("Open Settings")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Later")
                    }
                },
            )
        }
    }
}
