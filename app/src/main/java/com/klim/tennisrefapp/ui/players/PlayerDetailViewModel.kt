package com.klim.tennisrefapp.ui.players

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klim.tennisrefapp.domain.model.PlayerDetailInsight
import com.klim.tennisrefapp.domain.repository.PlayerInsightsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PlayerDetailUiState(
    val detail: PlayerDetailInsight? = null,
    val message: String? = null,
)

@HiltViewModel
class PlayerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    playerInsightsRepository: PlayerInsightsRepository,
) : ViewModel() {
    private val playerId: Long = checkNotNull(savedStateHandle["playerId"])
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PlayerDetailUiState> = combine(
        playerInsightsRepository.observePlayerDetailInsight(playerId),
        message,
    ) { detail, message ->
        PlayerDetailUiState(detail = detail, message = message)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlayerDetailUiState(),
        )

    fun clearMessage() {
        message.value = null
    }
}
