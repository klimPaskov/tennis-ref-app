package com.klim.tennisrefapp.ui.courts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klim.tennisrefapp.domain.model.CourtCard
import com.klim.tennisrefapp.domain.repository.CourtRepository
import com.klim.tennisrefapp.domain.repository.MatchRepository
import com.klim.tennisrefapp.domain.repository.TournamentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CourtsUiState(
    val courts: List<CourtCard> = emptyList(),
)

@HiltViewModel
class CourtsViewModel @Inject constructor(
    private val tournamentRepository: TournamentRepository,
    private val matchRepository: MatchRepository,
    courtRepository: CourtRepository,
) : ViewModel() {

    val uiState: StateFlow<CourtsUiState> = courtRepository.observeCourtCards()
        .map { courts -> CourtsUiState(courts = courts) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CourtsUiState(),
        )

    init {
        viewModelScope.launch {
            tournamentRepository.seedIfEmpty()
        }
    }

    fun onAssignMatch(courtId: Long) {
        viewModelScope.launch {
            matchRepository.assignFirstUnassignedMatch(courtId)
        }
    }

    fun onClearCourt(courtId: Long) {
        viewModelScope.launch {
            matchRepository.clearCourt(courtId)
        }
    }

    fun onSwapWithNext(courtId: Long, orderedCourtIds: List<Long>) {
        val idx = orderedCourtIds.indexOf(courtId)
        if (idx == -1 || idx == orderedCourtIds.lastIndex) return
        val nextId = orderedCourtIds[idx + 1]
        viewModelScope.launch {
            matchRepository.swapCourtMatches(courtId, nextId)
        }
    }
}
