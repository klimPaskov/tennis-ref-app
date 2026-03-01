package com.klim.tennisrefapp.ui.home

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
        onDeletePreloaded = viewModel::onDeletePreloadedOrderOfPlay,
        onSaveCurrentAsPreset = viewModel::onSaveCurrentAsPreset,
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
    onDeletePreloaded: (String) -> Unit,
    onSaveCurrentAsPreset: (String) -> Unit,
    onPlayers: () -> Unit,
    onHistory: () -> Unit,
    onDraws: () -> Unit,
    onSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var showPreloadedDialog by remember { mutableStateOf(false) }
    var showReloadConfirmation by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<PreloadedOrderOption?>(null) }
    var presetName by remember { mutableStateOf("") }

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
            Button(
                onClick = onDraws,
                enabled = uiState.hasDraws,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
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
                onClick = { showSavePresetDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !uiState.isSavingPreset && !uiState.isReloading,
            ) {
                Text(if (uiState.isSavingPreset) "Saving..." else "Save Current As Preset")
            }
            OutlinedButton(
                onClick = { showReloadConfirmation = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !uiState.isReloading,
            ) {
                Text(if (uiState.isReloading) "Loading..." else "Reload")
            }
            Text(
                text = "Made by Klim",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.CenterHorizontally),
            )
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
                    if (uiState.preloadedOptions.isEmpty()) {
                        Text("No presets saved.")
                    }
                    uiState.preloadedOptions.forEach { option ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    onLoadPreloaded(option.id)
                                    showPreloadedDialog = false
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(option.label)
                            }
                            if (option.isDeletable) {
                                IconButton(
                                    onClick = { deleteCandidate = option },
                                    enabled = !uiState.isDeletingPreset,
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .width(44.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete ${option.label}",
                                    )
                                }
                            }
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

    deleteCandidate?.let { option ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete Preset") },
            text = {
                Text("Delete preset \"${option.label}\"? This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePreloaded(option.id)
                        deleteCandidate = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancel")
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

    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Save Preset") },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveCurrentAsPreset(presetName)
                        presetName = ""
                        showSavePresetDialog = false
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        presetName = ""
                        showSavePresetDialog = false
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}
