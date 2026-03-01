package com.klim.tennisrefapp.ui.history

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klim.tennisrefapp.domain.model.MatchEvent
import com.klim.tennisrefapp.domain.model.MatchEventKind

@Composable
fun MatchLogRoute(
    onBack: () -> Unit,
    viewModel: MatchLogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    MatchLogScreen(
        uiState = uiState,
        onBack = onBack,
        onEditEvent = viewModel::onEditEvent,
        onDeleteEvent = viewModel::onDeleteEvent,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchLogScreen(
    uiState: MatchLogUiState,
    onBack: () -> Unit,
    onEditEvent: (Long, String, String) -> Unit,
    onDeleteEvent: (Long) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val detail = uiState.detail
    var eventToEdit by remember { mutableStateOf<MatchEvent?>(null) }
    var eventToDelete by remember { mutableStateOf<MatchEvent?>(null) }

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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Row {
                                IconButton(onClick = { eventToEdit = event }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "Edit",
                                    )
                                }
                                IconButton(onClick = { eventToDelete = event }) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete",
                                    )
                                }
                            }
                        }
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

    val editing = eventToEdit
    if (editing != null) {
        var timeLabel by remember(editing.id) { mutableStateOf(formatTime(editing.timestamp)) }
        var messageText by remember(editing.id) { mutableStateOf(editing.message.orEmpty()) }
        AlertDialog(
            onDismissRequest = { eventToEdit = null },
            title = { Text("Edit History Entry") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = timeLabel,
                        onValueChange = { timeLabel = it },
                        singleLine = true,
                        label = { Text("Time (HH:mm)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        label = { Text("Message") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEditEvent(editing.id, timeLabel, messageText)
                        eventToEdit = null
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { eventToEdit = null }) { Text("Cancel") }
            },
        )
    }

    val deleting = eventToDelete
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { eventToDelete = null },
            title = { Text("Delete History Entry") },
            text = { Text("This entry will be permanently removed from match history.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteEvent(deleting.id)
                        eventToDelete = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { eventToDelete = null }) { Text("Cancel") }
            },
        )
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
            if (message.startsWith("SCORE_SNAPSHOT|")) return "Live Score Updated"
            if (message.startsWith("ARGUMENT|")) return "Score Argument Logged"
            val parts = message.split("|")
            val timerType = parts.getOrNull(1).orEmpty()
            val action = parts.getOrNull(2)?.uppercase().orEmpty()
            val timerLabel = timerType
                .lowercase()
                .replace('_', ' ')
                .replaceFirstChar { it.uppercase() }
            when (action) {
                "START" -> "$timerLabel Started"
                "STOP" -> "$timerLabel Stopped"
                "DONE" -> "$timerLabel Ended"
                else -> "Timer Event"
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
    if (message.startsWith("SCORE_SNAPSHOT|")) {
        val payload = message.substringAfter("SCORE_SNAPSHOT|")
        val score = payload.substringBefore("|").trim()
        val left = payload.split("|").firstOrNull { it.startsWith("left=") }?.substringAfter("=").orEmpty().trim()
        val right = payload.split("|").firstOrNull { it.startsWith("right=") }?.substringAfter("=").orEmpty().trim()
        val perspective = if (left.isNotBlank() || right.isNotBlank()) {
            " (Left: ${left.ifBlank { "?" }}, Right: ${right.ifBlank { "?" }})"
        } else {
            ""
        }
        return score + perspective
    }
    if (message.startsWith("ARGUMENT|")) {
        return formatArgumentMessage(message)
    }
    if (!message.startsWith("TIMER|")) return message
    val parts = message.split("|")
    val timerType = parts.getOrNull(1)?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Timer"
    val action = parts.getOrNull(2)?.lowercase() ?: ""
    val playerName = parts
        .firstOrNull { token -> token.startsWith("playerName=") }
        ?.substringAfter("=")
        ?.trim()
        ?.ifBlank { null }
    return when (action) {
        "start" -> if (playerName != null) "$timerType started for $playerName" else "$timerType started"
        "stop" -> if (playerName != null) "$timerType stopped for $playerName" else "$timerType stopped"
        "done" -> if (playerName != null) "$timerType ended for $playerName" else "$timerType ended"
        else -> "$timerType $action".trim()
    }
}

private fun formatArgumentMessage(message: String): String {
    if (!message.startsWith("ARGUMENT|")) return message
    val parts = message.split("|")
    val left = parts.firstOrNull { it.startsWith("left=") }?.substringAfter("=").orEmpty().ifBlank { null }
    val right = parts.firstOrNull { it.startsWith("right=") }?.substringAfter("=").orEmpty().ifBlank { null }
    val server = parts.firstOrNull { it.startsWith("server=") }?.substringAfter("=").orEmpty().ifBlank { null }
    val result = parts.firstOrNull { it.startsWith("result=") }?.substringAfter("=").orEmpty().ifBlank { null }
    return buildList {
        if (left != null) add("Left claim: $left")
        if (right != null) add("Right claim: $right")
        if (server != null) add("Current server: $server")
        if (result != null) add("Decision: $result")
    }.joinToString(" • ")
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
