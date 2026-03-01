package com.klim.tennisrefapp.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klim.tennisrefapp.domain.model.MatchHistoryDetail
import com.klim.tennisrefapp.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MatchLogUiState(
    val detail: MatchHistoryDetail? = null,
)

@HiltViewModel
class MatchLogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    historyRepository: HistoryRepository,
) : ViewModel() {
    private val matchId: Long = checkNotNull(savedStateHandle["matchId"])

    val uiState: StateFlow<MatchLogUiState> = historyRepository.observeMatchHistory(matchId)
        .map { detail -> MatchLogUiState(detail = detail) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MatchLogUiState(),
        )
}
