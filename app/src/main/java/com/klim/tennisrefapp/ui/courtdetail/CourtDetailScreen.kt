package com.klim.tennisrefapp.ui.courtdetail

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klim.tennisrefapp.domain.model.CourtDetail
import com.klim.tennisrefapp.domain.model.CourtStatus
import com.klim.tennisrefapp.domain.model.PlayerRef
import com.klim.tennisrefapp.domain.model.SideParticipant
import com.klim.tennisrefapp.domain.model.ViolationCategory
import com.klim.tennisrefapp.domain.model.ViolationCounts
import com.klim.tennisrefapp.domain.repository.MatchResultType
import com.klim.tennisrefapp.domain.repository.MatchWinnerSide
import com.klim.tennisrefapp.ui.theme.TennisRefTheme

private data class ViolationTemplate(
    val name: String,
    val category: ViolationCategory,
    val info: String,
)

private val commonViolations = listOf(
    ViolationTemplate("Racquet or equipment abuse", ViolationCategory.CODE, "Intentional damage or unsafe racket/equipment behavior."),
    ViolationTemplate("Unsportsmanlike conduct", ViolationCategory.CODE, "Conduct contrary to fair play and sportsmanship."),
    ViolationTemplate("Coaching", ViolationCategory.CODE, "Unauthorized coaching during play."),
    ViolationTemplate("Ball abuse", ViolationCategory.CODE, "Intentional hitting/throwing balls dangerously or abusively."),
    ViolationTemplate("Audible obscenity", ViolationCategory.CODE, "Clearly audible obscene language."),
    ViolationTemplate("Visible obscenity", ViolationCategory.CODE, "Obscene gestures visible to others."),
    ViolationTemplate("Verbal abuse", ViolationCategory.CODE, "Abusive language directed at others."),
    ViolationTemplate("Leaving the court without permission", ViolationCategory.CODE, "Player leaves court without referee permission."),
    ViolationTemplate("Best efforts", ViolationCategory.CODE, "Failure to use best efforts."),
    ViolationTemplate("Time violation (server)", ViolationCategory.TIME_SERVER, "Time violation while serving."),
    ViolationTemplate("Time violation (receiver)", ViolationCategory.TIME_RECEIVER, "Time violation while receiving."),
)

@Composable
fun CourtDetailRoute(
    onBack: () -> Unit,
    viewModel: CourtDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        val message = uiState.feedbackMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(message = message, actionLabel = "Undo")
        if (result == SnackbarResult.ActionPerformed) {
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
    onSubmitFinalScore: (MatchWinnerSide, MatchResultType, String) -> Unit,
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
                Text(text = "No match assigned to this court.", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }

            SidePanels(
                detail = detail,
                selectedServerPlayerId = uiState.selectedServerPlayerId,
                onSelectServer = onSelectServer,
            )

            if (detail.canSwapSides) {
                OutlinedButton(onClick = onSwapSides) {
                    Text(text = "Swap sides")
                }
            }

            when (detail.status) {
                CourtStatus.SCHEDULED -> {
                    Button(
                        onClick = onStartMatch,
                        enabled = detail.canStartMatch && uiState.selectedServerPlayerId != null,
                    ) {
                        Text(text = "Start Match")
                    }
                }

                CourtStatus.IN_PROGRESS -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onToggleTimer) {
                            Text(text = timerLabel(uiState.timerState?.remainingMillis))
                        }
                        Button(onClick = { showViolationDialog = true }) {
                            Text(text = "Violations")
                        }
                        Button(onClick = { showScoreDialog = true }) {
                            Text(text = "Final Score")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showMoveDialog = true }) {
                            Text(text = "Move")
                        }
                        OutlinedButton(onClick = { showSwapDialog = true }) {
                            Text(text = "Swap Court")
                        }
                        OutlinedButton(onClick = onUndoViolation) {
                            Text(text = "Undo")
                        }
                    }
                }

                CourtStatus.EMPTY,
                CourtStatus.COMPLETED,
                -> Unit
            }

            ViolationSummary(
                left = detail.leftSide,
                right = detail.rightSide,
                countsByPlayer = uiState.violationCountsByPlayer,
            )
        }
    }

    if (showViolationDialog && detail != null) {
        ViolationDialog(
            detail = detail,
            onDismiss = { showViolationDialog = false },
            onApplyViolation = onApplyViolation,
        )
    }

    if (showScoreDialog && detail != null) {
        FinalScoreDialog(
            onDismiss = { showScoreDialog = false },
            onSubmit = { side, resultType, score ->
                onSubmitFinalScore(side, resultType, score)
                showScoreDialog = false
            },
        )
    }

    if (showMoveDialog) {
        MoveDialog(
            availableCourts = uiState.availableCourts,
            currentCourtId = detail?.courtId,
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
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val gap = 12.dp
        val panelWidth = (maxWidth - gap) / 2

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            ParticipantSideCard(
                modifier = Modifier.width(panelWidth),
                sideTitle = "Left",
                side = detail.leftSide,
                selectedServerPlayerId = selectedServerPlayerId,
                canSelectServer = detail.canStartMatch,
                onSelectServer = onSelectServer,
            )
            ParticipantSideCard(
                modifier = Modifier.width(panelWidth),
                sideTitle = "Right",
                side = detail.rightSide,
                selectedServerPlayerId = selectedServerPlayerId,
                canSelectServer = detail.canStartMatch,
                onSelectServer = onSelectServer,
            )
        }
    }
}

@Composable
private fun ViolationDialog(
    detail: CourtDetail,
    onDismiss: () -> Unit,
    onApplyViolation: (Long, ViolationCategory, String) -> Unit,
) {
    val players = buildList {
        addAll(detail.leftSide?.players.orEmpty())
        addAll(detail.rightSide?.players.orEmpty())
    }
    var selectedPlayerId by remember { mutableStateOf(players.firstOrNull()?.playerId) }
    var infoText by remember { mutableStateOf<String?>(null) }

    if (infoText != null) {
        AlertDialog(
            onDismissRequest = { infoText = null },
            title = { Text("Violation info") },
            text = { Text(infoText ?: "") },
            confirmButton = { TextButton(onClick = { infoText = null }) { Text("Close") } },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apply Violation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Offender")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    players.forEach { player ->
                        OutlinedButton(onClick = { selectedPlayerId = player.playerId }) {
                            Text(text = if (selectedPlayerId == player.playerId) "${player.name} ✓" else player.name)
                        }
                    }
                }
                commonViolations.forEach { violation ->
                    Card {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = violation.name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = { infoText = violation.info }) {
                                Text("Info")
                            }
                            Button(onClick = {
                                val offender = selectedPlayerId ?: return@Button
                                onApplyViolation(offender, violation.category, violation.name)
                                onDismiss()
                            }) {
                                Text("Apply")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun FinalScoreDialog(
    onDismiss: () -> Unit,
    onSubmit: (MatchWinnerSide, MatchResultType, String) -> Unit,
) {
    var winner by remember { mutableStateOf(MatchWinnerSide.LEFT) }
    var resultType by remember { mutableStateOf(MatchResultType.NORMAL) }
    var scoreText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Final Score") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { winner = MatchWinnerSide.LEFT }) {
                        Text(if (winner == MatchWinnerSide.LEFT) "Left ✓" else "Left")
                    }
                    OutlinedButton(onClick = { winner = MatchWinnerSide.RIGHT }) {
                        Text(if (winner == MatchWinnerSide.RIGHT) "Right ✓" else "Right")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MatchResultType.entries.forEach { type ->
                        OutlinedButton(onClick = { resultType = type }) {
                            Text(if (resultType == type) "${type.name} ✓" else type.name)
                        }
                    }
                }
                TextField(
                    value = scoreText,
                    onValueChange = { scoreText = it },
                    label = { Text("Score") },
                    placeholder = { Text("6-4 3-6 10-7") },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(winner, resultType, scoreText) }) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun MoveDialog(
    availableCourts: List<Pair<Long, String>>,
    currentCourtId: Long?,
    onDismiss: () -> Unit,
    onMove: (Long?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move Match") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { onMove(null) }) {
                    Text("Move to Unassigned")
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
private fun ViolationSummary(
    left: SideParticipant?,
    right: SideParticipant?,
    countsByPlayer: Map<Long, ViolationCounts>,
) {
    val players = left?.players.orEmpty() + right?.players.orEmpty()
    if (players.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "Violation counts", style = MaterialTheme.typography.titleSmall)
        players.forEach { player ->
            val counts = countsByPlayer[player.playerId] ?: ViolationCounts()
            Text(
                text = "${player.name}: Code ${counts.codeViolationCount} · Server TV ${counts.timeViolationCountServer} · Receiver TV ${counts.timeViolationCountReceiver}",
                style = MaterialTheme.typography.bodySmall,
            )
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
                fontWeight = FontWeight.SemiBold,
            )

            val players = side?.players.orEmpty()
            if (players.isNotEmpty()) {
                players.forEach { player ->
                    val isSelected = player.playerId == selectedServerPlayerId
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                    ) {
                        TextButton(
                            onClick = { onSelectServer(player.playerId) },
                            enabled = canSelectServer,
                        ) {
                            Text(text = player.name)
                        }
                    }
                }
            } else {
                Text(
                    text = side?.label ?: "TBD",
                    style = MaterialTheme.typography.bodyMedium,
                )
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

private fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun timerLabel(remainingMillis: Long?): String {
    if (remainingMillis == null) return "Timer 5:00"
    val total = (remainingMillis / 1000).toInt()
    val min = total / 60
    val sec = total % 60
    return "Timer %d:%02d".format(min, sec)
}

@Preview(showBackground = true)
@Composable
private fun ParticipantSideCardPreview() {
    TennisRefTheme {
        ParticipantSideCard(
            modifier = Modifier.fillMaxWidth(),
            sideTitle = "Left",
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
            onSubmitFinalScore = { _, _, _ -> },
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
            onSubmitFinalScore = { _, _, _ -> },
            onMoveMatchToCourt = {},
            onSwapWithCourt = {},
            onUndoViolation = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
