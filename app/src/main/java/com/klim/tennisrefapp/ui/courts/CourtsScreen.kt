package com.klim.tennisrefapp.ui.courts

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.klim.tennisrefapp.domain.model.CourtCard
import com.klim.tennisrefapp.domain.model.CourtStatus

@Composable
fun CourtsRoute(
    onCourtClick: (Long) -> Unit,
    viewModel: CourtsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CourtsScreen(
        courts = uiState.courts,
        onCourtClick = onCourtClick,
        onAssign = viewModel::onAssignMatch,
        onClear = viewModel::onClearCourt,
        onSwap = { courtId ->
            viewModel.onSwapWithNext(courtId, uiState.courts.map { it.courtId })
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourtsScreen(
    courts: List<CourtCard>,
    onCourtClick: (Long) -> Unit,
    onAssign: (Long) -> Unit,
    onClear: (Long) -> Unit,
    onSwap: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = "Courts") })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(courts, key = { it.courtId }) { court ->
                CourtCardItem(
                    court = court,
                    onOpen = { onCourtClick(court.courtId) },
                    onAssign = { onAssign(court.courtId) },
                    onClear = { onClear(court.courtId) },
                    onSwap = { onSwap(court.courtId) },
                )
            }
        }
    }
}

@Composable
private fun CourtCardItem(
    court: CourtCard,
    onOpen: () -> Unit,
    onAssign: () -> Unit,
    onClear: () -> Unit,
    onSwap: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = court.courtName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = court.currentMatchLabel,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Status: ${statusLabel(court.status)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            court.computedNotBeforeMillis?.let { notBefore ->
                Text(
                    text = "Not before: ${formatTime(notBefore)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen) {
                    Text("Open")
                }
                OutlinedButton(onClick = onAssign) {
                    Text("Assign")
                }
                OutlinedButton(onClick = onSwap) {
                    Text("Swap")
                }
                OutlinedButton(onClick = onClear) {
                    Text("Clear")
                }
            }
        }
    }
}

private fun statusLabel(status: CourtStatus): String {
    return when (status) {
        CourtStatus.EMPTY -> "Empty"
        CourtStatus.SCHEDULED -> "Scheduled"
        CourtStatus.IN_PROGRESS -> "In progress"
        CourtStatus.COMPLETED -> "Completed"
    }
}

private fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
