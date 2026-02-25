package com.klim.tennisrefapp.ui.courtdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klim.tennisrefapp.data.timer.CourtTimerManager
import com.klim.tennisrefapp.data.timer.CourtTimerState
import com.klim.tennisrefapp.domain.model.CourtDetail
import com.klim.tennisrefapp.domain.model.CourtStatus
import com.klim.tennisrefapp.domain.model.MatchEvent
import com.klim.tennisrefapp.domain.model.MatchEventKind
import com.klim.tennisrefapp.domain.model.ViolationCategory
import com.klim.tennisrefapp.domain.model.ViolationCounts
import com.klim.tennisrefapp.domain.repository.CourtRepository
import com.klim.tennisrefapp.domain.repository.EndMatchResult
import com.klim.tennisrefapp.domain.repository.EventLogRepository
import com.klim.tennisrefapp.domain.repository.MatchRepository
import com.klim.tennisrefapp.domain.repository.MatchResultType
import com.klim.tennisrefapp.domain.repository.MatchWinnerSide
import com.klim.tennisrefapp.domain.repository.StartMatchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CourtDetailUiState(
    val detail: CourtDetail? = null,
    val selectedServerPlayerId: Long? = null,
    val feedbackMessage: String? = null,
    val timerState: CourtTimerState? = null,
    val matchEvents: List<MatchEvent> = emptyList(),
    val violationCountsByPlayer: Map<Long, ViolationCounts> = emptyMap(),
    val availableCourts: List<Pair<Long, String>> = emptyList(),
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CourtDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val courtRepository: CourtRepository,
    private val matchRepository: MatchRepository,
    private val eventLogRepository: EventLogRepository,
    private val timerManager: CourtTimerManager,
) : ViewModel() {

    private val courtId: Long = checkNotNull(savedStateHandle.get<Long>("courtId"))
    private val selectedServerPlayerId = MutableStateFlow<Long?>(null)
    private val feedbackMessage = MutableStateFlow<String?>(null)

    private val detailFlow: Flow<CourtDetail?> = courtRepository.observeCourtDetail(courtId)

    private val eventsFlow: Flow<List<MatchEvent>> = detailFlow.flatMapLatest { detail ->
        val matchId = detail?.matchId ?: return@flatMapLatest flowOf(emptyList())
        eventLogRepository.observeEventsForMatch(matchId)
    }

    private val timerFlow: Flow<CourtTimerState?> = detailFlow.flatMapLatest { detail ->
        val matchId = detail?.matchId ?: return@flatMapLatest flowOf(null)
        if (matchId <= 0L) return@flatMapLatest flowOf(null)
        timerManager.observeTimer(detail.courtId)
    }

    private val courtsFlow = courtRepository.observeCourtCards()
        .map { cards -> cards.map { it.courtId to it.courtName } }

    val uiState: StateFlow<CourtDetailUiState> = combine(
        detailFlow,
        selectedServerPlayerId,
        feedbackMessage,
        timerFlow,
        eventsFlow,
        courtsFlow,
    ) { values ->
        val detail = values[0] as CourtDetail?
        val selectedServer = values[1] as Long?
        val feedback = values[2] as String?
        val timer = values[3] as CourtTimerState?
        @Suppress("UNCHECKED_CAST")
        val events = values[4] as List<MatchEvent>
        @Suppress("UNCHECKED_CAST")
        val courts = values[5] as List<Pair<Long, String>>
        val allowedServerIds = buildList {
            addAll(detail?.leftSide?.players?.map { it.playerId }.orEmpty())
            addAll(detail?.rightSide?.players?.map { it.playerId }.orEmpty())
        }
        val currentSelection = when {
            detail?.initialServerPlayerId != null -> detail.initialServerPlayerId
            selectedServer in allowedServerIds -> selectedServer
            else -> null
        }
        CourtDetailUiState(
            detail = detail,
            selectedServerPlayerId = currentSelection,
            feedbackMessage = feedback,
            timerState = timer,
            matchEvents = events,
            violationCountsByPlayer = countViolations(events),
            availableCourts = courts,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CourtDetailUiState(),
    )

    fun onSelectServer(playerId: Long) {
        val detail = uiState.value.detail ?: return
        if (!detail.canStartMatch) return
        selectedServerPlayerId.value = playerId
    }

    fun onSwapSides() {
        val matchId = uiState.value.detail?.matchId ?: return
        viewModelScope.launch {
            matchRepository.swapSides(matchId)
            selectedServerPlayerId.value = null
            feedbackMessage.value = null
        }
    }

    fun onStartMatch() {
        val detail = uiState.value.detail ?: return
        val matchId = detail.matchId ?: return
        val selectedServer = uiState.value.selectedServerPlayerId
        if (!detail.canStartMatch) {
            feedbackMessage.value = "Participants are not fully resolved yet."
            return
        }
        if (selectedServer == null) {
            feedbackMessage.value = "Select the initial server before starting."
            return
        }

        viewModelScope.launch {
            val result = matchRepository.startMatch(
                matchId = matchId,
                initialServerPlayerId = selectedServer,
            )
            feedbackMessage.value = when (result) {
                StartMatchResult.Success -> "Match started"
                StartMatchResult.MatchNotFound -> "Match not found"
                StartMatchResult.MissingResolvedParticipants -> "Participants are not resolved"
                StartMatchResult.InvalidInitialServer -> "Selected server is invalid"
                StartMatchResult.InvalidMatchState -> "Only scheduled matches can be started"
            }
        }
    }

    fun onToggleTimer() {
        val detail = uiState.value.detail ?: return
        val matchId = detail.matchId ?: return
        if (detail.status != CourtStatus.IN_PROGRESS) return

        val running = uiState.value.timerState?.isRunning == true
        if (running) {
            timerManager.stop(detail.courtId)
            viewModelScope.launch {
                eventLogRepository.logEvent(
                    matchId = matchId,
                    courtId = detail.courtId,
                    kind = MatchEventKind.TIMER,
                    offenderPlayerId = null,
                    message = "TIMER|STOPPED",
                )
            }
        } else {
            timerManager.startOrRestart(detail.courtId, matchId, detail.courtName)
            viewModelScope.launch {
                eventLogRepository.logEvent(
                    matchId = matchId,
                    courtId = detail.courtId,
                    kind = MatchEventKind.TIMER,
                    offenderPlayerId = null,
                    message = "TIMER|STARTED",
                )
            }
        }
    }

    fun onApplyViolation(offenderPlayerId: Long, category: ViolationCategory, violationName: String) {
        val matchId = uiState.value.detail?.matchId ?: return
        viewModelScope.launch {
            val result = matchRepository.applyViolation(
                matchId = matchId,
                offenderPlayerId = offenderPlayerId,
                category = category,
                violationName = violationName,
            )
            feedbackMessage.value = "${result.summary}. This offence #${result.guidance.offenceNumber} -> ${result.guidance.appliedPenalty}. Next -> ${result.guidance.nextPenalty}"
        }
    }

    fun onUndoLastViolation() {
        val matchId = uiState.value.detail?.matchId ?: return
        viewModelScope.launch {
            val undone = matchRepository.undoLastViolation(matchId)
            feedbackMessage.value = if (undone) "Last violation undone" else "No violation to undo"
        }
    }

    fun onSubmitFinalScore(winnerSide: MatchWinnerSide, resultType: MatchResultType, scoreText: String) {
        val matchId = uiState.value.detail?.matchId ?: return
        viewModelScope.launch {
            val result = matchRepository.endMatch(
                matchId = matchId,
                winnerSide = winnerSide,
                resultType = resultType,
                scoreText = scoreText,
            )
            feedbackMessage.value = when (result) {
                EndMatchResult.Success -> "Final score submitted"
                EndMatchResult.MatchNotFound -> "Match not found"
                EndMatchResult.InvalidMatchState -> "Match must be in progress"
                EndMatchResult.MissingResolvedParticipants -> "Unable to determine winner teams"
            }
        }
    }

    fun onMoveMatchToCourt(courtId: Long?) {
        val matchId = uiState.value.detail?.matchId ?: return
        viewModelScope.launch {
            val moved = matchRepository.moveMatchToCourt(matchId, courtId)
            feedbackMessage.value = if (moved) {
                if (courtId == null) "Match moved to unassigned" else "Match moved to new court"
            } else {
                "Failed to move match"
            }
        }
    }

    fun onSwapWithCourt(otherCourtId: Long) {
        val currentCourtId = uiState.value.detail?.courtId ?: return
        if (otherCourtId == currentCourtId) return
        viewModelScope.launch {
            val swapped = matchRepository.swapCourtMatches(currentCourtId, otherCourtId)
            feedbackMessage.value = if (swapped) "Court matches swapped" else "No matches to swap"
        }
    }

    fun clearFeedback() {
        feedbackMessage.value = null
    }

    private fun countViolations(events: List<MatchEvent>): Map<Long, ViolationCounts> {
        val byPlayer = mutableMapOf<Long, ViolationCounts>()
        events.filter { it.kind == MatchEventKind.VIOLATION }.forEach { event ->
            val playerId = event.offenderPlayerId ?: return@forEach
            val existing = byPlayer[playerId] ?: ViolationCounts()
            val category = parseViolationCategory(event.message) ?: return@forEach
            val updated = when (category) {
                ViolationCategory.CODE -> existing.copy(codeViolationCount = existing.codeViolationCount + 1)
                ViolationCategory.TIME_SERVER -> existing.copy(timeViolationCountServer = existing.timeViolationCountServer + 1)
                ViolationCategory.TIME_RECEIVER -> existing.copy(timeViolationCountReceiver = existing.timeViolationCountReceiver + 1)
            }
            byPlayer[playerId] = updated
        }
        return byPlayer
    }

    private fun parseViolationCategory(message: String?): ViolationCategory? {
        if (message == null) return null
        if (!message.startsWith("VIOLATION|")) return null
        val category = message.split("|").getOrNull(1) ?: return null
        return runCatching { ViolationCategory.valueOf(category) }.getOrNull()
    }
}
