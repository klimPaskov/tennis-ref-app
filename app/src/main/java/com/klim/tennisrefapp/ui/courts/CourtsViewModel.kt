package com.klim.tennisrefapp.ui.courts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klim.tennisrefapp.domain.model.CourtCard
import com.klim.tennisrefapp.domain.repository.CourtRepository
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
}
