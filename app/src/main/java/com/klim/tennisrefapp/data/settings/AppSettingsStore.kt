package com.klim.tennisrefapp.data.settings

import android.content.Context
import androidx.annotation.IntRange
import com.klim.tennisrefapp.data.timer.CourtTimerType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val DEFAULT_WARMUP_MINUTES = 5
private const val DEFAULT_SIDE_SWITCH_SECONDS = 90
private const val DEFAULT_SET_BREAK_SECONDS = 120
private const val DEFAULT_TOILET_BREAK_SECONDS = 180
private const val DEFAULT_OFF_COURT_SECONDS = 300
private const val DEFAULT_MEDICAL_TIMEOUT_SECONDS = 180
private const val DEFAULT_EQUIPMENT_FIX_SECONDS = 120

data class AppSettings(
    @IntRange(from = 1, to = 20)
    val warmupMinutes: Int = DEFAULT_WARMUP_MINUTES,
    @IntRange(from = 30, to = 180)
    val sideSwitchSeconds: Int = DEFAULT_SIDE_SWITCH_SECONDS,
    @IntRange(from = 60, to = 300)
    val setBreakSeconds: Int = DEFAULT_SET_BREAK_SECONDS,
    @IntRange(from = 60, to = 900)
    val toiletBreakSeconds: Int = DEFAULT_TOILET_BREAK_SECONDS,
    @IntRange(from = 60, to = 1200)
    val offCourtSeconds: Int = DEFAULT_OFF_COURT_SECONDS,
    @IntRange(from = 60, to = 600)
    val medicalTimeoutSeconds: Int = DEFAULT_MEDICAL_TIMEOUT_SECONDS,
    @IntRange(from = 30, to = 300)
    val equipmentFixSeconds: Int = DEFAULT_EQUIPMENT_FIX_SECONDS,
    val autoStartWarmup: Boolean = true,
    val autoStartMatchAfterWarmup: Boolean = true,
    val warmupOneMinuteWarning: Boolean = true,
    val warmupPendingReminder: Boolean = true,
)

@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun currentSettings(): AppSettings = _settings.value

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value).normalized()
        prefs.edit()
            .putInt(KEY_WARMUP_MINUTES, next.warmupMinutes)
            .putInt(KEY_SIDE_SWITCH_SECONDS, next.sideSwitchSeconds)
            .putInt(KEY_SET_BREAK_SECONDS, next.setBreakSeconds)
            .putInt(KEY_TOILET_BREAK_SECONDS, next.toiletBreakSeconds)
            .putInt(KEY_OFF_COURT_SECONDS, next.offCourtSeconds)
            .putInt(KEY_MEDICAL_TIMEOUT_SECONDS, next.medicalTimeoutSeconds)
            .putInt(KEY_EQUIPMENT_FIX_SECONDS, next.equipmentFixSeconds)
            .putBoolean(KEY_AUTO_START_WARMUP, next.autoStartWarmup)
            .putBoolean(KEY_AUTO_START_MATCH_AFTER_WARMUP, next.autoStartMatchAfterWarmup)
            .putBoolean(KEY_WARMUP_ONE_MINUTE_WARNING, next.warmupOneMinuteWarning)
            .putBoolean(KEY_WARMUP_PENDING_REMINDER, next.warmupPendingReminder)
            .apply()
        _settings.value = next
    }

    fun durationMillis(timerType: CourtTimerType): Long {
        val current = _settings.value
        val seconds = when (timerType) {
            CourtTimerType.WARMUP -> current.warmupMinutes * 60
            CourtTimerType.SIDE_SWITCH -> current.sideSwitchSeconds
            CourtTimerType.SET_BREAK -> current.setBreakSeconds
            CourtTimerType.TOILET_BREAK -> current.toiletBreakSeconds
            CourtTimerType.OFF_COURT -> current.offCourtSeconds
            CourtTimerType.MEDICAL_TIMEOUT -> current.medicalTimeoutSeconds
            CourtTimerType.EQUIPMENT_FIX -> current.equipmentFixSeconds
        }
        return seconds * 1000L
    }

    private fun readSettings(): AppSettings {
        return AppSettings(
            warmupMinutes = prefs.getInt(KEY_WARMUP_MINUTES, DEFAULT_WARMUP_MINUTES),
            sideSwitchSeconds = prefs.getInt(KEY_SIDE_SWITCH_SECONDS, DEFAULT_SIDE_SWITCH_SECONDS),
            setBreakSeconds = prefs.getInt(KEY_SET_BREAK_SECONDS, DEFAULT_SET_BREAK_SECONDS),
            toiletBreakSeconds = prefs.getInt(KEY_TOILET_BREAK_SECONDS, DEFAULT_TOILET_BREAK_SECONDS),
            offCourtSeconds = prefs.getInt(KEY_OFF_COURT_SECONDS, DEFAULT_OFF_COURT_SECONDS),
            medicalTimeoutSeconds = prefs.getInt(KEY_MEDICAL_TIMEOUT_SECONDS, DEFAULT_MEDICAL_TIMEOUT_SECONDS),
            equipmentFixSeconds = prefs.getInt(KEY_EQUIPMENT_FIX_SECONDS, DEFAULT_EQUIPMENT_FIX_SECONDS),
            autoStartWarmup = prefs.getBoolean(KEY_AUTO_START_WARMUP, true),
            autoStartMatchAfterWarmup = prefs.getBoolean(KEY_AUTO_START_MATCH_AFTER_WARMUP, true),
            warmupOneMinuteWarning = prefs.getBoolean(KEY_WARMUP_ONE_MINUTE_WARNING, true),
            warmupPendingReminder = prefs.getBoolean(KEY_WARMUP_PENDING_REMINDER, true),
        ).normalized()
    }

    private fun AppSettings.normalized(): AppSettings {
        return copy(
            warmupMinutes = warmupMinutes.coerceIn(1, 20),
            sideSwitchSeconds = sideSwitchSeconds.coerceIn(30, 180),
            setBreakSeconds = setBreakSeconds.coerceIn(60, 300),
            toiletBreakSeconds = toiletBreakSeconds.coerceIn(60, 900),
            offCourtSeconds = offCourtSeconds.coerceIn(60, 1200),
            medicalTimeoutSeconds = medicalTimeoutSeconds.coerceIn(60, 600),
            equipmentFixSeconds = equipmentFixSeconds.coerceIn(30, 300),
        )
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_WARMUP_MINUTES = "warmup_minutes"
        private const val KEY_SIDE_SWITCH_SECONDS = "side_switch_seconds"
        private const val KEY_SET_BREAK_SECONDS = "set_break_seconds"
        private const val KEY_TOILET_BREAK_SECONDS = "toilet_break_seconds"
        private const val KEY_OFF_COURT_SECONDS = "off_court_seconds"
        private const val KEY_MEDICAL_TIMEOUT_SECONDS = "medical_timeout_seconds"
        private const val KEY_EQUIPMENT_FIX_SECONDS = "equipment_fix_seconds"
        private const val KEY_AUTO_START_WARMUP = "auto_start_warmup"
        private const val KEY_AUTO_START_MATCH_AFTER_WARMUP = "auto_start_match_after_warmup"
        private const val KEY_WARMUP_ONE_MINUTE_WARNING = "warmup_one_minute_warning"
        private const val KEY_WARMUP_PENDING_REMINDER = "warmup_pending_reminder"
    }
}
