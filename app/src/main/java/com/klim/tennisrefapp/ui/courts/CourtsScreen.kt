package com.klim.tennisrefapp.ui.courts

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klim.tennisrefapp.domain.model.CourtCard
import com.klim.tennisrefapp.domain.model.CourtStatus
import com.klim.tennisrefapp.ui.theme.TennisRefTheme

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
                    onTap = { onCourtClick(court.courtId) },
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
    onTap: () -> Unit,
    onAssign: () -> Unit,
    onClear: () -> Unit,
    onSwap: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = court.courtName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = court.currentMatchLabel,
                style = MaterialTheme.typography.bodyLarge,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(statusColor(court.status), CircleShape),
                        )
                        Text(
                            text = statusLabel(court.status),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                court.computedNotBeforeMillis?.let { notBefore ->
                    Text(
                        text = "Not before ${formatTime(notBefore)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        CourtStatus.IN_PROGRESS -> "In Progress"
        CourtStatus.COMPLETED -> "Completed"
    }
}

@Composable
private fun statusColor(status: CourtStatus) = when (status) {
    CourtStatus.EMPTY -> MaterialTheme.colorScheme.outline
    CourtStatus.SCHEDULED -> MaterialTheme.colorScheme.secondary
    CourtStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
    CourtStatus.COMPLETED -> MaterialTheme.colorScheme.primary
}

private fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

@Preview(showBackground = true)
@Composable
private fun CourtCardItemPreview() {
    TennisRefTheme {
        CourtCardItem(
            court = CourtCard(
                courtId = 1,
                courtName = "Court 1",
                status = CourtStatus.SCHEDULED,
                currentMatchLabel = "Alice vs Bob",
                computedNotBeforeMillis = 1_700_000_000_000,
                matchId = 42,
            ),
            onTap = {},
            onAssign = {},
            onClear = {},
            onSwap = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun CourtsScreenPreview() {
    TennisRefTheme {
        CourtsScreen(
            courts = listOf(
                CourtCard(
                    courtId = 1,
                    courtName = "Court 1",
                    status = CourtStatus.SCHEDULED,
                    currentMatchLabel = "Alice vs Bob",
                    computedNotBeforeMillis = 1_700_000_000_000,
                    matchId = 101,
                ),
                CourtCard(
                    courtId = 2,
                    courtName = "Court 2",
                    status = CourtStatus.IN_PROGRESS,
                    currentMatchLabel = "Carla vs Diego",
                    computedNotBeforeMillis = null,
                    matchId = 102,
                ),
            ),
            onCourtClick = {},
            onAssign = {},
            onClear = {},
            onSwap = {},
        )
    }
}
