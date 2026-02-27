package com.klim.tennisrefapp.ui.courtdetail

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klim.tennisrefapp.data.timer.CourtTimerState
import com.klim.tennisrefapp.data.timer.CourtTimerType
import com.klim.tennisrefapp.domain.model.CourtDetail
import com.klim.tennisrefapp.domain.model.CourtStatus
import com.klim.tennisrefapp.domain.model.PlayerRef
import com.klim.tennisrefapp.domain.model.RestPrompt
import com.klim.tennisrefapp.domain.model.SideParticipant
import com.klim.tennisrefapp.domain.model.ViolationCategory
import com.klim.tennisrefapp.domain.model.ViolationCounts
import com.klim.tennisrefapp.domain.repository.MatchWinnerSide
import com.klim.tennisrefapp.ui.theme.TennisRefTheme
import kotlin.math.max
import kotlin.math.abs
import kotlinx.coroutines.delay

private data class ViolationTemplate(
    val name: String,
    val category: ViolationCategory,
)

private enum class ResolverSide {
    LEFT,
    RIGHT,
}

private data class ScoreCandidate(
    val leftGames: Int,
    val rightGames: Int,
)

private data class ScoreArgumentResolution(
    val title: String,
    val details: String,
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

private val inProgressTimers = listOf(
    CourtTimerType.SIDE_SWITCH,
    CourtTimerType.SET_BREAK,
    CourtTimerType.TOILET_BREAK,
    CourtTimerType.OFF_COURT,
    CourtTimerType.MEDICAL_TIMEOUT,
    CourtTimerType.EQUIPMENT_FIX,
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
        snackbarHostState.currentSnackbarData?.dismiss()
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
        onStartMatchNow = viewModel::onStartMatch,
        onStartTimer = viewModel::onStartTimer,
        onStopTimer = viewModel::onStopTimer,
        onApplyViolation = viewModel::onApplyViolation,
        onSubmitFinalScore = viewModel::onSubmitFinalScore,
        onDismissRestPrompt = viewModel::onDismissRestPrompt,
        onApplyRestChoices = viewModel::onApplyRestChoices,
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
    onStartMatchNow: () -> Unit,
    onStartTimer: (CourtTimerType) -> Unit,
    onStopTimer: () -> Unit,
    onApplyViolation: (Long, ViolationCategory, String) -> Unit,
    onSubmitFinalScore: (MatchWinnerSide, String) -> Unit,
    onDismissRestPrompt: () -> Unit,
    onApplyRestChoices: (Map<Long, Int>) -> Unit,
    onUndoViolation: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val detail = uiState.detail
    var showViolationDialog by remember { mutableStateOf(false) }
    var showScoreDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showArgumentDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = detail?.courtName ?: "Court") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
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
                Text("Loading...")
                return@Column
            }

            MatchHeader(detail = detail)

            if (detail.matchId == null) {
                Text("No match assigned to this court.", style = MaterialTheme.typography.bodyLarge)
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
                    ScheduledActions(
                        selectedServerPlayerId = uiState.selectedServerPlayerId,
                        timerState = uiState.timerState,
                        onStartMatchNow = onStartMatchNow,
                    )
                }

                CourtStatus.IN_PROGRESS -> {
                    InProgressActions(
                        timerState = uiState.timerState,
                        totalViolationCount = totalViolationCount(uiState.violationCountsByPlayer),
                        onOpenTimer = { showTimerDialog = true },
                        onOpenViolations = { showViolationDialog = true },
                        onOpenArgumentSolver = { showArgumentDialog = true },
                        onOpenFinalScore = { showScoreDialog = true },
                        onUndoViolation = onUndoViolation,
                    )
                }

                CourtStatus.EMPTY,
                CourtStatus.COMPLETED,
                -> Unit
            }
        }
    }

    if (showTimerDialog && detail != null) {
        TimerDialog(
            timerState = uiState.timerState,
            onDismiss = { showTimerDialog = false },
            onStart = { timerType ->
                onStartTimer(timerType)
                showTimerDialog = false
            },
            onStop = {
                onStopTimer()
                showTimerDialog = false
            },
        )
    }

    if (showViolationDialog && detail != null) {
        ViolationDialog(
            detail = detail,
            countsByPlayer = uiState.violationCountsByPlayer,
            onDismiss = { showViolationDialog = false },
            onApplyViolation = onApplyViolation,
        )
    }

    if (showArgumentDialog && detail != null) {
        ScoreArgumentSolverDialog(
            detail = detail,
            onDismiss = { showArgumentDialog = false },
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

    val restPrompt = uiState.restPrompt
    if (restPrompt != null) {
        RestPromptDialog(
            prompt = restPrompt,
            onDismiss = onDismissRestPrompt,
            onConfirm = onApplyRestChoices,
        )
    }
}

@Composable
private fun MatchHeader(detail: CourtDetail) {
    val nowMillis = rememberTickerNowMillis()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(statusColor(detail.status), RoundedCornerShape(5.dp)),
            )
            Text(
                text = statusLabel(detail.status),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (detail.status == CourtStatus.SCHEDULED) {
            detail.computedNotBeforeMillis?.let { notBefore ->
                Text("Not before ${formatTime(notBefore)}", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (detail.status == CourtStatus.IN_PROGRESS) {
            detail.startedAtMillis?.let { startedAt ->
                val elapsedMillis = (nowMillis - startedAt).coerceAtLeast(0L)
                Text(
                    text = "Elapsed: ${formatDuration(elapsedMillis)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        val scoreLabel = buildScoreLabel(detail.scoreText, detail.resultType)
        if (!scoreLabel.isNullOrBlank()) {
            Text(scoreLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }

        val durationLabel = matchDurationLabel(detail.startedAtMillis, detail.endedAtMillis)
        if (durationLabel != null) {
            Text(durationLabel, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ScheduledActions(
    selectedServerPlayerId: Long?,
    timerState: CourtTimerState?,
    onStartMatchNow: () -> Unit,
) {
    if (timerState?.timerType == CourtTimerType.WARMUP) {
        val warmupText = if (timerState.isRunning) {
            "Warm-up: ${formatRemaining(timerState.remainingMillis)}"
        } else {
            "Warm-up complete"
        }
        Text(warmupText, style = MaterialTheme.typography.bodyMedium)
    } else if (selectedServerPlayerId == null) {
        Text("Select the first server to start warm-up.", style = MaterialTheme.typography.bodyMedium)
    }

    Button(onClick = onStartMatchNow, modifier = Modifier.fillMaxWidth(), enabled = selectedServerPlayerId != null) {
        Text("Start Match Now")
    }
}

@Composable
private fun SidePanels(
    detail: CourtDetail,
    selectedServerPlayerId: Long?,
    onSelectServer: (Long) -> Unit,
    onSwapSides: () -> Unit,
) {
    val leftRows = detail.leftSide?.players?.size ?: 1
    val rightRows = detail.rightSide?.players?.size ?: 1
    val rowCount = max(leftRows, rightRows).coerceAtLeast(1)
    val sideCardHeight = (rowCount * 96 + 46).dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ParticipantSideCard(
            modifier = Modifier
                .weight(1f)
                .height(sideCardHeight),
            side = detail.leftSide,
            selectedServerPlayerId = selectedServerPlayerId,
            canSelectServer = detail.canStartMatch,
            onSelectServer = onSelectServer,
            isWinner = detail.status == CourtStatus.COMPLETED && detail.leftSide?.resolvedTeamId == detail.winnerTeamId,
        )
        FilledTonalIconButton(
            onClick = onSwapSides,
            enabled = detail.canSwapSides,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(Icons.Outlined.SwapHoriz, contentDescription = "Swap sides")
        }
        ParticipantSideCard(
            modifier = Modifier
                .weight(1f)
                .height(sideCardHeight),
            side = detail.rightSide,
            selectedServerPlayerId = selectedServerPlayerId,
            canSelectServer = detail.canStartMatch,
            onSelectServer = onSelectServer,
            isWinner = detail.status == CourtStatus.COMPLETED && detail.rightSide?.resolvedTeamId == detail.winnerTeamId,
        )
    }
}

@Composable
private fun InProgressActions(
    timerState: CourtTimerState?,
    totalViolationCount: Int,
    onOpenTimer: () -> Unit,
    onOpenViolations: () -> Unit,
    onOpenArgumentSolver: () -> Unit,
    onOpenFinalScore: () -> Unit,
    onUndoViolation: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onOpenTimer, modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.Timer, contentDescription = null)
            Text(
                text = " ${timerButtonLabel(timerState)}",
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
            FilledTonalIconButton(onClick = onOpenViolations, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = "Violations")
            }
        }
        FilledTonalIconButton(onClick = onOpenArgumentSolver, modifier = Modifier.size(34.dp)) {
            Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "Solve argument")
        }
        FilledTonalIconButton(onClick = onUndoViolation, modifier = Modifier.size(34.dp)) {
            Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo last violation")
        }
    }

    Button(onClick = onOpenFinalScore, modifier = Modifier.fillMaxWidth()) {
        Text("Finalize Match")
    }
}

@Composable
private fun TimerDialog(
    timerState: CourtTimerState?,
    onDismiss: () -> Unit,
    onStart: (CourtTimerType) -> Unit,
    onStop: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Match Timers") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (timerState != null) {
                    Card {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(timerState.timerType.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (timerState.isRunning) formatRemaining(timerState.remainingMillis) else "Completed",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            TextButton(onClick = onStop) {
                                Text("Stop")
                            }
                        }
                    }
                }

                Text("Start timer", style = MaterialTheme.typography.titleSmall)
                inProgressTimers.forEach { timerType ->
                    TextButton(onClick = { onStart(timerType) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null)
                        Text(
                            text = " ${timerType.label} (${formatRemaining(timerType.durationMillis)})",
                            modifier = Modifier.fillMaxWidth(),
                        )
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
    var pendingViolation by remember { mutableStateOf<ViolationTemplate?>(null) }
    val selectedPlayer = players.firstOrNull { it.playerId == selectedPlayerId }
    val selectedCounts = countsByPlayer[selectedPlayerId] ?: ViolationCounts()
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
                            text = "${displayPlayerLabel(player)}: Code ${counts.codeViolationCount}, Server TV ${counts.timeViolationCountServer}, Receiver TV ${counts.timeViolationCountReceiver}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val guidance = buildViolationGuidanceSummary(counts)
                        if (guidance.isNotBlank()) {
                            Text(guidance, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Text("Offender", style = MaterialTheme.typography.titleSmall)
                players.forEach { player ->
                    OutlinedButton(
                        onClick = { selectedPlayerId = player.playerId },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (selectedPlayerId == player.playerId) {
                            ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                    ) {
                        Text(displayPlayerLabel(player))
                    }
                }

                Text("Apply", style = MaterialTheme.typography.titleSmall)
                commonViolations.forEach { violation ->
                    OutlinedButton(
                        onClick = { pendingViolation = violation },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedPlayer != null,
                    ) {
                        Text(violation.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )

    val violation = pendingViolation
    if (violation != null && selectedPlayer != null) {
        val currentCount = categoryCount(selectedCounts, violation.category)
        val offenceNumber = currentCount + 1
        val appliedPenalty = penaltyForOffence(offenceNumber)
        val nextPenalty = penaltyForOffence(offenceNumber + 1)
        AlertDialog(
            onDismissRequest = { pendingViolation = null },
            title = { Text("Confirm Violation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${violation.name} for ${displayPlayerLabel(selectedPlayer)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "This offence #$offenceNumber: $appliedPenalty",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Next offence: $nextPenalty",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onApplyViolation(selectedPlayer.playerId, violation.category, violation.name)
                        pendingViolation = null
                        onDismiss()
                    },
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingViolation = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ScoreArgumentSolverDialog(
    detail: CourtDetail,
    onDismiss: () -> Unit,
) {
    var leftOwnGames by remember { mutableStateOf("") }
    var leftOpponentGames by remember { mutableStateOf("") }
    var rightOwnGames by remember { mutableStateOf("") }
    var rightOpponentGames by remember { mutableStateOf("") }
    var currentServerSide by remember { mutableStateOf<ResolverSide?>(null) }
    var resolution by remember { mutableStateOf<ScoreArgumentResolution?>(null) }

    val initialServerSide = initialServerSide(detail)
    val leftName = sidePrimaryLabel(detail.leftSide)
    val rightName = sidePrimaryLabel(detail.rightSide)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Solve Argument") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter each claim from that player's perspective: own games and opponent games.",
                    style = MaterialTheme.typography.bodySmall,
                )
                ArgumentScoreRow(
                    label = "$leftName says",
                    ownGames = leftOwnGames,
                    opponentGames = leftOpponentGames,
                    onOwnChange = { leftOwnGames = sanitizeScoreCell(it, maxDigits = 2) },
                    onOpponentChange = { leftOpponentGames = sanitizeScoreCell(it, maxDigits = 2) },
                )
                ArgumentScoreRow(
                    label = "$rightName says",
                    ownGames = rightOwnGames,
                    opponentGames = rightOpponentGames,
                    onOwnChange = { rightOwnGames = sanitizeScoreCell(it, maxDigits = 2) },
                    onOpponentChange = { rightOpponentGames = sanitizeScoreCell(it, maxDigits = 2) },
                )
                Text(
                    text = "Example: if both type 4 and 3, each means 4:3 to themselves.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Who serves now?", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { currentServerSide = ResolverSide.LEFT },
                        colors = if (currentServerSide == ResolverSide.LEFT) {
                            ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(leftName, textAlign = TextAlign.Center)
                    }
                    OutlinedButton(
                        onClick = { currentServerSide = ResolverSide.RIGHT },
                        colors = if (currentServerSide == ResolverSide.RIGHT) {
                            ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(rightName, textAlign = TextAlign.Center)
                    }
                }
                resolution?.let { result ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(result.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(result.details, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    resolution = resolveScoreArgument(
                        leftClaim = "$leftOwnGames:$leftOpponentGames",
                        rightClaim = "$rightOwnGames:$rightOpponentGames",
                        initialServer = initialServerSide,
                        currentServer = currentServerSide,
                        leftName = leftName,
                        rightName = rightName,
                    )
                },
            ) {
                Text("Solve")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun ArgumentScoreRow(
    label: String,
    ownGames: String,
    opponentGames: String,
    onOwnChange: (String) -> Unit,
    onOpponentChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Own",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(36.dp),
        )
        TextField(
            value = ownGames,
            onValueChange = { raw ->
                val sanitized = sanitizeScoreCell(raw, maxDigits = 2)
                onOwnChange(sanitized)
                if (ownGames.isEmpty() && sanitized.length == 1) {
                    focusManager.moveFocus(FocusDirection.Next)
                }
            },
            singleLine = true,
            modifier = Modifier.width(68.dp),
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Next) },
            ),
        )
        Text(":", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        TextField(
            value = opponentGames,
            onValueChange = { raw ->
                val sanitized = sanitizeScoreCell(raw, maxDigits = 2)
                onOpponentChange(sanitized)
                if (opponentGames.isEmpty() && sanitized.length == 1) {
                    focusManager.moveFocus(FocusDirection.Next)
                }
            },
            singleLine = true,
            modifier = Modifier.width(68.dp),
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Next) },
            ),
        )
        Text(
            text = "Opp",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun FinalScoreDialog(
    leftName: String,
    rightName: String,
    onDismiss: () -> Unit,
    onSubmit: (MatchWinnerSide, String) -> Unit,
) {
    var winner by remember { mutableStateOf<MatchWinnerSide?>(null) }
    var showRetConfirmation by remember { mutableStateOf(false) }
    var isThirdSetMatchTieBreakToTen by remember { mutableStateOf(false) }
    var set1Left by remember { mutableStateOf("") }
    var set1Right by remember { mutableStateOf("") }
    var set1TieBreak by remember { mutableStateOf("") }
    var set2Left by remember { mutableStateOf("") }
    var set2Right by remember { mutableStateOf("") }
    var set2TieBreak by remember { mutableStateOf("") }
    var set3Left by remember { mutableStateOf("") }
    var set3Right by remember { mutableStateOf("") }
    var set3TieBreak by remember { mutableStateOf("") }
    val showThirdSet = needsThirdSet(set1Left, set1Right, set2Left, set2Right)
    val scoreText = buildScoreFromSetFields(
        listOf(
            SetScoreInput(set1Left, set1Right, set1TieBreak),
            SetScoreInput(set2Left, set2Right, set2TieBreak),
            SetScoreInput(set3Left, set3Right, set3TieBreak),
        ).take(if (showThirdSet) 3 else 2),
    )
    val isIncomplete = isIncompleteScore(scoreText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Winner and Score") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Winner", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { winner = MatchWinnerSide.LEFT },
                        modifier = Modifier.weight(1f),
                        colors = if (winner == MatchWinnerSide.LEFT) {
                            ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                    ) {
                        Text(leftName, textAlign = TextAlign.Center)
                    }
                    OutlinedButton(
                        onClick = { winner = MatchWinnerSide.RIGHT },
                        modifier = Modifier.weight(1f),
                        colors = if (winner == MatchWinnerSide.RIGHT) {
                            ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                    ) {
                        Text(rightName, textAlign = TextAlign.Center)
                    }
                }

                Text("Score", style = MaterialTheme.typography.titleSmall)
                ScoreSetRow(
                    label = "Set 1",
                    left = set1Left,
                    right = set1Right,
                    tieBreakLoser = set1TieBreak,
                    onLeftChange = { set1Left = sanitizeScoreCell(it, maxDigits = 1) },
                    onRightChange = { set1Right = sanitizeScoreCell(it, maxDigits = 1) },
                    onTieBreakLoserChange = { set1TieBreak = sanitizeTieBreakCell(it) },
                    showTieBreakFields = isTieBreakSet(set1Left, set1Right),
                    autoAdvanceAfterSingleDigit = true,
                    isLastVisibleRow = false,
                )
                ScoreSetRow(
                    label = "Set 2",
                    left = set2Left,
                    right = set2Right,
                    tieBreakLoser = set2TieBreak,
                    onLeftChange = { set2Left = sanitizeScoreCell(it, maxDigits = 1) },
                    onRightChange = { set2Right = sanitizeScoreCell(it, maxDigits = 1) },
                    onTieBreakLoserChange = { set2TieBreak = sanitizeTieBreakCell(it) },
                    showTieBreakFields = isTieBreakSet(set2Left, set2Right),
                    autoAdvanceAfterSingleDigit = true,
                    isLastVisibleRow = !showThirdSet,
                )
                if (showThirdSet) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        FilterChip(
                            selected = isThirdSetMatchTieBreakToTen,
                            onClick = { isThirdSetMatchTieBreakToTen = !isThirdSetMatchTieBreakToTen },
                            label = { Text("Set 3 MTB to 10") },
                        )
                    }
                    ScoreSetRow(
                        label = "Set 3",
                        left = set3Left,
                        right = set3Right,
                        tieBreakLoser = set3TieBreak,
                        onLeftChange = {
                            set3Left = sanitizeScoreCell(
                                it,
                                maxDigits = if (isThirdSetMatchTieBreakToTen) 2 else 1,
                            )
                        },
                        onRightChange = {
                            set3Right = sanitizeScoreCell(
                                it,
                                maxDigits = if (isThirdSetMatchTieBreakToTen) 2 else 1,
                            )
                        },
                        onTieBreakLoserChange = { set3TieBreak = sanitizeTieBreakCell(it) },
                        showTieBreakFields = isTieBreakSet(set3Left, set3Right),
                        autoAdvanceAfterSingleDigit = !isThirdSetMatchTieBreakToTen,
                        isLastVisibleRow = true,
                    )
                }
                if (scoreText.isNotBlank()) {
                    Text(
                        text = "Recorded as: $scoreText",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val winnerSide = winner ?: return@Button
                    if (isIncomplete) {
                        showRetConfirmation = true
                    } else {
                        onSubmit(winnerSide, scoreText.trim())
                    }
                },
                enabled = winner != null,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    if (showRetConfirmation) {
        AlertDialog(
            onDismissRequest = { showRetConfirmation = false },
            title = { Text("Incomplete Score") },
            text = {
                Text("Submit incomplete score? The match will be marked as RET.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val winnerSide = winner ?: return@Button
                        showRetConfirmation = false
                        onSubmit(winnerSide, scoreText.trim())
                    },
                ) {
                    Text("Submit RET")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRetConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ParticipantSideCard(
    modifier: Modifier,
    side: SideParticipant?,
    selectedServerPlayerId: Long?,
    canSelectServer: Boolean,
    onSelectServer: (Long) -> Unit,
    isWinner: Boolean,
) {
    Card(
        modifier = modifier,
        border = if (isWinner) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
        } else {
            null
        },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isWinner) {
                Text(
                    text = "Winner",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            val players = side?.players.orEmpty()
            if (players.isNotEmpty()) {
                players.forEach { player ->
                    val isSelectedServer = player.playerId == selectedServerPlayerId
                    val backgroundColor = when {
                        isWinner -> MaterialTheme.colorScheme.primaryContainer
                        isSelectedServer -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(backgroundColor)
                            .clickable(enabled = canSelectServer, onClick = { onSelectServer(player.playerId) })
                            .height(94.dp)
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val flag = countryCodeToFlagEmoji(player.countryCode)
                        val (firstLine, secondLine) = splitNameIntoTwoLines(player.name)
                        val firstLineWithFlag = if (flag != null) "$flag $firstLine" else firstLine
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AutoSizingNameLine(
                                text = firstLineWithFlag,
                                isEmphasized = isWinner,
                            )
                            if (!secondLine.isNullOrBlank()) {
                                AutoSizingNameLine(
                                    text = secondLine,
                                    isEmphasized = isWinner,
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = side?.label ?: "TBD",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ScoreSetRow(
    label: String,
    left: String,
    right: String,
    tieBreakLoser: String,
    onLeftChange: (String) -> Unit,
    onRightChange: (String) -> Unit,
    onTieBreakLoserChange: (String) -> Unit,
    showTieBreakFields: Boolean,
    autoAdvanceAfterSingleDigit: Boolean,
    isLastVisibleRow: Boolean,
) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextField(
            value = left,
            onValueChange = { raw ->
                val sanitized = raw.filter { it.isDigit() }
                onLeftChange(sanitized)
                if (autoAdvanceAfterSingleDigit && left.isEmpty() && sanitized.length == 1) {
                    focusManager.moveFocus(FocusDirection.Next)
                }
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Next) },
            ),
        )
        Text(":")
        TextField(
            value = right,
            onValueChange = { raw ->
                val sanitized = raw.filter { it.isDigit() }
                onRightChange(sanitized)
                if (autoAdvanceAfterSingleDigit && right.isEmpty() && sanitized.length == 1) {
                    focusManager.moveFocus(FocusDirection.Next)
                }
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = if (showTieBreakFields || !isLastVisibleRow) ImeAction.Next else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Next) },
                onDone = { focusManager.clearFocus() },
            ),
        )
    }
    if (showTieBreakFields) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Tiebreak",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextField(
                value = tieBreakLoser,
                onValueChange = { raw ->
                    val sanitized = raw.filter { it.isDigit() }.take(1)
                    onTieBreakLoserChange(sanitized)
                    if (sanitized.length == 1) {
                        if (isLastVisibleRow) {
                            focusManager.clearFocus()
                        } else {
                            focusManager.moveFocus(FocusDirection.Next)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = if (isLastVisibleRow) ImeAction.Done else ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Next) },
                    onDone = { focusManager.clearFocus() },
                ),
            )
        }
    }
}

@Composable
private fun RestPromptDialog(
    prompt: RestPrompt,
    onDismiss: () -> Unit,
    onConfirm: (Map<Long, Int>) -> Unit,
) {
    val restByPlayer = remember(prompt) {
        mutableStateMapOf<Long, String>().apply {
            prompt.players.forEach { player ->
                put(player.playerId, player.suggestedRestMinutes.toString())
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rest Request") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Match lasted ${prompt.matchDurationMinutes} minutes. Set rest for players with upcoming matches.",
                    style = MaterialTheme.typography.bodySmall,
                )
                prompt.players.forEach { player ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = displayPlayerNameWithFlag(player.playerName, player.countryCode),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${player.upcomingCourtName ?: "Unassigned"} · ${player.upcomingMatchLabel}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("Rest (min)", style = MaterialTheme.typography.bodySmall)
                                TextField(
                                    value = restByPlayer[player.playerId].orEmpty(),
                                    onValueChange = { value ->
                                        restByPlayer[player.playerId] = value.filter { it.isDigit() }.take(3)
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val values = prompt.players.associate { player ->
                        val minutes = restByPlayer[player.playerId]
                            ?.toIntOrNull()
                            ?.coerceIn(0, 180)
                            ?: player.suggestedRestMinutes
                        player.playerId to minutes
                    }
                    onConfirm(values)
                },
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Later") }
        },
    )
}

@Composable
private fun AutoSizingNameLine(
    text: String,
    isEmphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    val baseStyle = MaterialTheme.typography.titleLarge
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val fontSize = remember(text, availableWidthPx) {
            chooseFontSizeToFitLine(
                text = text,
                maxWidthPx = availableWidthPx,
                textMeasurer = textMeasurer,
                baseStyle = baseStyle,
                fontWeight = if (isEmphasized) FontWeight.ExtraBold else FontWeight.SemiBold,
                max = 30.sp,
                min = 7.sp,
            )
        }
        Text(
            text = text,
            style = baseStyle.copy(
                fontSize = fontSize,
                fontWeight = if (isEmphasized) FontWeight.ExtraBold else FontWeight.SemiBold,
                lineHeight = (fontSize.value + 2f).sp,
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun chooseFontSizeToFitLine(
    text: String,
    maxWidthPx: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    baseStyle: TextStyle,
    fontWeight: FontWeight,
    max: TextUnit,
    min: TextUnit,
): TextUnit {
    var size = max.value
    while (size >= min.value) {
        val measured = textMeasurer.measure(
            text = text,
            style = baseStyle.copy(fontSize = size.sp, fontWeight = fontWeight),
            maxLines = 1,
            softWrap = false,
        )
        if (measured.size.width <= maxWidthPx) {
            return size.sp
        }
        size -= 1f
    }
    return min
}

private fun buildScoreLabel(scoreText: String?, resultType: String?): String? {
    val score = scoreText?.trim().orEmpty()
    return when (resultType) {
        "RETIREMENT" -> if (score.isBlank()) "RET" else "$score RET"
        "WALKOVER" -> if (score.isBlank()) "WO" else "$score WO"
        "DEFAULT" -> if (score.isBlank()) "DEF" else "$score DEF"
        else -> score.ifBlank { null }
    }
}

private fun initialServerSide(detail: CourtDetail): ResolverSide? {
    val serverId = detail.initialServerPlayerId ?: return null
    val leftHas = detail.leftSide?.players.orEmpty().any { it.playerId == serverId }
    val rightHas = detail.rightSide?.players.orEmpty().any { it.playerId == serverId }
    return when {
        leftHas -> ResolverSide.LEFT
        rightHas -> ResolverSide.RIGHT
        else -> null
    }
}

private fun parseScoreClaim(value: String): Pair<Int, Int>? {
    val match = Regex("^\\s*(\\d{1,2})\\s*[:\\-]\\s*(\\d{1,2})\\s*$").matchEntire(value) ?: return null
    val left = match.groupValues[1].toIntOrNull() ?: return null
    val right = match.groupValues[2].toIntOrNull() ?: return null
    return left to right
}

private fun resolveScoreArgument(
    leftClaim: String,
    rightClaim: String,
    initialServer: ResolverSide?,
    currentServer: ResolverSide?,
    leftName: String,
    rightName: String,
): ScoreArgumentResolution {
    val leftParsed = parseScoreClaim(leftClaim)
        ?: return ScoreArgumentResolution("Need score", "Enter $leftName claim as games, e.g. 4:3.")
    val rightParsed = parseScoreClaim(rightClaim)
        ?: return ScoreArgumentResolution("Need score", "Enter $rightName claim as games, e.g. 3:4.")
    if (initialServer == null || currentServer == null) {
        return ScoreArgumentResolution(
            "Need server info",
            "Set both initial server and current server side to solve this automatically.",
        )
    }

    val leftCandidate = ScoreCandidate(
        leftGames = leftParsed.first,
        rightGames = leftParsed.second,
    )
    val rightCandidate = ScoreCandidate(
        leftGames = rightParsed.second,
        rightGames = rightParsed.first,
    )

    val leftValid = candidateMatchesServer(leftCandidate, initialServer, currentServer)
    val rightValid = candidateMatchesServer(rightCandidate, initialServer, currentServer)
    val sameScore = leftCandidate.leftGames == rightCandidate.leftGames &&
        leftCandidate.rightGames == rightCandidate.rightGames

    return when {
        sameScore && (leftValid || rightValid) -> ScoreArgumentResolution(
            title = "Both can be right",
            details = "Score ${leftCandidate.leftGames}:${leftCandidate.rightGames} fits the serving order.",
        )

        leftValid && !rightValid -> ScoreArgumentResolution(
            title = "$leftName is right",
            details = "Use score ${leftCandidate.leftGames}:${leftCandidate.rightGames}.",
        )

        rightValid && !leftValid -> ScoreArgumentResolution(
            title = "$rightName is right",
            details = "Use score ${rightCandidate.leftGames}:${rightCandidate.rightGames}.",
        )

        leftValid && rightValid -> ScoreArgumentResolution(
            title = "Both are possible",
            details = "Both claims fit serving order. Referee should decide.",
        )

        else -> ScoreArgumentResolution(
            title = "Cannot solve automatically",
            details = "Neither claim matches serving order. Referee should decide.",
        )
    }
}

private fun candidateMatchesServer(
    candidate: ScoreCandidate,
    initialServer: ResolverSide,
    currentServer: ResolverSide,
): Boolean {
    if (candidate.leftGames < 0 || candidate.rightGames < 0) return false
    val totalGames = candidate.leftGames + candidate.rightGames
    val expected = if (totalGames % 2 == 0) {
        initialServer
    } else if (initialServer == ResolverSide.LEFT) {
        ResolverSide.RIGHT
    } else {
        ResolverSide.LEFT
    }
    return expected == currentServer
}

private fun sanitizeScoreCell(raw: String, maxDigits: Int): String {
    return raw.filter { it.isDigit() }.take(maxDigits)
}

private fun sanitizeTieBreakCell(raw: String): String {
    return raw.filter { it.isDigit() }.take(1)
}

private data class SetScoreInput(
    val left: String,
    val right: String,
    val tieBreakLoser: String,
)

private enum class SetWinner {
    LEFT,
    RIGHT,
}

private fun parseSetWinner(left: String, right: String): SetWinner? {
    val leftValue = left.toIntOrNull() ?: return null
    val rightValue = right.toIntOrNull() ?: return null
    if (leftValue == rightValue) return null
    return if (leftValue > rightValue) SetWinner.LEFT else SetWinner.RIGHT
}

private fun needsThirdSet(
    set1Left: String,
    set1Right: String,
    set2Left: String,
    set2Right: String,
): Boolean {
    val set1Winner = parseSetWinner(set1Left, set1Right) ?: return false
    val set2Winner = parseSetWinner(set2Left, set2Right) ?: return false
    return set1Winner != set2Winner
}

private fun isTieBreakSet(left: String, right: String): Boolean {
    val leftValue = left.toIntOrNull() ?: return false
    val rightValue = right.toIntOrNull() ?: return false
    return (leftValue == 7 && rightValue == 6) || (leftValue == 6 && rightValue == 7)
}

private fun buildScoreFromSetFields(sets: List<SetScoreInput>): String {
    return sets
        .mapNotNull { set ->
            val leftValue = set.left.trim()
            val rightValue = set.right.trim()
            if (leftValue.isBlank() && rightValue.isBlank()) {
                null
            } else {
                val base = "$leftValue:$rightValue"
                if (isTieBreakSet(leftValue, rightValue)) {
                    val tbLoser = set.tieBreakLoser.trim()
                    if (tbLoser.isNotBlank()) "$base($tbLoser)" else base
                } else {
                    base
                }
            }
        }
        .joinToString(" ")
}

private fun isIncompleteScore(scoreText: String): Boolean {
    val normalized = scoreText.trim()
    if (normalized.isBlank()) return false
    val tokens = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return true
    val parsed = mutableListOf<Pair<Int, Int>>()
    for (token in tokens) {
        val setToken = token.substringBefore("(").trim()
        val match = Regex("(\\d+)\\s*[:-]\\s*(\\d+)").matchEntire(setToken) ?: return true
        val left = match.groupValues[1].toIntOrNull() ?: return true
        val right = match.groupValues[2].toIntOrNull() ?: return true
        parsed += left to right
    }
    return parsed.any { (left, right) -> !isCompleteSet(left, right) }
}

private fun isCompleteSet(leftGames: Int, rightGames: Int): Boolean {
    if (leftGames < 0 || rightGames < 0) return false
    val high = maxOf(leftGames, rightGames)
    val low = minOf(leftGames, rightGames)
    if (high >= 10) return high - low >= 2
    if (high == 7 && (low == 5 || low == 6)) return true
    return high >= 6 && high - low >= 2
}

private fun matchDurationLabel(startedAtMillis: Long?, endedAtMillis: Long?): String? {
    val started = startedAtMillis ?: return null
    val ended = endedAtMillis ?: return null
    val durationMs = (ended - started).coerceAtLeast(0L)
    return "Played ${formatDuration(durationMs)}"
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

private fun timerButtonLabel(timerState: CourtTimerState?): String {
    if (timerState == null) return "Timers"
    return if (timerState.isRunning) {
        "${timerState.timerType.label} ${formatRemaining(timerState.remainingMillis)}"
    } else {
        "Timers"
    }
}

private fun formatRemaining(remainingMillis: Long): String {
    val totalSeconds = (remainingMillis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

private fun sidePrimaryLabel(side: SideParticipant?): String {
    val players = side?.players.orEmpty()
    return if (players.isNotEmpty()) {
        players.joinToString(" / ") { displayPlayerLabel(it).replace('\n', ' ') }
    } else {
        side?.label ?: "TBD"
    }
}

private fun totalViolationCount(countsByPlayer: Map<Long, ViolationCounts>): Int {
    return countsByPlayer.values.sumOf { counts ->
        counts.codeViolationCount + counts.timeViolationCountServer + counts.timeViolationCountReceiver
    }
}

private fun categoryCount(counts: ViolationCounts, category: ViolationCategory): Int {
    return when (category) {
        ViolationCategory.CODE -> counts.codeViolationCount
        ViolationCategory.TIME_SERVER -> counts.timeViolationCountServer
        ViolationCategory.TIME_RECEIVER -> counts.timeViolationCountReceiver
    }
}

private fun penaltyForOffence(offenceNumber: Int): String {
    return when {
        offenceNumber <= 1 -> "Warning"
        offenceNumber == 2 -> "Point Penalty"
        offenceNumber == 3 -> "Game Penalty"
        else -> "Default"
    }
}

private fun buildViolationGuidanceSummary(counts: ViolationCounts): String {
    val chunks = mutableListOf<String>()
    if (counts.codeViolationCount > 0) {
        chunks += "Code next: ${penaltyForOffence(counts.codeViolationCount + 1)}"
    }
    if (counts.timeViolationCountServer > 0) {
        chunks += "Server TV next: ${penaltyForOffence(counts.timeViolationCountServer + 1)}"
    }
    if (counts.timeViolationCountReceiver > 0) {
        chunks += "Receiver TV next: ${penaltyForOffence(counts.timeViolationCountReceiver + 1)}"
    }
    return chunks.joinToString(" • ")
}

private fun displayPlayerLabel(player: PlayerRef): String {
    return displayPlayerNameWithFlag(player.name, player.countryCode)
}

private fun displayPlayerNameWithFlag(name: String, countryCode: String?): String {
    val flag = countryCodeToFlagEmoji(countryCode)
    return if (flag != null) "$flag $name" else name
}

private fun splitNameIntoTwoLines(name: String): Pair<String, String?> {
    val parts = name.trim().split(Regex("\\s+"))
    if (parts.isEmpty()) return name to null
    if (parts.size == 1) return parts.first() to null
    return parts.first() to parts.drop(1).joinToString(" ")
}

private fun countryCodeToFlagEmoji(countryCode: String?): String? {
    val normalized = countryCode?.trim()?.uppercase()?.ifBlank { null } ?: return null
    val iso2 = if (normalized.length == 2) {
        normalized
    } else {
        ISO3_TO_ISO2[normalized]
    } ?: return null

    if (iso2.length != 2 || !iso2.all { it in 'A'..'Z' }) return null
    val first = Character.codePointAt(iso2, 0) - 0x41 + 0x1F1E6
    val second = Character.codePointAt(iso2, 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

private val ISO3_TO_ISO2 = mapOf(
    "BEL" to "BE",
    "CHN" to "CN",
    "CZE" to "CZ",
    "DEN" to "DK",
    "EST" to "EE",
    "FIN" to "FI",
    "GBR" to "GB",
    "GER" to "DE",
    "ISR" to "IL",
    "ITA" to "IT",
    "JPN" to "JP",
    "LAT" to "LV",
    "LIE" to "LI",
    "LTU" to "LT",
    "POL" to "PL",
    "ROU" to "RO",
    "SLO" to "SI",
    "SUI" to "CH",
    "SWE" to "SE",
    "UKR" to "UA",
)

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
    CourtStatus.SCHEDULED -> MaterialTheme.colorScheme.primary
    CourtStatus.IN_PROGRESS -> androidx.compose.ui.graphics.Color(0xFFE0B422)
    CourtStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
}

private fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

@Preview(showBackground = true)
@Composable
private fun ParticipantSideCardPreview() {
    TennisRefTheme {
        Box(modifier = Modifier.padding(8.dp)) {
            ParticipantSideCard(
                modifier = Modifier.fillMaxWidth(),
                side = SideParticipant(
                    slotId = 1,
                    label = "Player",
                    players = listOf(
                        PlayerRef(1, "Yiyuan Su", "CHN"),
                        PlayerRef(2, "Vincent Fletcher", "GBR"),
                    ),
                    resolvedTeamId = 1,
                    isResolved = true,
                ),
                selectedServerPlayerId = 2,
                canSelectServer = true,
                onSelectServer = {},
                isWinner = false,
            )
        }
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
                    courtName = "Court 2",
                    status = CourtStatus.SCHEDULED,
                    matchId = 101,
                    leftSide = SideParticipant(
                        slotId = 1,
                        label = "Left",
                        players = listOf(PlayerRef(1, "Hanna Vanni", "FIN")),
                        resolvedTeamId = 1,
                        isResolved = true,
                    ),
                    rightSide = SideParticipant(
                        slotId = 2,
                        label = "Right",
                        players = listOf(PlayerRef(2, "Vera Morozova", "EST")),
                        resolvedTeamId = 2,
                        isResolved = true,
                    ),
                    computedNotBeforeMillis = 1_700_000_000_000,
                    startedAtMillis = null,
                    endedAtMillis = null,
                    initialServerPlayerId = null,
                    winnerTeamId = null,
                    canSwapSides = true,
                    canStartMatch = true,
                    scoreText = null,
                    resultType = null,
                ),
                selectedServerPlayerId = 1,
                timerState = CourtTimerState(
                    courtId = 1,
                    matchId = 101,
                    courtName = "Court 2",
                    timerType = CourtTimerType.WARMUP,
                    remainingMillis = 120_000,
                    isRunning = true,
                ),
            ),
            onBack = {},
            onSelectServer = {},
            onSwapSides = {},
            onStartMatchNow = {},
            onStartTimer = {},
            onStopTimer = {},
            onApplyViolation = { _, _, _ -> },
            onSubmitFinalScore = { _, _ -> },
            onDismissRestPrompt = {},
            onApplyRestChoices = {},
            onUndoViolation = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview(showBackground = true, heightDp = 820)
@Composable
private fun CourtDetailCompletedPreview() {
    TennisRefTheme {
        CourtDetailScreen(
            uiState = CourtDetailUiState(
                detail = CourtDetail(
                    courtId = 6,
                    courtName = "Court 7",
                    status = CourtStatus.COMPLETED,
                    matchId = 120,
                    leftSide = SideParticipant(
                        slotId = 10,
                        label = "Left",
                        players = listOf(PlayerRef(10, "Edwin Averjanov", "EST")),
                        resolvedTeamId = 10,
                        isResolved = true,
                    ),
                    rightSide = SideParticipant(
                        slotId = 11,
                        label = "Right",
                        players = listOf(PlayerRef(11, "Jan Sykora", "CZE")),
                        resolvedTeamId = 11,
                        isResolved = true,
                    ),
                    computedNotBeforeMillis = null,
                    startedAtMillis = 1_700_000_000_000,
                    endedAtMillis = 1_700_002_100_000,
                    initialServerPlayerId = 10,
                    winnerTeamId = 11,
                    canSwapSides = false,
                    canStartMatch = false,
                    scoreText = "4-6 3-6",
                    resultType = "NORMAL",
                ),
            ),
            onBack = {},
            onSelectServer = {},
            onSwapSides = {},
            onStartMatchNow = {},
            onStartTimer = {},
            onStopTimer = {},
            onApplyViolation = { _, _, _ -> },
            onSubmitFinalScore = { _, _ -> },
            onDismissRestPrompt = {},
            onApplyRestChoices = {},
            onUndoViolation = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
