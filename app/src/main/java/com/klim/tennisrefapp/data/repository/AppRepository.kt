package com.klim.tennisrefapp.data.repository

import android.content.Context
import android.net.Uri
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
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil

@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext
    private val appContext: Context,
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
                val isCompleted = false
                val startedAt: Long? = null
                val endedAt: Long? = null
                val winnerTeamId: Long? = null
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
                    initialLeftTeamId = null,
                    initialRightTeamId = null,
                    initialServerPlayerId = null,
                    resultType = null,
                    scoreText = null,
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

    override suspend fun reloadOrderOfPlayFromFile(uriString: String): Boolean {
        val imported = parseOrderOfPlayFromUri(uriString) ?: return false
        if (imported.matches.isEmpty()) return false

        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }

        val now = System.currentTimeMillis()
        database.withTransaction {
            tournamentDao.insert(
                TournamentEntity(
                    id = 1L,
                    name = imported.tournamentName,
                    defaultRestMinutes = DEFAULT_REST_MINUTES,
                    createdAt = now,
                ),
            )

            val courtEntities = imported.courts.mapIndexed { index, name ->
                CourtEntity(
                    id = (index + 1).toLong(),
                    tournamentId = 1L,
                    name = name,
                    sortOrder = index + 1,
                )
            }
            courtDao.insertAll(courtEntities)
            val courtIdByName = courtEntities.associate { it.name to it.id }

            val entrants = imported.matches
                .flatMap { listOf(it.left, it.right) }
                .fold(linkedMapOf<String, ParsedEntrant>()) { acc, entrant ->
                    val existing = acc[entrant.name]
                    acc[entrant.name] = mergeEntrant(existing, entrant)
                    acc
                }
                .values
                .toList()
            val players = entrants.mapIndexed { index, entrant ->
                PlayerEntity(
                    id = (index + 1).toLong(),
                    tournamentId = 1L,
                    displayName = buildEntrantDisplayName(entrant.name, entrant.seed),
                    countryCode = entrant.countryCode,
                    restMinutes = DEFAULT_REST_MINUTES,
                )
            }
            playerDao.insertAll(players)
            teamDao.insertAll(
                players.map { player ->
                    TeamEntity(
                        id = player.id,
                        tournamentId = player.tournamentId,
                        name = null,
                    )
                },
            )
            teamPlayerDao.insertAll(
                players.map { player ->
                    TeamPlayerCrossRefEntity(
                        teamId = player.id,
                        playerId = player.id,
                        playerOrder = 0,
                    )
                },
            )

            val playerIdByName = entrants
                .mapIndexed { index, entrant -> entrant.name to (index + 1L) }
                .toMap()
            val matchEntities = mutableListOf<MatchEntity>()
            val slotEntities = mutableListOf<SlotEntity>()
            var matchId = 100L
            imported.matches.forEach { importedMatch ->
                val currentMatchId = matchId++
                val leftTeamId = playerIdByName[importedMatch.left.name] ?: return@forEach
                val rightTeamId = playerIdByName[importedMatch.right.name] ?: return@forEach
                matchEntities += MatchEntity(
                    id = currentMatchId,
                    tournamentId = 1L,
                    courtId = courtIdByName[importedMatch.courtName],
                    roundName = "${importedMatch.order}. ${importedMatch.scheduleLabel}",
                    status = MatchStatus.SCHEDULED,
                    manualNotBefore = importedMatch.manualNotBefore,
                    computedNotBefore = null,
                    startedAt = null,
                    endedAt = null,
                    winnerTeamId = null,
                    initialLeftTeamId = null,
                    initialRightTeamId = null,
                    initialServerPlayerId = null,
                    resultType = null,
                    scoreText = null,
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
            }
            matchDao.insertAll(matchEntities)
            slotDao.insertAll(slotEntities)
        }

        recomputeNotBeforeAndSchedule()
        return true
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
                canRequestRest = canPlayerRequestRest(completed, next != null),
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
                        winnerLabel = match.winnerTeamId?.let { winnerTeamId ->
                            labelForTeam(winnerTeamId, context)
                        },
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
                        startedAtMillis = null,
                    )
                } else {
                    CourtCard(
                        courtId = court.id,
                        courtName = court.name,
                        status = currentMatch.status.toCourtStatus(),
                        currentMatchLabel = formatMatchLabel(currentMatch, context),
                        computedNotBeforeMillis = computeNotBefore(currentMatch, context),
                        matchId = currentMatch.id,
                        startedAtMillis = currentMatch.startedAt,
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

    private fun canPlayerRequestRest(completed: List<PlayerMatchSnapshot>, hasUpcomingMatch: Boolean): Boolean {
        if (!hasUpcomingMatch) return false
        return completed.any { snapshot ->
            val started = snapshot.startedAtMillis ?: return@any false
            val ended = snapshot.endedAtMillis ?: return@any false
            val durationMinutes = ((ended - started).coerceAtLeast(0L) / 60_000L).toInt()
            durationMinutes >= REST_PROMPT_MIN_MATCH_MINUTES
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

    private suspend fun parseOrderOfPlayFromUri(uriString: String): ImportedOrderDocument? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val mimeType = appContext.contentResolver.getType(uri)?.lowercase().orEmpty()
        if (isImageFile(uri, mimeType)) {
            parseOrderOfPlayFromImage(uri)?.let { return it }
        }

        val text = readOrderOfPlayText(uri, mimeType) ?: return null
        val lines = text
            .replace('\u000C', '\n')
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) return null

        val courts = extractCourtNames(lines)
        val courtNames = if (courts.isNotEmpty()) courts else listOf("Court 1")
        val parsedMatches = extractMatchPairs(lines)
        if (parsedMatches.isEmpty()) return null

        val indexedMatches = assignParsedMatchesToCourts(parsedMatches, courtNames)
        return ImportedOrderDocument(
            tournamentName = extractTournamentName(lines) ?: "Imported Order Of Play",
            courts = courtNames,
            matches = indexedMatches,
        )
    }

    private suspend fun readOrderOfPlayText(uri: Uri, mimeTypeHint: String? = null): String? = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val mimeType = mimeTypeHint ?: resolver.getType(uri)?.lowercase().orEmpty()
        if (isImageFile(uri, mimeType)) {
            return@withContext readOrderOfPlayTextFromImage(uri)
        }
        val input = resolver.openInputStream(uri) ?: return@withContext null
        input.use { stream ->
            if (isPdfFile(uri, mimeType)) {
                PDFBoxResourceLoader.init(appContext)
                PDDocument.load(stream).use { document ->
                    PDFTextStripper().getText(document)
                }
            } else {
                stream.bufferedReader().readText()
            }
        }
    }

    private suspend fun readOrderOfPlayTextFromImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        recognizeImageText(uri)?.text
    }

    private fun isPdfFile(uri: Uri, mimeType: String): Boolean {
        return mimeType.contains("pdf") || uri.toString().lowercase().endsWith(".pdf")
    }

    private fun isImageFile(uri: Uri, mimeType: String): Boolean {
        val lower = uri.toString().lowercase()
        return mimeType.startsWith("image/") ||
            lower.endsWith(".png") ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".webp")
    }

    private suspend fun recognizeImageText(uri: Uri): Text? = withContext(Dispatchers.IO) {
        runCatching {
            val image = InputImage.fromFilePath(appContext, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                Tasks.await(recognizer.process(image))
            } finally {
                recognizer.close()
            }
        }.getOrNull()
    }

    private suspend fun parseOrderOfPlayFromImage(uri: Uri): ImportedOrderDocument? {
        val recognized = recognizeImageText(uri) ?: return null
        val ocrLines = extractOcrLines(recognized)
        if (ocrLines.isEmpty()) return null

        val columns = detectCourtColumns(ocrLines)
        if (columns.isEmpty()) return null
        val rowBands = detectRowBands(ocrLines, columns)
        if (rowBands.isEmpty()) return null

        val matches = mutableListOf<ImportedOrderMatch>()
        rowBands.sortedBy { it.order }.forEach { row ->
            columns.forEach columnLoop@ { column ->
                val cellLines = ocrLines
                    .filter { line ->
                        line.centerX >= column.leftBoundary &&
                            line.centerX < column.rightBoundary &&
                            line.centerY >= row.topBoundary &&
                            line.centerY < row.bottomBoundary
                    }
                    .sortedBy { it.top }
                val entrants = parseMatchPairFromCellLines(cellLines) ?: return@columnLoop
                val schedule = parseScheduleContextFromCell(cellLines)
                matches += ImportedOrderMatch(
                    courtName = column.name,
                    order = row.order,
                    left = entrants.first,
                    right = entrants.second,
                    scheduleLabel = schedule.label,
                    manualNotBefore = schedule.manualNotBefore,
                )
            }
        }

        if (matches.size < columns.size) return null
        val flatLines = recognized.text
            .replace('\u000C', '\n')
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        return ImportedOrderDocument(
            tournamentName = extractTournamentName(flatLines) ?: "Imported Order Of Play",
            courts = columns.map { it.name },
            matches = matches,
        )
    }

    private fun extractOcrLines(recognized: Text): List<OcrLine> {
        return recognized.textBlocks
            .flatMap { block -> block.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                val text = line.text.normalizeSpaces().trim()
                if (text.isBlank()) {
                    null
                } else {
                    OcrLine(
                        text = text,
                        left = box.left.toFloat(),
                        right = box.right.toFloat(),
                        top = box.top.toFloat(),
                        bottom = box.bottom.toFloat(),
                    )
                }
            }
    }

    private fun detectCourtColumns(lines: List<OcrLine>): List<CourtColumnLayout> {
        val minTop = lines.minOfOrNull { it.top } ?: return emptyList()
        val maxBottom = lines.maxOfOrNull { it.bottom } ?: return emptyList()
        val minLeft = lines.minOfOrNull { it.left } ?: return emptyList()
        val maxRight = lines.maxOfOrNull { it.right } ?: return emptyList()
        val topBandLimit = minTop + (maxBottom - minTop) * 0.25f
        val byName = linkedMapOf<String, OcrLine>()
        lines.asSequence()
            .filter { it.top <= topBandLimit }
            .forEach { line ->
                val courtName = parseCourtName(line.text) ?: return@forEach
                val existing = byName[courtName]
                if (existing == null || line.top < existing.top) {
                    byName[courtName] = line
                }
            }

        val headers = byName.entries.sortedBy { it.value.centerX }
        if (headers.isEmpty()) return emptyList()
        val centers = headers.map { it.value.centerX }
        return headers.mapIndexed { index, entry ->
            val leftBoundary = if (index == 0) {
                if (centers.size > 1) {
                    (centers[0] - (centers[1] - centers[0]) / 2f).coerceAtMost(centers[0] - 8f)
                        .coerceAtLeast(minLeft - 8f)
                } else {
                    minLeft - 8f
                }
            } else {
                (centers[index - 1] + centers[index]) / 2f
            }
            val rightBoundary = if (index == headers.lastIndex) {
                if (centers.size > 1) {
                    (centers.last() + (centers.last() - centers[centers.lastIndex - 1]) / 2f)
                        .coerceAtLeast(centers.last() + 8f)
                        .coerceAtMost(maxRight + 8f)
                } else {
                    maxRight + 8f
                }
            } else {
                (centers[index] + centers[index + 1]) / 2f
            }
            CourtColumnLayout(
                name = entry.key,
                leftBoundary = leftBoundary,
                rightBoundary = rightBoundary,
                headerBottom = entry.value.bottom,
            )
        }
    }

    private fun detectRowBands(lines: List<OcrLine>, columns: List<CourtColumnLayout>): List<RowBandLayout> {
        val firstColumn = columns.firstOrNull() ?: return emptyList()
        val headerBottom = columns.maxOfOrNull { it.headerBottom } ?: 0f
        val maxBottom = lines.maxOfOrNull { it.bottom } ?: return emptyList()
        val firstColumnWidth = (firstColumn.rightBoundary - firstColumn.leftBoundary).coerceAtLeast(1f)
        val markerRightLimit = firstColumn.leftBoundary + (firstColumnWidth * 0.32f).coerceAtLeast(60f)

        val rowMarkers = lines
            .filter { line ->
                line.top >= headerBottom &&
                    line.left <= markerRightLimit
            }
            .mapNotNull { line ->
                parseRowNumber(line.text)?.let { line.centerY }
            }
            .sorted()
            .fold(mutableListOf<Float>()) { acc, y ->
                if (acc.isEmpty() || abs(acc.last() - y) > 14f) acc += y
                acc
            }

        val anchors = if (rowMarkers.isNotEmpty()) {
            rowMarkers
        } else {
            lines
                .filter { line ->
                        line.top >= headerBottom &&
                        containsScheduleKeyword(line.text)
                }
                .sortedBy { it.centerY }
                .fold(mutableListOf<Float>()) { acc, line ->
                    if (acc.isEmpty() || abs(acc.last() - line.centerY) > 20f) {
                        acc += line.centerY
                    }
                    acc
                }
        }

        if (anchors.isEmpty()) {
            return listOf(
                RowBandLayout(
                    order = 1,
                    topBoundary = headerBottom + 2f,
                    bottomBoundary = maxBottom + 12f,
                ),
            )
        }

        return anchors.mapIndexed { index, centerY ->
            val topBoundary = if (index == 0) {
                headerBottom + 2f
            } else {
                (anchors[index - 1] + centerY) / 2f
            }
            val bottomBoundary = if (index == anchors.lastIndex) {
                maxBottom + 12f
            } else {
                (centerY + anchors[index + 1]) / 2f
            }
            RowBandLayout(
                order = index + 1,
                topBoundary = topBoundary,
                bottomBoundary = bottomBoundary,
            )
        }
    }

    private fun parseMatchPairFromCellLines(cellLines: List<OcrLine>): Pair<ParsedEntrant, ParsedEntrant>? {
        if (cellLines.isEmpty()) return null

        val inlineCandidates = mutableListOf<Pair<ParsedEntrant, ParsedEntrant>>()
        cellLines.forEach { line ->
            val inline = extractInlinePair(line.text) ?: return@forEach
            val left = parseEntrant(inline.leftRaw) ?: return@forEach
            val right = parseEntrant(inline.rightRaw) ?: return@forEach
            if (!left.name.equals(right.name, ignoreCase = true)) {
                inlineCandidates += sanitizeSeedConflict(left, right, inline.leftRaw, inline.rightRaw)
            }
        }
        if (inlineCandidates.isNotEmpty()) {
            return inlineCandidates.minByOrNull { (left, right) ->
                left.name.split(' ').size + right.name.split(' ').size
            }
        }

        val vsIndex = cellLines.indexOfFirst { isVsToken(it.text) }
        if (vsIndex != -1) {
            val leftRaw = cellLines
                .subList(0, vsIndex)
                .asReversed()
                .firstOrNull { isPotentialEntrantLine(it.text) }
                ?.text
            val rightRaw = cellLines
                .drop(vsIndex + 1)
                .firstOrNull { isPotentialEntrantLine(it.text) }
                ?.text
            if (!leftRaw.isNullOrBlank() && !rightRaw.isNullOrBlank()) {
                val left = parseEntrant(leftRaw)
                val right = parseEntrant(rightRaw)
                if (left != null && right != null && !left.name.equals(right.name, ignoreCase = true)) {
                    return sanitizeSeedConflict(left, right, leftRaw, rightRaw)
                }
            }
        }

        val fallbackEntrants = cellLines
            .mapNotNull { line ->
                if (!isPotentialEntrantLine(line.text)) return@mapNotNull null
                val entrant = parseEntrant(line.text) ?: return@mapNotNull null
                ParsedEntrantLine(
                    centerY = line.centerY,
                    raw = line.text,
                    entrant = entrant,
                )
            }
        if (fallbackEntrants.size >= 2) {
            val selected = selectFallbackEntrants(fallbackEntrants) ?: return null
            val left = selected.first
            val right = selected.second
            if (!left.entrant.name.equals(right.entrant.name, ignoreCase = true)) {
                return sanitizeSeedConflict(left.entrant, right.entrant, left.raw, right.raw)
            }
        }
        return null
    }

    private fun selectFallbackEntrants(entries: List<ParsedEntrantLine>): Pair<ParsedEntrantLine, ParsedEntrantLine>? {
        if (entries.size < 2) return null
        if (entries.size == 2) return entries[0] to entries[1]

        val sorted = entries.sortedBy { it.centerY }
        var bestPair: Pair<ParsedEntrantLine, ParsedEntrantLine>? = null
        var bestGap = Float.MAX_VALUE
        for (index in 0 until sorted.lastIndex) {
            val left = sorted[index]
            val right = sorted[index + 1]
            if (left.entrant.name.equals(right.entrant.name, ignoreCase = true)) continue
            val gap = (right.centerY - left.centerY).coerceAtLeast(0f)
            if (gap < bestGap) {
                bestGap = gap
                bestPair = left to right
            }
        }
        return bestPair
    }

    private fun parseScheduleContextFromCell(cellLines: List<OcrLine>): ScheduleContext {
        if (cellLines.isEmpty()) return ScheduleContext(label = "Followed By", manualNotBefore = null)
        val joined = cellLines.joinToString(" ") { it.text }
        return parseScheduleContextFromText(joined)
    }

    private fun parseScheduleContextFromText(raw: String): ScheduleContext {
        val normalized = raw.normalizeSpaces()
        val label = scheduleKeywordLabel(normalized) ?: "Followed By"
        val time = extractTime(normalized)
        val manualNotBefore = if (label == "Not Before" || label == "Starting at") {
            time?.let { toTodayMillis(it) }
        } else {
            null
        }
        val scheduleLabel = if (time != null && (label == "Not Before" || label == "Starting at")) {
            "$label $time"
        } else {
            label
        }
        return ScheduleContext(label = scheduleLabel, manualNotBefore = manualNotBefore)
    }

    private fun parseCourtName(raw: String): String? {
        val normalized = raw.normalizeSpaces()
        val match = Regex("(?i)c[o0]u?r?t\\s*([0-9]{1,2}|[A-Za-z])").find(normalized) ?: return null
        return "Court ${match.groupValues[1].uppercase()}"
    }

    private fun parseRowNumber(raw: String): Int? {
        val token = raw.trim().removeSuffix(".")
        token.toIntOrNull()?.takeIf { it in 1..64 }?.let { return it }
        val prefixed = Regex("^(\\d{1,2})\\b").find(raw.trim()) ?: return null
        val value = prefixed.groupValues[1].toIntOrNull()?.takeIf { it in 1..64 } ?: return null
        val tail = raw.trim().substring(prefixed.range.last + 1).trim()
        if (tail.isEmpty()) return value
        val normalizedTail = tail.trimStart('.', ')', '-', ':', ' ')
        if (normalizedTail.isEmpty() || containsScheduleKeyword(normalizedTail)) return value
        return null
    }

    private fun mergeEntrant(existing: ParsedEntrant?, incoming: ParsedEntrant): ParsedEntrant {
        if (existing == null) return incoming
        return existing.copy(
            countryCode = existing.countryCode ?: incoming.countryCode,
            seed = existing.seed ?: incoming.seed,
        )
    }

    private fun buildEntrantDisplayName(name: String, seed: Int?): String {
        return if (seed != null) "$name [$seed]" else name
    }

    private fun sanitizeSeedConflict(
        left: ParsedEntrant,
        right: ParsedEntrant,
        leftRaw: String,
        rightRaw: String,
    ): Pair<ParsedEntrant, ParsedEntrant> {
        val leftSeed = left.seed
        val rightSeed = right.seed
        if (leftSeed == null || rightSeed == null || leftSeed != rightSeed) {
            return left to right
        }
        val leftExplicit = hasExplicitSeedMarker(leftRaw)
        val rightExplicit = hasExplicitSeedMarker(rightRaw)
        return when {
            leftExplicit && !rightExplicit -> left to right.copy(seed = null)
            !leftExplicit && rightExplicit -> left.copy(seed = null) to right
            else -> left to right.copy(seed = null)
        }
    }

    private fun hasExplicitSeedMarker(raw: String): Boolean {
        if (Regex("\\[(\\d{1,2})\\]").containsMatchIn(raw)) return true
        val paren = Regex("\\((\\d{1,2})\\)").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return paren != null && paren in 1..32
    }

    private fun scheduleKeywordLabel(value: String): String? {
        val compact = value
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
        return when {
            compact.contains("notbefore") -> "Not Before"
            compact.contains("startingat") -> "Starting at"
            compact.contains("followedby") || compact.contains("followed") -> "Followed By"
            else -> null
        }
    }

    private fun containsScheduleKeyword(value: String): Boolean {
        return scheduleKeywordLabel(value) != null
    }

    private fun refineInlineEntrantRaw(raw: String, takeTail: Boolean): String {
        var value = raw
            .replace(Regex("(?i)\\b(?:starting\\s+at|followed\\s+by|not\\s+before)\\b"), " ")
            .replace(Regex("(?i)\\bcourt\\s*[A-Za-z0-9]+\\b"), " ")
            .replace(Regex("\\s+\\d+[\\-:][0-9()\\-:\\s]*$"), " ")
            .normalizeSpaces()
            .trim()

        if (takeTail && value.contains(')')) {
            val tail = value.substringAfterLast(')').trim()
            if (tail.isNotBlank()) value = tail
        }
        if (takeTail && value.contains(']')) {
            val tail = value.substringAfterLast(']').trim()
            if (tail.isNotBlank()) value = tail
        }

        val words = value.split(' ').filter { it.isNotBlank() }
        if (words.size > 5) {
            value = if (takeTail) {
                words.takeLast(4).joinToString(" ")
            } else {
                words.take(4).joinToString(" ")
            }
        }
        return value.normalizeSpaces().trim()
    }

    private fun detectCountryCode(raw: String): String? {
        val candidates = mutableListOf<String>()
        Regex("\\(\\s*([A-Za-z0-9|]{2,6})\\s*\\)").findAll(raw).forEach { match ->
            candidates += match.groupValues[1]
        }
        Regex("\\b([A-Za-z0-9|]{2,6})\\b").findAll(raw).forEach { match ->
            candidates += match.groupValues[1]
        }
        return candidates.firstNotNullOfOrNull { normalizeCountryCodeToken(it) }
    }

    private fun detectSeed(raw: String, countryCode: String?): Int? {
        Regex("\\[(\\d{1,2})\\]").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { value ->
            if (value in 1..32) return value
        }
        Regex("\\((\\d{1,2})\\)").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { value ->
            if (value in 1..32) return value
        }

        val tokens = Regex("[A-Za-z0-9|\\[\\]]+")
            .findAll(raw.uppercase())
            .map { it.value }
            .toList()
            .asReversed()
        return tokens.firstNotNullOfOrNull { token ->
            if (token.any { it.isDigit() } || token.contains('[') || token.contains(']') || token.contains('|')) {
                decodeSeedToken(token, countryCode)
            } else {
                null
            }
        }
    }

    private fun decodeSeedToken(token: String, countryCode: String?): Int? {
        if (token.isBlank()) return null
        val compact = token
            .replace('[', 'I')
            .replace(']', 'I')
            .replace('|', 'I')
            .uppercase()

        if (normalizeCountryCodeToken(compact) != null) return null
        val iso2 = ISO3_TO_ISO2[countryCode].orEmpty()
        if (compact == countryCode || compact == iso2) return null

        val inner = Regex("^[IL]?([A-Z0-9]{1,3})[IL1]$").matchEntire(compact)?.groupValues?.getOrNull(1)
        val candidates = buildList {
            add(compact)
            if (inner != null) add(inner)
            add(compact.trimStart('I', 'L').trimEnd('I', 'L', '1'))
        }

        for (candidate in candidates) {
            val digits = candidate
                .replace('Z', '7')
                .replace('S', '5')
                .replace('O', '0')
                .replace('B', '8')
                .replace('I', '1')
                .replace('L', '1')
                .filter { it.isDigit() }
            if (digits.isBlank()) continue
            digits.toIntOrNull()?.let { value ->
                if (value in 1..32) return value
            }
            digits.firstOrNull()?.digitToIntOrNull()?.let { single ->
                if (single in 1..9) return single
            }
        }
        return null
    }

    private fun normalizeCountryCodeToken(token: String): String? {
        if (token.isBlank()) return null
        val corrected = token
            .uppercase()
            .replace('|', 'I')
            .replace('0', 'O')
            .replace('1', 'I')
            .replace('2', 'Z')
            .replace('5', 'S')
            .replace('8', 'B')
            .filter { it.isLetter() }
        if (corrected.isBlank()) return null

        val variants = linkedSetOf<String>()
        variants += corrected
        if (corrected.length >= 2) {
            variants += corrected.take(2)
            variants += corrected.takeLast(2)
        }
        if (corrected.length >= 3) {
            variants += corrected.take(3)
            variants += corrected.takeLast(3)
            for (start in 0..(corrected.length - 3)) {
                variants += corrected.substring(start, start + 3)
            }
        }

        val direct = variants.firstOrNull { candidate ->
            candidate in ISO3_TO_ISO2.keys || candidate in ISO2_CODES
        }
        val resolved = direct ?: variants
            .mapNotNull { variant -> bestIso3Match(variant) }
            .firstOrNull()

        return resolved?.let { candidate ->
            if (candidate.length == 2) {
                ISO3_TO_ISO2.entries.firstOrNull { it.value == candidate }?.key ?: candidate
            } else {
                candidate
            }
        }
    }

    private fun bestIso3Match(candidate: String): String? {
        if (candidate.length < 3) return null
        val windows = if (candidate.length == 3) {
            listOf(candidate)
        } else {
            (0..candidate.length - 3).map { start -> candidate.substring(start, start + 3) }
        }
        return windows.firstNotNullOfOrNull { token ->
            ISO3_TO_ISO2.keys.minByOrNull { iso -> mismatchCount(token, iso) }
                ?.takeIf { iso -> mismatchCount(token, iso) <= 1 }
        }
    }

    private fun mismatchCount(left: String, right: String): Int {
        if (left.length != right.length) return Int.MAX_VALUE
        return left.indices.count { left[it] != right[it] }
    }

    private fun isPotentialEntrantLine(value: String): Boolean {
        val normalized = value.normalizeSpaces().trim()
        if (normalized.isBlank()) return false
        if (isVsToken(normalized)) return false
        if (containsScheduleKeyword(normalized)) return false
        if (parseCourtName(normalized) != null) return false
        if (parseRowNumber(normalized) != null) return false
        if (isNoiseLine(normalized) || isSeedToken(normalized) || isScoreLine(normalized)) return false
        if (normalized.equals("GS", ignoreCase = true) || normalized.equals("BS", ignoreCase = true)) return false
        return normalized.any { it.isLetter() }
    }

    private fun cleanupNameTokens(tokens: List<String>): List<String> {
        if (tokens.isEmpty()) return emptyList()
        val filtered = tokens.filter { token ->
            token.any { it.isLetter() } &&
                token.length > 1 &&
                !token.equals("VS", ignoreCase = true)
        }.toMutableList()

        while (filtered.size >= 3 && isLikelyNoiseNameToken(filtered.last())) {
            filtered.removeAt(filtered.lastIndex)
        }
        return filtered
    }

    private fun isLikelyNoiseNameToken(token: String): Boolean {
        val normalized = token.lowercase()
        if (normalized in OCR_NOISE_TOKENS) return true
        return token.length <= 3 && token.all { it in "IiLlZzSsrR0123456789" }
    }

    private fun normalizePersonNameCase(name: String): String {
        return name
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                token
                    .split('-')
                    .joinToString("-") { hyphenPart ->
                        hyphenPart
                            .split('\'')
                            .joinToString("'") { apostrophePart ->
                                apostrophePart
                                    .lowercase()
                                    .replaceFirstChar { first ->
                                        if (first.isLowerCase()) first.titlecase() else first.toString()
                                    }
                            }
                    }
            }
    }

    private fun extractTournamentName(lines: List<String>): String? {
        val markerIndex = lines.indexOfFirst { it.equals("Order of Play", ignoreCase = true) }
        if (markerIndex > 0) {
            val candidate = lines[markerIndex - 1]
            if (!isNoiseLine(candidate)) return candidate
        }
        return lines.firstOrNull { line ->
            !isNoiseLine(line) &&
                !line.contains("Court", ignoreCase = true) &&
                !line.contains("Week Of", ignoreCase = true) &&
                !line.contains("City", ignoreCase = true)
        }
    }

    private fun extractCourtNames(lines: List<String>): List<String> {
        val courts = mutableListOf<String>()
        val regex = Regex("(?i)Court\\s*([A-Za-z0-9]+)")
        lines.forEach { line ->
            regex.findAll(line).forEach { match ->
                val name = "Court ${match.groupValues[1]}"
                if (name !in courts) courts += name
            }
        }
        return courts
    }

    private fun extractMatchPairs(lines: List<String>): List<ParsedPair> {
        val pairs = mutableListOf<ParsedPair>()
        val seen = mutableSetOf<String>()
        fun appendPair(index: Int, leftRaw: String, rightRaw: String) {
            if (leftRaw.isBlank() || rightRaw.isBlank()) return
            val left = parseEntrant(leftRaw) ?: return
            val right = parseEntrant(rightRaw) ?: return
            val sanitized = sanitizeSeedConflict(left, right, leftRaw, rightRaw)
            val leftNormalized = sanitized.first
            val rightNormalized = sanitized.second
            val key = "${leftNormalized.name.lowercase()}|${rightNormalized.name.lowercase()}|$index"
            if (!seen.add(key)) return
            val schedule = findScheduleContext(lines, index)
            pairs += ParsedPair(
                left = leftNormalized,
                right = rightNormalized,
                scheduleLabel = schedule.label,
                manualNotBefore = schedule.manualNotBefore,
            )
        }
        for (index in lines.indices) {
            val line = lines[index]
            if (isVsToken(line)) {
                val leftRaw = collectNameBackward(lines, index - 1)
                val rightCollection = collectNameForward(lines, index + 1)
                appendPair(index, leftRaw, rightCollection.name)
                continue
            }
            val inlinePair = extractInlinePair(line) ?: continue
            appendPair(index, inlinePair.leftRaw, inlinePair.rightRaw)
        }
        return pairs
    }

    private fun assignParsedMatchesToCourts(
        parsedMatches: List<ParsedPair>,
        courtNames: List<String>,
    ): List<ImportedOrderMatch> {
        if (parsedMatches.isEmpty()) return emptyList()
        if (courtNames.isEmpty()) return parsedMatches.mapIndexed { index, pair ->
            ImportedOrderMatch(
                courtName = "Court 1",
                order = index + 1,
                left = pair.left,
                right = pair.right,
                scheduleLabel = pair.scheduleLabel,
                manualNotBefore = pair.manualNotBefore,
            )
        }
        val courtCount = courtNames.size
        val rounds = ceil(parsedMatches.size.toDouble() / courtCount.toDouble()).toInt().coerceAtLeast(1)

        fun rowMajorPlacement(index: Int): CourtPlacement {
            return CourtPlacement(
                courtIndex = index % courtCount,
                order = index / courtCount + 1,
            )
        }

        fun columnMajorPlacement(index: Int): CourtPlacement {
            return CourtPlacement(
                courtIndex = (index / rounds).coerceAtMost(courtCount - 1),
                order = index % rounds + 1,
            )
        }

        val rowMajorScore = scheduleCoherenceScore(parsedMatches, ::rowMajorPlacement)
        val columnMajorScore = scheduleCoherenceScore(parsedMatches, ::columnMajorPlacement)
        val placement = if (columnMajorScore > rowMajorScore) {
            ::columnMajorPlacement
        } else {
            ::rowMajorPlacement
        }

        return parsedMatches.mapIndexed { index, pair ->
            val place = placement(index)
            ImportedOrderMatch(
                courtName = courtNames[place.courtIndex],
                order = place.order,
                left = pair.left,
                right = pair.right,
                scheduleLabel = pair.scheduleLabel,
                manualNotBefore = pair.manualNotBefore,
            )
        }
    }

    private fun scheduleCoherenceScore(
        matches: List<ParsedPair>,
        placement: (Int) -> CourtPlacement,
    ): Int {
        if (matches.isEmpty()) return 0
        val groupedByOrder = mutableMapOf<Int, MutableList<String>>()
        matches.forEachIndexed { index, pair ->
            val order = placement(index).order
            groupedByOrder.getOrPut(order) { mutableListOf() } += scheduleType(pair.scheduleLabel)
        }
        return groupedByOrder.values.sumOf { labels ->
            labels.groupingBy { it }.eachCount().values.maxOrNull() ?: 0
        }
    }

    private fun scheduleType(scheduleLabel: String): String {
        return when {
            scheduleLabel.contains("Not Before", ignoreCase = true) -> "NOT_BEFORE"
            scheduleLabel.contains("Starting at", ignoreCase = true) -> "STARTING_AT"
            scheduleLabel.contains("Followed By", ignoreCase = true) -> "FOLLOWED_BY"
            else -> "UNKNOWN"
        }
    }

    private fun collectNameBackward(lines: List<String>, start: Int): String {
        val chunks = mutableListOf<String>()
        var index = start
        while (index >= 0) {
            val line = lines[index]
            if (isNameBoundary(line)) break
            if (!isSeedToken(line)) chunks += line
            if (chunks.size >= 6) break
            index--
        }
        return chunks.asReversed().joinToString(" ").normalizeSpaces()
    }

    private fun collectNameForward(lines: List<String>, start: Int): CollectedName {
        val chunks = mutableListOf<String>()
        var index = start
        while (index < lines.size) {
            val line = lines[index]
            if (isNameBoundary(line)) break
            if (!isSeedToken(line)) chunks += line
            if (chunks.size >= 6) break
            index++
        }
        return CollectedName(
            name = chunks.joinToString(" ").normalizeSpaces(),
            endIndex = index,
        )
    }

    private fun parseEntrant(raw: String): ParsedEntrant? {
        val matchedCountry = detectCountryCode(raw)
        val seed = detectSeed(raw, matchedCountry)

        val cleaned = raw
            .replace(Regex("\\[[^\\]]*]"), "")
            .replace(Regex("\\((\\d{1,2})\\)"), " ")
            .replace(Regex("\\(\\s*[A-Za-z0-9|]{2,6}\\s*\\)"), " ")
            .replace(Regex("(?i)\\b(?:GS|BS)\\b"), " ")
            .replace(Regex("(?i)\\b(?:starting\\s+at|followed\\s+by|not\\s+before)\\b"), " ")
            .replace(Regex("(?i)^court\\s+[A-Za-z0-9]+\\s*"), "")
            .replace(Regex("^\\d+\\.?\\s*"), "")
            .replace(Regex("\\b\\d{1,2}:\\d{2}\\b"), " ")
            .replace(Regex("\\s+\\d+[\\-:][0-9()\\-:\\s]*$"), "")
            .replace(Regex("(^|\\s)[\\d()\\-:]+($|\\s)"), " ")
            .replace("(", " ")
            .replace(")", " ")
            .replace(".", " ")
            .normalizeSpaces()
            .trim()
        if (cleaned.isBlank()) return null
        val tokens = cleaned
            .split(' ')
            .map { token ->
                token.trim().trim(',', ';', ':', '.', '(', ')', '[', ']', '{', '}', '|')
            }
            .filter { token ->
                token.isNotBlank() &&
                    token.any { it.isLetter() } &&
                    !token.any { it.isDigit() } &&
                    !tokenMatchesCountry(token, matchedCountry) &&
                    !isLikelySeedArtifactToken(token)
            }
        val normalizedTokens = cleanupNameTokens(tokens)
        if (normalizedTokens.isEmpty()) return null
        if (normalizedTokens.size == 1 && normalizedTokens.first().length < 4) return null
        val name = normalizePersonNameCase(normalizedTokens.joinToString(" ").normalizeSpaces())
        if (name.isBlank()) return null
        return ParsedEntrant(
            name = name,
            countryCode = matchedCountry,
            seed = seed,
        )
    }

    private fun tokenMatchesCountry(token: String, countryCode: String?): Boolean {
        val matched = countryCode ?: return false
        val normalized = normalizeCountryCodeToken(token) ?: return false
        return normalized == matched || normalized == ISO3_TO_ISO2[matched]
    }

    private fun isLikelySeedArtifactToken(token: String): Boolean {
        if (token.isBlank()) return false
        if (token.any { it.isDigit() }) return true
        if (token.contains('[') || token.contains(']') || token.contains('|')) return true
        return token.length <= 2 && token.all { it in "IiLlZzSsrR" }
    }

    private fun findScheduleContext(lines: List<String>, aroundIndex: Int): ScheduleContext {
        val lowerBound = (aroundIndex - 20).coerceAtLeast(0)
        var candidateLabel = "Followed By"
        var candidateTime: String? = null
        for (index in aroundIndex downTo lowerBound) {
            val line = lines[index]
            when {
                line.contains("Not Before", ignoreCase = true) -> {
                    candidateLabel = "Not Before"
                    candidateTime = extractTime(line) ?: findNeighbourTime(lines, index)
                    break
                }

                line.contains("Starting at", ignoreCase = true) -> {
                    candidateLabel = "Starting at"
                    candidateTime = extractTime(line) ?: findNeighbourTime(lines, index)
                    break
                }

                line.contains("Followed By", ignoreCase = true) -> {
                    candidateLabel = "Followed By"
                    break
                }
            }
        }
        val manual = if (candidateLabel == "Not Before" || candidateLabel == "Starting at") {
            candidateTime?.let { toTodayMillis(it) }
        } else {
            null
        }
        val label = if (candidateTime != null) "$candidateLabel $candidateTime" else candidateLabel
        return ScheduleContext(label = label, manualNotBefore = manual)
    }

    private fun findNeighbourTime(lines: List<String>, index: Int): String? {
        val window = (index - 2).coerceAtLeast(0)..(index + 2).coerceAtMost(lines.lastIndex)
        return window.firstNotNullOfOrNull { candidate ->
            extractTime(lines[candidate])
        }
    }

    private fun extractTime(value: String): String? {
        return Regex("(\\d{1,2}:\\d{2})").find(value)?.groupValues?.getOrNull(1)
    }

    private fun toTodayMillis(timeLabel: String): Long? {
        val parts = timeLabel.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        val now = java.util.Calendar.getInstance()
        now.set(java.util.Calendar.HOUR_OF_DAY, hour)
        now.set(java.util.Calendar.MINUTE, minute)
        now.set(java.util.Calendar.SECOND, 0)
        now.set(java.util.Calendar.MILLISECOND, 0)
        return now.timeInMillis
    }

    private fun isVsToken(line: String): Boolean {
        val token = line
            .trim()
            .lowercase()
            .replace('5', 's')
            .replace('$', 's')
            .replace(Regex("[^a-z0-9]"), "")
        return token == "vs" || token == "v"
    }

    private fun extractInlinePair(line: String): InlinePair? {
        val normalized = line.normalizeSpaces().trim()
        if (normalized.isBlank()) return null
        val tokenMatch = INLINE_VS_REGEX.find(normalized) ?: return null
        val leftRaw = normalized
            .substring(0, tokenMatch.range.first)
            .trim()
        var rightRaw = normalized
            .substring(tokenMatch.range.last + 1)
            .trim()
        if (leftRaw.isBlank() || rightRaw.isBlank()) return null

        val refinedLeftRaw = refineInlineEntrantRaw(leftRaw, takeTail = true)
        rightRaw = refineInlineEntrantRaw(rightRaw, takeTail = false)

        val scheduleStart = Regex("(?i)\\b(?:starting\\s+at|followed\\s+by|not\\s+before|court\\s+[A-Za-z0-9]+)\\b")
            .find(rightRaw)
            ?.range
            ?.first
        if (scheduleStart != null && scheduleStart > 0) {
            rightRaw = rightRaw.substring(0, scheduleStart).trim()
        }
        rightRaw = rightRaw
            .replace(Regex("\\s+\\d+[\\-:][0-9()\\-:\\s]*$"), "")
            .normalizeSpaces()
            .trim()
        if (refinedLeftRaw.isBlank() || rightRaw.isBlank()) return null

        return InlinePair(leftRaw = refinedLeftRaw, rightRaw = rightRaw)
    }

    private fun isSeedToken(line: String): Boolean {
        return line.trim().matches(Regex("\\[[^\\]]+]"))
    }

    private fun isNameBoundary(line: String): Boolean {
        val value = line.trim()
        if (value.isBlank()) return true
        if (isVsToken(value)) return true
        if (value.equals("GS", ignoreCase = true) || value.equals("BS", ignoreCase = true)) return true
        if (value.matches(Regex("\\d+\\.?"))) return true
        if (value.matches(Regex("\\d{1,2}:\\d{2}"))) return true
        if (value.contains("Court", ignoreCase = true)) return true
        if (value.contains("Starting at", ignoreCase = true) ||
            value.contains("Followed By", ignoreCase = true) ||
            value.contains("Not Before", ignoreCase = true)
        ) return true
        if (isScoreLine(value)) return true
        return isNoiseLine(value)
    }

    private fun isScoreLine(line: String): Boolean {
        val normalized = line.trim()
        if (!normalized.any { it.isDigit() }) return false
        if (!normalized.contains('-') && !normalized.contains(':')) return false
        return normalized.matches(Regex("^[0-9()\\-:\\s]+$"))
    }

    private fun isNoiseLine(line: String): Boolean {
        return line.startsWith("http", ignoreCase = true) ||
            line.contains("sign-in", ignoreCase = true) ||
            line.contains("Released", ignoreCase = true) ||
            line.contains("local time", ignoreCase = true) ||
            line.contains("ORDER OF PLAY", ignoreCase = true) ||
            line.equals("Order of Play", ignoreCase = true)
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

    private data class ParsedEntrant(
        val name: String,
        val countryCode: String?,
        val seed: Int?,
    )

    private data class ParsedPair(
        val left: ParsedEntrant,
        val right: ParsedEntrant,
        val scheduleLabel: String,
        val manualNotBefore: Long?,
    )

    private data class ScheduleContext(
        val label: String,
        val manualNotBefore: Long?,
    )

    private data class CollectedName(
        val name: String,
        val endIndex: Int,
    )

    private data class OcrLine(
        val text: String,
        val left: Float,
        val right: Float,
        val top: Float,
        val bottom: Float,
    ) {
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
    }

    private data class CourtColumnLayout(
        val name: String,
        val leftBoundary: Float,
        val rightBoundary: Float,
        val headerBottom: Float,
    )

    private data class RowBandLayout(
        val order: Int,
        val topBoundary: Float,
        val bottomBoundary: Float,
    )

    private data class InlinePair(
        val leftRaw: String,
        val rightRaw: String,
    )

    private data class ParsedEntrantLine(
        val centerY: Float,
        val raw: String,
        val entrant: ParsedEntrant,
    )

    private data class CourtPlacement(
        val courtIndex: Int,
        val order: Int,
    )

    private data class ImportedOrderMatch(
        val courtName: String,
        val order: Int,
        val left: ParsedEntrant,
        val right: ParsedEntrant,
        val scheduleLabel: String,
        val manualNotBefore: Long?,
    )

    private data class ImportedOrderDocument(
        val tournamentName: String,
        val courts: List<String>,
        val matches: List<ImportedOrderMatch>,
    )

    private companion object {
        const val DEFAULT_REST_MINUTES = 30
        const val REST_PROMPT_MIN_MATCH_MINUTES = 60
        val INLINE_VS_REGEX = Regex("(?i)\\b(?:v\\.?[s5$]\\.?|vs\\.?|v\\.)\\b")
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
        val ISO2_CODES = ISO3_TO_ISO2.values.toSet()
        val OCR_NOISE_TOKENS = setOf("hii", "hll", "iii", "ii", "iz", "zi", "rs", "sr", "ir", "lr")
    }
}

private fun <T, R> Flow<List<T>>.mapList(transform: (T) -> R): Flow<List<R>> =
    map { list -> list.map(transform) }

private fun String.normalizeSpaces(): String = trim().replace(Regex("\\s+"), " ")
