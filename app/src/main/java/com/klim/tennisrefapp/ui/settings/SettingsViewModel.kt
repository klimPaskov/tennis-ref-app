package com.klim.tennisrefapp.ui.settings

import androidx.lifecycle.ViewModel
import com.klim.tennisrefapp.data.settings.AppSettings
import com.klim.tennisrefapp.data.settings.AppSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettingsStore: AppSettingsStore,
) : ViewModel() {
    val uiState: StateFlow<AppSettings> = appSettingsStore.settings

    fun onAutoStartWarmupChanged(enabled: Boolean) {
        appSettingsStore.updateSettings { it.copy(autoStartWarmup = enabled) }
    }

    fun onAutoStartMatchAfterWarmupChanged(enabled: Boolean) {
        appSettingsStore.updateSettings { it.copy(autoStartMatchAfterWarmup = enabled) }
    }

    fun onWarmupOneMinuteWarningChanged(enabled: Boolean) {
        appSettingsStore.updateSettings { it.copy(warmupOneMinuteWarning = enabled) }
    }

    fun onWarmupPendingReminderChanged(enabled: Boolean) {
        appSettingsStore.updateSettings { it.copy(warmupPendingReminder = enabled) }
    }

    fun incrementWarmupMinutes() = bump { it.copy(warmupMinutes = it.warmupMinutes + 1) }
    fun decrementWarmupMinutes() = bump { it.copy(warmupMinutes = it.warmupMinutes - 1) }

    fun incrementSideSwitchSeconds() = bump { it.copy(sideSwitchSeconds = it.sideSwitchSeconds + 15) }
    fun decrementSideSwitchSeconds() = bump { it.copy(sideSwitchSeconds = it.sideSwitchSeconds - 15) }

    fun incrementSetBreakSeconds() = bump { it.copy(setBreakSeconds = it.setBreakSeconds + 15) }
    fun decrementSetBreakSeconds() = bump { it.copy(setBreakSeconds = it.setBreakSeconds - 15) }

    fun incrementToiletBreakSeconds() = bump { it.copy(toiletBreakSeconds = it.toiletBreakSeconds + 30) }
    fun decrementToiletBreakSeconds() = bump { it.copy(toiletBreakSeconds = it.toiletBreakSeconds - 30) }

    fun incrementOffCourtSeconds() = bump { it.copy(offCourtSeconds = it.offCourtSeconds + 30) }
    fun decrementOffCourtSeconds() = bump { it.copy(offCourtSeconds = it.offCourtSeconds - 30) }

    fun incrementMedicalTimeoutSeconds() = bump { it.copy(medicalTimeoutSeconds = it.medicalTimeoutSeconds + 30) }
    fun decrementMedicalTimeoutSeconds() = bump { it.copy(medicalTimeoutSeconds = it.medicalTimeoutSeconds - 30) }

    fun incrementEquipmentFixSeconds() = bump { it.copy(equipmentFixSeconds = it.equipmentFixSeconds + 15) }
    fun decrementEquipmentFixSeconds() = bump { it.copy(equipmentFixSeconds = it.equipmentFixSeconds - 15) }

    private fun bump(transform: (AppSettings) -> AppSettings) {
        appSettingsStore.updateSettings(transform)
    }
}
