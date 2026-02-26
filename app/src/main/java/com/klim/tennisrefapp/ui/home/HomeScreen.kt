package com.klim.tennisrefapp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeRoute(
    onContinue: () -> Unit,
    onPlayers: () -> Unit,
    onHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    HomeScreen(
        uiState = uiState,
        onContinue = onContinue,
        onReload = viewModel::onReloadOrderOfPlay,
        onPlayers = onPlayers,
        onHistory = onHistory,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    onContinue: () -> Unit,
    onReload: () -> Unit,
    onPlayers: () -> Unit,
    onHistory: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Order Of Play") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("Continue Order Of Play")
            }
            Button(
                onClick = onReload,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isReloading,
            ) {
                Text(if (uiState.isReloading) "Loading..." else "Load Order Of Play")
            }
            Button(onClick = onPlayers, modifier = Modifier.fillMaxWidth()) {
                Text("Players")
            }
            Button(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
                Text("History")
            }
        }
    }
}
