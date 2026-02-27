package com.klim.tennisrefapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klim.tennisrefapp.data.settings.AppSettings

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings = viewModel.uiState.collectAsStateWithLifecycle().value
    SettingsScreen(
        settings = settings,
        onBack = onBack,
        onAutoStartWarmupChanged = viewModel::onAutoStartWarmupChanged,
        onAutoStartMatchAfterWarmupChanged = viewModel::onAutoStartMatchAfterWarmupChanged,
        onWarmupOneMinuteWarningChanged = viewModel::onWarmupOneMinuteWarningChanged,
        onWarmupPendingReminderChanged = viewModel::onWarmupPendingReminderChanged,
        onIncrementWarmupMinutes = viewModel::incrementWarmupMinutes,
        onDecrementWarmupMinutes = viewModel::decrementWarmupMinutes,
        onIncrementSideSwitchSeconds = viewModel::incrementSideSwitchSeconds,
        onDecrementSideSwitchSeconds = viewModel::decrementSideSwitchSeconds,
        onIncrementSetBreakSeconds = viewModel::incrementSetBreakSeconds,
        onDecrementSetBreakSeconds = viewModel::decrementSetBreakSeconds,
        onIncrementToiletBreakSeconds = viewModel::incrementToiletBreakSeconds,
        onDecrementToiletBreakSeconds = viewModel::decrementToiletBreakSeconds,
        onIncrementOffCourtSeconds = viewModel::incrementOffCourtSeconds,
        onDecrementOffCourtSeconds = viewModel::decrementOffCourtSeconds,
        onIncrementMedicalTimeoutSeconds = viewModel::incrementMedicalTimeoutSeconds,
        onDecrementMedicalTimeoutSeconds = viewModel::decrementMedicalTimeoutSeconds,
        onIncrementEquipmentFixSeconds = viewModel::incrementEquipmentFixSeconds,
        onDecrementEquipmentFixSeconds = viewModel::decrementEquipmentFixSeconds,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onAutoStartWarmupChanged: (Boolean) -> Unit,
    onAutoStartMatchAfterWarmupChanged: (Boolean) -> Unit,
    onWarmupOneMinuteWarningChanged: (Boolean) -> Unit,
    onWarmupPendingReminderChanged: (Boolean) -> Unit,
    onIncrementWarmupMinutes: () -> Unit,
    onDecrementWarmupMinutes: () -> Unit,
    onIncrementSideSwitchSeconds: () -> Unit,
    onDecrementSideSwitchSeconds: () -> Unit,
    onIncrementSetBreakSeconds: () -> Unit,
    onDecrementSetBreakSeconds: () -> Unit,
    onIncrementToiletBreakSeconds: () -> Unit,
    onDecrementToiletBreakSeconds: () -> Unit,
    onIncrementOffCourtSeconds: () -> Unit,
    onDecrementOffCourtSeconds: () -> Unit,
    onIncrementMedicalTimeoutSeconds: () -> Unit,
    onDecrementMedicalTimeoutSeconds: () -> Unit,
    onIncrementEquipmentFixSeconds: () -> Unit,
    onDecrementEquipmentFixSeconds: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SectionTitle("Match Flow")
                    ToggleSettingRow(
                        label = "Auto-start warm-up after server selection",
                        checked = settings.autoStartWarmup,
                        onCheckedChange = onAutoStartWarmupChanged,
                    )
                    Divider()
                    ToggleSettingRow(
                        label = "Auto-start match when warm-up ends",
                        checked = settings.autoStartMatchAfterWarmup,
                        onCheckedChange = onAutoStartMatchAfterWarmupChanged,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SectionTitle("Alerts")
                    ToggleSettingRow(
                        label = "Warm-up warning at 1 minute",
                        checked = settings.warmupOneMinuteWarning,
                        onCheckedChange = onWarmupOneMinuteWarningChanged,
                    )
                    Divider()
                    ToggleSettingRow(
                        label = "Repeat warm-up reminder every 2 minutes",
                        checked = settings.warmupPendingReminder,
                        onCheckedChange = onWarmupPendingReminderChanged,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SectionTitle("Timer Durations")
                    StepperSettingRow(
                        label = "Warm-up",
                        valueText = "${settings.warmupMinutes} min",
                        onDecrement = onDecrementWarmupMinutes,
                        onIncrement = onIncrementWarmupMinutes,
                    )
                    Divider()
                    StepperSettingRow(
                        label = "Side switch",
                        valueText = "${settings.sideSwitchSeconds}s",
                        onDecrement = onDecrementSideSwitchSeconds,
                        onIncrement = onIncrementSideSwitchSeconds,
                    )
                    Divider()
                    StepperSettingRow(
                        label = "Set break",
                        valueText = "${settings.setBreakSeconds}s",
                        onDecrement = onDecrementSetBreakSeconds,
                        onIncrement = onIncrementSetBreakSeconds,
                    )
                    Divider()
                    StepperSettingRow(
                        label = "Toilet break",
                        valueText = "${settings.toiletBreakSeconds}s",
                        onDecrement = onDecrementToiletBreakSeconds,
                        onIncrement = onIncrementToiletBreakSeconds,
                    )
                    Divider()
                    StepperSettingRow(
                        label = "Off-court",
                        valueText = "${settings.offCourtSeconds}s",
                        onDecrement = onDecrementOffCourtSeconds,
                        onIncrement = onIncrementOffCourtSeconds,
                    )
                    Divider()
                    StepperSettingRow(
                        label = "Medical timeout",
                        valueText = "${settings.medicalTimeoutSeconds}s",
                        onDecrement = onDecrementMedicalTimeoutSeconds,
                        onIncrement = onIncrementMedicalTimeoutSeconds,
                    )
                    Divider()
                    StepperSettingRow(
                        label = "Equipment fix",
                        valueText = "${settings.equipmentFixSeconds}s",
                        onDecrement = onDecrementEquipmentFixSeconds,
                        onIncrement = onIncrementEquipmentFixSeconds,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ToggleSettingRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun StepperSettingRow(
    label: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        IconButton(onClick = onDecrement) {
            Icon(Icons.Outlined.Remove, contentDescription = "Decrease $label")
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(72.dp),
        )
        IconButton(onClick = onIncrement) {
            Icon(Icons.Outlined.Add, contentDescription = "Increase $label")
        }
    }
}
