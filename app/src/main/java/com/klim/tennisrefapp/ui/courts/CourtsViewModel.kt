package com.klim.tennisrefapp.ui.courts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klim.tennisrefapp.data.timer.CourtTimerManager
import com.klim.tennisrefapp.data.timer.CourtTimerState
import com.klim.tennisrefapp.data.timer.CourtTimerType
import com.klim.tennisrefapp.domain.model.CourtCard
import com.klim.tennisrefapp.domain.model.CourtStatus
import com.klim.tennisrefapp.domain.repository.CourtRepository
import com.klim.tennisrefapp.domain.repository.MatchRepository
import com.klim.tennisrefapp.domain.repository.TournamentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CourtListItemUi(
    val court: CourtCard,
    val activeTimerState: CourtTimerState? = null,
)

data class CourtsUiState(
    val courts: List<CourtListItemUi> = emptyList(),
)

@HiltViewModel
class CourtsViewModel @Inject constructor(
    private val tournamentRepository: TournamentRepository,
    private val matchRepository: MatchRepository,
    courtRepository: CourtRepository,
    timerManager: CourtTimerManager,
) : ViewModel() {

    val uiState: StateFlow<CourtsUiState> = combine(
        courtRepository.observeCourtCards(),
        timerManager.observeAllTimers(),
    ) { courts, timers ->
        val rows = courts.map { court ->
            val timerState = timers[court.courtId]
            CourtListItemUi(court = court, activeTimerState = timerState)
        }.sortedWith(
            compareBy<CourtListItemUi>(
                { timerSortRank(it.activeTimerState) },
                { followedBySortRank(it.court) },
                { statusSortRank(it.court.status) },
                { it.court.computedNotBeforeMillis ?: Long.MAX_VALUE },
                { it.court.courtName },
            ),
        )
        CourtsUiState(courts = rows)
    }
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

    private fun statusSortRank(status: CourtStatus): Int {
        return when (status) {
            CourtStatus.IN_PROGRESS -> 1
            CourtStatus.SCHEDULED -> 2
            CourtStatus.EMPTY -> 3
            CourtStatus.COMPLETED -> 4
        }
    }

    private fun timerSortRank(timerState: CourtTimerState?): Int {
        if (timerState == null) return 2
        if (timerState.isRunning) return 0
        return if (timerState.timerType == CourtTimerType.WARMUP) 1 else 2
    }

    private fun followedBySortRank(court: CourtCard): Int {
        val isFollowedBy = court.status == CourtStatus.SCHEDULED &&
            court.scheduleLabel?.contains("Followed By", ignoreCase = true) == true
        return if (isFollowedBy) 0 else 1
    }
}
