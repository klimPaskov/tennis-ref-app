package com.klim.tennisrefapp.ui.draws

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klim.tennisrefapp.domain.model.CourtStatus
import com.klim.tennisrefapp.domain.model.DrawBoard
import com.klim.tennisrefapp.domain.model.DrawMatchNode
import com.klim.tennisrefapp.domain.model.DrawRound

@Composable
fun DrawsRoute(
    onBack: () -> Unit,
    viewModel: DrawsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DrawsScreen(
        uiState = uiState,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawsScreen(
    uiState: DrawsUiState,
    onBack: () -> Unit,
) {
    var selectedBoardCode by rememberSaveable { mutableStateOf<String?>(null) }
    val boards = uiState.boards
    val selectedBoard = boards.firstOrNull { it.eventCode == selectedBoardCode } ?: boards.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Draws") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (boards.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No draw available for the current order of play.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("draw_selector") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    boards.forEach { board ->
                        FilterChip(
                            onClick = { selectedBoardCode = board.eventCode },
                            selected = selectedBoard?.eventCode == board.eventCode,
                            label = {
                                Text(if (board.eventCode == "BSQ") "Boys Qualification" else "Girls Qualification")
                            },
                        )
                    }
                }
            }
            selectedBoard?.let { board ->
                item("board_title_${board.eventCode}") {
                    Text(
                        text = if (board.eventCode == "BSQ") "Boys Qualification Draw" else "Girls Qualification Draw",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                board.rounds.forEach { round ->
                    item("round_title_${board.eventCode}_${round.depth}") {
                        Text(
                            text = round.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    items(round.matches, key = { match -> match.matchId }) { match ->
                        DrawMatchCard(match = match)
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawMatchCard(match: DrawMatchNode) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = matchPairWithWinner(
                    left = match.leftLabel,
                    right = match.rightLabel,
                    winner = match.winnerLabel,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = match.courtName ?: "Unassigned",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = statusLabel(match.status),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            match.scoreLabel?.let { score ->
                Text(
                    text = "Score: $score",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun matchPairWithWinner(left: String, right: String, winner: String?) = buildAnnotatedString {
    val winnerName = winner?.trim().orEmpty()
    val leftWins = winnerName.isNotBlank() && left.contains(winnerName, ignoreCase = true)
    val rightWins = winnerName.isNotBlank() && right.contains(winnerName, ignoreCase = true)
    if (leftWins) {
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append(left) }
    } else {
        append(left)
    }
    append(" vs ")
    if (rightWins) {
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append(right) }
    } else {
        append(right)
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
