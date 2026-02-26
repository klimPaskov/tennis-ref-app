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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    val grouped = uiState.matches.groupBy { it.courtName ?: "Unassigned" }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
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
                            .clickable(onClick = { onOpenMatchLog(match.matchId) }),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(match.matchLabel, style = MaterialTheme.typography.bodyLarge)
                            val score = match.scoreLabel ?: "-"
                            Text("Score: $score", style = MaterialTheme.typography.bodyMedium)
                            match.endedAtMillis?.let { endedAt ->
                                Text("Ended: ${formatDateTime(endedAt)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDateTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
