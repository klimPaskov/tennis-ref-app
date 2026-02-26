package com.klim.tennisrefapp.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klim.tennisrefapp.domain.model.CompletedCourtMatch
import com.klim.tennisrefapp.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val matches: List<CompletedCourtMatch> = emptyList(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    historyRepository: HistoryRepository,
) : ViewModel() {
    val uiState: StateFlow<HistoryUiState> = historyRepository.observeCompletedMatchesByCourt()
        .map { matches -> HistoryUiState(matches = matches) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState(),
        )
}
