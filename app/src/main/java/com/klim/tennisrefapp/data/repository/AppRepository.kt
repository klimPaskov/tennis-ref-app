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
import com.klim.tennisrefapp.domain.model.CompletedCourtMatch
import com.klim.tennisrefapp.domain.model.MatchDependency
import com.klim.tennisrefapp.domain.model.MatchEvent
import com.klim.tennisrefapp.domain.model.MatchEventKind
import com.klim.tennisrefapp.domain.model.MatchHistoryDetail
import com.klim.tennisrefapp.domain.model.NotBeforeInput
import com.klim.tennisrefapp.domain.model.PlayerAvailability
import com.klim.tennisrefapp.domain.model.PlayerDetailInsight
import com.klim.tennisrefapp.domain.model.PlayerMatchSnapshot
import com.klim.tennisrefapp.domain.model.PlayerOverview
import com.klim.tennisrefapp.domain.model.PlayerRef
import com.klim.tennisrefapp.domain.model.PlayerSummary
import com.klim.tennisrefapp.domain.model.RestPrompt
import com.klim.tennisrefapp.domain.model.RestPromptPlayer
import com.klim.tennisrefapp.domain.model.SideParticipant
import com.klim.tennisrefapp.domain.model.SlotReference
import com.klim.tennisrefapp.domain.model.SlotPositionRef
import com.klim.tennisrefapp.domain.model.SlotSummary
import com.klim.tennisrefapp.domain.model.SlotTypeRef
import com.klim.tennisrefapp.domain.model.TeamSummary
import com.klim.tennisrefapp.domain.repository.CourtRepository
import com.klim.tennisrefapp.domain.repository.EventLogRepository
import com.klim.tennisrefapp.domain.repository.HistoryRepository
import com.klim.tennisrefapp.domain.repository.MatchResultType
import com.klim.tennisrefapp.domain.repository.MatchRepository
import com.klim.tennisrefapp.domain.repository.MatchWinnerSide
import com.klim.tennisrefapp.domain.repository.PlayerInsightsRepository
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

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
    HistoryRepository,
    PlayerRepository,
    PlayerInsightsRepository,
    TeamRepository,
    SlotRepository {

    override suspend fun seedIfEmpty() {
        if (tournamentDao.count() > 0) return

        val now = System.currentTimeMillis()
        data class SeedEntrant(val name: String, val countryCode: String)
        data class SeedMatch(
            val courtName: String,
            val order: Int,
            val left: SeedEntrant,
            val right: SeedEntrant,
            val score: String? = null,
            val winnerSide: MatchWinnerSide? = null,
        )
        val courtNames = listOf("Court 2", "Court 3", "Court 4", "Court 5", "Court 6", "Court 7", "Court 8")
        val seedMatches = listOf(
            SeedMatch("Court 2", 1, SeedEntrant("Yiyuan Su", "CHN"), SeedEntrant("Vincent Fletcher", "GBR"), "3-6 3-6", MatchWinnerSide.RIGHT),
            SeedMatch("Court 2", 2, SeedEntrant("Hanna Vanni", "FIN"), SeedEntrant("Vera Morozova", "EST")),
            SeedMatch("Court 2", 3, SeedEntrant("Elizabete Marta Hamitova", "LAT"), SeedEntrant("Margarita Novik", "EST")),
            SeedMatch("Court 2", 4, SeedEntrant("Filip Hesser", "SWE"), SeedEntrant("Andreas Kuum", "EST")),
            SeedMatch("Court 3", 1, SeedEntrant("Sonia Sirenius", "FIN"), SeedEntrant("Vladislava Lescinska", "LAT"), "6-2 6-4", MatchWinnerSide.LEFT),
            SeedMatch("Court 3", 2, SeedEntrant("Kamilla Cernousova", "LAT"), SeedEntrant("Marie Benmouyal", "DEN")),
            SeedMatch("Court 3", 3, SeedEntrant("Mark Griaziutin Tverskoj", "LTU"), SeedEntrant("Alex Tuomolin", "FIN")),
            SeedMatch("Court 3", 4, SeedEntrant("Gabriel Maciel Araujo Pyndt", "DEN"), SeedEntrant("Nick Mertgens", "GER")),
            SeedMatch("Court 4", 1, SeedEntrant("Elizaveta Anikina", "EST"), SeedEntrant("Sofia Ioana Voicu", "BEL"), "6-1 6-0", MatchWinnerSide.LEFT),
            SeedMatch("Court 4", 2, SeedEntrant("Nikita Smirnov", "EST"), SeedEntrant("Moritz Glauser", "LIE")),
            SeedMatch("Court 4", 3, SeedEntrant("Julia Kolodziejska", "POL"), SeedEntrant("Viktoria Sidorenkova", "EST")),
            SeedMatch("Court 4", 4, SeedEntrant("Daria Podlipskaya", "EST"), SeedEntrant("Elin Kalmus", "EST")),
            SeedMatch("Court 5", 1, SeedEntrant("Sonja Wedenski", "SWE"), SeedEntrant("Varvara Trifonova", "EST"), "6-2 6-1", MatchWinnerSide.LEFT),
            SeedMatch("Court 5", 2, SeedEntrant("Svit Suljic", "SLO"), SeedEntrant("Ken Ink", "EST")),
            SeedMatch("Court 5", 3, SeedEntrant("Liisa Marii Pohjala", "EST"), SeedEntrant("Grace Bernstein", "SWE")),
            SeedMatch("Court 5", 4, SeedEntrant("Luukas Raudkivi", "EST"), SeedEntrant("Jan Werblinski", "POL")),
            SeedMatch("Court 6", 1, SeedEntrant("Asia Basiletti", "ITA"), SeedEntrant("Aleksandra Maksimova", "EST"), "6-1 6-2", MatchWinnerSide.LEFT),
            SeedMatch("Court 6", 2, SeedEntrant("Margarita Aktuganova", "ISR"), SeedEntrant("Ksenia Semjonova", "EST")),
            SeedMatch("Court 6", 3, SeedEntrant("Vlad Secara", "ROU"), SeedEntrant("Rasmus Stalmeister", "EST")),
            SeedMatch("Court 6", 4, SeedEntrant("Nand Vandepoele", "BEL"), SeedEntrant("Egert Trave", "EST")),
            SeedMatch("Court 7", 1, SeedEntrant("Edwin Averjanov", "EST"), SeedEntrant("Jan Sykora", "CZE"), "4-6 3-6", MatchWinnerSide.RIGHT),
            SeedMatch("Court 7", 2, SeedEntrant("Eriks Valeinis", "LAT"), SeedEntrant("Mads Bruhn Aaby", "DEN")),
            SeedMatch("Court 7", 3, SeedEntrant("Elizaveta Bryakina", "EST"), SeedEntrant("Luana Kappeler", "SUI")),
            SeedMatch("Court 7", 4, SeedEntrant("Kirils Sokolovs", "LAT"), SeedEntrant("Verneri Riikonen", "FIN")),
            SeedMatch("Court 8", 1, SeedEntrant("Markus Valtson", "EST"), SeedEntrant("Asger Sorensen", "DEN"), "2-6 6-4 1-6", MatchWinnerSide.RIGHT),
            SeedMatch("Court 8", 2, SeedEntrant("Maryna Levadna", "UKR"), SeedEntrant("Marie Johanna Lapimaa", "EST")),
            SeedMatch("Court 8", 3, SeedEntrant("Ewangelina Penkevych", "POL"), SeedEntrant("Nora Olivia Adamson", "EST")),
            SeedMatch("Court 8", 4, SeedEntrant("Hitoshi Shirabe", "JPN"), SeedEntrant("Mark Gregor Toniste", "EST")),
        )
        val entrants = seedMatches
            .flatMap { listOf(it.left, it.right) }
            .distinctBy { it.name }

        database.withTransaction {
            tournamentDao.insert(
                TournamentEntity(
                    id = 1L,
                    name = "J60 Tallinn (Order of Play sample)",
                    defaultRestMinutes = 30,
                    createdAt = now,
                ),
            )

            val courtEntities = courtNames.mapIndexed { index, name ->
                CourtEntity(
                    id = (index + 1).toLong(),
                    tournamentId = 1L,
                    name = name,
                    sortOrder = index + 1,
                )
            }
            courtDao.insertAll(courtEntities)

            val playerEntities = entrants.mapIndexed { index, entrant ->
                PlayerEntity(
                    id = (index + 1).toLong(),
                    tournamentId = 1L,
                    displayName = entrant.name,
                    countryCode = entrant.countryCode,
                    restMinutes = 30,
                )
            }
            playerDao.insertAll(playerEntities)

            teamDao.insertAll(
                playerEntities.map { player ->
                    TeamEntity(
                        id = player.id,
                        tournamentId = player.tournamentId,
                        name = null,
                    )
                },
            )

            teamPlayerDao.insertAll(
                playerEntities.map { player ->
                    TeamPlayerCrossRefEntity(
                        teamId = player.id,
                        playerId = player.id,
                        playerOrder = 0,
                    )
                },
            )

            val playerIdByName = playerEntities.associate { it.displayName to it.id }
            val courtIdByName = courtEntities.associate { it.name to it.id }
            val matchEntities = mutableListOf<MatchEntity>()
            val slotEntities = mutableListOf<SlotEntity>()
            val eventEntities = mutableListOf<EventLogEntity>()
            var matchId = 100L

            seedMatches.forEach { seed ->
                val currentMatchId = matchId++
                val courtEntityId = courtIdByName.getValue(seed.courtName)
                val leftTeamId = playerIdByName.getValue(seed.left.name)
                val rightTeamId = playerIdByName.getValue(seed.right.name)
                val isCompleted = seed.order == 1
                val completedCourtOffset = courtEntityId - 1L
                val startedAt = if (isCompleted) {
                    now - (240L - completedCourtOffset * 6L) * 60_000L
                } else {
                    null
                }
                val endedAt = if (isCompleted) startedAt?.plus(35L * 60_000L) else null
                val winnerTeamId = when (seed.winnerSide) {
                    MatchWinnerSide.LEFT -> leftTeamId
                    MatchWinnerSide.RIGHT -> rightTeamId
                    null -> null
                }
                val manualNotBefore = when (seed.order) {
                    2 -> now + 15L * 60_000L
                    3 -> now + 90L * 60_000L
                    4 -> now + 180L * 60_000L
                    else -> null
                }

                matchEntities += MatchEntity(
                    id = currentMatchId,
                    tournamentId = 1L,
                    courtId = courtEntityId,
                    roundName = when (seed.order) {
                        1 -> "1. Starting at 09:00"
                        2 -> "2. Followed By"
                        3 -> "3. Not Before 11:30"
                        else -> "4. Followed By"
                    },
                    status = if (isCompleted) MatchStatus.COMPLETED else MatchStatus.SCHEDULED,
                    manualNotBefore = manualNotBefore,
                    computedNotBefore = null,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    winnerTeamId = winnerTeamId,
                    initialLeftTeamId = if (isCompleted) leftTeamId else null,
                    initialRightTeamId = if (isCompleted) rightTeamId else null,
                    initialServerPlayerId = if (isCompleted) leftTeamId else null,
                    resultType = if (isCompleted) MatchResultTypeEntity.NORMAL else null,
                    scoreText = seed.score,
                )

                slotEntities += SlotEntity(
                    id = currentMatchId * 10L + 1L,
                    matchId = currentMatchId,
                    position = SlotPosition.LEFT,
                    slotType = SlotType.DIRECT_TEAM,
                    directTeamId = leftTeamId,
                    winnerOfMatchId = null,
                )
                slotEntities += SlotEntity(
                    id = currentMatchId * 10L + 2L,
                    matchId = currentMatchId,
                    position = SlotPosition.RIGHT,
                    slotType = SlotType.DIRECT_TEAM,
                    directTeamId = rightTeamId,
                    winnerOfMatchId = null,
                )

                if (isCompleted) {
                    val startedAtMillis = checkNotNull(startedAt)
                    val endedAtMillis = checkNotNull(endedAt)
                    eventEntities += EventLogEntity(
                        matchId = currentMatchId,
                        courtId = courtEntityId,
                        timestamp = startedAtMillis,
                        eventType = EventType.MATCH_STARTED,
                        offenderPlayerId = null,
                        message = "Match started",
                    )
                    eventEntities += EventLogEntity(
                        matchId = currentMatchId,
                        courtId = courtEntityId,
                        timestamp = endedAtMillis,
                        eventType = EventType.MATCH_ENDED,
                        offenderPlayerId = null,
                        message = "Final score ${seed.score}",
                    )
                }
            }

            matchDao.insertAll(matchEntities)
            slotDao.insertAll(slotEntities)
            eventLogDao.insertAll(eventEntities)
        }

        recomputeNotBeforeAndSchedule()
    }

    override suspend fun reloadOrderOfPlay() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
        seedIfEmpty()
    }

    override fun observePlayers(): Flow<List<PlayerSummary>> {
        return playerDao.observeAll().map { players ->
            players.map { player ->
                PlayerSummary(
                    id = player.id,
                    displayName = player.displayName,
                    countryCode = player.countryCode,
                    restMinutes = player.restMinutes,
                )
            }
        }
    }

    override suspend fun updateRestMinutes(playerId: Long, restMinutes: Int): Boolean {
        val normalized = restMinutes.coerceIn(0, 180)
        val updated = playerDao.updateRestMinutes(playerId, normalized) > 0
        if (updated) {
            recomputeNotBeforeAndSchedule()
        }
        return updated
    }

    override fun observePlayerOverviews(): Flow<List<PlayerOverview>> {
        return combine(
            playerDao.observeAll(),
            matchDao.observeAll(),
            slotDao.observeAll(),
            teamPlayerDao.observeAll(),
            courtDao.observeActiveCourts(),
            tournamentDao.observeFirst(),
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val players = values[0] as List<PlayerEntity>
            @Suppress("UNCHECKED_CAST")
            val matches = values[1] as List<MatchEntity>
            @Suppress("UNCHECKED_CAST")
            val slots = values[2] as List<SlotEntity>
            @Suppress("UNCHECKED_CAST")
            val teamPlayers = values[3] as List<TeamPlayerCrossRefEntity>
            @Suppress("UNCHECKED_CAST")
            val courts = values[4] as List<CourtEntity>
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
            val courtsById = courts.associateBy { it.id }

            players.map { player ->
                val playerMatches = matches
                    .filter { match -> matchHasPlayer(match.id, player.id, context) }
                    .sortedBy { it.id }
                val completedCount = playerMatches.count { it.status == MatchStatus.COMPLETED }
                val next = playerMatches
                    .asSequence()
                    .filter { it.status == MatchStatus.SCHEDULED || it.status == MatchStatus.IN_PROGRESS }
                    .sortedWith(
                        compareBy<MatchEntity> { computeNotBefore(it, context) ?: Long.MAX_VALUE }
                            .thenBy { it.id },
                    )
                    .firstOrNull()
                PlayerOverview(
                    playerId = player.id,
                    name = player.displayName,
                    countryCode = player.countryCode,
                    completedCount = completedCount,
                    nextMatchLabel = next?.let { formatMatchLabel(it, context) },
                    nextCourtName = next?.courtId?.let { courtsById[it]?.name ?: "Court $it" },
                    nextNotBeforeMillis = next?.let { computeNotBefore(it, context) },
                )
            }.sortedBy { it.name }
        }
    }

    override fun observePlayerDetailInsight(playerId: Long): Flow<PlayerDetailInsight?> {
        return combine(
            playerDao.observeAll(),
            matchDao.observeAll(),
            slotDao.observeAll(),
            teamPlayerDao.observeAll(),
            courtDao.observeActiveCourts(),
            tournamentDao.observeFirst(),
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val players = values[0] as List<PlayerEntity>
            @Suppress("UNCHECKED_CAST")
            val matches = values[1] as List<MatchEntity>
            @Suppress("UNCHECKED_CAST")
            val slots = values[2] as List<SlotEntity>
            @Suppress("UNCHECKED_CAST")
            val teamPlayers = values[3] as List<TeamPlayerCrossRefEntity>
            @Suppress("UNCHECKED_CAST")
            val courts = values[4] as List<CourtEntity>
            val tournament = values[5] as TournamentEntity?

            val player = players.firstOrNull { it.id == playerId } ?: return@combine null
            val context = BuildContext(
                matches = matches,
                slotsByMatch = slots.groupBy { it.matchId },
                playersById = players.associateBy { it.id },
                teamPlayersByTeam = teamPlayers.groupBy { it.teamId }.mapValues { entry ->
                    entry.value.sortedBy { it.playerOrder }.mapNotNull { ref -> players.firstOrNull { it.id == ref.playerId } }
                },
                defaultRestMinutes = tournament?.defaultRestMinutes ?: DEFAULT_REST_MINUTES,
            )
            val courtsById = courts.associateBy { it.id }
            val playerMatches = matches.filter { matchHasPlayer(it.id, player.id, context) }

            val completed = playerMatches
                .filter { it.status == MatchStatus.COMPLETED }
                .sortedByDescending { it.endedAt ?: Long.MIN_VALUE }
                .map { match ->
                    PlayerMatchSnapshot(
                        matchId = match.id,
                        courtName = match.courtId?.let { courtsById[it]?.name ?: "Court $it" },
                        matchLabel = formatMatchLabel(match, context),
                        status = match.status.toCourtStatus(),
                        scoreLabel = buildScoreLabel(match.scoreText, match.resultType),
                        notBeforeMillis = null,
                        startedAtMillis = match.startedAt,
                        endedAtMillis = match.endedAt,
                    )
                }

            val next = playerMatches
                .asSequence()
                .filter { it.status == MatchStatus.SCHEDULED || it.status == MatchStatus.IN_PROGRESS }
                .sortedWith(
                    compareBy<MatchEntity> { computeNotBefore(it, context) ?: Long.MAX_VALUE }
                        .thenBy { it.id },
                )
                .firstOrNull()
                ?.let { match ->
                    PlayerMatchSnapshot(
                        matchId = match.id,
                        courtName = match.courtId?.let { courtsById[it]?.name ?: "Court $it" },
                        matchLabel = formatMatchLabel(match, context),
                        status = match.status.toCourtStatus(),
                        scoreLabel = buildScoreLabel(match.scoreText, match.resultType),
                        notBeforeMillis = computeNotBefore(match, context),
                        startedAtMillis = match.startedAt,
                        endedAtMillis = match.endedAt,
                    )
                }

            PlayerDetailInsight(
                playerId = player.id,
                name = player.displayName,
                countryCode = player.countryCode,
                currentRestMinutes = player.restMinutes ?: context.defaultRestMinutes,
                completedMatches = completed,
                nextMatch = next,
            )
        }
    }

    override fun observeCompletedMatchesByCourt(): Flow<List<CompletedCourtMatch>> {
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
            val courtsById = courts.associateBy { it.id }
            matches
                .filter { it.status == MatchStatus.COMPLETED }
                .sortedWith(
                    compareBy<MatchEntity>({ courtsById[it.courtId]?.sortOrder ?: Int.MAX_VALUE })
                        .thenByDescending { it.endedAt ?: Long.MIN_VALUE },
                )
                .map { match ->
                    CompletedCourtMatch(
                        matchId = match.id,
                        courtId = match.courtId,
                        courtName = match.courtId?.let { courtsById[it]?.name ?: "Court $it" },
                        matchLabel = formatMatchLabel(match, context),
                        scoreLabel = buildScoreLabel(match.scoreText, match.resultType),
                        startedAtMillis = match.startedAt,
                        endedAtMillis = match.endedAt,
                    )
                }
        }
    }

    override fun observeMatchHistory(matchId: Long): Flow<MatchHistoryDetail?> {
        return combine(
            matchDao.observeAll(),
            courtDao.observeActiveCourts(),
            slotDao.observeAll(),
            teamPlayerDao.observeAll(),
            playerDao.observeAll(),
            tournamentDao.observeFirst(),
            eventLogDao.observeByMatchId(matchId),
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val matches = values[0] as List<MatchEntity>
            @Suppress("UNCHECKED_CAST")
            val courts = values[1] as List<CourtEntity>
            @Suppress("UNCHECKED_CAST")
            val slots = values[2] as List<SlotEntity>
            @Suppress("UNCHECKED_CAST")
            val teamPlayers = values[3] as List<TeamPlayerCrossRefEntity>
            @Suppress("UNCHECKED_CAST")
            val players = values[4] as List<PlayerEntity>
            val tournament = values[5] as TournamentEntity?
            @Suppress("UNCHECKED_CAST")
            val events = values[6] as List<EventLogEntity>

            val match = matches.firstOrNull { it.id == matchId } ?: return@combine null
            val context = BuildContext(
                matches = matches,
                slotsByMatch = slots.groupBy { it.matchId },
                playersById = players.associateBy { it.id },
                teamPlayersByTeam = teamPlayers.groupBy { it.teamId }.mapValues { entry ->
                    entry.value.sortedBy { it.playerOrder }.mapNotNull { ref -> players.firstOrNull { it.id == ref.playerId } }
                },
                defaultRestMinutes = tournament?.defaultRestMinutes ?: DEFAULT_REST_MINUTES,
            )
            val courtName = match.courtId?.let { courtId ->
                courts.firstOrNull { it.id == courtId }?.name ?: "Court $courtId"
            }
            MatchHistoryDetail(
                matchId = match.id,
                courtName = courtName,
                matchLabel = formatMatchLabel(match, context),
                scoreLabel = buildScoreLabel(match.scoreText, match.resultType),
                startedAtMillis = match.startedAt,
                endedAtMillis = match.endedAt,
                events = events.map { event ->
                    MatchEvent(
                        id = event.id,
                        matchId = event.matchId,
                        courtId = event.courtId,
                        timestamp = event.timestamp,
                        kind = event.eventType.toDomainKind(),
                        offenderPlayerId = event.offenderPlayerId,
                        message = event.message,
                    )
                },
            )
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
                    endedAtMillis = null,
                    initialServerPlayerId = null,
                    winnerTeamId = null,
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
                    endedAtMillis = currentMatch.endedAt,
                    initialServerPlayerId = currentMatch.initialServerPlayerId,
                    winnerTeamId = currentMatch.winnerTeamId,
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
            matchDao.update(match.copy(initialServerPlayerId = null))
        }
    }

    override suspend fun setScheduledInitialServer(matchId: Long, playerId: Long): Boolean {
        return database.withTransaction {
            val match = matchDao.getById(matchId) ?: return@withTransaction false
            if (match.status != MatchStatus.SCHEDULED) return@withTransaction false

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
            val validServerIds = left?.players.orEmpty().map { it.playerId } + right?.players.orEmpty().map { it.playerId }
            if (playerId !in validServerIds) return@withTransaction false

            matchDao.update(match.copy(initialServerPlayerId = playerId))
            true
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
            val offenderName = playerDao.getAll()
                .firstOrNull { it.id == offenderPlayerId }
                ?.displayName
                ?: "Player $offenderPlayerId"
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
                summary = "Applied: $violationName to $offenderName",
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

    override suspend fun autoStartMatchFromWarmup(matchId: Long): StartMatchResult {
        val match = matchDao.getById(matchId) ?: return StartMatchResult.MatchNotFound
        val initialServerPlayerId = match.initialServerPlayerId ?: return StartMatchResult.InvalidInitialServer
        return startMatch(
            matchId = matchId,
            initialServerPlayerId = initialServerPlayerId,
        )
    }

    override suspend fun getRestPrompt(matchId: Long): RestPrompt? {
        val match = matchDao.getById(matchId) ?: return null
        if (match.status != MatchStatus.COMPLETED) return null
        val startedAt = match.startedAt ?: return null
        val endedAt = match.endedAt ?: return null
        val durationMinutes = ((endedAt - startedAt).coerceAtLeast(0L) / 60_000L).toInt()
        if (durationMinutes < REST_PROMPT_MIN_MATCH_MINUTES) return null

        val matches = matchDao.getAll()
        val slots = slotDao.getAll()
        val players = playerDao.getAll()
        val teamPlayers = teamPlayerDao.getAll()
        val tournament = tournamentDao.getFirst()
        val courtsById = courtDao.getAll().associateBy { it.id }
        val context = BuildContext(
            matches = matches,
            slotsByMatch = slots.groupBy { it.matchId },
            playersById = players.associateBy { it.id },
            teamPlayersByTeam = teamPlayers.groupBy { it.teamId }.mapValues { entry ->
                entry.value.sortedBy { it.playerOrder }.mapNotNull { ref -> players.firstOrNull { it.id == ref.playerId } }
            },
            defaultRestMinutes = tournament?.defaultRestMinutes ?: DEFAULT_REST_MINUTES,
        )

        val participantIds = mutableSetOf<Long>()
        listOfNotNull(match.initialLeftTeamId, match.initialRightTeamId).forEach { teamId ->
            context.teamPlayersByTeam[teamId].orEmpty().forEach { player -> participantIds += player.id }
        }
        if (participantIds.isEmpty()) {
            val left = sideForMatch(match.id, SlotPosition.LEFT, context)
            val right = sideForMatch(match.id, SlotPosition.RIGHT, context)
            left?.players.orEmpty().forEach { participantIds += it.playerId }
            right?.players.orEmpty().forEach { participantIds += it.playerId }
        }
        if (participantIds.isEmpty()) return null

        val restPlayers = participantIds.mapNotNull { playerId ->
            val player = context.playersById[playerId] ?: return@mapNotNull null
            val upcoming = matches
                .asSequence()
                .filter { candidate ->
                    candidate.id != match.id &&
                        (candidate.status == MatchStatus.SCHEDULED || candidate.status == MatchStatus.IN_PROGRESS) &&
                        matchHasPlayer(candidate.id, playerId, context)
                }
                .sortedWith(
                    compareBy<MatchEntity> { computeNotBefore(it, context) ?: Long.MAX_VALUE }
                        .thenBy { it.id },
                )
                .firstOrNull()
                ?: return@mapNotNull null

            RestPromptPlayer(
                playerId = player.id,
                playerName = player.displayName,
                countryCode = player.countryCode,
                suggestedRestMinutes = player.restMinutes ?: context.defaultRestMinutes,
                upcomingMatchLabel = formatMatchLabel(upcoming, context),
                upcomingCourtName = upcoming.courtId?.let { courtsById[it]?.name ?: "Court $it" },
            )
        }.sortedBy { it.playerName }

        if (restPlayers.isEmpty()) return null
        return RestPrompt(
            matchId = match.id,
            matchDurationMinutes = durationMinutes,
            players = restPlayers,
        )
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
        val leftSide = sideForMatch(match.id, SlotPosition.LEFT, context)
        val rightSide = sideForMatch(match.id, SlotPosition.RIGHT, context)
        val left = formatSideShortLabel(leftSide)
        val right = formatSideShortLabel(rightSide)
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
                    PlayerRef(playerId = it.id, name = it.displayName, countryCode = it.countryCode)
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
                    PlayerRef(playerId = it.id, name = it.displayName, countryCode = it.countryCode)
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

    private fun matchHasPlayer(matchId: Long, playerId: Long, context: BuildContext): Boolean {
        val left = sideForMatch(matchId, SlotPosition.LEFT, context)
        val right = sideForMatch(matchId, SlotPosition.RIGHT, context)
        val ids = buildList {
            addAll(left?.players?.map { it.playerId }.orEmpty())
            addAll(right?.players?.map { it.playerId }.orEmpty())
        }
        return playerId in ids
    }

    private fun buildScoreLabel(scoreText: String?, resultType: MatchResultTypeEntity?): String? {
        val score = scoreText?.trim().orEmpty()
        return when (resultType) {
            MatchResultTypeEntity.RETIREMENT -> if (score.isBlank()) "RET" else "$score RET"
            MatchResultTypeEntity.WALKOVER -> if (score.isBlank()) "WO" else "$score WO"
            MatchResultTypeEntity.DEFAULT -> if (score.isBlank()) "DEF" else "$score DEF"
            MatchResultTypeEntity.NORMAL -> score.ifBlank { null }
            null -> score.ifBlank { null }
        }
    }

    private fun formatSideShortLabel(side: SideParticipant?): String {
        val players = side?.players.orEmpty()
        if (players.isNotEmpty()) {
            return players.joinToString(" / ") { player ->
                val flag = countryCodeToFlagEmoji(player.countryCode)
                if (flag != null) "$flag ${player.name}" else player.name
            }
        }
        return side?.label ?: "TBD"
    }

    private fun countryCodeToFlagEmoji(countryCode: String?): String? {
        val normalized = countryCode?.trim()?.uppercase()?.ifBlank { null } ?: return null
        val iso2 = if (normalized.length == 2) {
            normalized
        } else {
            ISO3_TO_ISO2[normalized]
        } ?: return null
        if (iso2.length != 2 || !iso2.all { it in 'A'..'Z' }) return null
        val first = Character.codePointAt(iso2, 0) - 0x41 + 0x1F1E6
        val second = Character.codePointAt(iso2, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
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
        const val REST_PROMPT_MIN_MATCH_MINUTES = 60
        val ISO3_TO_ISO2 = mapOf(
            "BEL" to "BE",
            "CHN" to "CN",
            "CZE" to "CZ",
            "DEN" to "DK",
            "EST" to "EE",
            "FIN" to "FI",
            "GBR" to "GB",
            "GER" to "DE",
            "ISR" to "IL",
            "ITA" to "IT",
            "JPN" to "JP",
            "LAT" to "LV",
            "LIE" to "LI",
            "LTU" to "LT",
            "POL" to "PL",
            "ROU" to "RO",
            "SLO" to "SI",
            "SUI" to "CH",
            "SWE" to "SE",
            "UKR" to "UA",
        )
    }
}

private fun <T, R> Flow<List<T>>.mapList(transform: (T) -> R): Flow<List<R>> =
    map { list -> list.map(transform) }
