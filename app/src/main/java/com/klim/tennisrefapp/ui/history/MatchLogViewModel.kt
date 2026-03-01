package com.klim.tennisrefapp.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klim.tennisrefapp.domain.model.MatchHistoryDetail
import com.klim.tennisrefapp.domain.repository.EventLogRepository
import com.klim.tennisrefapp.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MatchLogUiState(
    val detail: MatchHistoryDetail? = null,
    val message: String? = null,
)

@HiltViewModel
class MatchLogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    historyRepository: HistoryRepository,
    private val eventLogRepository: EventLogRepository,
) : ViewModel() {
    private val matchId: Long = checkNotNull(savedStateHandle["matchId"])
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MatchLogUiState> = combine(
        historyRepository.observeMatchHistory(matchId),
        message.asStateFlow(),
    ) { detail, feedback ->
        MatchLogUiState(detail = detail, message = feedback)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MatchLogUiState(),
        )

    fun onEditEvent(eventId: Long, timeLabel: String, messageText: String) {
        val normalizedTime = timeLabel.trim()
        val parsed = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(normalizedTime)
        if (parsed == null) {
            message.value = "Enter time as HH:mm."
            return
        }
        val hour = parsed.groupValues[1].toIntOrNull()
        val minute = parsed.groupValues[2].toIntOrNull()
        if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
            message.value = "Enter a valid time."
            return
        }
        val event = uiState.value.detail?.events?.firstOrNull { it.id == eventId }
        if (event == null) {
            message.value = "Event not found."
            return
        }
        val newTimestamp = java.util.Calendar.getInstance().apply {
            timeInMillis = event.timestamp
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val newMessage = messageText.trim().ifBlank { null }
        viewModelScope.launch {
            val updated = eventLogRepository.updateEvent(
                eventId = eventId,
                timestamp = newTimestamp,
                message = newMessage,
            )
            message.value = if (updated) "History entry updated." else "Could not update history entry."
        }
    }

    fun onDeleteEvent(eventId: Long) {
        viewModelScope.launch {
            val deleted = eventLogRepository.deleteEvent(eventId)
            message.value = if (deleted) "History entry deleted." else "Could not delete history entry."
        }
    }

    fun clearMessage() {
        message.value = null
    }
}
