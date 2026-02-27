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
    val query: String = "",
)

@HiltViewModel
class PlayersViewModel @Inject constructor(
    playerInsightsRepository: PlayerInsightsRepository,
) : ViewModel() {
    private val selectedSort = MutableStateFlow(PlayersSort.NAME)
    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<PlayersUiState> = combine(
        playerInsightsRepository.observePlayerOverviews(),
        selectedSort,
        searchQuery,
    ) { players, sort, query ->
        val normalizedQuery = query.trim()
        val filtered = if (normalizedQuery.isBlank()) {
            players
        } else {
            players.filter { overview ->
                overview.name.contains(normalizedQuery, ignoreCase = true)
            }
        }
        val sorted = when (sort) {
            PlayersSort.NAME -> filtered.sortedBy { it.name }
            PlayersSort.COMPLETED -> filtered.sortedWith(
                compareByDescending<PlayerOverview> { it.completedCount }
                    .thenBy { it.name },
            )
            PlayersSort.NEXT_MATCH -> filtered.sortedWith(
                compareBy<PlayerOverview> { it.nextNotBeforeMillis ?: Long.MAX_VALUE }
                    .thenBy { it.name },
            )
        }
        PlayersUiState(players = sorted, sort = sort, query = query)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlayersUiState(),
        )

    fun onSortSelected(sort: PlayersSort) {
        selectedSort.value = sort
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }
}
