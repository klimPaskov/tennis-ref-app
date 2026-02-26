package com.klim.tennisrefapp.ui.courtdetail

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klim.tennisrefapp.data.timer.CourtTimerState
import com.klim.tennisrefapp.domain.model.CourtDetail
import com.klim.tennisrefapp.domain.model.CourtStatus
import com.klim.tennisrefapp.domain.model.PlayerRef
import com.klim.tennisrefapp.domain.model.SideParticipant
import com.klim.tennisrefapp.domain.model.ViolationCategory
import com.klim.tennisrefapp.domain.model.ViolationCounts
import com.klim.tennisrefapp.domain.repository.MatchWinnerSide
import com.klim.tennisrefapp.ui.theme.TennisRefTheme

private data class ViolationTemplate(
    val name: String,
    val category: ViolationCategory,
)

private val commonViolations = listOf(
    ViolationTemplate("Racquet or equipment abuse", ViolationCategory.CODE),
    ViolationTemplate("Unsportsmanlike conduct", ViolationCategory.CODE),
    ViolationTemplate("Coaching", ViolationCategory.CODE),
    ViolationTemplate("Ball abuse", ViolationCategory.CODE),
    ViolationTemplate("Audible obscenity", ViolationCategory.CODE),
    ViolationTemplate("Visible obscenity", ViolationCategory.CODE),
    ViolationTemplate("Verbal abuse", ViolationCategory.CODE),
    ViolationTemplate("Leaving court without permission", ViolationCategory.CODE),
    ViolationTemplate("Best efforts", ViolationCategory.CODE),
    ViolationTemplate("Time violation (server)", ViolationCategory.TIME_SERVER),
    ViolationTemplate("Time violation (receiver)", ViolationCategory.TIME_RECEIVER),
)

@Composable
fun CourtDetailRoute(
    onBack: () -> Unit,
    viewModel: CourtDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        val feedback = uiState.feedbackMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = feedback.message,
            actionLabel = feedback.actionLabel,
            withDismissAction = feedback.actionLabel == null,
            duration = if (feedback.actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed && feedback.actionLabel == "Undo") {
            viewModel.onUndoLastViolation()
        }
        viewModel.clearFeedback()
    }

    CourtDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onSelectServer = viewModel::onSelectServer,
        onSwapSides = viewModel::onSwapSides,
        onStartMatch = viewModel::onStartMatch,
        onToggleTimer = viewModel::onToggleTimer,
        onApplyViolation = viewModel::onApplyViolation,
        onSubmitFinalScore = viewModel::onSubmitFinalScore,
        onMoveMatchToCourt = viewModel::onMoveMatchToCourt,
        onSwapWithCourt = viewModel::onSwapWithCourt,
        onUndoViolation = viewModel::onUndoLastViolation,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourtDetailScreen(
    uiState: CourtDetailUiState,
    onBack: () -> Unit,
    onSelectServer: (Long) -> Unit,
    onSwapSides: () -> Unit,
    onStartMatch: () -> Unit,
    onToggleTimer: () -> Unit,
    onApplyViolation: (Long, ViolationCategory, String) -> Unit,
    onSubmitFinalScore: (MatchWinnerSide, String) -> Unit,
    onMoveMatchToCourt: (Long?) -> Unit,
    onSwapWithCourt: (Long) -> Unit,
    onUndoViolation: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val detail = uiState.detail
    var showViolationDialog by remember { mutableStateOf(false) }
    var showScoreDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showSwapDialog by remember { mutableStateOf(false) }

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

            MatchHeader(detail = detail)

            if (detail.matchId == null) {
                Text(
                    text = "No match assigned to this court.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                return@Column
            }

            SidePanels(
                detail = detail,
                selectedServerPlayerId = uiState.selectedServerPlayerId,
                onSelectServer = onSelectServer,
                onSwapSides = onSwapSides,
            )

            when (detail.status) {
                CourtStatus.SCHEDULED -> {
                    Button(
                        onClick = onStartMatch,
                        enabled = detail.canStartMatch && uiState.selectedServerPlayerId != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Start Match")
                    }
                }

                CourtStatus.IN_PROGRESS -> {
                    InProgressActions(
                        timerState = uiState.timerState,
                        totalViolationCount = totalViolationCount(uiState.violationCountsByPlayer),
                        onToggleTimer = onToggleTimer,
                        onOpenViolations = { showViolationDialog = true },
                        onOpenFinalScore = { showScoreDialog = true },
                        onUndoViolation = onUndoViolation,
                        onOpenMove = { showMoveDialog = true },
                        onOpenSwapCourt = { showSwapDialog = true },
                    )
                }

                CourtStatus.EMPTY,
                CourtStatus.COMPLETED,
                -> Unit
            }
        }
    }

    if (showViolationDialog && detail != null) {
        ViolationDialog(
            detail = detail,
            countsByPlayer = uiState.violationCountsByPlayer,
            onDismiss = { showViolationDialog = false },
            onApplyViolation = onApplyViolation,
        )
    }

    if (showScoreDialog && detail != null) {
        FinalScoreDialog(
            leftName = sidePrimaryLabel(detail.leftSide),
            rightName = sidePrimaryLabel(detail.rightSide),
            onDismiss = { showScoreDialog = false },
            onSubmit = { winnerSide, score ->
                onSubmitFinalScore(winnerSide, score)
                showScoreDialog = false
            },
        )
    }

    if (showMoveDialog && detail != null) {
        MoveDialog(
            availableCourts = uiState.availableCourts,
            currentCourtId = detail.courtId,
            onDismiss = { showMoveDialog = false },
            onMove = { newCourtId ->
                onMoveMatchToCourt(newCourtId)
                showMoveDialog = false
            },
        )
    }

    if (showSwapDialog && detail != null) {
        SwapDialog(
            availableCourts = uiState.availableCourts,
            currentCourtId = detail.courtId,
            onDismiss = { showSwapDialog = false },
            onSwap = { otherCourtId ->
                onSwapWithCourt(otherCourtId)
                showSwapDialog = false
            },
        )
    }
}

@Composable
private fun MatchHeader(detail: CourtDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = statusLabel(detail.status),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        detail.computedNotBeforeMillis?.let { notBefore ->
            Text(
                text = "Not before ${formatTime(notBefore)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!detail.scoreText.isNullOrBlank()) {
            Text(
                text = "${detail.resultType ?: "NORMAL"} · ${detail.scoreText}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SidePanels(
    detail: CourtDetail,
    selectedServerPlayerId: Long?,
    onSelectServer: (Long) -> Unit,
    onSwapSides: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ParticipantSideCard(
            modifier = Modifier.weight(1f),
            side = detail.leftSide,
            selectedServerPlayerId = selectedServerPlayerId,
            canSelectServer = detail.canStartMatch,
            onSelectServer = onSelectServer,
        )
        FilledTonalIconButton(
            onClick = onSwapSides,
            enabled = detail.canSwapSides,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.SwapHoriz,
                contentDescription = "Swap sides",
            )
        }
        ParticipantSideCard(
            modifier = Modifier.weight(1f),
            side = detail.rightSide,
            selectedServerPlayerId = selectedServerPlayerId,
            canSelectServer = detail.canStartMatch,
            onSelectServer = onSelectServer,
        )
    }
}

@Composable
private fun InProgressActions(
    timerState: CourtTimerState?,
    totalViolationCount: Int,
    onToggleTimer: () -> Unit,
    onOpenViolations: () -> Unit,
    onOpenFinalScore: () -> Unit,
    onUndoViolation: () -> Unit,
    onOpenMove: () -> Unit,
    onOpenSwapCourt: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onToggleTimer,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Outlined.Timer,
                contentDescription = null,
            )
            Text(
                text = if (timerState?.isRunning == true) " Timer running" else " Timer",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        BadgedBox(
            badge = {
                if (totalViolationCount > 0) {
                    Badge { Text(totalViolationCount.toString()) }
                }
            },
        ) {
            FilledTonalIconButton(
                onClick = onOpenViolations,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = "Violations",
                )
            }
        }
        FilledTonalIconButton(
            onClick = onUndoViolation,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Undo,
                contentDescription = "Undo last violation",
            )
        }
    }

    Button(
        onClick = onOpenFinalScore,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Set Winner and Score")
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onOpenMove,
            modifier = Modifier.weight(1f),
        ) {
            Text("Move")
        }
        OutlinedButton(
            onClick = onOpenSwapCourt,
            modifier = Modifier.weight(1f),
        ) {
            Text("Swap Court")
        }
    }
}

@Composable
private fun ViolationDialog(
    detail: CourtDetail,
    countsByPlayer: Map<Long, ViolationCounts>,
    onDismiss: () -> Unit,
    onApplyViolation: (Long, ViolationCategory, String) -> Unit,
) {
    val players = buildList {
        addAll(detail.leftSide?.players.orEmpty())
        addAll(detail.rightSide?.players.orEmpty())
    }
    var selectedPlayerId by remember { mutableStateOf(players.firstOrNull()?.playerId) }
    val playersWithViolations = players.mapNotNull { player ->
        val counts = countsByPlayer[player.playerId] ?: return@mapNotNull null
        val total = counts.codeViolationCount + counts.timeViolationCountServer + counts.timeViolationCountReceiver
        if (total <= 0) null else player to counts
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Violations") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (playersWithViolations.isNotEmpty()) {
                    Text("Current counts", style = MaterialTheme.typography.titleSmall)
                    playersWithViolations.forEach { (player, counts) ->
                        Text(
                            text = "${player.name}: Code ${counts.codeViolationCount}, Server TV ${counts.timeViolationCountServer}, Receiver TV ${counts.timeViolationCountReceiver}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Text("Offender", style = MaterialTheme.typography.titleSmall)
                players.forEach { player ->
                    OutlinedButton(
                        onClick = { selectedPlayerId = player.playerId },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (selectedPlayerId == player.playerId) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                    ) {
                        Text(player.name)
                    }
                }

                Text("Apply", style = MaterialTheme.typography.titleSmall)
                commonViolations.forEach { violation ->
                    TextButton(
                        onClick = {
                            val offender = selectedPlayerId ?: return@TextButton
                            onApplyViolation(offender, violation.category, violation.name)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(violation.name, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun FinalScoreDialog(
    leftName: String,
    rightName: String,
    onDismiss: () -> Unit,
    onSubmit: (MatchWinnerSide, String) -> Unit,
) {
    var winner by remember { mutableStateOf<MatchWinnerSide?>(null) }
    var scoreText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Winner and Score") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Winner", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { winner = MatchWinnerSide.LEFT },
                        modifier = Modifier.weight(1f),
                        colors = if (winner == MatchWinnerSide.LEFT) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                    ) {
                        Text(leftName)
                    }
                    OutlinedButton(
                        onClick = { winner = MatchWinnerSide.RIGHT },
                        modifier = Modifier.weight(1f),
                        colors = if (winner == MatchWinnerSide.RIGHT) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                    ) {
                        Text(rightName)
                    }
                }

                TextField(
                    value = scoreText,
                    onValueChange = { scoreText = it },
                    label = { Text("Score (optional)") },
                    placeholder = { Text("6-4 6-3") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Leave score empty for walkover. Incomplete score (for example, 4:3) is saved as retirement.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { scoreText = "6-4 6-3" }, modifier = Modifier.weight(1f)) {
                        Text("6-4 6-3")
                    }
                    TextButton(onClick = { scoreText = "6-4 3-6 10-7" }, modifier = Modifier.weight(1f)) {
                        Text("6-4 3-6 10-7")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { winner?.let { onSubmit(it, scoreText.trim()) } },
                enabled = winner != null,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun MoveDialog(
    availableCourts: List<Pair<Long, String>>,
    currentCourtId: Long,
    onDismiss: () -> Unit,
    onMove: (Long?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move Match") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { onMove(null) }) {
                    Text("Move to unassigned")
                }
                availableCourts.forEach { (courtId, name) ->
                    if (courtId == currentCourtId) return@forEach
                    TextButton(onClick = { onMove(courtId) }) {
                        Text("Move to $name")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun SwapDialog(
    availableCourts: List<Pair<Long, String>>,
    currentCourtId: Long,
    onDismiss: () -> Unit,
    onSwap: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Swap With Court") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                availableCourts.forEach { (courtId, name) ->
                    if (courtId == currentCourtId) return@forEach
                    TextButton(onClick = { onSwap(courtId) }) {
                        Text("Swap with $name")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun ParticipantSideCard(
    modifier: Modifier,
    side: SideParticipant?,
    selectedServerPlayerId: Long?,
    canSelectServer: Boolean,
    onSelectServer: (Long) -> Unit,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val players = side?.players.orEmpty()
            if (players.isNotEmpty()) {
                players.forEach { player ->
                    val isSelected = player.playerId == selectedServerPlayerId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = MaterialTheme.shapes.small,
                            )
                            .clickable(
                                enabled = canSelectServer,
                                onClick = { onSelectServer(player.playerId) },
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = player.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Text(
                                text = "Server",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = side?.label ?: "TBD",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun sidePrimaryLabel(side: SideParticipant?): String {
    val players = side?.players.orEmpty()
    return if (players.isNotEmpty()) {
        players.joinToString(" / ") { it.name }
    } else {
        side?.label ?: "TBD"
    }
}

private fun totalViolationCount(countsByPlayer: Map<Long, ViolationCounts>): Int {
    return countsByPlayer.values.sumOf { counts ->
        counts.codeViolationCount + counts.timeViolationCountServer + counts.timeViolationCountReceiver
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

private fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

@Preview(showBackground = true)
@Composable
private fun ParticipantSideCardPreview() {
    TennisRefTheme {
        ParticipantSideCard(
            modifier = Modifier.fillMaxWidth(),
            side = SideParticipant(
                slotId = 1,
                label = "Alice",
                players = listOf(PlayerRef(1, "Alice"), PlayerRef(2, "Bob")),
                resolvedTeamId = 1,
                isResolved = true,
            ),
            selectedServerPlayerId = 2,
            canSelectServer = true,
            onSelectServer = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 820)
@Composable
private fun CourtDetailScheduledPreview() {
    TennisRefTheme {
        CourtDetailScreen(
            uiState = CourtDetailUiState(
                detail = CourtDetail(
                    courtId = 1,
                    courtName = "Court 1",
                    status = CourtStatus.SCHEDULED,
                    matchId = 101,
                    leftSide = SideParticipant(
                        slotId = 1,
                        label = "Alice",
                        players = listOf(PlayerRef(1, "Alice")),
                        resolvedTeamId = 1,
                        isResolved = true,
                    ),
                    rightSide = SideParticipant(
                        slotId = 2,
                        label = "Bob",
                        players = listOf(PlayerRef(2, "Bob")),
                        resolvedTeamId = 2,
                        isResolved = true,
                    ),
                    computedNotBeforeMillis = 1_700_000_000_000,
                    startedAtMillis = null,
                    initialServerPlayerId = null,
                    canSwapSides = true,
                    canStartMatch = true,
                    scoreText = null,
                    resultType = null,
                ),
                selectedServerPlayerId = 1,
                availableCourts = listOf(1L to "Court 1", 2L to "Court 2"),
            ),
            onBack = {},
            onSelectServer = {},
            onSwapSides = {},
            onStartMatch = {},
            onToggleTimer = {},
            onApplyViolation = { _, _, _ -> },
            onSubmitFinalScore = { _, _ -> },
            onMoveMatchToCourt = {},
            onSwapWithCourt = {},
            onUndoViolation = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview(showBackground = true, heightDp = 820)
@Composable
private fun CourtDetailInProgressPreview() {
    TennisRefTheme {
        CourtDetailScreen(
            uiState = CourtDetailUiState(
                detail = CourtDetail(
                    courtId = 2,
                    courtName = "Court 2",
                    status = CourtStatus.IN_PROGRESS,
                    matchId = 102,
                    leftSide = SideParticipant(
                        slotId = 3,
                        label = "Carla",
                        players = listOf(PlayerRef(3, "Carla")),
                        resolvedTeamId = 3,
                        isResolved = true,
                    ),
                    rightSide = SideParticipant(
                        slotId = 4,
                        label = "Diego",
                        players = listOf(PlayerRef(4, "Diego")),
                        resolvedTeamId = 4,
                        isResolved = true,
                    ),
                    computedNotBeforeMillis = null,
                    startedAtMillis = 1_700_000_100_000,
                    initialServerPlayerId = 3,
                    canSwapSides = false,
                    canStartMatch = false,
                    scoreText = null,
                    resultType = null,
                ),
                selectedServerPlayerId = 3,
                availableCourts = listOf(1L to "Court 1", 2L to "Court 2", 3L to "Court 3"),
                violationCountsByPlayer = mapOf(
                    3L to ViolationCounts(codeViolationCount = 1),
                    4L to ViolationCounts(timeViolationCountServer = 1),
                ),
            ),
            onBack = {},
            onSelectServer = {},
            onSwapSides = {},
            onStartMatch = {},
            onToggleTimer = {},
            onApplyViolation = { _, _, _ -> },
            onSubmitFinalScore = { _, _ -> },
            onMoveMatchToCourt = {},
            onSwapWithCourt = {},
            onUndoViolation = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
