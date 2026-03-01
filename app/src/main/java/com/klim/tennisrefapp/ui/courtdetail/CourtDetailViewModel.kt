package com.klim.tennisrefapp.ui.courtdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klim.tennisrefapp.data.settings.AppSettings
import com.klim.tennisrefapp.data.settings.AppSettingsStore
import com.klim.tennisrefapp.data.timer.CourtTimerManager
import com.klim.tennisrefapp.data.timer.CourtTimerState
import com.klim.tennisrefapp.data.timer.CourtTimerType
import com.klim.tennisrefapp.domain.model.CourtDetail
import com.klim.tennisrefapp.domain.model.CourtStatus
import com.klim.tennisrefapp.domain.model.MatchEvent
import com.klim.tennisrefapp.domain.model.MatchEventKind
import com.klim.tennisrefapp.domain.model.PlayerSummary
import com.klim.tennisrefapp.domain.model.SideParticipant
import com.klim.tennisrefapp.domain.model.ViolationCategory
import com.klim.tennisrefapp.domain.model.ViolationCounts
import com.klim.tennisrefapp.domain.repository.CourtRepository
import com.klim.tennisrefapp.domain.repository.EndMatchResult
import com.klim.tennisrefapp.domain.repository.EventLogRepository
import com.klim.tennisrefapp.domain.repository.MatchRepository
import com.klim.tennisrefapp.domain.repository.MatchResultType
import com.klim.tennisrefapp.domain.repository.MatchWinnerSide
import com.klim.tennisrefapp.domain.repository.PlayerRepository
import com.klim.tennisrefapp.domain.repository.ScheduledMatchTiming
import com.klim.tennisrefapp.domain.repository.StartMatchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CourtDetailUiState(
    val detail: CourtDetail? = null,
    val availablePlayers: List<PlayerSummary> = emptyList(),
    val selectedServerPlayerId: Long? = null,
    val feedbackMessage: UiFeedback? = null,
    val timerState: CourtTimerState? = null,
    val matchEvents: List<MatchEvent> = emptyList(),
    val violationCountsByPlayer: Map<Long, ViolationCounts> = emptyMap(),
    val timerDurationsByType: Map<CourtTimerType, Long> = emptyMap(),
)

data class UiFeedback(
    val message: String,
    val actionLabel: String? = null,
)

sealed interface CourtDetailNavigationEvent {
    data object BackToCourts : CourtDetailNavigationEvent
    data class OpenCourt(val courtId: Long) : CourtDetailNavigationEvent
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CourtDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val courtRepository: CourtRepository,
    private val matchRepository: MatchRepository,
    private val playerRepository: PlayerRepository,
    private val eventLogRepository: EventLogRepository,
    private val timerManager: CourtTimerManager,
    private val appSettingsStore: AppSettingsStore,
) : ViewModel() {

    private val courtId: Long = checkNotNull(savedStateHandle.get<Long>("courtId"))
    private val selectedServerPlayerId = MutableStateFlow<Long?>(null)
    private val feedbackMessage = MutableStateFlow<UiFeedback?>(null)
    private val _navigationEvents = MutableSharedFlow<CourtDetailNavigationEvent>(extraBufferCapacity = 1)
    val navigationEvents: SharedFlow<CourtDetailNavigationEvent> = _navigationEvents.asSharedFlow()
    private var warmupCompletedForMatchId: Long? = null
    private var warmupStartedForMatchId: Long? = null

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
    private val settingsFlow = appSettingsStore.settings
    private val playersFlow = playerRepository.observePlayers()

    val uiState: StateFlow<CourtDetailUiState> = combine(
        detailFlow,
        selectedServerPlayerId,
        feedbackMessage,
        timerFlow,
        eventsFlow,
        settingsFlow,
        playersFlow,
    ) { values ->
        val detail = values[0] as CourtDetail?
        val selectedServer = values[1] as Long?
        val feedback = values[2] as UiFeedback?
        val timer = values[3] as CourtTimerState?
        @Suppress("UNCHECKED_CAST")
        val events = values[4] as List<MatchEvent>
        val settings = values[5] as AppSettings
        @Suppress("UNCHECKED_CAST")
        val players = values[6] as List<PlayerSummary>
        val allowedServerIds = buildList {
            addAll(detail?.leftSide?.players?.map { it.playerId }.orEmpty())
            addAll(detail?.rightSide?.players?.map { it.playerId }.orEmpty())
        }
        val currentSelection = when {
            detail?.status == CourtStatus.COMPLETED -> null
            detail?.initialServerPlayerId != null -> detail.initialServerPlayerId
            selectedServer in allowedServerIds -> selectedServer
            else -> null
        }
        CourtDetailUiState(
            detail = detail,
            availablePlayers = players.sortedBy { it.displayName.lowercase() },
            selectedServerPlayerId = currentSelection,
            feedbackMessage = feedback,
            timerState = timer,
            matchEvents = events,
            violationCountsByPlayer = countViolations(events),
            timerDurationsByType = buildTimerDurations(settings),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CourtDetailUiState(),
    )

    init {
        viewModelScope.launch {
            combine(detailFlow, selectedServerPlayerId, timerFlow, settingsFlow) { detail, selectedServer, timer, settings ->
                arrayOf(detail, selectedServer, timer, settings)
            }.collect { values ->
                val detail = values[0] as CourtDetail?
                val selectedServer = values[1] as Long?
                val timer = values[2] as CourtTimerState?
                val settings = values[3] as AppSettings
                maybeStartWarmup(detail, selectedServer, timer, settings)
                maybeAutoStartAfterWarmup(detail, selectedServer, timer, settings)
            }
        }
    }

    fun onSelectServer(playerId: Long) {
        val detail = uiState.value.detail ?: return
        val matchId = detail.matchId ?: return
        if (!detail.canStartMatch) return
        viewModelScope.launch {
            val updated = matchRepository.setScheduledInitialServer(matchId, playerId)
            if (updated) {
                selectedServerPlayerId.value = playerId
            } else {
                feedbackMessage.value = UiFeedback("Unable to set initial server.")
            }
        }
    }

    fun onSwapSides() {
        val matchId = uiState.value.detail?.matchId ?: return
        viewModelScope.launch {
            matchRepository.swapSides(matchId)
            timerManager.stop(courtId)
            warmupCompletedForMatchId = null
            warmupStartedForMatchId = null
            selectedServerPlayerId.value = null
            feedbackMessage.value = null
        }
    }

    fun onStartMatch() {
        val detail = uiState.value.detail ?: return
        val matchId = detail.matchId ?: return
        val selectedServer = uiState.value.selectedServerPlayerId
        if (!detail.canStartMatch) {
            feedbackMessage.value = UiFeedback("Participants are not fully resolved yet.")
            return
        }
        if (selectedServer == null) {
            feedbackMessage.value = UiFeedback("Select the initial server before starting.")
            return
        }

        viewModelScope.launch {
            warmupCompletedForMatchId = matchId
            warmupStartedForMatchId = null
            timerManager.stop(detail.courtId)
            val result = matchRepository.startMatch(
                matchId = matchId,
                initialServerPlayerId = selectedServer,
            )
            feedbackMessage.value = when (result) {
                StartMatchResult.Success -> UiFeedback("Match started")
                StartMatchResult.MatchNotFound -> UiFeedback("Match not found")
                StartMatchResult.MissingResolvedParticipants -> UiFeedback("Participants are not resolved")
                StartMatchResult.InvalidInitialServer -> UiFeedback("Selected server is invalid")
                StartMatchResult.InvalidMatchState -> UiFeedback("Only scheduled matches can be started")
            }
        }
    }

    fun onStartTimer(timerType: CourtTimerType) {
        val detail = uiState.value.detail ?: return
        val matchId = detail.matchId ?: return
        if (detail.status != CourtStatus.IN_PROGRESS) return
        timerManager.startOrRestart(
            courtId = detail.courtId,
            matchId = matchId,
            courtName = detail.courtName,
            timerType = timerType,
            durationMillisOverride = uiState.value.timerDurationsByType[timerType],
        )
    }

    fun onStopTimer() {
        val detail = uiState.value.detail ?: return
        timerManager.stop(detail.courtId)
    }

    fun onStartMedicalTimeout(playerId: Long) {
        val detail = uiState.value.detail ?: return
        val matchId = detail.matchId ?: return
        if (detail.status != CourtStatus.IN_PROGRESS) return

        val player = (detail.leftSide?.players.orEmpty() + detail.rightSide?.players.orEmpty())
            .firstOrNull { it.playerId == playerId }
        if (player == null) {
            feedbackMessage.value = UiFeedback("Player not found for this match.")
            return
        }

        if (hasMedicalTimeoutAlreadyUsed(uiState.value.matchEvents, playerId)) {
            feedbackMessage.value = UiFeedback("${player.name} already used medical timeout.")
            return
        }

        timerManager.startOrRestart(
            courtId = detail.courtId,
            matchId = matchId,
            courtName = detail.courtName,
            timerType = CourtTimerType.MEDICAL_TIMEOUT,
            durationMillisOverride = uiState.value.timerDurationsByType[CourtTimerType.MEDICAL_TIMEOUT],
            subjectPlayerId = player.playerId,
            subjectPlayerName = player.name,
        )
        feedbackMessage.value = UiFeedback("Medical timeout started for ${player.name}.")
    }

    fun onOpenDependencyCourt(courtId: Long?) {
        if (courtId == null || courtId <= 0L) {
            feedbackMessage.value = UiFeedback("Source court is not assigned yet.")
            return
        }
        _navigationEvents.tryEmit(CourtDetailNavigationEvent.OpenCourt(courtId))
    }

    fun onSaveLiveScoreSnapshot(rawScore: String) {
        val detail = uiState.value.detail ?: return
        val matchId = detail.matchId ?: return
        if (detail.status != CourtStatus.IN_PROGRESS) return
        val score = normalizeLiveScore(rawScore)
        if (score.isBlank()) {
            feedbackMessage.value = UiFeedback("Enter score before saving.")
            return
        }
        viewModelScope.launch {
            val leftName = sideName(detail.leftSide).replace("|", "/")
            val rightName = sideName(detail.rightSide).replace("|", "/")
            eventLogRepository.logEvent(
                matchId = matchId,
                courtId = detail.courtId,
                kind = MatchEventKind.TIMER,
                offenderPlayerId = null,
                message = "SCORE_SNAPSHOT|$score|left=$leftName|right=$rightName",
            )
            feedbackMessage.value = UiFeedback("Live score saved: $score")
        }
    }

    fun onLogScoreArgument(summary: String) {
        val detail = uiState.value.detail ?: return
        val matchId = detail.matchId ?: return
        if (summary.isBlank()) return
        viewModelScope.launch {
            eventLogRepository.logEvent(
                matchId = matchId,
                courtId = detail.courtId,
                kind = MatchEventKind.TIMER,
                offenderPlayerId = null,
                message = "ARGUMENT|$summary",
            )
            feedbackMessage.value = UiFeedback("Argument saved to log.")
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
            feedbackMessage.value = UiFeedback(
                message = "${result.summary}. Offence ${result.guidance.offenceNumber}: ${result.guidance.appliedPenalty}. Next: ${result.guidance.nextPenalty}",
                actionLabel = "Undo",
            )
        }
    }

    fun onUndoLastViolation() {
        val matchId = uiState.value.detail?.matchId ?: return
        viewModelScope.launch {
            val undone = matchRepository.undoLastViolation(matchId)
            feedbackMessage.value = UiFeedback(
                message = if (undone) "Last violation undone" else "No violation to undo",
            )
        }
    }

    fun onSubmitFinalScore(winnerSide: MatchWinnerSide, scoreText: String) {
        val matchId = uiState.value.detail?.matchId ?: return
        viewModelScope.launch {
            val resultType = inferResultType(scoreText)
            val result = matchRepository.endMatch(
                matchId = matchId,
                winnerSide = winnerSide,
                resultType = resultType,
                scoreText = scoreText,
            )
            feedbackMessage.value = when (result) {
                EndMatchResult.Success -> {
                    selectedServerPlayerId.value = null
                    warmupCompletedForMatchId = null
                    warmupStartedForMatchId = null
                    timerManager.stop(courtId)
                    _navigationEvents.tryEmit(CourtDetailNavigationEvent.BackToCourts)
                    UiFeedback("Final score submitted")
                }
                EndMatchResult.MatchNotFound -> UiFeedback("Match not found")
                EndMatchResult.InvalidMatchState -> UiFeedback("Match must be in progress")
                EndMatchResult.MissingResolvedParticipants -> UiFeedback("Unable to determine winner teams")
            }
        }
    }

    fun onUpdateScheduledMatch(
        leftPlayerId: Long,
        rightPlayerId: Long,
        timing: ScheduledMatchTiming,
        notBeforeLabel: String?,
    ) {
        val detail = uiState.value.detail ?: return
        val matchId = detail.matchId ?: return
        if (detail.status != CourtStatus.SCHEDULED) {
            feedbackMessage.value = UiFeedback("Only scheduled matches can be edited.")
            return
        }
        viewModelScope.launch {
            val updated = matchRepository.updateScheduledMatch(
                matchId = matchId,
                leftPlayerId = leftPlayerId,
                rightPlayerId = rightPlayerId,
                timing = timing,
                notBeforeLabel = notBeforeLabel,
            )
            if (updated) {
                selectedServerPlayerId.value = null
                feedbackMessage.value = UiFeedback("Scheduled match updated.")
            } else {
                feedbackMessage.value = UiFeedback("Could not update scheduled match.")
            }
        }
    }

    fun clearFeedback() {
        feedbackMessage.value = null
    }

    private suspend fun maybeStartWarmup(
        detail: CourtDetail?,
        selectedServer: Long?,
        timerState: CourtTimerState?,
        settings: AppSettings,
    ) {
        val match = detail ?: return
        val matchId = match.matchId ?: return
        if (match.status != CourtStatus.SCHEDULED) return
        if (!settings.autoStartWarmup) return
        if (!match.canStartMatch || selectedServer == null) return
        if (warmupCompletedForMatchId == matchId) return
        if (warmupStartedForMatchId == matchId) return
        if (timerState?.timerType == CourtTimerType.WARMUP) return

        warmupStartedForMatchId = matchId
        timerManager.startOrRestart(
            courtId = match.courtId,
            matchId = matchId,
            courtName = match.courtName,
            timerType = CourtTimerType.WARMUP,
            durationMillisOverride = uiState.value.timerDurationsByType[CourtTimerType.WARMUP],
        )
        feedbackMessage.value = UiFeedback("Warm-up timer started.")
    }

    private suspend fun maybeAutoStartAfterWarmup(
        detail: CourtDetail?,
        selectedServer: Long?,
        timerState: CourtTimerState?,
        settings: AppSettings,
    ) {
        val match = detail ?: return
        val matchId = match.matchId ?: return
        if (match.status != CourtStatus.SCHEDULED) return
        if (!settings.autoStartMatchAfterWarmup) return
        if (selectedServer == null) return
        if (timerState?.timerType != CourtTimerType.WARMUP) return
        if (timerState.isRunning || timerState.remainingMillis > 0) return
        if (warmupCompletedForMatchId == matchId) return

        warmupCompletedForMatchId = matchId
        warmupStartedForMatchId = null
        val result = matchRepository.autoStartMatchFromWarmup(matchId)
        feedbackMessage.value = when (result) {
            StartMatchResult.Success -> UiFeedback("Warm-up finished. Match started.")
            StartMatchResult.MatchNotFound -> UiFeedback("Match not found")
            StartMatchResult.MissingResolvedParticipants -> UiFeedback("Participants are not resolved")
            StartMatchResult.InvalidInitialServer -> UiFeedback("Selected server is invalid")
            StartMatchResult.InvalidMatchState -> UiFeedback("Only scheduled matches can be started")
        }
        timerManager.stop(match.courtId)
    }

    private fun inferResultType(scoreText: String): MatchResultType {
        val normalized = scoreText.trim()
        if (normalized.isBlank()) {
            return MatchResultType.WALKOVER
        }

        val lowered = normalized.lowercase()
        if (lowered == "wo" || lowered == "w/o" || lowered == "walkover") {
            return MatchResultType.WALKOVER
        }

        val tokens = normalized
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val setScores = tokens
            .map { token -> token.substringBefore("(").trim() }
            .mapNotNull { token ->
                val match = SET_SCORE_REGEX.matchEntire(token) ?: return@mapNotNull null
                val left = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val right = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                left to right
            }

        if (setScores.size != tokens.size) {
            return MatchResultType.RETIREMENT
        }

        if (setScores.isEmpty()) {
            return MatchResultType.RETIREMENT
        }

        return if (setScores.all { (left, right) -> isCompleteSet(left, right) }) {
            MatchResultType.NORMAL
        } else {
            MatchResultType.RETIREMENT
        }
    }

    private fun isCompleteSet(leftGames: Int, rightGames: Int): Boolean {
        if (leftGames < 0 || rightGames < 0) return false
        val high = maxOf(leftGames, rightGames)
        val low = minOf(leftGames, rightGames)
        if (high >= 10) {
            return high - low >= 2
        }
        if (high == 7 && (low == 5 || low == 6)) {
            return true
        }
        return high >= 6 && high - low >= 2
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

    private fun hasMedicalTimeoutAlreadyUsed(events: List<MatchEvent>, playerId: Long): Boolean {
        return events.any { event ->
            event.kind == MatchEventKind.TIMER &&
                event.message?.startsWith("TIMER|MEDICAL_TIMEOUT|START") == true &&
                extractPlayerIdFromTimerMessage(event.message) == playerId
        }
    }

    private fun extractPlayerIdFromTimerMessage(message: String?): Long? {
        val value = message
            ?.split("|")
            ?.firstOrNull { token -> token.startsWith("playerId=") }
            ?.substringAfter("=")
            ?.trim()
        return value?.toLongOrNull()
    }

    private fun normalizeLiveScore(raw: String): String {
        return raw
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace("-", ":")
    }

    private fun sideName(side: SideParticipant?): String {
        val players = side?.players.orEmpty()
        if (players.isNotEmpty()) {
            return players.joinToString(" / ") { player -> player.name }
        }
        return side?.label ?: "TBD"
    }

    private companion object {
        val SET_SCORE_REGEX = Regex("(\\d+)\\s*[:-]\\s*(\\d+)")
    }

    private fun buildTimerDurations(settings: AppSettings): Map<CourtTimerType, Long> {
        return mapOf(
            CourtTimerType.WARMUP to settings.warmupMinutes * 60_000L,
            CourtTimerType.SIDE_SWITCH to settings.sideSwitchSeconds * 1_000L,
            CourtTimerType.SET_BREAK to settings.setBreakSeconds * 1_000L,
            CourtTimerType.TOILET_BREAK to settings.toiletBreakSeconds * 1_000L,
            CourtTimerType.OFF_COURT to settings.offCourtSeconds * 1_000L,
            CourtTimerType.MEDICAL_TIMEOUT to settings.medicalTimeoutSeconds * 1_000L,
            CourtTimerType.EQUIPMENT_FIX to settings.equipmentFixSeconds * 1_000L,
        )
    }
}
