package com.klim.tennisrefapp.ui.players

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klim.tennisrefapp.common.CountryFlags

@Composable
fun PlayerDetailRoute(
    onBack: () -> Unit,
    viewModel: PlayerDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    PlayerDetailScreen(
        uiState = uiState,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerDetailScreen(
    uiState: PlayerDetailUiState,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val detail = uiState.detail
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Player Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        if (detail == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            ) {
                Text("Player not found.")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                val flag = countryCodeToFlagEmoji(detail.countryCode)
                Text(
                    text = if (flag != null) "$flag ${detail.name}" else detail.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                Text("Next Match", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val next = detail.nextMatch
                if (next == null) {
                    Text("No upcoming match")
                } else {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(next.matchLabel)
                            Text("${next.courtName ?: "Unassigned"} · ${next.status.name}")
                            next.notBeforeMillis?.let { notBefore ->
                                Text("Not before ${formatTime(notBefore)}")
                            }
                        }
                    }
                }
            }
            item {
                Text("Completed Matches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(detail.completedMatches, key = { it.matchId }) { match ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(match.matchLabel)
                        Text(match.courtName ?: "Unassigned")
                        if (!match.scoreLabel.isNullOrBlank()) {
                            Text("Score: ${match.scoreLabel}")
                        }
                        matchDurationLabel(match.startedAtMillis, match.endedAtMillis)?.let { duration ->
                            Text(duration)
                        }
                        match.endedAtMillis?.let { endedAt ->
                            Text("Ended ${formatTime(endedAt)}")
                        }
                    }
                }
            }
        }
    }
}

private fun matchDurationLabel(startedAtMillis: Long?, endedAtMillis: Long?): String? {
    val started = startedAtMillis ?: return null
    val ended = endedAtMillis ?: return null
    val durationMs = (ended - started).coerceAtLeast(0L)
    val totalMinutes = durationMs / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "Duration ${hours}h ${minutes}m" else "Duration ${minutes}m"
}

private fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun countryCodeToFlagEmoji(countryCode: String?): String? {
    return CountryFlags.toFlagEmoji(countryCode)
}
