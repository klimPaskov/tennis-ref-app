package com.klim.tennisrefapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klim.tennisrefapp.domain.repository.TournamentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isReloading: Boolean = false,
    val message: String? = null,
    val preloadedOptions: List<PreloadedOrderOption> = DEFAULT_PRELOADED_OPTIONS,
)

data class PreloadedOrderOption(
    val id: String,
    val label: String,
)

private val DEFAULT_PRELOADED_OPTIONS = listOf(
    PreloadedOrderOption(id = "example", label = "Example"),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tournamentRepository: TournamentRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun onOrderOfPlaySelected(uriString: String?) {
        if (uriString.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(message = "No file selected.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isReloading = true, message = null)
            val imported = tournamentRepository.reloadOrderOfPlayFromFile(uriString)
            _uiState.value = HomeUiState(
                isReloading = false,
                message = if (imported) {
                    "New order of play loaded from selected file."
                } else {
                    "Could not parse order of play file."
                },
            )
        }
    }

    fun onReloadCurrentOrderOfPlay() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isReloading = true, message = null)
            val reloaded = tournamentRepository.reloadOrderOfPlay()
            _uiState.value = HomeUiState(
                isReloading = false,
                message = if (reloaded) {
                    "Reloaded current order of play."
                } else {
                    "Could not reload current order of play."
                },
            )
        }
    }

    fun onLoadPreloadedOrderOfPlay(preloadId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isReloading = true, message = null)
            val loaded = tournamentRepository.loadPreloadedOrderOfPlay(preloadId)
            _uiState.value = HomeUiState(
                isReloading = false,
                message = if (loaded) {
                    "Loaded verified preloaded order of play."
                } else {
                    "Could not load preloaded order of play."
                },
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
