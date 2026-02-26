package com.klim.tennisrefapp.ui.players

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klim.tennisrefapp.domain.model.PlayerDetailInsight
import com.klim.tennisrefapp.domain.repository.PlayerInsightsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PlayerDetailUiState(
    val detail: PlayerDetailInsight? = null,
)

@HiltViewModel
class PlayerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    playerInsightsRepository: PlayerInsightsRepository,
) : ViewModel() {
    private val playerId: Long = checkNotNull(savedStateHandle["playerId"])

    val uiState: StateFlow<PlayerDetailUiState> = playerInsightsRepository.observePlayerDetailInsight(playerId)
        .map { detail -> PlayerDetailUiState(detail = detail) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlayerDetailUiState(),
        )
}
