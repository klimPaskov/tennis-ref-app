package com.klim.tennisrefapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.klim.tennisrefapp.ui.navigation.TennisRefNavGraph
import com.klim.tennisrefapp.ui.theme.TennisRefTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var launchCourtId by mutableStateOf<Long?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

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
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchCourtId = extractCourtId(intent)
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun extractCourtId(intent: Intent?): Long? {
        val source = intent ?: return null
        val courtId = source.getLongExtra(EXTRA_OPEN_COURT_ID, -1L)
        return courtId.takeIf { it > 0L }
    }

    companion object {
        const val ACTION_OPEN_COURT = "com.klim.tennisrefapp.action.OPEN_COURT"
        const val EXTRA_OPEN_COURT_ID = "extra_open_court_id"
    }
}
