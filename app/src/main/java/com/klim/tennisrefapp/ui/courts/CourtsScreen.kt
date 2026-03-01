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
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

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

private enum class CourtPageMode {
    CURRENT,
    NEXT,
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
        val pagerState = rememberPagerState(pageCount = { 2 })
        val scope = rememberCoroutineScope()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = pagerState.currentPage == 0,
                    onClick = {
                        if (pagerState.currentPage != 0) {
                            scope.launch { pagerState.animateScrollToPage(0) }
                        }
                    },
                    label = { Text("Current") },
                )
                FilterChip(
                    selected = pagerState.currentPage == 1,
                    onClick = {
                        if (pagerState.currentPage != 1) {
                            scope.launch { pagerState.animateScrollToPage(1) }
                        }
                    },
                    label = { Text("Next") },
                )
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val mode = if (page == 0) CourtPageMode.CURRENT else CourtPageMode.NEXT
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(courts, key = { it.court.courtId }) { row ->
                        CourtCardItem(
                            row = row,
                            mode = mode,
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
    mode: CourtPageMode,
    onTap: () -> Unit,
) {
    val nowMillis = rememberTickerNowMillis()
    val court = row.court
    val timerState = row.activeTimerState
    val timerType = timerState?.timerType
    val warmupExpiredPendingStart = timerState?.timerType == CourtTimerType.WARMUP &&
        timerState.isRunning == false &&
        court.status == CourtStatus.SCHEDULED
    val hasAlertHighlight = (timerState?.isRunning == true) || warmupExpiredPendingStart
    val isNextPage = mode == CourtPageMode.NEXT
    val statusTint = if (isNextPage) {
        MaterialTheme.colorScheme.outline
    } else {
        statusColor(court.status, hasAlertHighlight)
    }
    val cardModifier = Modifier
        .fillMaxWidth()
        .let { modifier ->
            if (isNextPage) {
                modifier
            } else {
                modifier.clickable(onClick = onTap)
            }
        }
    val titleColor = if (isNextPage) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val subtitleColor = if (isNextPage) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val matchLabel = if (isNextPage) court.nextMatchLabel ?: "No next match" else court.currentMatchLabel

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
                if (!isNextPage) {
                    Text(
                        text = when {
                            timerState?.isRunning == true && timerType != null -> timerLabel(timerType)
                            warmupExpiredPendingStart -> "Warm-up done"
                            else -> statusLabel(court.status)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = statusTint,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (!isNextPage && timerState?.isRunning == true) {
                    Text(
                        text = formatRemaining(timerState.remainingMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else if (!isNextPage && court.status == CourtStatus.IN_PROGRESS) {
                    court.startedAtMillis?.let { startedAt ->
                        Text(
                            text = "Elapsed ${formatDuration((nowMillis - startedAt).coerceAtLeast(0L))}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    val scheduleLabel = if (isNextPage) court.nextScheduleLabel else court.scheduleLabel
                    val notBeforeMillis = if (isNextPage) court.nextNotBeforeMillis else court.computedNotBeforeMillis
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
        CourtStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
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
                ),
            ),
            mode = CourtPageMode.CURRENT,
            onTap = {},
        )
    }
}
