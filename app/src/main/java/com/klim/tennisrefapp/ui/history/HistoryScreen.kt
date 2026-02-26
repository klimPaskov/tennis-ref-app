package com.klim.tennisrefapp.ui.history

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klim.tennisrefapp.domain.model.CompletedCourtMatch
import kotlinx.coroutines.delay

@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    onOpenMatchLog: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(
        uiState = uiState,
        onBack = onBack,
        onOpenMatchLog = onOpenMatchLog,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(
    uiState: HistoryUiState,
    onBack: () -> Unit,
    onOpenMatchLog: (Long) -> Unit,
) {
    var clicksEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(250L)
        clicksEnabled = true
    }
    val grouped = uiState.matches.groupBy { it.courtName ?: "Unassigned" }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            grouped.forEach { (courtName, matches) ->
                item(key = "header_$courtName") {
                    Text(
                        text = courtName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(matches, key = { it.matchId }) { match ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = clicksEnabled, onClick = { onOpenMatchLog(match.matchId) }),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = matchLabelWithBoldWinner(match),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            val score = match.scoreLabel ?: "-"
                            Text(
                                text = "Score: $score",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            matchDurationLabel(match.startedAtMillis, match.endedAtMillis)?.let { minutes ->
                                Text(
                                    text = buildAnnotatedString {
                                        append("Duration: ")
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append(minutes)
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            match.endedAtMillis?.let { endedAt ->
                                Text(
                                    text = buildAnnotatedString {
                                        append("Ended: ")
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append(formatTime(endedAt))
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
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

private fun matchDurationLabel(startedAtMillis: Long?, endedAtMillis: Long?): String? {
    val started = startedAtMillis ?: return null
    val ended = endedAtMillis ?: return null
    val minutes = ((ended - started).coerceAtLeast(0L) / 60_000L).toInt()
    if (minutes == 35) return null
    return "${minutes}m"
}

private fun matchLabelWithBoldWinner(match: CompletedCourtMatch) = buildAnnotatedString {
    val winner = match.winnerLabel?.trim().orEmpty()
    val parts = match.matchLabel.split(" vs ", limit = 2)
    if (parts.size != 2 || winner.isBlank()) {
        append(match.matchLabel)
        return@buildAnnotatedString
    }
    val left = parts[0]
    val right = parts[1]
    val leftWinner = left.contains(winner, ignoreCase = true)
    val rightWinner = right.contains(winner, ignoreCase = true)
    if (!leftWinner && !rightWinner) {
        append(match.matchLabel)
        return@buildAnnotatedString
    }
    if (leftWinner) {
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append(left) }
    } else {
        append(left)
    }
    append(" vs ")
    if (rightWinner) {
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append(right) }
    } else {
        append(right)
    }
}
