package com.klim.tennisrefapp.ui.courtdetail

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.klim.tennisrefapp.domain.model.CourtDetail
import com.klim.tennisrefapp.domain.model.CourtStatus
import com.klim.tennisrefapp.domain.model.SideParticipant

@Composable
fun CourtDetailRoute(
    onBack: () -> Unit,
    viewModel: CourtDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        val message = uiState.feedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearFeedback()
    }

    CourtDetailScreen(
        detail = uiState.detail,
        selectedServerPlayerId = uiState.selectedServerPlayerId,
        onBack = onBack,
        onSelectServer = viewModel::onSelectServer,
        onSwapSides = viewModel::onSwapSides,
        onStartMatch = viewModel::onStartMatch,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourtDetailScreen(
    detail: CourtDetail?,
    selectedServerPlayerId: Long?,
    onBack: () -> Unit,
    onSelectServer: (Long) -> Unit,
    onSwapSides: () -> Unit,
    onStartMatch: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = detail?.courtName ?: "Court") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(text = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (detail == null) {
                Text(text = "Loading...")
                return@Column
            }

            Text(
                text = "Status: ${statusLabel(detail.status)}",
                style = MaterialTheme.typography.titleMedium,
            )

            detail.computedNotBeforeMillis?.let { notBefore ->
                Text(
                    text = "Not before: ${formatTime(notBefore)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (detail.matchId == null) {
                Text(text = "No match assigned to this court.")
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ParticipantSideCard(
                    modifier = Modifier.weight(1f),
                    sideTitle = "Left",
                    side = detail.leftSide,
                    selectedServerPlayerId = selectedServerPlayerId,
                    canSelectServer = detail.canStartMatch,
                    onSelectServer = onSelectServer,
                )
                ParticipantSideCard(
                    modifier = Modifier.weight(1f),
                    sideTitle = "Right",
                    side = detail.rightSide,
                    selectedServerPlayerId = selectedServerPlayerId,
                    canSelectServer = detail.canStartMatch,
                    onSelectServer = onSelectServer,
                )
            }

            if (detail.canSwapSides) {
                OutlinedButton(onClick = onSwapSides) {
                    Text(text = "Swap sides")
                }
            }

            if (detail.status == CourtStatus.SCHEDULED) {
                Button(
                    onClick = onStartMatch,
                    enabled = detail.canStartMatch && selectedServerPlayerId != null,
                ) {
                    Text(text = "Start Match")
                }
            }

            if (detail.status == CourtStatus.IN_PROGRESS) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                        Text(text = "Timer")
                    }
                    Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                        Text(text = "Violations")
                    }
                    Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                        Text(text = "Score")
                    }
                }
            }
        }
    }
}

@Composable
private fun ParticipantSideCard(
    modifier: Modifier,
    sideTitle: String,
    side: SideParticipant?,
    selectedServerPlayerId: Long?,
    canSelectServer: Boolean,
    onSelectServer: (Long) -> Unit,
) {
    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = sideTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = side?.label ?: "TBD",
                style = MaterialTheme.typography.bodyLarge,
            )
            val players = side?.players.orEmpty()
            if (players.isEmpty()) {
                Text(text = "No players")
            } else {
                players.forEach { player ->
                    val isSelected = player.playerId == selectedServerPlayerId
                    Card {
                        TextButton(
                            onClick = { onSelectServer(player.playerId) },
                            enabled = canSelectServer,
                        ) {
                            val marker = if (isSelected) " (Server)" else ""
                            Text(text = player.name + marker)
                        }
                    }
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
