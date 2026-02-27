package com.klim.tennisrefapp.ui.home

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeRoute(
    onContinue: () -> Unit,
    onPlayers: () -> Unit,
    onHistory: () -> Unit,
    onDraws: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val pickOrderOfPlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        viewModel.onOrderOfPlaySelected(uri?.toString())
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    HomeScreen(
        uiState = uiState,
        onContinue = onContinue,
        onNewOrderOfPlay = {
            pickOrderOfPlayLauncher.launch(arrayOf("image/*", "application/pdf", "text/plain", "*/*"))
        },
        onReloadCurrent = viewModel::onReloadCurrentOrderOfPlay,
        onLoadPreloaded = viewModel::onLoadPreloadedOrderOfPlay,
        onPlayers = onPlayers,
        onHistory = onHistory,
        onDraws = onDraws,
        onSettings = onSettings,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    onContinue: () -> Unit,
    onNewOrderOfPlay: () -> Unit,
    onReloadCurrent: () -> Unit,
    onLoadPreloaded: (String) -> Unit,
    onPlayers: () -> Unit,
    onHistory: () -> Unit,
    onDraws: () -> Unit,
    onSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var showPreloadedDialog by remember { mutableStateOf(false) }
    var showReloadConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Home") })
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
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Courts")
            }
            Button(onClick = onPlayers, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Players")
            }
            Button(onClick = onHistory, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("History")
            }
            Button(onClick = onDraws, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Draws")
            }
            Button(onClick = onSettings, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Settings")
            }
            Button(
                onClick = onNewOrderOfPlay,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !uiState.isReloading,
            ) {
                Text(if (uiState.isReloading) "Loading..." else "New Order Of Play")
            }
            Button(
                onClick = { showPreloadedDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !uiState.isReloading,
            ) {
                Text(if (uiState.isReloading) "Loading..." else "Load Preloaded Order Of Play")
            }
            OutlinedButton(
                onClick = { showReloadConfirmation = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !uiState.isReloading,
            ) {
                Text(if (uiState.isReloading) "Loading..." else "Reload")
            }
        }
    }

    if (showPreloadedDialog) {
        AlertDialog(
            onDismissRequest = { showPreloadedDialog = false },
            title = { Text("Choose Preloaded Order Of Play") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.preloadedOptions.forEach { option ->
                        OutlinedButton(
                            onClick = {
                                onLoadPreloaded(option.id)
                                showPreloadedDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(option.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPreloadedDialog = false }) {
                    Text("Close")
                }
            },
        )
    }

    if (showReloadConfirmation) {
        AlertDialog(
            onDismissRequest = { showReloadConfirmation = false },
            title = { Text("Reload Current Order Of Play") },
            text = {
                Text("This will clear all current match progress and reload the currently loaded order of play from scratch.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReloadCurrent()
                        showReloadConfirmation = false
                    },
                ) {
                    Text("Reload")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReloadConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
