package com.klim.tennisrefapp.ui.players

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PlayerDetailRoute(
    onBack: () -> Unit,
    viewModel: PlayerDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PlayerDetailScreen(uiState = uiState, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerDetailScreen(
    uiState: PlayerDetailUiState,
    onBack: () -> Unit,
) {
    val detail = uiState.detail
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Player Detail") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
            )
        },
    ) { padding ->
        if (detail == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            ) {
                Text("Player not found.")
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
            item {
                val flag = countryCodeToFlagEmoji(detail.countryCode)
                Text(
                    text = if (flag != null) "$flag ${detail.name}" else detail.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                Text("Next Match", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val next = detail.nextMatch
                if (next == null) {
                    Text("No upcoming match")
                } else {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(next.matchLabel)
                            Text("${next.courtName ?: "Unassigned"} · ${next.status.name}")
                            next.notBeforeMillis?.let { notBefore ->
                                Text("Not before ${formatDateTime(notBefore)}")
                            }
                        }
                    }
                }
            }
            item {
                Text("Completed Matches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(detail.completedMatches, key = { it.matchId }) { match ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(match.matchLabel)
                        Text(match.courtName ?: "Unassigned")
                        if (!match.scoreLabel.isNullOrBlank()) {
                            Text("Score: ${match.scoreLabel}")
                        }
                        match.endedAtMillis?.let { endedAt ->
                            Text("Ended ${formatDateTime(endedAt)}")
                        }
                    }
                }
            }
        }
    }
}

private fun formatDateTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
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
