package com.klim.tennisrefapp.ui.draws

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klim.tennisrefapp.domain.model.DrawBoard
import com.klim.tennisrefapp.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DrawsUiState(
    val boards: List<DrawBoard> = emptyList(),
)

@HiltViewModel
class DrawsViewModel @Inject constructor(
    historyRepository: HistoryRepository,
) : ViewModel() {
    val uiState: StateFlow<DrawsUiState> = historyRepository.observeDrawBoards()
        .map { boards -> DrawsUiState(boards = boards) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DrawsUiState(),
        )
}
