package com.klim.tennisrefapp.ui.history

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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klim.tennisrefapp.domain.model.MatchEventKind

@Composable
fun MatchLogRoute(
    onBack: () -> Unit,
    viewModel: MatchLogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MatchLogScreen(uiState = uiState, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchLogScreen(
    uiState: MatchLogUiState,
    onBack: () -> Unit,
) {
    val detail = uiState.detail
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Match Log") },
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
    ) { padding ->
        if (detail == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            ) {
                Text("No match history.")
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(detail.matchLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    detail.winnerLabel?.takeIf { it.isNotBlank() }?.let { winner ->
                        Text(
                            text = "Winner: $winner",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    detail.courtName?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    detail.scoreLabel?.let { Text("Score: $it", style = MaterialTheme.typography.bodyMedium) }
                    matchDurationLabel(detail.startedAtMillis, detail.endedAtMillis)?.let { duration ->
                        Text(duration, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(detail.events, key = { it.id }) { event ->
                val title = eventTitle(event.kind, event.message)
                val details = eventDetails(event.kind, event.message, title)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = formatTime(event.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!details.isNullOrBlank()) {
                            Text(details, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun eventTitle(kind: MatchEventKind, message: String?): String {
    return when (kind) {
        MatchEventKind.MATCH_STARTED -> "Match Started"
        MatchEventKind.MATCH_ENDED -> "Match Ended"
        MatchEventKind.VIOLATION -> "Violation"
        MatchEventKind.TIMER -> {
            if (message == null) return "Timer"
            val parts = message.split("|")
            val timerType = parts.getOrNull(1)?.uppercase().orEmpty()
            val action = parts.getOrNull(2)?.uppercase().orEmpty()
            when {
                timerType == "WARMUP" && action == "START" -> "Warm-up Started"
                timerType == "WARMUP" && action == "DONE" -> "Warm-up Ended"
                else -> "Timer"
            }
        }
    }
}

private fun eventDetails(kind: MatchEventKind, message: String?, title: String): String? {
    if (message.isNullOrBlank()) return null
    return when (kind) {
        MatchEventKind.MATCH_STARTED,
        MatchEventKind.MATCH_ENDED,
        -> null

        MatchEventKind.TIMER -> {
            val details = formatTimerMessage(message)
            if (normalizeForComparison(details) == normalizeForComparison(title)) null else details
        }
        MatchEventKind.VIOLATION -> formatViolationMessage(message)
    }
}

private fun normalizeForComparison(value: String): String {
    return value.lowercase().replace(Regex("[^a-z0-9]"), "")
}

private fun formatTimerMessage(message: String): String {
    if (!message.startsWith("TIMER|")) return message
    val parts = message.split("|")
    val timerType = parts.getOrNull(1)?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Timer"
    val action = parts.getOrNull(2)?.lowercase() ?: ""
    return when (action) {
        "start" -> "$timerType started"
        "stop" -> "$timerType stopped"
        "done" -> "$timerType finished"
        else -> "$timerType $action".trim()
    }
}

private fun formatViolationMessage(message: String): String {
    if (!message.startsWith("VIOLATION|")) return message
    val parts = message.split("|")
    val name = parts.getOrNull(2).orEmpty()
    val offence = parts.firstOrNull { it.startsWith("offence=") }?.substringAfter("=")
    val applied = parts.firstOrNull { it.startsWith("applied=") }?.substringAfter("=")
    val next = parts.firstOrNull { it.startsWith("next=") }?.substringAfter("=")
    val summary = buildList {
        if (offence != null && applied != null) add("Offence $offence: $applied")
        if (next != null) add("Next: $next")
    }.joinToString(" • ")
    return if (summary.isBlank()) name else "$name • $summary"
}

private fun matchDurationLabel(startedAtMillis: Long?, endedAtMillis: Long?): String? {
    val started = startedAtMillis ?: return null
    val ended = endedAtMillis ?: return null
    val minutes = ((ended - started).coerceAtLeast(0L) / 60_000L).toInt()
    return "Duration ${minutes}m"
}
