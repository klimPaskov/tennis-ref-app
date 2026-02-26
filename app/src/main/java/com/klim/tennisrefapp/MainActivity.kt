package com.klim.tennisrefapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
    ) { granted ->
        notificationPrompt = if (granted) NotificationPrompt.NONE else nextNotificationPrompt()
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
        if (!isNotificationGranted()) {
            notificationPrompt = nextNotificationPrompt()
        } else {
            notificationPrompt = NotificationPrompt.NONE
        }
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (!isNotificationGranted()) {
                notificationPrompt = NotificationPrompt.SETTINGS
            }
            return
        }
        if (isNotificationGranted()) return
        if (!wasNotificationPermissionRequested()) {
            launchNotificationPermissionRequest()
            return
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return NotificationPrompt.NONE
        if (isNotificationGranted()) return NotificationPrompt.NONE
        return if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            NotificationPrompt.REQUEST
        } else {
            NotificationPrompt.SETTINGS
        }
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
}

@androidx.compose.runtime.Composable
private fun NotificationPermissionDialog(
    prompt: NotificationPrompt,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
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
    }
}
