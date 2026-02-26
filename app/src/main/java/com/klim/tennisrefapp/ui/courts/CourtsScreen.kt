package com.klim.tennisrefapp.ui.courts

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klim.tennisrefapp.data.timer.CourtTimerType
import com.klim.tennisrefapp.domain.model.CourtStatus
import com.klim.tennisrefapp.ui.theme.TennisRefTheme

@Composable
fun CourtsRoute(
    onCourtClick: (Long) -> Unit,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: CourtsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CourtsScreen(
        courts = uiState.courts,
        onCourtClick = onCourtClick,
        onHome = onBack,
        onOpenHistory = onOpenHistory,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourtsScreen(
    courts: List<CourtListItemUi>,
    onCourtClick: (Long) -> Unit,
    onHome: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Courts") },
                navigationIcon = {
                    IconButton(onClick = onHome) {
                        Icon(Icons.Outlined.Home, contentDescription = "Home")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenHistory) { Text("History") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(courts, key = { it.court.courtId }) { row ->
                CourtCardItem(
                    row = row,
                    onTap = { onCourtClick(row.court.courtId) },
                )
            }
        }
    }
}

@Composable
private fun CourtCardItem(
    row: CourtListItemUi,
    onTap: () -> Unit,
) {
    val court = row.court
    val timerState = row.activeTimerState
    val timerType = timerState?.timerType

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        border = BorderStroke(2.dp, statusColor(court.status, timerType)),
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
                Text(
                    text = if (timerType != null) timerLabel(timerType) else statusLabel(court.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor(court.status, timerType),
                    fontWeight = FontWeight.SemiBold,
                )
                if (timerState != null) {
                    Text(
                        text = formatRemaining(timerState.remainingMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else if (court.status == CourtStatus.SCHEDULED) {
                    court.computedNotBeforeMillis?.let { notBefore ->
                        Text(
                            text = "Not before ${formatTime(notBefore)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

private fun timerLabel(timerType: CourtTimerType): String {
    return when (timerType) {
        CourtTimerType.WARMUP -> "Warm-up"
        else -> timerType.label
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

private fun statusColor(status: CourtStatus, timerType: CourtTimerType? = null): Color {
    if (timerType == CourtTimerType.WARMUP) return Color(0xFFE91E63)
    if (timerType != null) return Color(0xFFE53935)
    return when (status) {
        CourtStatus.EMPTY -> Color(0xFF9E9E9E)
        CourtStatus.SCHEDULED -> Color(0xFFE91E63)
        CourtStatus.IN_PROGRESS -> Color(0xFFF57C00)
        CourtStatus.COMPLETED -> Color(0xFF2E7D32)
    }
}

private fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun formatRemaining(remainingMillis: Long): String {
    val totalSeconds = (remainingMillis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

@Preview(showBackground = true)
@Composable
private fun CourtCardItemPreview() {
    TennisRefTheme {
        CourtCardItem(
            row = CourtListItemUi(
                court = com.klim.tennisrefapp.domain.model.CourtCard(
                    courtId = 1,
                    courtName = "Court 2",
                    status = CourtStatus.SCHEDULED,
                    currentMatchLabel = "🇨🇳 Yiyuan Su vs 🇬🇧 Vincent Fletcher",
                    computedNotBeforeMillis = 1_700_000_000_000,
                    matchId = 42,
                ),
            ),
            onTap = {},
        )
    }
}
