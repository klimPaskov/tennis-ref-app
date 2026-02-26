package com.klim.tennisrefapp.ui.players

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klim.tennisrefapp.domain.model.PlayerOverview
import com.klim.tennisrefapp.domain.repository.PlayerInsightsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PlayersUiState(
    val players: List<PlayerOverview> = emptyList(),
)

@HiltViewModel
class PlayersViewModel @Inject constructor(
    playerInsightsRepository: PlayerInsightsRepository,
) : ViewModel() {
    val uiState: StateFlow<PlayersUiState> = playerInsightsRepository.observePlayerOverviews()
        .map { players -> PlayersUiState(players = players) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlayersUiState(),
        )
}
