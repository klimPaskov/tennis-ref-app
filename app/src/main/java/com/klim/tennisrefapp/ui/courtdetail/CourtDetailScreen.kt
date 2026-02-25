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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klim.tennisrefapp.domain.model.CourtDetail
import com.klim.tennisrefapp.domain.model.CourtStatus
import com.klim.tennisrefapp.domain.model.MatchEvent
import com.klim.tennisrefapp.domain.model.MatchEventKind
import com.klim.tennisrefapp.domain.model.SideParticipant
import com.klim.tennisrefapp.domain.model.ViolationCategory
import com.klim.tennisrefapp.domain.model.ViolationCounts
import com.klim.tennisrefapp.domain.repository.MatchResultType
import com.klim.tennisrefapp.domain.repository.MatchWinnerSide

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
    ViolationTemplate("Leaving court without permission", ViolationCategory.CODE, "Player leaves court without referee permission."),
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

            if (!detail.scoreText.isNullOrBlank()) {
                Text(
                    text = "Result: ${detail.resultType ?: "NORMAL"} | Score: ${detail.scoreText}",
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
                    modifier = Modifier.fillMaxWidth(0.48f),
                    sideTitle = "Left",
                    side = detail.leftSide,
                    selectedServerPlayerId = uiState.selectedServerPlayerId,
                    canSelectServer = detail.canStartMatch,
                    onSelectServer = onSelectServer,
                )
                ParticipantSideCard(
                    modifier = Modifier.fillMaxWidth(0.48f),
                    sideTitle = "Right",
                    side = detail.rightSide,
                    selectedServerPlayerId = uiState.selectedServerPlayerId,
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
                    enabled = detail.canStartMatch && uiState.selectedServerPlayerId != null,
                ) {
                    Text(text = "Start Match")
                }
            }

            if (detail.status == CourtStatus.IN_PROGRESS) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onToggleTimer) {
                        Text(text = timerLabel(uiState.timerState?.remainingMillis))
                    }
                    Button(onClick = { showViolationDialog = true }) {
                        Text(text = "Violations")
                    }
                    Button(onClick = { showScoreDialog = true }) {
                        Text(text = "Score")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { showMoveDialog = true }) {
                        Text(text = "Move")
                    }
                    OutlinedButton(onClick = { showSwapDialog = true }) {
                        Text(text = "Swap Court")
                    }
                    OutlinedButton(onClick = onUndoViolation) {
                        Text(text = "Undo Violation")
                    }
                }
            }

            ViolationSummary(
                left = detail.leftSide,
                right = detail.rightSide,
                countsByPlayer = uiState.violationCountsByPlayer,
            )

            Text(text = "Match Log", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(uiState.matchEvents.takeLast(20), key = { it.id }) { event ->
                    Card {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(text = "${event.kind.name} @ ${formatTime(event.timestamp)}")
                            if (!event.message.isNullOrBlank()) {
                                Text(text = event.message)
                            }
                        }
                    }
                }
            }
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
    var query by remember { mutableStateOf("") }
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
                Text("Select offender")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    players.forEach { player ->
                        OutlinedButton(onClick = { selectedPlayerId = player.playerId }) {
                            val marker = if (selectedPlayerId == player.playerId) "*" else ""
                            Text(text = "$marker ${player.name}")
                        }
                    }
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                )
                val filtered = commonViolations.filter {
                    query.isBlank() || it.name.contains(query, ignoreCase = true)
                }
                filtered.forEach { violation ->
                    Card {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = violation.name, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Text(if (winner == MatchWinnerSide.LEFT) "* Left winner" else "Left winner")
                    }
                    OutlinedButton(onClick = { winner = MatchWinnerSide.RIGHT }) {
                        Text(if (winner == MatchWinnerSide.RIGHT) "* Right winner" else "Right winner")
                    }
                }
                Text("Result type")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MatchResultType.entries.forEach { type ->
                        OutlinedButton(onClick = { resultType = type }) {
                            Text(if (resultType == type) "* ${type.name}" else type.name)
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "Violation Counts", style = MaterialTheme.typography.titleMedium)
        (left?.players.orEmpty() + right?.players.orEmpty()).forEach { player ->
            val counts = countsByPlayer[player.playerId] ?: ViolationCounts()
            Text(
                text = "${player.name}: code=${counts.codeViolationCount}, time(server)=${counts.timeViolationCountServer}, time(receiver)=${counts.timeViolationCountReceiver}",
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
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun timerLabel(remainingMillis: Long?): String {
    if (remainingMillis == null) return "Timer 5:00"
    val total = (remainingMillis / 1000).toInt()
    val min = total / 60
    val sec = total % 60
    return "Timer %d:%02d".format(min, sec)
}
