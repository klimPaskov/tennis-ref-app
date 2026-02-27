package com.klim.tennisrefapp.ui.players

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klim.tennisrefapp.common.CountryFlags
import kotlinx.coroutines.delay

@Composable
fun PlayersRoute(
    onBack: () -> Unit,
    onPlayerClick: (Long) -> Unit,
    viewModel: PlayersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PlayersScreen(
        uiState = uiState,
        onBack = onBack,
        onPlayerClick = onPlayerClick,
        onSortSelected = viewModel::onSortSelected,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayersScreen(
    uiState: PlayersUiState,
    onBack: () -> Unit,
    onPlayerClick: (Long) -> Unit,
    onSortSelected: (PlayersSort) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
) {
    var clicksEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(250L)
        clicksEnabled = true
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Players") },
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
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = onSearchQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search player") },
                    singleLine = true,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PlayersSort.entries) { sort ->
                        FilterChip(
                            selected = uiState.sort == sort,
                            onClick = { onSortSelected(sort) },
                            label = { Text(playersSortLabel(sort)) },
                        )
                    }
                }
            }
            items(uiState.players, key = { it.playerId }) { player ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = clicksEnabled, onClick = { onPlayerClick(player.playerId) }),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val flag = countryCodeToFlagEmoji(player.countryCode)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (flag != null) "$flag ${player.name}" else player.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val nextOnInline = player.nextNotBeforeMillis?.let { formatTime(it) }
                            Text(
                                text = nextOnInline ?: "-",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text("Completed matches: ${player.completedCount}", style = MaterialTheme.typography.bodySmall)
                        if (player.nextMatchLabel != null) {
                            val nextOn = player.nextNotBeforeMillis?.let { formatTime(it) } ?: "TBD"
                            Text(
                                text = "Next: ${player.nextCourtName ?: "Unassigned"} · $nextOn",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(player.nextMatchLabel, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("No upcoming match", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun playersSortLabel(sort: PlayersSort): String {
    return when (sort) {
        PlayersSort.NAME -> "Name"
        PlayersSort.COMPLETED -> "Completed"
        PlayersSort.NEXT_MATCH -> "Next Match"
    }
}

private fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun countryCodeToFlagEmoji(countryCode: String?): String? {
    return CountryFlags.toFlagEmoji(countryCode)
}
