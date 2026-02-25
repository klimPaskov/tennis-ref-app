package com.klim.tennisrefapp.ui.courtdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klim.tennisrefapp.domain.model.CourtDetail
import com.klim.tennisrefapp.domain.repository.CourtRepository
import com.klim.tennisrefapp.domain.repository.MatchRepository
import com.klim.tennisrefapp.domain.repository.StartMatchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CourtDetailUiState(
    val detail: CourtDetail? = null,
    val selectedServerPlayerId: Long? = null,
    val feedbackMessage: String? = null,
)

@HiltViewModel
class CourtDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val courtRepository: CourtRepository,
    private val matchRepository: MatchRepository,
) : ViewModel() {

    private val courtId: Long = checkNotNull(savedStateHandle.get<Long>("courtId"))
    private val selectedServerPlayerId = MutableStateFlow<Long?>(null)
    private val feedbackMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CourtDetailUiState> = combine(
        courtRepository.observeCourtDetail(courtId),
        selectedServerPlayerId,
        feedbackMessage,
    ) { detail, selectedServer, feedback ->
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

    fun clearFeedback() {
        feedbackMessage.value = null
    }
}
