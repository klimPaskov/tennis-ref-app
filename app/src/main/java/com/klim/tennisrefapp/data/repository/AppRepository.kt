package com.klim.tennisrefapp.data.repository

import androidx.room.withTransaction
import com.klim.tennisrefapp.data.local.AppDatabase
import com.klim.tennisrefapp.data.local.dao.CourtDao
import com.klim.tennisrefapp.data.local.dao.EventLogDao
import com.klim.tennisrefapp.data.local.dao.MatchDao
import com.klim.tennisrefapp.data.local.dao.PlayerDao
import com.klim.tennisrefapp.data.local.dao.SlotDao
import com.klim.tennisrefapp.data.local.dao.TeamDao
import com.klim.tennisrefapp.data.local.dao.TeamPlayerDao
import com.klim.tennisrefapp.data.local.dao.TournamentDao
import com.klim.tennisrefapp.data.local.entity.EventLogEntity
import com.klim.tennisrefapp.data.local.entity.EventType
import com.klim.tennisrefapp.data.local.entity.MatchEntity
import com.klim.tennisrefapp.data.local.entity.MatchStatus
import com.klim.tennisrefapp.data.local.entity.PlayerEntity
import com.klim.tennisrefapp.data.local.entity.SlotEntity
import com.klim.tennisrefapp.data.local.entity.SlotPosition
import com.klim.tennisrefapp.data.local.entity.SlotType
import com.klim.tennisrefapp.data.local.entity.TeamPlayerCrossRefEntity
import com.klim.tennisrefapp.data.local.entity.TournamentEntity
import com.klim.tennisrefapp.domain.model.CourtCard
import com.klim.tennisrefapp.domain.model.CourtDetail
import com.klim.tennisrefapp.domain.model.CourtStatus
import com.klim.tennisrefapp.domain.model.MatchDependency
import com.klim.tennisrefapp.domain.model.MatchEvent
import com.klim.tennisrefapp.domain.model.MatchEventKind
import com.klim.tennisrefapp.domain.model.NotBeforeInput
import com.klim.tennisrefapp.domain.model.PlayerAvailability
import com.klim.tennisrefapp.domain.model.PlayerRef
import com.klim.tennisrefapp.domain.model.PlayerSummary
import com.klim.tennisrefapp.domain.model.SideParticipant
import com.klim.tennisrefapp.domain.model.SlotReference
import com.klim.tennisrefapp.domain.model.SlotPositionRef
import com.klim.tennisrefapp.domain.model.SlotSummary
import com.klim.tennisrefapp.domain.model.SlotTypeRef
import com.klim.tennisrefapp.domain.model.TeamSummary
import com.klim.tennisrefapp.domain.repository.CourtRepository
import com.klim.tennisrefapp.domain.repository.EventLogRepository
import com.klim.tennisrefapp.domain.repository.MatchRepository
import com.klim.tennisrefapp.domain.repository.PlayerRepository
import com.klim.tennisrefapp.domain.repository.SlotRepository
import com.klim.tennisrefapp.domain.repository.StartMatchResult
import com.klim.tennisrefapp.domain.repository.TeamRepository
import com.klim.tennisrefapp.domain.repository.TournamentRepository
import com.klim.tennisrefapp.domain.usecase.ComputeNotBeforeUseCase
import com.klim.tennisrefapp.domain.usecase.ResolveSlotUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class AppRepository @Inject constructor(
    private val database: AppDatabase,
    private val tournamentDao: TournamentDao,
    private val courtDao: CourtDao,
    private val playerDao: PlayerDao,
    private val teamDao: TeamDao,
    private val teamPlayerDao: TeamPlayerDao,
    private val matchDao: MatchDao,
    private val slotDao: SlotDao,
    private val eventLogDao: EventLogDao,
    private val resolveSlotUseCase: ResolveSlotUseCase,
    private val computeNotBeforeUseCase: ComputeNotBeforeUseCase,
) : TournamentRepository,
    CourtRepository,
    MatchRepository,
    EventLogRepository,
    PlayerRepository,
    TeamRepository,
    SlotRepository {

    override suspend fun seedIfEmpty() {
        if (tournamentDao.count() > 0) return

        val now = System.currentTimeMillis()
        database.withTransaction {
            tournamentDao.insert(
                TournamentEntity(
                    id = 1L,
                    name = "Spring Open",
                    defaultRestMinutes = 30,
                    createdAt = now,
                ),
            )

            courtDao.insertAll(
                listOf(
                    CourtEntity(id = 1L, tournamentId = 1L, name = "Court 1", sortOrder = 1),
                    CourtEntity(id = 2L, tournamentId = 1L, name = "Court 2", sortOrder = 2),
                    CourtEntity(id = 3L, tournamentId = 1L, name = "Court 3", sortOrder = 3),
                ),
            )

            playerDao.insertAll(
                listOf(
                    PlayerEntity(id = 1L, tournamentId = 1L, displayName = "Alice", restMinutes = 30),
                    PlayerEntity(id = 2L, tournamentId = 1L, displayName = "Bob", restMinutes = 25),
                    PlayerEntity(id = 3L, tournamentId = 1L, displayName = "Carla", restMinutes = 40),
                    PlayerEntity(id = 4L, tournamentId = 1L, displayName = "Diego", restMinutes = 30),
                    PlayerEntity(id = 5L, tournamentId = 1L, displayName = "Emma", restMinutes = 20),
                    PlayerEntity(id = 6L, tournamentId = 1L, displayName = "Felix", restMinutes = 20),
                ),
            )

            teamDao.insertAll(
                listOf(
                    TeamEntity(id = 1L, tournamentId = 1L, name = null),
                    TeamEntity(id = 2L, tournamentId = 1L, name = null),
                    TeamEntity(id = 3L, tournamentId = 1L, name = null),
                    TeamEntity(id = 4L, tournamentId = 1L, name = null),
                    TeamEntity(id = 5L, tournamentId = 1L, name = "Emma / Felix"),
                ),
            )

            teamPlayerDao.insertAll(
                listOf(
                    TeamPlayerCrossRefEntity(teamId = 1L, playerId = 1L, playerOrder = 0),
                    TeamPlayerCrossRefEntity(teamId = 2L, playerId = 2L, playerOrder = 0),
                    TeamPlayerCrossRefEntity(teamId = 3L, playerId = 3L, playerOrder = 0),
                    TeamPlayerCrossRefEntity(teamId = 4L, playerId = 4L, playerOrder = 0),
                    TeamPlayerCrossRefEntity(teamId = 5L, playerId = 5L, playerOrder = 0),
                    TeamPlayerCrossRefEntity(teamId = 5L, playerId = 6L, playerOrder = 1),
                ),
            )

            matchDao.insertAll(
                listOf(
                    MatchEntity(
                        id = 100L,
                        tournamentId = 1L,
                        courtId = 2L,
                        roundName = "R16",
                        status = MatchStatus.COMPLETED,
                        manualNotBefore = null,
                        computedNotBefore = null,
                        startedAt = now - 120 * 60_000,
                        endedAt = now - 90 * 60_000,
                        winnerTeamId = 1L,
                        initialLeftTeamId = 1L,
                        initialRightTeamId = 2L,
                        initialServerPlayerId = 1L,
                    ),
                    MatchEntity(
                        id = 101L,
                        tournamentId = 1L,
                        courtId = 1L,
                        roundName = "QF",
                        status = MatchStatus.SCHEDULED,
                        manualNotBefore = now + 20 * 60_000,
                        computedNotBefore = null,
                        startedAt = null,
                        endedAt = null,
                        winnerTeamId = null,
                        initialLeftTeamId = null,
                        initialRightTeamId = null,
                        initialServerPlayerId = null,
                    ),
                    MatchEntity(
                        id = 102L,
                        tournamentId = 1L,
                        courtId = 3L,
                        roundName = "R16",
                        status = MatchStatus.IN_PROGRESS,
                        manualNotBefore = null,
                        computedNotBefore = null,
                        startedAt = now - 15 * 60_000,
                        endedAt = null,
                        winnerTeamId = null,
                        initialLeftTeamId = 4L,
                        initialRightTeamId = 3L,
                        initialServerPlayerId = 4L,
                    ),
                    MatchEntity(
                        id = 103L,
                        tournamentId = 1L,
                        courtId = null,
                        roundName = "R32",
                        status = MatchStatus.SCHEDULED,
                        manualNotBefore = null,
                        computedNotBefore = null,
                        startedAt = null,
                        endedAt = null,
                        winnerTeamId = null,
                        initialLeftTeamId = null,
                        initialRightTeamId = null,
                        initialServerPlayerId = null,
                    ),
                ),
            )

            slotDao.insertAll(
                listOf(
                    SlotEntity(
                        id = 1001L,
                        matchId = 100L,
                        position = SlotPosition.LEFT,
                        slotType = SlotType.DIRECT_TEAM,
                        directTeamId = 1L,
                        winnerOfMatchId = null,
                    ),
                    SlotEntity(
                        id = 1002L,
                        matchId = 100L,
                        position = SlotPosition.RIGHT,
                        slotType = SlotType.DIRECT_TEAM,
                        directTeamId = 2L,
                        winnerOfMatchId = null,
                    ),
                    SlotEntity(
                        id = 1011L,
                        matchId = 101L,
                        position = SlotPosition.LEFT,
                        slotType = SlotType.WINNER_OF_MATCH,
                        directTeamId = null,
                        winnerOfMatchId = 100L,
                    ),
                    SlotEntity(
                        id = 1012L,
                        matchId = 101L,
                        position = SlotPosition.RIGHT,
                        slotType = SlotType.DIRECT_TEAM,
                        directTeamId = 3L,
                        winnerOfMatchId = null,
                    ),
                    SlotEntity(
                        id = 1021L,
                        matchId = 102L,
                        position = SlotPosition.LEFT,
                        slotType = SlotType.DIRECT_TEAM,
                        directTeamId = 4L,
                        winnerOfMatchId = null,
                    ),
                    SlotEntity(
                        id = 1022L,
                        matchId = 102L,
                        position = SlotPosition.RIGHT,
                        slotType = SlotType.DIRECT_TEAM,
                        directTeamId = 3L,
                        winnerOfMatchId = null,
                    ),
                    SlotEntity(
                        id = 1031L,
                        matchId = 103L,
                        position = SlotPosition.LEFT,
                        slotType = SlotType.DIRECT_TEAM,
                        directTeamId = 5L,
                        winnerOfMatchId = null,
                    ),
                    SlotEntity(
                        id = 1032L,
                        matchId = 103L,
                        position = SlotPosition.RIGHT,
                        slotType = SlotType.BYE,
                        directTeamId = null,
                        winnerOfMatchId = null,
                    ),
                ),
            )

            eventLogDao.insertAll(
                listOf(
                    EventLogEntity(
                        matchId = 100L,
                        courtId = 2L,
                        timestamp = now - 120 * 60_000,
                        eventType = EventType.MATCH_STARTED,
                        offenderPlayerId = null,
                        message = "Match started",
                    ),
                    EventLogEntity(
                        matchId = 100L,
                        courtId = 2L,
                        timestamp = now - 110 * 60_000,
                        eventType = EventType.VIOLATION,
                        offenderPlayerId = 2L,
                        message = "Racquet abuse",
                    ),
                    EventLogEntity(
                        matchId = 100L,
                        courtId = 2L,
                        timestamp = now - 90 * 60_000,
                        eventType = EventType.MATCH_ENDED,
                        offenderPlayerId = null,
                        message = "Final score 6-4 6-3",
                    ),
                    EventLogEntity(
                        matchId = 102L,
                        courtId = 3L,
                        timestamp = now - 13 * 60_000,
                        eventType = EventType.TIMER,
                        offenderPlayerId = null,
                        message = "Warm-up timer started",
                    ),
                    EventLogEntity(
                        matchId = 102L,
                        courtId = 3L,
                        timestamp = now - 12 * 60_000,
                        eventType = EventType.TIMER,
                        offenderPlayerId = null,
                        message = "Warm-up reached 3:00",
                    ),
                ),
            )
        }
    }

    override fun observePlayers(): Flow<List<PlayerSummary>> {
        return playerDao.observeAll().map { players ->
            players.map { player ->
                PlayerSummary(
                    id = player.id,
                    displayName = player.displayName,
                    restMinutes = player.restMinutes,
                )
            }
        }
    }

    override fun observeTeams(): Flow<List<TeamSummary>> {
        return combine(teamDao.observeAll(), teamPlayerDao.observeAll()) { teams, crossRefs ->
            val playerIdsByTeam = crossRefs.groupBy { it.teamId }.mapValues { entry ->
                entry.value.sortedBy { it.playerOrder }.map { it.playerId }
            }
            teams.map { team ->
                TeamSummary(
                    id = team.id,
                    name = team.name,
                    playerIds = playerIdsByTeam[team.id].orEmpty(),
                )
            }
        }
    }

    override fun observeSlotsForMatch(matchId: Long): Flow<List<SlotSummary>> {
        return slotDao.observeByMatchId(matchId).map { slots ->
            slots.map { slot ->
                SlotSummary(
                    id = slot.id,
                    matchId = slot.matchId,
                    position = slot.position.toSlotPositionRef(),
                    type = slot.slotType.toSlotTypeRef(),
                    directTeamId = slot.directTeamId,
                    winnerOfMatchId = slot.winnerOfMatchId,
                )
            }
        }
    }

    override fun observeCourtCards(): Flow<List<CourtCard>> {
        return combine(
            courtDao.observeActiveCourts(),
            matchDao.observeAll(),
            slotDao.observeAll(),
            teamPlayerDao.observeAll(),
            playerDao.observeAll(),
            tournamentDao.observeFirst(),
        ) { courts, matches, slots, teamPlayers, players, tournament ->
            val context = BuildContext(
                matches = matches,
                slotsByMatch = slots.groupBy { it.matchId },
                playersById = players.associateBy { it.id },
                teamPlayersByTeam = teamPlayers.groupBy { it.teamId }.mapValues { entry ->
                    entry.value.sortedBy { it.playerOrder }.mapNotNull { ref -> players.firstOrNull { it.id == ref.playerId } }
                },
                defaultRestMinutes = tournament?.defaultRestMinutes ?: DEFAULT_REST_MINUTES,
            )
            courts.map { court ->
                val currentMatch = pickCurrentMatch(court.id, matches)
                if (currentMatch == null) {
                    CourtCard(
                        courtId = court.id,
                        courtName = court.name,
                        status = CourtStatus.EMPTY,
                        currentMatchLabel = "No match",
                        computedNotBeforeMillis = null,
                    )
                } else {
                    CourtCard(
                        courtId = court.id,
                        courtName = court.name,
                        status = currentMatch.status.toCourtStatus(),
                        currentMatchLabel = formatMatchLabel(currentMatch, context),
                        computedNotBeforeMillis = computeNotBefore(currentMatch, context),
                    )
                }
            }
        }
    }

    override fun observeCourtDetail(courtId: Long): Flow<CourtDetail?> {
        return combine(
            courtDao.observeById(courtId),
            matchDao.observeAll(),
            slotDao.observeAll(),
            teamPlayerDao.observeAll(),
            playerDao.observeAll(),
            tournamentDao.observeFirst(),
        ) { court, matches, slots, teamPlayers, players, tournament ->
            val currentCourt = court ?: return@combine null
            val context = BuildContext(
                matches = matches,
                slotsByMatch = slots.groupBy { it.matchId },
                playersById = players.associateBy { it.id },
                teamPlayersByTeam = teamPlayers.groupBy { it.teamId }.mapValues { entry ->
                    entry.value.sortedBy { it.playerOrder }.mapNotNull { ref -> players.firstOrNull { it.id == ref.playerId } }
                },
                defaultRestMinutes = tournament?.defaultRestMinutes ?: DEFAULT_REST_MINUTES,
            )
            val currentMatch = pickCurrentMatch(courtId, matches)
            if (currentMatch == null) {
                CourtDetail(
                    courtId = currentCourt.id,
                    courtName = currentCourt.name,
                    status = CourtStatus.EMPTY,
                    matchId = null,
                    leftSide = null,
                    rightSide = null,
                    computedNotBeforeMillis = null,
                    startedAtMillis = null,
                    initialServerPlayerId = null,
                    canSwapSides = false,
                    canStartMatch = false,
                )
            } else {
                val left = sideForMatch(currentMatch.id, SlotPosition.LEFT, context)
                val right = sideForMatch(currentMatch.id, SlotPosition.RIGHT, context)
                val canStart = currentMatch.status == MatchStatus.SCHEDULED &&
                    left?.resolvedTeamId != null && right?.resolvedTeamId != null
                CourtDetail(
                    courtId = currentCourt.id,
                    courtName = currentCourt.name,
                    status = currentMatch.status.toCourtStatus(),
                    matchId = currentMatch.id,
                    leftSide = left,
                    rightSide = right,
                    computedNotBeforeMillis = computeNotBefore(currentMatch, context),
                    startedAtMillis = currentMatch.startedAt,
                    initialServerPlayerId = currentMatch.initialServerPlayerId,
                    canSwapSides = currentMatch.status == MatchStatus.SCHEDULED,
                    canStartMatch = canStart,
                )
            }
        }
    }

    override suspend fun swapSides(matchId: Long) {
        database.withTransaction {
            val match = matchDao.getById(matchId) ?: return@withTransaction
            if (match.status != MatchStatus.SCHEDULED) return@withTransaction

            val slots = slotDao.getByMatchId(matchId)
            val left = slots.firstOrNull { it.position == SlotPosition.LEFT } ?: return@withTransaction
            val right = slots.firstOrNull { it.position == SlotPosition.RIGHT } ?: return@withTransaction

            slotDao.update(left.copy(position = SlotPosition.RIGHT))
            slotDao.update(right.copy(position = SlotPosition.LEFT))
        }
    }

    override suspend fun startMatch(
        matchId: Long,
        initialServerPlayerId: Long,
        timestamp: Long,
    ): StartMatchResult {
        return database.withTransaction {
            val match = matchDao.getById(matchId) ?: return@withTransaction StartMatchResult.MatchNotFound
            if (match.status != MatchStatus.SCHEDULED) {
                return@withTransaction StartMatchResult.InvalidMatchState
            }

            val allMatches = matchDao.getAll()
            val allSlots = slotDao.getAll()
            val players = playerDao.getAll()
            val teamPlayers = teamPlayerDao.getAll()
            val tournament = tournamentDao.getFirst()
            val context = BuildContext(
                matches = allMatches,
                slotsByMatch = allSlots.groupBy { it.matchId },
                playersById = players.associateBy { it.id },
                teamPlayersByTeam = teamPlayers.groupBy { it.teamId }.mapValues { entry ->
                    entry.value.sortedBy { it.playerOrder }.mapNotNull { ref -> players.firstOrNull { it.id == ref.playerId } }
                },
                defaultRestMinutes = tournament?.defaultRestMinutes ?: DEFAULT_REST_MINUTES,
            )

            val left = sideForMatch(match.id, SlotPosition.LEFT, context)
            val right = sideForMatch(match.id, SlotPosition.RIGHT, context)
            val leftTeam = left?.resolvedTeamId
            val rightTeam = right?.resolvedTeamId

            if (leftTeam == null || rightTeam == null) {
                return@withTransaction StartMatchResult.MissingResolvedParticipants
            }

            val validServerIds = left.players.map { it.playerId } + right.players.map { it.playerId }
            if (initialServerPlayerId !in validServerIds) {
                return@withTransaction StartMatchResult.InvalidInitialServer
            }

            matchDao.update(
                match.copy(
                    status = MatchStatus.IN_PROGRESS,
                    startedAt = timestamp,
                    initialLeftTeamId = leftTeam,
                    initialRightTeamId = rightTeam,
                    initialServerPlayerId = initialServerPlayerId,
                ),
            )

            eventLogDao.insert(
                EventLogEntity(
                    matchId = match.id,
                    courtId = match.courtId,
                    timestamp = timestamp,
                    eventType = EventType.MATCH_STARTED,
                    offenderPlayerId = null,
                    message = "Match started with server $initialServerPlayerId",
                ),
            )

            StartMatchResult.Success
        }
    }

    override suspend fun logEvent(
        matchId: Long?,
        courtId: Long?,
        kind: MatchEventKind,
        offenderPlayerId: Long?,
        message: String?,
        timestamp: Long,
    ) {
        eventLogDao.insert(
            EventLogEntity(
                matchId = matchId,
                courtId = courtId,
                timestamp = timestamp,
                eventType = kind.toEntityType(),
                offenderPlayerId = offenderPlayerId,
                message = message,
            ),
        )
    }

    override fun observeEventsForMatch(matchId: Long): Flow<List<MatchEvent>> {
        return eventLogDao.observeByMatchId(matchId).mapList { event ->
            MatchEvent(
                id = event.id,
                matchId = event.matchId,
                courtId = event.courtId,
                timestamp = event.timestamp,
                kind = event.eventType.toDomainKind(),
                offenderPlayerId = event.offenderPlayerId,
                message = event.message,
            )
        }
    }

    private fun computeNotBefore(match: MatchEntity, context: BuildContext): Long? {
        val players = buildKnownPlayersForMatch(match.id, context)
        val latestEndedByPlayer = latestEndedByPlayer(context)
        val constraints = players.map { player ->
            PlayerAvailability(
                playerId = player.id,
                restMinutes = player.restMinutes ?: context.defaultRestMinutes,
                lastEndedAtMillis = latestEndedByPlayer[player.id],
            )
        }
        return computeNotBeforeUseCase(
            NotBeforeInput(
                manualNotBeforeMillis = match.manualNotBefore,
                participants = constraints,
            ),
        )
    }

    private fun buildKnownPlayersForMatch(matchId: Long, context: BuildContext): List<PlayerEntity> {
        val left = sideForMatch(matchId, SlotPosition.LEFT, context)
        val right = sideForMatch(matchId, SlotPosition.RIGHT, context)
        val ids = buildList {
            addAll(left?.players?.map { it.playerId }.orEmpty())
            addAll(right?.players?.map { it.playerId }.orEmpty())
        }
        return ids.distinct().mapNotNull { context.playersById[it] }
    }

    private fun latestEndedByPlayer(context: BuildContext): Map<Long, Long> {
        val latest = mutableMapOf<Long, Long>()
        for (match in context.matches) {
            val endedAt = match.endedAt ?: continue
            val teamIds = listOfNotNull(
                match.initialLeftTeamId,
                match.initialRightTeamId,
            ).ifEmpty {
                val left = sideForMatch(match.id, SlotPosition.LEFT, context)
                val right = sideForMatch(match.id, SlotPosition.RIGHT, context)
                listOfNotNull(left?.resolvedTeamId, right?.resolvedTeamId)
            }
            for (teamId in teamIds) {
                val players = context.teamPlayersByTeam[teamId].orEmpty()
                for (player in players) {
                    val existing = latest[player.id]
                    if (existing == null || endedAt > existing) {
                        latest[player.id] = endedAt
                    }
                }
            }
        }
        return latest
    }

    private fun formatMatchLabel(match: MatchEntity, context: BuildContext): String {
        val left = sideForMatch(match.id, SlotPosition.LEFT, context)?.label ?: "TBD"
        val right = sideForMatch(match.id, SlotPosition.RIGHT, context)?.label ?: "TBD"
        return "$left vs $right"
    }

    private fun sideForMatch(
        matchId: Long,
        position: SlotPosition,
        context: BuildContext,
    ): SideParticipant? {
        val slot = context.slotsByMatch[matchId].orEmpty().firstOrNull { it.position == position } ?: return null
        return when (slot.slotType) {
            SlotType.DIRECT_TEAM -> {
                val teamId = slot.directTeamId
                val players = teamId?.let { context.teamPlayersByTeam[it].orEmpty() }.orEmpty().map {
                    PlayerRef(playerId = it.id, name = it.displayName)
                }
                SideParticipant(
                    slotId = slot.id,
                    label = labelForTeam(teamId, context),
                    players = players,
                    resolvedTeamId = teamId,
                    isResolved = teamId != null,
                )
            }

            SlotType.WINNER_OF_MATCH -> {
                val winnerOfMatch = slot.winnerOfMatchId
                val resolution = resolveSlotUseCase(
                    SlotReference.WinnerOfMatch(winnerOfMatch ?: -1L),
                    context.matches.associate { candidate ->
                        candidate.id to MatchDependency(candidate.id, candidate.winnerTeamId)
                    },
                )
                val players = resolution.resolvedTeamId?.let { context.teamPlayersByTeam[it].orEmpty() }
                    .orEmpty()
                    .map {
                    PlayerRef(playerId = it.id, name = it.displayName)
                }
                SideParticipant(
                    slotId = slot.id,
                    label = if (resolution.resolvedTeamId != null) {
                        labelForTeam(resolution.resolvedTeamId, context)
                    } else {
                        "Winner of Match ${winnerOfMatch ?: "?"}"
                    },
                    players = players,
                    resolvedTeamId = resolution.resolvedTeamId,
                    isResolved = resolution.isResolved,
                )
            }

            SlotType.BYE -> {
                SideParticipant(
                    slotId = slot.id,
                    label = "BYE",
                    players = emptyList(),
                    resolvedTeamId = null,
                    isResolved = true,
                )
            }
        }
    }

    private fun labelForTeam(teamId: Long?, context: BuildContext): String {
        if (teamId == null) return "TBD"
        val players = context.teamPlayersByTeam[teamId].orEmpty()
        if (players.isNotEmpty()) {
            return players.joinToString(separator = " / ") { it.displayName }
        }
        return "Team $teamId"
    }

    private fun pickCurrentMatch(courtId: Long, matches: List<MatchEntity>): MatchEntity? {
        val matchesForCourt = matches.filter { it.courtId == courtId }
        return matchesForCourt
            .sortedBy { it.id }
            .firstOrNull { it.status == MatchStatus.IN_PROGRESS }
            ?: matchesForCourt.firstOrNull { it.status == MatchStatus.SCHEDULED }
            ?: matchesForCourt.firstOrNull { it.status == MatchStatus.COMPLETED }
    }

    private fun MatchStatus.toCourtStatus(): CourtStatus {
        return when (this) {
            MatchStatus.SCHEDULED -> CourtStatus.SCHEDULED
            MatchStatus.IN_PROGRESS -> CourtStatus.IN_PROGRESS
            MatchStatus.COMPLETED -> CourtStatus.COMPLETED
        }
    }

    private fun MatchEventKind.toEntityType(): EventType {
        return when (this) {
            MatchEventKind.MATCH_STARTED -> EventType.MATCH_STARTED
            MatchEventKind.MATCH_ENDED -> EventType.MATCH_ENDED
            MatchEventKind.VIOLATION -> EventType.VIOLATION
            MatchEventKind.TIMER -> EventType.TIMER
        }
    }

    private fun EventType.toDomainKind(): MatchEventKind {
        return when (this) {
            EventType.MATCH_STARTED -> MatchEventKind.MATCH_STARTED
            EventType.MATCH_ENDED -> MatchEventKind.MATCH_ENDED
            EventType.VIOLATION -> MatchEventKind.VIOLATION
            EventType.TIMER -> MatchEventKind.TIMER
        }
    }

    private fun SlotPosition.toSlotPositionRef(): SlotPositionRef {
        return when (this) {
            SlotPosition.LEFT -> SlotPositionRef.LEFT
            SlotPosition.RIGHT -> SlotPositionRef.RIGHT
        }
    }

    private fun SlotType.toSlotTypeRef(): SlotTypeRef {
        return when (this) {
            SlotType.DIRECT_TEAM -> SlotTypeRef.DIRECT_TEAM
            SlotType.WINNER_OF_MATCH -> SlotTypeRef.WINNER_OF_MATCH
            SlotType.BYE -> SlotTypeRef.BYE
        }
    }

    private data class BuildContext(
        val matches: List<MatchEntity>,
        val slotsByMatch: Map<Long, List<SlotEntity>>,
        val playersById: Map<Long, PlayerEntity>,
        val teamPlayersByTeam: Map<Long, List<PlayerEntity>>,
        val defaultRestMinutes: Int,
    )

    private companion object {
        const val DEFAULT_REST_MINUTES = 30
    }
}

private fun <T, R> Flow<List<T>>.mapList(transform: (T) -> R): Flow<List<R>> =
    map { list -> list.map(transform) }
