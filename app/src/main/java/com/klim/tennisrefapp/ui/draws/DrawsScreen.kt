package com.klim.tennisrefapp.ui.draws

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klim.tennisrefapp.domain.model.CourtStatus
import com.klim.tennisrefapp.domain.model.DrawBoard
import com.klim.tennisrefapp.domain.model.DrawMatchNode
import com.klim.tennisrefapp.domain.model.DrawRound
import com.klim.tennisrefapp.ui.theme.TennisRefTheme

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DrawsScreen(
    uiState: DrawsUiState,
    onBack: () -> Unit,
) {
    var selectedBoardCode by rememberSaveable { mutableStateOf<String?>(null) }
    val boards = uiState.boards
    val selectedBoard = boards.firstOrNull { it.eventCode == selectedBoardCode } ?: boards.firstOrNull()
    val rounds = selectedBoard?.rounds.orEmpty()
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { rounds.size.coerceAtLeast(1) },
    )

    LaunchedEffect(selectedBoard?.eventCode) {
        if (selectedBoard != null) pagerState.scrollToPage(0)
    }

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
                boards.forEach { board ->
                    FilterChip(
                        onClick = { selectedBoardCode = board.eventCode },
                        selected = selectedBoard?.eventCode == board.eventCode,
                        label = {
                            Text(
                                if (board.eventCode == "BSQ") "Boys Qualification"
                                else if (board.eventCode == "GSQ") "Girls Qualification"
                                else board.eventCode,
                            )
                        },
                    )
                }
            }

            if (selectedBoard == null || rounds.isEmpty()) {
                Text(
                    text = "No rounds available.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                return@Column
            }

            val currentRound = rounds.getOrElse(pagerState.currentPage) { rounds.last() }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currentRound.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${pagerState.currentPage + 1}/${rounds.size}",
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                DrawRoundPage(round = rounds[page])
            }
        }
    }
}

@Composable
private fun DrawRoundPage(round: DrawRound) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(round.matches, key = { match -> match.matchId }) { match ->
            DrawBracketMatchCard(match = match)
        }
    }
}

@Composable
private fun DrawBracketMatchCard(match: DrawMatchNode) {
    val winnerName = match.winnerLabel?.trim().orEmpty()
    val leftWins = winnerName.isNotBlank() && match.leftLabel.contains(winnerName, ignoreCase = true)
    val rightWins = winnerName.isNotBlank() && match.rightLabel.contains(winnerName, ignoreCase = true)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DrawEntryRow(
                label = match.leftLabel,
                isWinner = leftWins,
            )
            HorizontalDivider()
            DrawEntryRow(
                label = match.rightLabel,
                isWinner = rightWins,
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
                    color = statusColor(match.status),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            match.scoreLabel?.let { score ->
                Text(
                    text = "Score: $score",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun DrawEntryRow(
    label: String,
    isWinner: Boolean,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Medium,
    )
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
private fun statusColor(status: CourtStatus): androidx.compose.ui.graphics.Color {
    return when (status) {
        CourtStatus.EMPTY -> MaterialTheme.colorScheme.outline
        CourtStatus.SCHEDULED -> MaterialTheme.colorScheme.primary
        CourtStatus.IN_PROGRESS -> androidx.compose.ui.graphics.Color(0xFFE0B422)
        CourtStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
    }
}

@Preview(showBackground = true)
@Composable
private fun DrawBracketMatchCardPreview() {
    TennisRefTheme {
        DrawBracketMatchCard(
            match = DrawMatchNode(
                matchId = 1L,
                order = 1,
                courtName = "Court 4",
                leftLabel = "🇺🇸 Hanna Vanni [7]",
                rightLabel = "🇪🇪 Vera Morozova",
                winnerLabel = "Hanna Vanni",
                scoreLabel = "6:4 6:3",
                status = CourtStatus.COMPLETED,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DrawsScreenPreview() {
    TennisRefTheme {
        DrawsScreen(
            uiState = DrawsUiState(
                boards = listOf(
                    DrawBoard(
                        eventCode = "BSQ",
                        rounds = listOf(
                            DrawRound(
                                depth = 1,
                                label = "Round 1",
                                matches = listOf(
                                    DrawMatchNode(
                                        matchId = 1L,
                                        order = 1,
                                        courtName = "Court 4",
                                        leftLabel = "🇫🇮 Player One",
                                        rightLabel = "🇪🇪 Player Two",
                                        winnerLabel = null,
                                        scoreLabel = null,
                                        status = CourtStatus.SCHEDULED,
                                    ),
                                ),
                            ),
                            DrawRound(
                                depth = 2,
                                label = "Round 2",
                                matches = listOf(
                                    DrawMatchNode(
                                        matchId = 2L,
                                        order = 2,
                                        courtName = "Court 6",
                                        leftLabel = "TBD",
                                        rightLabel = "TBD",
                                        winnerLabel = null,
                                        scoreLabel = null,
                                        status = CourtStatus.SCHEDULED,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            onBack = {},
        )
    }
}
