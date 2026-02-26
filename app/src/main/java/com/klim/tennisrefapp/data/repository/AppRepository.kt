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
import com.klim.tennisrefapp.data.local.entity.CourtEntity
import com.klim.tennisrefapp.data.local.entity.EventLogEntity
import com.klim.tennisrefapp.data.local.entity.EventType
import com.klim.tennisrefapp.data.local.entity.MatchEntity
import com.klim.tennisrefapp.data.local.entity.MatchResultTypeEntity
import com.klim.tennisrefapp.data.local.entity.MatchStatus
import com.klim.tennisrefapp.data.local.entity.PlayerEntity
import com.klim.tennisrefapp.data.local.entity.SlotEntity
import com.klim.tennisrefapp.data.local.entity.SlotPosition
import com.klim.tennisrefapp.data.local.entity.SlotType
import com.klim.tennisrefapp.data.local.entity.TeamEntity
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
import com.klim.tennisrefapp.domain.repository.MatchResultType
import com.klim.tennisrefapp.domain.repository.MatchRepository
import com.klim.tennisrefapp.domain.repository.MatchWinnerSide
import com.klim.tennisrefapp.domain.repository.PlayerRepository
import com.klim.tennisrefapp.domain.repository.SlotRepository
import com.klim.tennisrefapp.domain.repository.ApplyViolationResult
import com.klim.tennisrefapp.domain.repository.EndMatchResult
import com.klim.tennisrefapp.domain.repository.StartMatchResult
import com.klim.tennisrefapp.domain.repository.TeamRepository
import com.klim.tennisrefapp.domain.repository.TournamentRepository
import com.klim.tennisrefapp.domain.repository.ReminderScheduler
import com.klim.tennisrefapp.domain.model.ViolationCategory
import com.klim.tennisrefapp.domain.model.ViolationCounts
import com.klim.tennisrefapp.domain.usecase.ApplyViolationUseCase
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
    private val applyViolationUseCase: ApplyViolationUseCase,
    private val reminderScheduler: ReminderScheduler,
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
        val playerNames = listOf(
            "Yiyuan Su",
            "Vincent Fletcher",
            "Sonia Sirenius",
            "Vladislava Lescinska",
            "Elizaveta Anikina",
            "Sofia Ioana Voicu",
            "Hanna Vanni",
            "Vera Morozova",
            "Kamilla Cernousova",
            "Marie Benmouyal",
            "Filip Hesser",
            "Andreas Kuum",
        )

        database.withTransaction {
            tournamentDao.insert(
                TournamentEntity(
                    id = 1L,
                    name = "J60 Tallinn (Order of Play sample)",
                    defaultRestMinutes = 30,
                    createdAt = now,
                ),
            )

            courtDao.insertAll(
                listOf(
                    CourtEntity(id = 1L, tournamentId = 1L, name = "Court 2", sortOrder = 1),
                    CourtEntity(id = 2L, tournamentId = 1L, name = "Court 3", sortOrder = 2),
                    CourtEntity(id = 3L, tournamentId = 1L, name = "Court 4", sortOrder = 3),
                ),
            )

            playerDao.insertAll(
                playerNames.mapIndexed { index, name ->
                    PlayerEntity(
                        id = (index + 1).toLong(),
                        tournamentId = 1L,
                        displayName = name,
                        restMinutes = 30,
                    )
                },
            )

            teamDao.insertAll(
                playerNames.indices.map { index ->
                    TeamEntity(
                        id = (index + 1).toLong(),
                        tournamentId = 1L,
                        name = null,
                    )
                },
            )

            teamPlayerDao.insertAll(
                playerNames.indices.map { index ->
                    TeamPlayerCrossRefEntity(
                        teamId = (index + 1).toLong(),
                        playerId = (index + 1).toLong(),
                        playerOrder = 0,
                    )
                },
            )

            matchDao.insertAll(
                listOf(
                    MatchEntity(
                        id = 100L,
                        tournamentId = 1L,
                        courtId = 1L,
                        roundName = "First Match",
                        status = MatchStatus.COMPLETED,
                        manualNotBefore = null,
                        computedNotBefore = null,
                        startedAt = now - 240 * 60_000L,
                        endedAt = now - 210 * 60_000L,
                        winnerTeamId = 2L,
                        initialLeftTeamId = 1L,
                        initialRightTeamId = 2L,
                        initialServerPlayerId = 1L,
                        resultType = MatchResultTypeEntity.NORMAL,
                        scoreText = "3-6 3-6",
                    ),
                    MatchEntity(
                        id = 101L,
                        tournamentId = 1L,
                        courtId = 2L,
                        roundName = "First Match",
                        status = MatchStatus.COMPLETED,
                        manualNotBefore = null,
                        computedNotBefore = null,
                        startedAt = now - 230 * 60_000L,
                        endedAt = now - 200 * 60_000L,
                        winnerTeamId = 3L,
                        initialLeftTeamId = 3L,
                        initialRightTeamId = 4L,
                        initialServerPlayerId = 3L,
                        resultType = MatchResultTypeEntity.NORMAL,
                        scoreText = "6-2 6-4",
                    ),
                    MatchEntity(
                        id = 102L,
                        tournamentId = 1L,
                        courtId = 3L,
                        roundName = "First Match",
                        status = MatchStatus.COMPLETED,
                        manualNotBefore = null,
                        computedNotBefore = null,
                        startedAt = now - 220 * 60_000L,
                        endedAt = now - 190 * 60_000L,
                        winnerTeamId = 5L,
                        initialLeftTeamId = 5L,
                        initialRightTeamId = 6L,
                        initialServerPlayerId = 5L,
                        resultType = MatchResultTypeEntity.NORMAL,
                        scoreText = "6-1 6-0",
                    ),
                    MatchEntity(
                        id = 103L,
                        tournamentId = 1L,
                        courtId = 1L,
                        roundName = "Followed By",
                        status = MatchStatus.SCHEDULED,
                        manualNotBefore = now + 15 * 60_000L,
                        computedNotBefore = null,
                        startedAt = null,
                        endedAt = null,
                        winnerTeamId = null,
                        initialLeftTeamId = null,
                        initialRightTeamId = null,
                        initialServerPlayerId = null,
                        resultType = null,
                        scoreText = null,
                    ),
                    MatchEntity(
                        id = 104L,
                        tournamentId = 1L,
                        courtId = 2L,
                        roundName = "Followed By",
                        status = MatchStatus.SCHEDULED,
                        manualNotBefore = now + 20 * 60_000L,
                        computedNotBefore = null,
                        startedAt = null,
                        endedAt = null,
                        winnerTeamId = null,
                        initialLeftTeamId = null,
                        initialRightTeamId = null,
                        initialServerPlayerId = null,
                        resultType = null,
                        scoreText = null,
                    ),
                    MatchEntity(
                        id = 105L,
                        tournamentId = 1L,
                        courtId = 3L,
                        roundName = "Followed By",
                        status = MatchStatus.SCHEDULED,
                        manualNotBefore = now + 25 * 60_000L,
                        computedNotBefore = null,
                        startedAt = null,
                        endedAt = null,
                        winnerTeamId = null,
                        initialLeftTeamId = null,
                        initialRightTeamId = null,
                        initialServerPlayerId = null,
                        resultType = null,
                        scoreText = null,
                    ),
                    MatchEntity(
                        id = 106L,
                        tournamentId = 1L,
                        courtId = null,
                        roundName = "Extra",
                        status = MatchStatus.SCHEDULED,
                        manualNotBefore = now + 30 * 60_000L,
                        computedNotBefore = null,
                        startedAt = null,
                        endedAt = null,
                        winnerTeamId = null,
                        initialLeftTeamId = null,
                        initialRightTeamId = null,
                        initialServerPlayerId = null,
                        resultType = null,
                        scoreText = null,
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
                        slotType = SlotType.DIRECT_TEAM,
                        directTeamId = 3L,
                        winnerOfMatchId = null,
                    ),
                    SlotEntity(
                        id = 1012L,
                        matchId = 101L,
                        position = SlotPosition.RIGHT,
                        slotType = SlotType.DIRECT_TEAM,
                        directTeamId = 4L,
                        winnerOfMatchId = null,
                    ),
                    SlotEntity(
                        id = 1021L,
                        matchId = 102L,
                        position = SlotPosition.LEFT,
                        slotType = SlotType.DIRECT_TEAM,
                        directTeamId = 5L,
                        winnerOfMatchId = null,
                    ),
                    SlotEntity(
                        id = 1022L,
                        matchId = 102L,
                        position = SlotPosition.RIGHT,
                        slotType = SlotType.DIRECT_TEAM,
                        directTeamId = 6L,
                        winnerOfMatchId = null,
                    ),
                    SlotEntity(
                        id = 1031L,
                        matchId = 103L,
                        position = SlotPosition.LEFT,
                        slotType = SlotType.WINNER_OF_MATCH,
                        directTeamId = null,
                        winnerOfMatchId = 100L,
                    ),
                    SlotEntity(
                        id = 1032L,
                        matchId = 103L,
                        position = SlotPosition.RIGHT,
                        slotType = SlotType.DIRECT_TEAM,
                        directTeamId = 7L,
                        winnerOfMatchId = null,
                    ),
                    SlotEntity(
                        id = 1041L,
                        matchId = 104L,
                        position = SlotPosition.LEFT,
                        slotType = SlotType.WINNER_OF_MATCH,
                        directTeamId = null,
                        winnerOfMatchId = 101L,
                    ),
                    SlotEntity(
                        id = 1042L,
                        matchId = 104L,
                        position = SlotPosition.RIGHT,
                        slotType = SlotType.DIRECT_TEAM,
                        directTeamId = 10L,
                        winnerOfMatchId = null,
                    ),
                    SlotEntity(
                        id = 1051L,
                        matchId = 105L,
                        position = SlotPosition.LEFT,
                        slotType = SlotType.WINNER_OF_MATCH,
                        directTeamId = null,
                        winnerOfMatchId = 102L,
                    ),
                    SlotEntity(
                        id = 1052L,
                        matchId = 105L,
                        position = SlotPosition.RIGHT,
                        slotType = SlotType.DIRECT_TEAM,
                        directTeamId = 12L,
                        winnerOfMatchId = null,
                    ),
                    SlotEntity(
                        id = 1061L,
                        matchId = 106L,
                        position = SlotPosition.LEFT,
                        slotType = SlotType.DIRECT_TEAM,
                        directTeamId = 8L,
                        winnerOfMatchId = null,
                    ),
                    SlotEntity(
                        id = 1062L,
                        matchId = 106L,
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
                        courtId = 1L,
                        timestamp = now - 240 * 60_000L,
                        eventType = EventType.MATCH_STARTED,
                        offenderPlayerId = null,
                        message = "Match started",
                    ),
                    EventLogEntity(
                        matchId = 100L,
                        courtId = 1L,
                        timestamp = now - 210 * 60_000L,
                        eventType = EventType.MATCH_ENDED,
                        offenderPlayerId = null,
                        message = "Final score 3-6 3-6",
                    ),
                    EventLogEntity(
                        matchId = 101L,
                        courtId = 2L,
                        timestamp = now - 230 * 60_000L,
                        eventType = EventType.MATCH_STARTED,
                        offenderPlayerId = null,
                        message = "Match started",
                    ),
                    EventLogEntity(
                        matchId = 101L,
                        courtId = 2L,
                        timestamp = now - 200 * 60_000L,
                        eventType = EventType.MATCH_ENDED,
                        offenderPlayerId = null,
                        message = "Final score 6-2 6-4",
                    ),
                    EventLogEntity(
                        matchId = 102L,
                        courtId = 3L,
                        timestamp = now - 220 * 60_000L,
                        eventType = EventType.MATCH_STARTED,
                        offenderPlayerId = null,
                        message = "Match started",
                    ),
                    EventLogEntity(
                        matchId = 102L,
                        courtId = 3L,
                        timestamp = now - 190 * 60_000L,
                        eventType = EventType.MATCH_ENDED,
                        offenderPlayerId = null,
                        message = "Final score 6-1 6-0",
                    ),
                ),
            )
        }

        recomputeNotBeforeAndSchedule()
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
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val courts = values[0] as List<CourtEntity>
            @Suppress("UNCHECKED_CAST")
            val matches = values[1] as List<MatchEntity>
            @Suppress("UNCHECKED_CAST")
            val slots = values[2] as List<SlotEntity>
            @Suppress("UNCHECKED_CAST")
            val teamPlayers = values[3] as List<TeamPlayerCrossRefEntity>
            @Suppress("UNCHECKED_CAST")
            val players = values[4] as List<PlayerEntity>
            val tournament = values[5] as TournamentEntity?

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
                        matchId = null,
                    )
                } else {
                    CourtCard(
                        courtId = court.id,
                        courtName = court.name,
                        status = currentMatch.status.toCourtStatus(),
                        currentMatchLabel = formatMatchLabel(currentMatch, context),
                        computedNotBeforeMillis = computeNotBefore(currentMatch, context),
                        matchId = currentMatch.id,
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
        ) { values ->
            val court = values[0] as CourtEntity?
            @Suppress("UNCHECKED_CAST")
            val matches = values[1] as List<MatchEntity>
            @Suppress("UNCHECKED_CAST")
            val slots = values[2] as List<SlotEntity>
            @Suppress("UNCHECKED_CAST")
            val teamPlayers = values[3] as List<TeamPlayerCrossRefEntity>
            @Suppress("UNCHECKED_CAST")
            val players = values[4] as List<PlayerEntity>
            val tournament = values[5] as TournamentEntity?

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
                    scoreText = null,
                    resultType = null,
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
                    scoreText = currentMatch.scoreText,
                    resultType = currentMatch.resultType?.name,
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
            .also {
                if (it == StartMatchResult.Success) {
                    recomputeNotBeforeAndSchedule()
                }
            }
    }

    override suspend fun endMatch(
        matchId: Long,
        winnerSide: MatchWinnerSide,
        resultType: MatchResultType,
        scoreText: String,
        timestamp: Long,
    ): EndMatchResult {
        return database.withTransaction {
            val match = matchDao.getById(matchId) ?: return@withTransaction EndMatchResult.MatchNotFound
            if (match.status != MatchStatus.IN_PROGRESS) {
                return@withTransaction EndMatchResult.InvalidMatchState
            }
            val leftTeam = match.initialLeftTeamId
            val rightTeam = match.initialRightTeamId
            if (leftTeam == null || rightTeam == null) {
                return@withTransaction EndMatchResult.MissingResolvedParticipants
            }
            val winnerTeamId = if (winnerSide == MatchWinnerSide.LEFT) leftTeam else rightTeam
            matchDao.update(
                match.copy(
                    status = MatchStatus.COMPLETED,
                    endedAt = timestamp,
                    winnerTeamId = winnerTeamId,
                    resultType = resultType.toEntityType(),
                    scoreText = scoreText,
                ),
            )
            eventLogDao.insert(
                EventLogEntity(
                    matchId = match.id,
                    courtId = match.courtId,
                    timestamp = timestamp,
                    eventType = EventType.MATCH_ENDED,
                    offenderPlayerId = null,
                    message = "RESULT|${resultType.name}|$scoreText|winnerTeamId=$winnerTeamId",
                ),
            )
            EndMatchResult.Success
        }.also {
            if (it == EndMatchResult.Success) {
                recomputeNotBeforeAndSchedule()
            }
        }
    }

    override suspend fun applyViolation(
        matchId: Long,
        offenderPlayerId: Long,
        category: ViolationCategory,
        violationName: String,
        timestamp: Long,
    ): ApplyViolationResult {
        return database.withTransaction {
            val match = matchDao.getById(matchId) ?: return@withTransaction ApplyViolationResult(
                guidance = applyViolationUseCase.apply(ViolationCounts(), category).guidance,
                summary = "Match not found",
            )
            val existing = eventLogDao.getByMatchAndType(matchId, EventType.VIOLATION)
                .filter { it.offenderPlayerId == offenderPlayerId }
            val currentCounts = existing.fold(ViolationCounts()) { acc, event ->
                when (parseViolationCategory(event.message)) {
                    ViolationCategory.CODE -> acc.copy(codeViolationCount = acc.codeViolationCount + 1)
                    ViolationCategory.TIME_SERVER -> acc.copy(timeViolationCountServer = acc.timeViolationCountServer + 1)
                    ViolationCategory.TIME_RECEIVER -> acc.copy(timeViolationCountReceiver = acc.timeViolationCountReceiver + 1)
                    null -> acc
                }
            }
            val decision = applyViolationUseCase.apply(currentCounts, category)
            eventLogDao.insert(
                EventLogEntity(
                    matchId = matchId,
                    courtId = match.courtId,
                    timestamp = timestamp,
                    eventType = EventType.VIOLATION,
                    offenderPlayerId = offenderPlayerId,
                    message = "VIOLATION|${category.name}|$violationName|offence=${decision.guidance.offenceNumber}|applied=${decision.guidance.appliedPenalty}|next=${decision.guidance.nextPenalty}",
                ),
            )
            ApplyViolationResult(
                guidance = decision.guidance,
                summary = "Applied: $violationName to player #$offenderPlayerId",
            )
        }
    }

    override suspend fun undoLastViolation(matchId: Long): Boolean {
        val latest = eventLogDao.getLatestByMatchAndType(matchId, EventType.VIOLATION) ?: return false
        eventLogDao.deleteById(latest.id)
        return true
    }

    override suspend fun clearCourt(courtId: Long) {
        database.withTransaction {
            val all = matchDao.getAll()
            val target = pickCurrentMatch(courtId, all) ?: return@withTransaction
            if (target.status == MatchStatus.COMPLETED) return@withTransaction
            matchDao.update(target.copy(courtId = null))
        }
    }

    override suspend fun assignFirstUnassignedMatch(courtId: Long): Boolean {
        return database.withTransaction {
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
            val candidate = allMatches
                .asSequence()
                .filter { it.courtId == null && it.status == MatchStatus.SCHEDULED }
                .sortedBy { it.id }
                .firstOrNull { match ->
                    val left = sideForMatch(match.id, SlotPosition.LEFT, context)
                    val right = sideForMatch(match.id, SlotPosition.RIGHT, context)
                    left?.resolvedTeamId != null && right?.resolvedTeamId != null
                } ?: return@withTransaction false
            matchDao.update(candidate.copy(courtId = courtId))
            true
        }.also {
            if (it) recomputeNotBeforeAndSchedule()
        }
    }

    override suspend fun swapCourtMatches(courtIdA: Long, courtIdB: Long): Boolean {
        return database.withTransaction {
            val all = matchDao.getAll()
            val first = pickCurrentMatch(courtIdA, all)
            val second = pickCurrentMatch(courtIdB, all)
            if (first == null && second == null) return@withTransaction false

            first?.let { matchDao.update(it.copy(courtId = courtIdB)) }
            second?.let { matchDao.update(it.copy(courtId = courtIdA)) }
            true
        }.also {
            if (it) recomputeNotBeforeAndSchedule()
        }
    }

    override suspend fun moveMatchToCourt(matchId: Long, courtId: Long?): Boolean {
        return database.withTransaction {
            val match = matchDao.getById(matchId) ?: return@withTransaction false
            matchDao.update(match.copy(courtId = courtId))
            true
        }.also {
            if (it) recomputeNotBeforeAndSchedule()
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

    private fun MatchResultType.toEntityType(): MatchResultTypeEntity {
        return when (this) {
            MatchResultType.NORMAL -> MatchResultTypeEntity.NORMAL
            MatchResultType.RETIREMENT -> MatchResultTypeEntity.RETIREMENT
            MatchResultType.WALKOVER -> MatchResultTypeEntity.WALKOVER
            MatchResultType.DEFAULT -> MatchResultTypeEntity.DEFAULT
        }
    }

    private fun parseViolationCategory(message: String?): ViolationCategory? {
        if (message == null) return null
        if (!message.startsWith("VIOLATION|")) return null
        val categoryLabel = message.split("|").getOrNull(1) ?: return null
        return runCatching { ViolationCategory.valueOf(categoryLabel) }.getOrNull()
    }

    private suspend fun recomputeNotBeforeAndSchedule() {
        database.withTransaction {
            val matches = matchDao.getAll()
            val slots = slotDao.getAll()
            val players = playerDao.getAll()
            val teamPlayers = teamPlayerDao.getAll()
            val tournament = tournamentDao.getFirst()
            val courts = courtDao.getAll().associateBy { it.id }
            val context = BuildContext(
                matches = matches,
                slotsByMatch = slots.groupBy { it.matchId },
                playersById = players.associateBy { it.id },
                teamPlayersByTeam = teamPlayers.groupBy { it.teamId }.mapValues { entry ->
                    entry.value.sortedBy { it.playerOrder }.mapNotNull { ref -> players.firstOrNull { it.id == ref.playerId } }
                },
                defaultRestMinutes = tournament?.defaultRestMinutes ?: DEFAULT_REST_MINUTES,
            )

            for (match in matches) {
                val computed = computeNotBefore(match, context)
                if (computed != match.computedNotBefore) {
                    matchDao.update(match.copy(computedNotBefore = computed))
                }
                if (match.status == MatchStatus.SCHEDULED && computed != null) {
                    reminderScheduler.scheduleNotBeforeReminder(
                        matchId = match.id,
                        triggerAtMillis = computed - 10 * 60_000L,
                        courtName = match.courtId?.let { courts[it]?.name ?: "Court $it" },
                    )
                } else {
                    reminderScheduler.cancelNotBeforeReminder(match.id)
                }
            }
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
