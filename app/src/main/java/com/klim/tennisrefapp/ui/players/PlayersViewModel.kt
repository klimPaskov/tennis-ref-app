package com.klim.tennisrefapp.ui.players

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klim.tennisrefapp.domain.model.PlayerOverview
import com.klim.tennisrefapp.domain.repository.PlayerInsightsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class PlayersSort {
    NAME,
    COMPLETED,
    NEXT_MATCH,
}

data class PlayersUiState(
    val players: List<PlayerOverview> = emptyList(),
    val sort: PlayersSort = PlayersSort.NAME,
)

@HiltViewModel
class PlayersViewModel @Inject constructor(
    playerInsightsRepository: PlayerInsightsRepository,
) : ViewModel() {
    private val selectedSort = MutableStateFlow(PlayersSort.NAME)

    val uiState: StateFlow<PlayersUiState> = combine(
        playerInsightsRepository.observePlayerOverviews(),
        selectedSort,
    ) { players, sort ->
        val sorted = when (sort) {
            PlayersSort.NAME -> players.sortedBy { it.name }
            PlayersSort.COMPLETED -> players.sortedWith(
                compareByDescending<PlayerOverview> { it.completedCount }
                    .thenBy { it.name },
            )
            PlayersSort.NEXT_MATCH -> players.sortedWith(
                compareBy<PlayerOverview> { it.nextNotBeforeMillis ?: Long.MAX_VALUE }
                    .thenBy { it.name },
            )
        }
        PlayersUiState(players = sorted, sort = sort)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlayersUiState(),
        )

    fun onSortSelected(sort: PlayersSort) {
        selectedSort.value = sort
    }
}
