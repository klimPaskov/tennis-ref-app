package com.klim.tennisrefapp.ui.courts

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.produceState
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
import kotlinx.coroutines.delay

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
        val pageCount = courts.maxOfOrNull { row ->
            row.court.queueEntries.size.coerceAtLeast(1)
        } ?: 1
        val pagerState = rememberPagerState(pageCount = { pageCount })
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "${pagerState.currentPage + 1}.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(courts, key = { it.court.courtId }) { row ->
                        CourtCardItem(
                            row = row,
                            pageIndex = page,
                            onTap = { onCourtClick(row.court.courtId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourtCardItem(
    row: CourtListItemUi,
    pageIndex: Int,
    onTap: () -> Unit,
) {
    val nowMillis = rememberTickerNowMillis()
    val court = row.court
    val entry = court.queueEntries.getOrNull(pageIndex)
    val status = entry?.status ?: CourtStatus.EMPTY
    val isActiveEntry = pageIndex == court.activeEntryIndex
    val isFutureEntry = pageIndex > court.activeEntryIndex
    val timerState = row.activeTimerState
    val timerType = timerState?.timerType
    val warmupExpiredPendingStart = timerState?.timerType == CourtTimerType.WARMUP &&
        timerState.isRunning == false &&
        status == CourtStatus.SCHEDULED
    val hasAlertHighlight = isActiveEntry && ((timerState?.isRunning == true) || warmupExpiredPendingStart)
    val statusTint = if (isFutureEntry) {
        MaterialTheme.colorScheme.outline
    } else {
        statusColor(status, hasAlertHighlight)
    }
    val cardModifier = Modifier
        .fillMaxWidth()
        .let { modifier ->
            if (!isActiveEntry || entry?.matchId == null) {
                modifier
            } else {
                modifier.clickable(onClick = onTap)
            }
        }
    val titleColor = if (isFutureEntry) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val subtitleColor = if (isFutureEntry) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val matchLabel = entry?.matchLabel ?: "No match"

    Card(
        modifier = cardModifier,
        border = BorderStroke(2.dp, statusTint),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = court.courtName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
            )
            Text(
                text = matchLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = subtitleColor,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isFutureEntry) {
                    Text(
                        text = when {
                            isActiveEntry && timerState?.isRunning == true && timerType != null -> timerLabel(timerType)
                            warmupExpiredPendingStart -> "Warm-up done"
                            else -> statusLabel(status)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = statusTint,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (!isFutureEntry && isActiveEntry && timerState?.isRunning == true) {
                    Text(
                        text = formatRemaining(timerState.remainingMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else if (!isFutureEntry && status == CourtStatus.IN_PROGRESS) {
                    entry?.startedAtMillis?.let { startedAt ->
                        Text(
                            text = "Elapsed ${formatDuration((nowMillis - startedAt).coerceAtLeast(0L))}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    val scheduleLabel = entry?.scheduleLabel
                    val notBeforeMillis = entry?.computedNotBeforeMillis
                    when {
                        scheduleLabel?.contains("Followed By", ignoreCase = true) == true -> {
                            Text(
                                text = "Followed By",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = subtitleColor,
                            )
                        }
                        !scheduleLabel.isNullOrBlank() -> {
                            Text(
                                text = scheduleLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = subtitleColor,
                            )
                        }
                        notBeforeMillis != null -> {
                            Text(
                                text = "Not before ${formatTime(notBeforeMillis)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = subtitleColor,
                            )
                        }
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

@Composable
private fun statusColor(status: CourtStatus, hasAlertHighlight: Boolean): androidx.compose.ui.graphics.Color {
    if (hasAlertHighlight) return Color(0xFFD64444)
    return when (status) {
        CourtStatus.EMPTY -> MaterialTheme.colorScheme.outline
        CourtStatus.SCHEDULED -> MaterialTheme.colorScheme.primary
        CourtStatus.IN_PROGRESS -> Color(0xFFE0B422)
        CourtStatus.COMPLETED -> Color(0xFF2FA85A)
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

private fun formatDuration(durationMillis: Long): String {
    val totalMinutes = durationMillis / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
private fun rememberTickerNowMillis(intervalMillis: Long = 1_000L): Long {
    return produceState(initialValue = System.currentTimeMillis(), intervalMillis) {
        while (true) {
            value = System.currentTimeMillis()
            delay(intervalMillis)
        }
    }.value
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
                    scheduleLabel = "Followed By",
                    computedNotBeforeMillis = 1_700_000_000_000,
                    nextMatchLabel = "🇫🇮 Hanna Vanni [7] vs 🇪🇪 Vera Morozova",
                    nextScheduleLabel = "Not before 11:30",
                    nextNotBeforeMillis = 1_700_000_600_000,
                    matchId = 42,
                    startedAtMillis = null,
                    queueEntries = listOf(
                        com.klim.tennisrefapp.domain.model.CourtQueueEntry(
                            order = 1,
                            matchId = 42,
                            status = CourtStatus.SCHEDULED,
                            matchLabel = "🇨🇳 Yiyuan Su vs 🇬🇧 Vincent Fletcher",
                            scheduleLabel = "Starting at 09:00",
                            computedNotBeforeMillis = 1_700_000_000_000,
                            startedAtMillis = null,
                        ),
                        com.klim.tennisrefapp.domain.model.CourtQueueEntry(
                            order = 2,
                            matchId = 43,
                            status = CourtStatus.SCHEDULED,
                            matchLabel = "🇫🇮 Hanna Vanni [7] vs 🇪🇪 Vera Morozova",
                            scheduleLabel = "Not before 11:30",
                            computedNotBeforeMillis = 1_700_000_600_000,
                            startedAtMillis = null,
                        ),
                    ),
                    activeEntryIndex = 0,
                ),
            ),
            pageIndex = 0,
            onTap = {},
        )
    }
}
