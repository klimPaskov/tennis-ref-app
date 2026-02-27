package com.klim.tennisrefapp.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.withTransaction
import com.klim.tennisrefapp.common.CountryFlags
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
import com.klim.tennisrefapp.domain.model.DrawBoard
import com.klim.tennisrefapp.domain.model.DrawMatchNode
import com.klim.tennisrefapp.domain.model.DrawRound
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
import com.tom_roush.pdfbox.rendering.PDFRenderer
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
import kotlin.math.roundToInt

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
        saveReloadSource(sourceType = RELOAD_SOURCE_SEED, sourceValue = null)
    }

    override suspend fun reloadOrderOfPlay(): Boolean {
        val source = loadReloadSource()
        return when (source.type) {
            RELOAD_SOURCE_FILE -> {
                val uriString = source.value ?: return false
                val imported = parseOrderOfPlayFromUri(uriString) ?: return false
                val replaced = replaceTournamentWithImportedOrder(imported)
                if (replaced) {
                    saveReloadSource(sourceType = RELOAD_SOURCE_FILE, sourceValue = uriString)
                }
                replaced
            }
            RELOAD_SOURCE_PRELOADED -> {
                val preloadId = source.value ?: DEFAULT_PRELOADED_ID
                loadPreloadedOrderOfPlay(preloadId)
            }
            else -> {
                withContext(Dispatchers.IO) {
                    database.clearAllTables()
                }
                seedIfEmpty()
                true
            }
        }
    }

    override suspend fun reloadOrderOfPlayFromFile(uriString: String): Boolean {
        val imported = parseOrderOfPlayFromUri(uriString) ?: return false
        val replaced = replaceTournamentWithImportedOrder(imported)
        if (replaced) {
            saveReloadSource(sourceType = RELOAD_SOURCE_FILE, sourceValue = uriString)
        }
        return replaced
    }

    override suspend fun loadPreloadedOrderOfPlay(preloadId: String): Boolean {
        val loaded = when (preloadId.lowercase()) {
            "example" -> replaceTournamentWithImportedOrder(verifiedPreloadedOrderOfPlay())
            "28.02.2026" -> replaceTournamentWithImportedOrder(preloadedOrderOfPlay20260228())
            else -> false
        }
        if (loaded) {
            saveReloadSource(sourceType = RELOAD_SOURCE_PRELOADED, sourceValue = preloadId.lowercase())
        }
        return loaded
    }

    private suspend fun replaceTournamentWithImportedOrder(imported: ImportedOrderDocument): Boolean {
        if (imported.matches.isEmpty()) return false
        val knownMatchRefs = imported.matches
            .map { match -> ImportedMatchRef(courtName = match.courtName, order = match.order) }
            .toSet()
        val hasUnknownDependency = imported.matches.any { match ->
            listOfNotNull(match.leftWinnerOf, match.rightWinnerOf).any { dependency ->
                dependency !in knownMatchRefs
            }
        }
        if (hasUnknownDependency) return false
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
                .flatMap { listOfNotNull(it.left, it.right) }
                .fold(linkedMapOf<String, ParsedEntrant>()) { acc, entrant ->
                    val existing = acc[entrant.name]
                    acc[entrant.name] = mergeEntrant(existing, entrant)
                    acc
                }
                .values
                .toList()

            data class TeamDraft(
                val entrantName: String,
                val countryCode: String?,
                val seed: Int?,
                val memberNames: List<String>,
            )
            data class PlayerDraft(
                val name: String,
                val countryCode: String?,
                var seed: Int?,
            )

            val teamDrafts = entrants.map { entrant ->
                TeamDraft(
                    entrantName = entrant.name,
                    countryCode = entrant.countryCode,
                    seed = entrant.seed,
                    memberNames = if (entrant.memberNames.isNotEmpty()) {
                        entrant.memberNames
                    } else {
                        splitEntrantIntoPlayerNames(entrant.name)
                    },
                )
            }

            val playerDraftByKey = linkedMapOf<String, PlayerDraft>()
            teamDrafts.forEach { team ->
                team.memberNames.forEachIndexed { memberIndex, memberName ->
                    val key = teamPlayerKey(memberName, team.countryCode)
                    val incomingSeed = if (team.memberNames.size == 1 || memberIndex == 0) team.seed else null
                    val existing = playerDraftByKey[key]
                    if (existing == null) {
                        playerDraftByKey[key] = PlayerDraft(
                            name = memberName,
                            countryCode = team.countryCode,
                            seed = incomingSeed,
                        )
                    } else if (existing.seed == null && incomingSeed != null) {
                        existing.seed = incomingSeed
                    }
                }
            }

            val players = playerDraftByKey.values.mapIndexed { index, draft ->
                PlayerEntity(
                    id = (index + 1).toLong(),
                    tournamentId = 1L,
                    displayName = buildEntrantDisplayName(draft.name, draft.seed),
                    countryCode = draft.countryCode,
                    restMinutes = DEFAULT_REST_MINUTES,
                )
            }
            playerDao.insertAll(players)

            val teamEntities = teamDrafts.mapIndexed { index, draft ->
                TeamEntity(
                    id = (index + 1).toLong(),
                    tournamentId = 1L,
                    name = if (draft.memberNames.size > 1) draft.memberNames.joinToString(" / ") else null,
                )
            }
            teamDao.insertAll(teamEntities)

            val playerIdByKey = playerDraftByKey.keys
                .mapIndexed { index, key -> key to (index + 1L) }
                .toMap()
            teamPlayerDao.insertAll(
                teamDrafts.flatMapIndexed { teamIndex, team ->
                    val teamId = (teamIndex + 1L)
                    team.memberNames.mapIndexed { memberOrder, memberName ->
                        TeamPlayerCrossRefEntity(
                            teamId = teamId,
                            playerId = playerIdByKey.getValue(teamPlayerKey(memberName, team.countryCode)),
                            playerOrder = memberOrder,
                        )
                    }
                },
            )

            val teamIdByEntrantName = teamDrafts
                .mapIndexed { index, draft -> draft.entrantName to (index + 1L) }
                .toMap()
            val matchEntities = mutableListOf<MatchEntity>()
            val slotEntities = mutableListOf<SlotEntity>()
            val matchIdByRef = linkedMapOf<ImportedMatchRef, Long>()
            imported.matches.forEachIndexed { index, importedMatch ->
                val currentMatchId = 100L + index
                val matchRef = ImportedMatchRef(
                    courtName = importedMatch.courtName,
                    order = importedMatch.order,
                )
                matchIdByRef[matchRef] = currentMatchId
                matchEntities += MatchEntity(
                    id = currentMatchId,
                    tournamentId = 1L,
                    courtId = courtIdByName[importedMatch.courtName],
                    roundName = buildRoundLabel(importedMatch),
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
            }

            imported.matches.forEachIndexed { index, importedMatch ->
                val currentMatchId = 100L + index
                slotEntities += buildImportedSlotEntity(
                    slotId = currentMatchId * 10L + 1L,
                    matchId = currentMatchId,
                    position = SlotPosition.LEFT,
                    entrant = importedMatch.left,
                    winnerOf = importedMatch.leftWinnerOf,
                    isBye = importedMatch.leftIsBye,
                    teamIdByEntrantName = teamIdByEntrantName,
                    matchIdByRef = matchIdByRef,
                )
                slotEntities += buildImportedSlotEntity(
                    slotId = currentMatchId * 10L + 2L,
                    matchId = currentMatchId,
                    position = SlotPosition.RIGHT,
                    entrant = importedMatch.right,
                    winnerOf = importedMatch.rightWinnerOf,
                    isBye = importedMatch.rightIsBye,
                    teamIdByEntrantName = teamIdByEntrantName,
                    matchIdByRef = matchIdByRef,
                )
            }
            matchDao.insertAll(matchEntities)
            slotDao.insertAll(slotEntities)
        }

        recomputeNotBeforeAndSchedule()
        return true
    }

    private fun buildRoundLabel(match: ImportedOrderMatch): String {
        val suffix = match.eventCode?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        return "${match.order}. ${match.scheduleLabel}$suffix"
    }

    private fun buildImportedSlotEntity(
        slotId: Long,
        matchId: Long,
        position: SlotPosition,
        entrant: ParsedEntrant?,
        winnerOf: ImportedMatchRef?,
        isBye: Boolean,
        teamIdByEntrantName: Map<String, Long>,
        matchIdByRef: Map<ImportedMatchRef, Long>,
    ): SlotEntity {
        return when {
            isBye -> SlotEntity(
                id = slotId,
                matchId = matchId,
                position = position,
                slotType = SlotType.BYE,
                directTeamId = null,
                winnerOfMatchId = null,
            )

            winnerOf != null -> SlotEntity(
                id = slotId,
                matchId = matchId,
                position = position,
                slotType = SlotType.WINNER_OF_MATCH,
                directTeamId = null,
                winnerOfMatchId = matchIdByRef[winnerOf],
            )

            entrant != null -> SlotEntity(
                id = slotId,
                matchId = matchId,
                position = position,
                slotType = SlotType.DIRECT_TEAM,
                directTeamId = teamIdByEntrantName[entrant.name],
                winnerOfMatchId = null,
            )

            else -> SlotEntity(
                id = slotId,
                matchId = matchId,
                position = position,
                slotType = SlotType.WINNER_OF_MATCH,
                directTeamId = null,
                winnerOfMatchId = null,
            )
        }
    }

    private fun loadReloadSource(): ReloadSource {
        val prefs = appContext.getSharedPreferences(RELOAD_PREFS_NAME, Context.MODE_PRIVATE)
        val type = prefs.getString(KEY_RELOAD_SOURCE_TYPE, RELOAD_SOURCE_SEED)
            ?.takeIf { it.isNotBlank() }
            ?: RELOAD_SOURCE_SEED
        return ReloadSource(
            type = type,
            value = prefs.getString(KEY_RELOAD_SOURCE_VALUE, null),
        )
    }

    private fun saveReloadSource(
        sourceType: String,
        sourceValue: String?,
    ) {
        appContext
            .getSharedPreferences(RELOAD_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RELOAD_SOURCE_TYPE, sourceType)
            .putString(KEY_RELOAD_SOURCE_VALUE, sourceValue)
            .apply()
    }

    private fun verifiedPreloadedOrderOfPlay(): ImportedOrderDocument {
        fun entrant(name: String, country: String, seed: Int? = null): ParsedEntrant {
            return ParsedEntrant(name = name, countryCode = country, seed = seed)
        }
        val start0900 = toTodayMillis("09:00")
        val notBefore1130 = toTodayMillis("11:30")
        fun schedule(order: Int): Pair<String, Long?> {
            return when (order) {
                1 -> "Starting at 09:00" to start0900
                3 -> "Not Before 11:30" to notBefore1130
                else -> "Followed By" to null
            }
        }
        fun match(
            court: String,
            order: Int,
            left: ParsedEntrant,
            right: ParsedEntrant,
            scheduleOverride: Pair<String, Long?>? = null,
        ): ImportedOrderMatch {
            val (label, manual) = scheduleOverride ?: schedule(order)
            return ImportedOrderMatch(
                courtName = court,
                order = order,
                left = left,
                right = right,
                scheduleLabel = label,
                manualNotBefore = manual,
            )
        }

        val matches = listOf(
            match("Court 2", 1, entrant("Yiyuan Su", "CHN"), entrant("Vincent Fletcher", "GBR", 5)),
            match("Court 2", 2, entrant("Hanna Vanni", "FIN", 7), entrant("Vera Morozova", "EST")),
            match("Court 2", 3, entrant("Elizabete Marta Hamitova", "LAT"), entrant("Margarita Novik", "EST")),
            match("Court 2", 4, entrant("Filip Hesser", "SWE", 6), entrant("Andreas Kuum", "EST")),
            match("Court 3", 1, entrant("Sonia Sirenius", "FIN"), entrant("Vladislava Lescinska", "LAT", 8)),
            match("Court 3", 2, entrant("Kamilla Cernousova", "LAT"), entrant("Marie Benmouyal", "DEN", 5)),
            match("Court 3", 3, entrant("Mark Griaziutin Tverskoj", "LTU"), entrant("Alex Tuomolin", "FIN")),
            match("Court 3", 4, entrant("Gabriel Maciel Araujo Pyndt", "DEN"), entrant("Nick Mertgens", "GER")),
            match("Court 4", 1, entrant("Elizaveta Anikina", "EST", 1), entrant("Sofia Ioana Voicu", "BEL")),
            match("Court 4", 2, entrant("Svit Suljic", "SLO"), entrant("Ken Ink", "EST", 2)),
            match("Court 4", 3, entrant("Ewangelina Penkevych", "POL"), entrant("Nora Olivia Adamson", "EST", 3)),
            match("Court 4", 4, entrant("Daria Podlipskaya", "EST"), entrant("Elin Kalmus", "EST")),
            match("Court 5", 1, entrant("Sonja Wedenski", "SWE"), entrant("Varvara Trifonova", "EST")),
            match("Court 5", 2, entrant("Margarita Aktuganova", "ISR", 6), entrant("Ksenia Semjonova", "EST")),
            match("Court 5", 3, entrant("Nikita Smirnov", "EST"), entrant("Moritz Glauser", "LIE", 4)),
            match("Court 5", 4, entrant("Luukas Raudkivi", "EST"), entrant("Jan Werblinski", "POL")),
            match("Court 6", 1, entrant("Asia Basiletti", "ITA"), entrant("Aleksandra Maksimova", "EST")),
            match("Court 6", 2, entrant("Julia Kolodziejska", "POL", 4), entrant("Viktoria Sidorenkova", "EST")),
            match("Court 6", 3, entrant("Vlad Secara", "ROU", 7), entrant("Rasmus Stalmeister", "EST")),
            match("Court 6", 4, entrant("Nand Vandepoele", "BEL", 3), entrant("Egert Trave", "EST")),
            match("Court 7", 1, entrant("Edwin Averjanov", "EST", 1), entrant("Jan Sykora", "CZE")),
            match("Court 7", 2, entrant("Liisa Marii Pohjala", "EST"), entrant("Grace Bernstein", "SWE")),
            match("Court 7", 3, entrant("Eriks Valeinis", "LAT"), entrant("Mads Bruhn Aaby", "DEN")),
            match("Court 7", 4, entrant("Kirils Sokolovs", "LAT"), entrant("Verneri Riikonen", "FIN")),
            match("Court 8", 1, entrant("Markus Valtson", "EST"), entrant("Asger Sorensen", "DEN")),
            match("Court 8", 2, entrant("Elizaveta Bryakina", "EST"), entrant("Luana Kappeler", "SUI")),
            match("Court 8", 3, entrant("Maryna Levadna", "UKR"), entrant("Marie Johanna Lapimaa", "EST")),
            match("Court 8", 4, entrant("Hitoshi Shirabe", "JPN"), entrant("Mark Gregor Toniste", "EST")),
            match("Court 9", 1, entrant("Isak Bossfall", "SWE"), entrant("Armands Aire", "LAT")),
            match("Court 9", 2, entrant("Olesja Demcenkova", "LAT"), entrant("Alexandra Nedyalkova", "EST")),
            match("Court 9", 3, entrant("Daria Barsukov", "GER"), entrant("Anna Azarova", "LAT", 2)),
            match("Court 9", 4, entrant("Mingus Zack Hansen", "DEN"), entrant("Hugo Pawel Rytelewski", "POL", 8)),
        )

        return ImportedOrderDocument(
            tournamentName = "J60 Tallinn (Verified Preloaded)",
            courts = listOf("Court 2", "Court 3", "Court 4", "Court 5", "Court 6", "Court 7", "Court 8", "Court 9"),
            matches = matches,
        )
    }

    private fun preloadedOrderOfPlay20260228(): ImportedOrderDocument {
        fun entrant(name: String, country: String, seed: Int? = null): ParsedEntrant {
            return ParsedEntrant(name = name, countryCode = country, seed = seed)
        }
        fun ref(court: String, order: Int): ImportedMatchRef = ImportedMatchRef(courtName = court, order = order)
        fun match(
            court: String,
            order: Int,
            scheduleLabel: String,
            manualNotBefore: Long?,
            eventCode: String,
            left: ParsedEntrant? = null,
            right: ParsedEntrant? = null,
            leftWinnerOf: ImportedMatchRef? = null,
            rightWinnerOf: ImportedMatchRef? = null,
        ): ImportedOrderMatch {
            return ImportedOrderMatch(
                courtName = court,
                order = order,
                left = left,
                right = right,
                scheduleLabel = scheduleLabel,
                manualNotBefore = manualNotBefore,
                eventCode = eventCode,
                leftWinnerOf = leftWinnerOf,
                rightWinnerOf = rightWinnerOf,
            )
        }

        val notBefore1500 = toTodayMillis("15:00")
        val start1000 = toTodayMillis("10:00")
        val notBefore1200 = toTodayMillis("12:00")
        val notBefore1430 = toTodayMillis("14:30")

        val matches = listOf(
            // Row 1
            match("Court 2", 1, "Not Before 15:00", notBefore1500, "GSQ",
                left = entrant("Elizaveta Bryakina", "EST", 2),
                rightWinnerOf = ref("Court 6", 2)),
            match("Court 3", 1, "Not Before 15:00", notBefore1500, "GSQ",
                left = entrant("Lisette Leppik", "EST", 1),
                rightWinnerOf = ref("Court 8", 2)),
            match("Court 4", 1, "Starting at 10:00", start1000, "GSQ",
                left = entrant("Guste Maliutoryte", "LTU", 4),
                right = entrant("Ivetta Serova", "EST")),
            match("Court 5", 1, "Starting at 10:00", start1000, "GSQ",
                left = entrant("Ella-Maria Vosu", "EST"),
                right = entrant("Amanda Nicoli", "FIN")),
            match("Court 6", 1, "Starting at 10:00", start1000, "GSQ",
                left = entrant("Meta Petkute", "LTU"),
                right = entrant("Krete Visnapuu", "EST")),
            match("Court 7", 1, "Starting at 10:00", start1000, "GSQ",
                left = entrant("Zoe Kammandel", "GER"),
                right = entrant("Arsineh Kazanjian", "EST")),
            match("Court 8", 1, "Starting at 10:00", start1000, "GSQ",
                left = entrant("Doris Audova", "EST"),
                right = entrant("Siiri Metsaeranta", "FIN", 5)),
            match("Court 9", 1, "Starting at 10:00", start1000, "GSQ",
                left = entrant("Sofia Traverso", "EST"),
                right = entrant("Harriet Podra", "EST")),

            // Row 2
            match("Court 2", 2, "Followed By", null, "BSQ",
                left = entrant("Callum Hooper", "GBR", 3),
                rightWinnerOf = ref("Court 4", 3)),
            match("Court 3", 2, "Followed By", null, "BSQ",
                left = entrant("Markas Mazrimas", "LTU", 1),
                rightWinnerOf = ref("Court 5", 3)),
            match("Court 4", 2, "Followed By", null, "GSQ",
                left = entrant("Amelia Maljugina", "EST"),
                right = entrant("Emily Stoian", "USA")),
            match("Court 5", 2, "Followed By", null, "GSQ",
                left = entrant("Pilar Aurora Rim", "EST"),
                right = entrant("Simona Sepp", "EST", 8)),
            match("Court 6", 2, "Followed By", null, "GSQ",
                left = entrant("Katlin Tonts", "EST"),
                right = entrant("Ona Kleidisaite", "LTU")),
            match("Court 7", 2, "Followed By", null, "GSQ",
                left = entrant("Marta Vellerand", "EST"),
                right = entrant("Ieva Sikliutaite", "LTU", 6)),
            match("Court 8", 2, "Followed By", null, "GSQ",
                left = entrant("Henriette Jarv", "EST"),
                right = entrant("Alisa Lokk", "EST")),
            match("Court 9", 2, "Followed By", null, "GSQ",
                left = entrant("Mariana Nitu", "MDA", 3),
                right = entrant("Kristel Eva Torkina", "EST")),

            // Row 3
            match("Court 4", 3, "Not Before 12:00", notBefore1200, "BSQ",
                left = entrant("Leo Kurtilia", "FIN"),
                right = entrant("Hugo Narmont", "EST", 8)),
            match("Court 5", 3, "Not Before 12:00", notBefore1200, "BSQ",
                left = entrant("Andreas Kuum", "EST"),
                right = entrant("Jekabs Stikuts", "LAT")),
            match("Court 6", 3, "Not Before 12:00", notBefore1200, "BSQ",
                left = entrant("Kristjan Simon Hannus", "EST"),
                right = entrant("Stanislaw Zaranski", "POL")),
            match("Court 7", 3, "Not Before 12:00", notBefore1200, "BSQ",
                left = entrant("Arvi Kauhanen", "FIN"),
                right = entrant("Robin Rait Rikberg", "EST", 5)),
            match("Court 8", 3, "Not Before 12:00", notBefore1200, "BSQ",
                left = entrant("Evan Lainurm", "EST"),
                right = entrant("Felix Standertskjold-Nordenstam", "FIN")),
            match("Court 9", 3, "Not Before 12:00", notBefore1200, "GSQ",
                left = entrant("Eliza Mangule", "LAT"),
                right = entrant("Marie Roomussaar", "EST")),

            // Row 4
            match("Court 4", 4, "Followed By", null, "BSQ",
                left = entrant("Otto Herman Pikk", "EST"),
                right = entrant("Tom Barcia", "ITA")),
            match("Court 5", 4, "Followed By", null, "GSQ",
                left = entrant("Eva Laanots", "EST"),
                right = entrant("Annika Alifin", "FIN", 7)),
            match("Court 6", 4, "Followed By", null, "BSQ",
                left = entrant("Hugo Thor Teiv", "EST"),
                right = entrant("Richard Hiemann", "GER")),
            match("Court 7", 4, "Followed By", null, "BSQ",
                left = entrant("Marats Apens", "LAT"),
                right = entrant("Joel Nylund", "FIN")),
            match("Court 8", 4, "Followed By", null, "BSQ",
                left = entrant("Euan Greenslade", "GBR"),
                right = entrant("Arthur de Dampierre", "FIN")),
            match("Court 9", 4, "Followed By", null, "BSQ",
                left = entrant("Alexander Mononen", "FIN"),
                right = entrant("Kristo Vellerand", "EST")),

            // Row 5
            match("Court 4", 5, "Not Before 14:30", notBefore1430, "GSQ",
                leftWinnerOf = ref("Court 6", 1),
                rightWinnerOf = ref("Court 5", 4)),
            match("Court 5", 5, "Not Before 14:30", notBefore1430, "GSQ",
                leftWinnerOf = ref("Court 9", 3),
                rightWinnerOf = ref("Court 8", 1)),
            match("Court 6", 5, "Not Before 14:30", notBefore1430, "BSQ",
                left = entrant("Johann Juergen Hueherson", "EST"),
                right = entrant("Samuel Tammi", "FIN", 6)),
            match("Court 7", 5, "Not Before 14:30", notBefore1430, "GSQ",
                leftWinnerOf = ref("Court 9", 2),
                rightWinnerOf = ref("Court 9", 1)),
            match("Court 8", 5, "Not Before 14:30", notBefore1430, "BSQ",
                left = entrant("Leonardo Lori", "ITA"),
                right = entrant("Aaron Kelle", "EST", 7)),
            match("Court 9", 5, "Not Before 14:30", notBefore1430, "GSQ",
                leftWinnerOf = ref("Court 7", 1),
                rightWinnerOf = ref("Court 7", 2)),

            // Row 6
            match("Court 4", 6, "Followed By", null, "BSQ",
                leftWinnerOf = ref("Court 4", 4),
                rightWinnerOf = ref("Court 2", 2)),
            match("Court 5", 6, "Followed By", null, "GSQ",
                leftWinnerOf = ref("Court 4", 5),
                rightWinnerOf = ref("Court 5", 5)),
            match("Court 6", 6, "Followed By", null, "BSQ",
                leftWinnerOf = ref("Court 6", 4),
                rightWinnerOf = ref("Court 6", 5)),
            match("Court 7", 6, "Followed By", null, "BSQ",
                left = entrant("Konrad Punger", "EST", 4),
                rightWinnerOf = ref("Court 7", 4)),
            match("Court 8", 6, "Followed By", null, "GSQ",
                leftWinnerOf = ref("Court 7", 5),
                rightWinnerOf = ref("Court 9", 5)),
            match("Court 9", 6, "Followed By", null, "BSQ",
                left = entrant("Juozas Juozapavicius", "LTU", 2),
                rightWinnerOf = ref("Court 9", 4)),

            // Row 7
            match("Court 6", 7, "Followed By", null, "BSQ",
                leftWinnerOf = ref("Court 6", 3),
                rightWinnerOf = ref("Court 7", 3)),
            match("Court 8", 7, "Followed By", null, "BSQ",
                leftWinnerOf = ref("Court 8", 4),
                rightWinnerOf = ref("Court 8", 5)),
        )

        return ImportedOrderDocument(
            tournamentName = "28.02.2026",
            courts = listOf("Court 2", "Court 3", "Court 4", "Court 5", "Court 6", "Court 7", "Court 8", "Court 9"),
            matches = matches,
        )
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
                winnerLabel = match.winnerTeamId?.let { winnerTeamId ->
                    labelForTeam(winnerTeamId, context)
                },
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

    override fun observeDrawBoards(): Flow<List<DrawBoard>> {
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

            val slotsByMatch = slots.groupBy { it.matchId }
            val context = BuildContext(
                matches = matches,
                slotsByMatch = slotsByMatch,
                playersById = players.associateBy { it.id },
                teamPlayersByTeam = teamPlayers.groupBy { it.teamId }.mapValues { entry ->
                    entry.value.sortedBy { it.playerOrder }.mapNotNull { ref -> players.firstOrNull { it.id == ref.playerId } }
                },
                defaultRestMinutes = tournament?.defaultRestMinutes ?: DEFAULT_REST_MINUTES,
            )
            val courtSortById = courts.associate { it.id to it.sortOrder }
            val courtNameById = courts.associate { it.id to it.name }
            val depthCache = mutableMapOf<Long, Int>()

            val nodes = matches.mapNotNull { match ->
                val eventCode = extractEventCodeFromRoundName(match.roundName) ?: return@mapNotNull null
                val left = sideForMatch(match.id, SlotPosition.LEFT, context)
                val right = sideForMatch(match.id, SlotPosition.RIGHT, context)
                val depth = computeDrawDepth(
                    matchId = match.id,
                    slotsByMatch = slotsByMatch,
                    cache = depthCache,
                    visiting = mutableSetOf(),
                )
                DrawNodeWithMeta(
                    eventCode = eventCode,
                    depth = depth,
                    order = extractOrderFromRoundName(match.roundName, match.id),
                    courtSort = match.courtId?.let { courtSortById[it] } ?: Int.MAX_VALUE,
                    node = DrawMatchNode(
                        matchId = match.id,
                        order = extractOrderFromRoundName(match.roundName, match.id),
                        courtName = match.courtId?.let { courtNameById[it] ?: "Court $it" },
                        leftLabel = formatSideShortLabel(left),
                        rightLabel = formatSideShortLabel(right),
                        winnerLabel = match.winnerTeamId?.let { labelForTeam(it, context) },
                        scoreLabel = buildScoreLabel(match.scoreText, match.resultType),
                        status = match.status.toCourtStatus(),
                    ),
                )
            }

            nodes.groupBy { it.eventCode }
                .toList()
                .sortedBy { (eventCode, _) -> eventSortWeight(eventCode) }
                .map { (eventCode, eventNodes) ->
                    val maxDepth = eventNodes.maxOfOrNull { it.depth } ?: 1
                    DrawBoard(
                        eventCode = eventCode,
                        rounds = eventNodes
                            .groupBy { it.depth }
                            .toList()
                            .sortedBy { (depth, _) -> depth }
                            .map { (depth, roundNodes) ->
                                DrawRound(
                                    depth = depth,
                                    label = drawRoundLabel(depth = depth, maxDepth = maxDepth),
                                    matches = roundNodes
                                        .sortedWith(
                                            compareBy<DrawNodeWithMeta> { it.order }
                                                .thenBy { it.courtSort }
                                                .thenBy { it.node.matchId },
                                        )
                                        .map { it.node },
                                )
                            },
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
                        scheduleLabel = null,
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
                        scheduleLabel = extractScheduleLabel(currentMatch.roundName),
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
                    scheduleLabel = null,
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
                    scheduleLabel = extractScheduleLabel(currentMatch.roundName),
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

    private fun extractScheduleLabel(roundName: String?): String? {
        val text = roundName?.trim().orEmpty()
        if (text.isBlank()) return null
        return when {
            text.contains("Followed By", ignoreCase = true) -> "Followed By"
            text.contains("Not Before", ignoreCase = true) -> {
                val value = Regex("(?i)not\\s*before\\s*(\\d{1,2}:\\d{2})")
                    .find(text)
                    ?.groupValues
                    ?.getOrNull(1)
                if (value != null) "Not before $value" else "Not before"
            }
            text.contains("Starting at", ignoreCase = true) -> {
                val value = Regex("(?i)starting\\s*at\\s*(\\d{1,2}:\\d{2})")
                    .find(text)
                    ?.groupValues
                    ?.getOrNull(1)
                if (value != null) "Starting at $value" else "Starting at"
            }
            else -> null
        }
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
                val sourceMatch = winnerOfMatch?.let { sourceId ->
                    context.matches.firstOrNull { candidate -> candidate.id == sourceId }
                }
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
                        "TBD"
                    },
                    players = players,
                    resolvedTeamId = resolution.resolvedTeamId,
                    isResolved = resolution.isResolved,
                    dependencyMatchId = winnerOfMatch,
                    dependencyCourtId = sourceMatch?.courtId,
                    dependencyLabel = sourceMatch?.let { match ->
                        val sourceCourt = match.courtId?.let { "Court $it" } ?: "Unassigned court"
                        "Winner from $sourceCourt • ${formatMatchLabel(match, context)}"
                    },
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

    private fun computeDrawDepth(
        matchId: Long,
        slotsByMatch: Map<Long, List<SlotEntity>>,
        cache: MutableMap<Long, Int>,
        visiting: MutableSet<Long>,
    ): Int {
        cache[matchId]?.let { return it }
        if (!visiting.add(matchId)) return 1
        val dependencies = slotsByMatch[matchId].orEmpty()
            .mapNotNull { slot ->
                if (slot.slotType == SlotType.WINNER_OF_MATCH) slot.winnerOfMatchId else null
            }
            .distinct()
        val depth = if (dependencies.isEmpty()) {
            1
        } else {
            dependencies.maxOf { dependencyId ->
                computeDrawDepth(
                    matchId = dependencyId,
                    slotsByMatch = slotsByMatch,
                    cache = cache,
                    visiting = visiting,
                )
            } + 1
        }
        visiting.remove(matchId)
        cache[matchId] = depth
        return depth
    }

    private fun extractEventCodeFromRoundName(roundName: String?): String? {
        val value = roundName?.trim().orEmpty()
        if (value.isBlank()) return null
        return Regex("\\b([A-Z]{3,4})\\s*$")
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun extractOrderFromRoundName(roundName: String?, fallback: Long): Int {
        return roundName
            ?.let { Regex("^\\s*(\\d+)\\b").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            ?: fallback.toInt()
    }

    private fun drawRoundLabel(depth: Int, maxDepth: Int): String {
        return when {
            depth == maxDepth && maxDepth >= 2 -> "Final"
            depth == maxDepth - 1 && maxDepth >= 3 -> "Semi-finals"
            depth == maxDepth - 2 && maxDepth >= 4 -> "Quarter-finals"
            else -> "Round $depth"
        }
    }

    private fun eventSortWeight(eventCode: String): String {
        return when (eventCode.uppercase()) {
            "BSQ" -> "0_$eventCode"
            "GSQ" -> "1_$eventCode"
            else -> "2_$eventCode"
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
        return CountryFlags.toFlagEmoji(countryCode)
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
            return parseOrderOfPlayFromImage(uri)
        }
        if (isPdfFile(uri, mimeType)) {
            return parseOrderOfPlayFromPdfByOcr(uri)
        }

        val text = readOrderOfPlayText(uri, mimeType) ?: return null
        val lines = text
            .replace('\u000C', '\n')
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        return parseOrderOfPlayFromTextLines(lines)
    }

    private fun parseOrderOfPlayFromTextLines(
        lines: List<String>,
        fallbackCourts: List<String>? = null,
    ): ImportedOrderDocument? {
        if (lines.isEmpty()) return null
        val courts = extractCourtNames(lines)
        val courtNames = when {
            courts.isNotEmpty() -> courts
            !fallbackCourts.isNullOrEmpty() -> fallbackCourts
            else -> listOf("Court 1")
        }
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
        return parseOrderOfPlayFromRecognizedTable(recognized)
    }

    private suspend fun parseOrderOfPlayFromPdfByOcr(uri: Uri): ImportedOrderDocument? = withContext(Dispatchers.IO) {
        runCatching {
            PDFBoxResourceLoader.init(appContext)
            val resolver = appContext.contentResolver
            resolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { document ->
                    val pageCount = document.numberOfPages
                    if (pageCount <= 0) {
                        null
                    } else {
                        val renderer = PDFRenderer(document)
                        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        try {
                            val candidates = mutableListOf<ImportedOrderDocument>()
                            for (pageIndex in 0 until minOf(pageCount, PDF_OCR_MAX_PAGES)) {
                                val rendered = renderer.renderImage(pageIndex, PDF_OCR_RENDER_SCALE)
                                val pageBitmap = scaleBitmapIfNeeded(rendered, PDF_OCR_MAX_DIMENSION)
                                if (pageBitmap !== rendered && !rendered.isRecycled) {
                                    rendered.recycle()
                                }
                                try {
                                    val recognized = Tasks.await(recognizer.process(InputImage.fromBitmap(pageBitmap, 0)))
                                    parseOrderOfPlayFromRecognizedTable(recognized)?.let { candidates += it }
                                } finally {
                                    if (!pageBitmap.isRecycled) {
                                        pageBitmap.recycle()
                                    }
                                }
                            }
                            candidates.maxByOrNull { it.matches.size * 100 + it.courts.size }
                        } finally {
                            recognizer.close()
                        }
                    }
                }
            }
        }.getOrNull()
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largest.toFloat()
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun parseOrderOfPlayFromRecognizedTable(recognized: Text): ImportedOrderDocument? {
        val ocrLines = extractOcrLines(recognized)
        if (ocrLines.isEmpty()) return null

        val columns = detectCourtColumns(ocrLines)
        if (columns.isEmpty()) return null
        val rowBands = detectRowBands(ocrLines, columns)
        if (rowBands.isEmpty()) return null

        val matches = mutableListOf<ImportedOrderMatch>()
        rowBands.sortedBy { it.order }.forEach { row ->
            val rowLines = ocrLines
                .filter { line ->
                    line.centerY >= row.topBoundary &&
                        line.centerY < row.bottomBoundary
                }
                .sortedBy { it.top }
            val linesByColumn = groupRowLinesByColumn(rowLines, columns)
            val rowSchedule = parseScheduleContextFromRow(rowLines)
            val rowPairs = mutableListOf<Pair<ImportedOrderMatch, ScheduleContext>>()
            columns.forEach columnLoop@ { column ->
                val cellLines = linesByColumn[column.name].orEmpty().sortedBy { it.top }
                val entrants = parseMatchPairWithRecovery(
                    cellLines = cellLines,
                    row = row,
                    column = column,
                    allLines = ocrLines,
                ) ?: return@columnLoop
                val schedule = parseScheduleContextFromCell(cellLines)
                rowPairs += ImportedOrderMatch(
                    courtName = column.name,
                    order = row.order,
                    left = entrants.first,
                    right = entrants.second,
                    scheduleLabel = schedule.label,
                    manualNotBefore = schedule.manualNotBefore,
                ) to schedule
            }
            val explicitByKey = rowPairs
                .map { (_, schedule) -> schedule }
                .filter { it.explicitKeyword }
                .groupingBy { schedule -> schedule.label to schedule.manualNotBefore }
                .eachCount()
            val rowFallback = explicitByKey.maxByOrNull { it.value }?.key
                ?: if (rowSchedule.explicitKeyword || rowSchedule.manualNotBefore != null) {
                    rowSchedule.label to rowSchedule.manualNotBefore
                } else {
                    null
                }
            rowPairs.forEach { (match, schedule) ->
                if (!schedule.explicitKeyword && rowFallback != null) {
                    matches += match.copy(
                        scheduleLabel = rowFallback.first,
                        manualNotBefore = rowFallback.second,
                    )
                } else {
                    matches += match
                }
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

    private fun parseMatchPairWithRecovery(
        cellLines: List<OcrLine>,
        row: RowBandLayout,
        column: CourtColumnLayout,
        allLines: List<OcrLine>,
    ): Pair<ParsedEntrant, ParsedEntrant>? {
        parseMatchPairFromCellLines(cellLines)?.let { return it }
        val expandedLines = allLines
            .asSequence()
            .filter { line ->
                line.centerY >= row.topBoundary - ROW_RECOVERY_MARGIN &&
                    line.centerY < row.bottomBoundary + ROW_RECOVERY_MARGIN &&
                    columnOverlapWidth(line, column) >= ((line.right - line.left).coerceAtLeast(1f) * 0.18f)
            }
            .sortedBy { it.top }
            .toList()
        if (expandedLines.isEmpty()) return null
        parseMatchPairFromCellLines(expandedLines)?.let { return it }

        val entrantFocused = expandedLines.filter { line ->
            isPotentialEntrantLine(line.text) || isVsToken(line.text) || containsScheduleKeyword(line.text)
        }
        return parseMatchPairFromCellLines(entrantFocused)
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
            .let { values -> clusterAnchorValues(values, tolerance = 14f) }

        val scheduleAnchors = lines
            .filter { line ->
                line.top >= headerBottom &&
                    containsScheduleKeyword(line.text)
            }
            .map { it.centerY }
            .let { values -> clusterAnchorValues(values, tolerance = 24f) }

        val vsAnchors = lines
            .filter { line ->
                line.top >= headerBottom &&
                    isVsToken(line.text)
            }
            .map { it.centerY }
            .let { values -> clusterAnchorValues(values, tolerance = 22f) }

        val anchors = if (
            rowMarkers.isNotEmpty() &&
            (
                (scheduleAnchors.isEmpty() || rowMarkers.size >= (scheduleAnchors.size - 1)) &&
                    (vsAnchors.isEmpty() || rowMarkers.size >= (vsAnchors.size - 1))
                )
        ) {
            rowMarkers
        } else {
            clusterAnchorValues(rowMarkers + scheduleAnchors + vsAnchors, tolerance = 18f)
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

    private fun clusterAnchorValues(values: List<Float>, tolerance: Float): List<Float> {
        if (values.isEmpty()) return emptyList()
        return values
            .sorted()
            .fold(mutableListOf<Float>()) { acc, value ->
                if (acc.isEmpty() || abs(acc.last() - value) > tolerance) {
                    acc += value
                } else {
                    acc[acc.lastIndex] = (acc.last() + value) / 2f
                }
                acc
            }
    }

    private fun parseMatchPairFromCellLines(cellLines: List<OcrLine>): Pair<ParsedEntrant, ParsedEntrant>? {
        if (cellLines.isEmpty()) return null

        parseStructuredPairFromCellLines(cellLines)?.let { return it }

        val concatenatedPair = cellLines
            .asSequence()
            .mapNotNull { line -> extractConcatenatedPair(line.text) }
            .firstOrNull()
        if (concatenatedPair != null) {
            return concatenatedPair
        }

        val inlineCandidates = mutableListOf<InlinePairCandidate>()
        cellLines.forEach { line ->
            val inline = extractInlinePair(line.text) ?: return@forEach
            val left = parseEntrant(inline.leftRaw) ?: return@forEach
            val right = parseEntrant(inline.rightRaw) ?: return@forEach
            if (!left.name.equals(right.name, ignoreCase = true)) {
                val sanitized = sanitizeSeedConflict(left, right, inline.leftRaw, inline.rightRaw)
                inlineCandidates += InlinePairCandidate(
                    left = sanitized.first,
                    right = sanitized.second,
                    confidence = inlinePairConfidence(
                        left = sanitized.first,
                        right = sanitized.second,
                        leftRaw = inline.leftRaw,
                        rightRaw = inline.rightRaw,
                    ),
                )
            }
        }
        if (inlineCandidates.isNotEmpty()) {
            val best = inlineCandidates.maxWithOrNull(
                compareBy<InlinePairCandidate>({ it.confidence }, { -it.wordCount }),
            )
            if (best != null) {
                return best.left to best.right
            }
        }

        val vsIndex = cellLines.indexOfFirst { isVsToken(it.text) }
        if (vsIndex != -1) {
            val leftCandidates = cellLines
                .subList(0, vsIndex)
                .filter { isPotentialEntrantLine(it.text) }
            val rightCandidates = cellLines
                .drop(vsIndex + 1)
                .filter { isPotentialEntrantLine(it.text) }
            val leftRaw = bestEntrantRaw(leftCandidates, takeTail = true)
            val rightRaw = bestEntrantRaw(rightCandidates, takeTail = false)
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

    private fun parseStructuredPairFromCellLines(cellLines: List<OcrLine>): Pair<ParsedEntrant, ParsedEntrant>? {
        val allCandidates = extractStructuredEntrantCandidates(cellLines)
        if (allCandidates.size < 2) return null
        val candidates = allCandidates.filter { candidate ->
            val words = candidate.entrant.name.split(' ').count { it.isNotBlank() }
            words >= 2 || candidate.confidence >= 12
        }
        if (candidates.size < 2) return null

        val vsIndex = cellLines.indexOfFirst { isVsToken(it.text) }
        if (vsIndex != -1) {
            val splitY = cellLines[vsIndex].centerY
            val upper = candidates
                .filter { it.centerY <= splitY + 8f }
                .maxByOrNull { it.confidence }
            val lower = candidates
                .filter { candidate ->
                    candidate.centerY >= splitY - 8f &&
                        candidate.nameKey != upper?.nameKey
                }
                .maxByOrNull { it.confidence }
            if (upper != null && lower != null &&
                !upper.entrant.name.equals(lower.entrant.name, ignoreCase = true)
            ) {
                return sanitizeSeedConflict(
                    upper.entrant,
                    lower.entrant,
                    upper.sourceText,
                    lower.sourceText,
                )
            }
        }

        val sorted = candidates.sortedBy { it.centerY }
        var bestPair: Pair<StructuredEntrantCandidate, StructuredEntrantCandidate>? = null
        var bestScore = Int.MIN_VALUE
        for (i in 0 until sorted.lastIndex) {
            for (j in i + 1 until sorted.size) {
                val upper = sorted[i]
                val lower = sorted[j]
                if (upper.nameKey == lower.nameKey) continue
                if (upper.entrant.name.equals(lower.entrant.name, ignoreCase = true)) continue
                val verticalGapScore = ((lower.centerY - upper.centerY).coerceAtLeast(0f) / 6f).toInt().coerceAtMost(16)
                val pairScore = upper.confidence + lower.confidence + verticalGapScore
                if (pairScore > bestScore) {
                    bestScore = pairScore
                    bestPair = upper to lower
                }
            }
        }
        val selected = bestPair ?: return null
        return sanitizeSeedConflict(
            selected.first.entrant,
            selected.second.entrant,
            selected.first.sourceText,
            selected.second.sourceText,
        )
    }

    private fun extractStructuredEntrantCandidates(cellLines: List<OcrLine>): List<StructuredEntrantCandidate> {
        if (cellLines.isEmpty()) return emptyList()
        val snippets = mutableListOf<Pair<String, Float>>()
        cellLines.forEach { line ->
            snippets += line.text to line.centerY
        }
        for (i in 0 until cellLines.lastIndex) {
            val first = cellLines[i]
            val second = cellLines[i + 1]
            if (abs(first.centerY - second.centerY) <= 34f) {
                snippets += "${first.text} ${second.text}" to ((first.centerY + second.centerY) / 2f)
            }
        }

        val bestByKey = linkedMapOf<String, StructuredEntrantCandidate>()
        snippets.forEach { (text, centerY) ->
            parseStructuredEntrantsFromText(text).forEach { parsed ->
                val key = parsed.name.lowercase()
                val confidence = structuredEntrantConfidence(parsed, text)
                val candidate = StructuredEntrantCandidate(
                    entrant = parsed,
                    centerY = centerY,
                    confidence = confidence,
                    sourceText = text,
                    nameKey = key,
                )
                val existing = bestByKey[key]
                if (existing == null || candidate.confidence > existing.confidence) {
                    bestByKey[key] = candidate
                }
            }
        }
        return bestByKey.values.toList()
    }

    private fun parseStructuredEntrantsFromText(text: String): List<ParsedEntrant> {
        val normalized = text.normalizeSpaces().trim()
        if (normalized.isBlank()) return emptyList()
        val entrantPattern = Regex("([A-Za-z][A-Za-z'\\-\\. ]{1,80}?)\\s*\\(\\s*([A-Za-z0-9|]{2,6})\\s*\\)\\s*(?:\\[\\s*([A-Za-z0-9|]{1,4})\\s*])?")
        val matches = entrantPattern.findAll(normalized).toList()
        if (matches.isEmpty()) return emptyList()

        val parsed = mutableListOf<ParsedEntrant>()
        matches.forEach { match ->
            val rawName = match.groupValues.getOrNull(1).orEmpty().normalizeSpaces()
            val rawCountry = match.groupValues.getOrNull(2).orEmpty()
            val rawSeed = match.groupValues.getOrNull(3).orEmpty()
            val country = normalizeCountryCodeToken(rawCountry, allowFuzzy = true)
            val baseTokens = rawName
                .split(' ')
                .map(::normalizeNameToken)
                .filter { token ->
                    token.isNotBlank() &&
                        token.any { it.isLetter() } &&
                        !tokenMatchesCountry(token, country)
                }
            val seed = when {
                rawSeed.isBlank() -> null
                else -> decodeSeedToken(rawSeed, country) ?: rawSeed.toIntOrNull()?.takeIf { it in 1..32 }
            }
            structuredNameTokenCandidates(baseTokens).forEach candidateLoop@ { candidateTokens ->
                val cleanedTokens = cleanupNameTokens(candidateTokens)
                if (cleanedTokens.isEmpty()) return@candidateLoop
                val name = normalizePersonNameCase(cleanedTokens.joinToString(" "))
                if (name.isBlank()) return@candidateLoop
                parsed += ParsedEntrant(name = name, countryCode = country, seed = seed)
            }
        }
        return parsed
            .distinctBy { entrant -> "${entrant.name.lowercase()}|${entrant.countryCode}|${entrant.seed}" }
    }

    private fun structuredEntrantConfidence(entrant: ParsedEntrant, sourceText: String): Int {
        val words = entrant.name.split(' ').filter { it.isNotBlank() }
        val tokenCountScore = when (words.size) {
            1 -> -4
            in 2..3 -> 8
            4 -> 4
            5 -> 1
            else -> -2
        }
        val wordShapeScore = words.fold(0) { acc, word ->
            acc + when {
                word.length <= 1 -> -3
                word.length in 2..16 -> 2
                word.length in 17..24 -> 1
                else -> -1
            }
        }
        val countryScore = if (entrant.countryCode != null) 6 else 0
        val seedScore = if (entrant.seed != null) 3 else 0
        val markerScore = if (sourceText.contains('(') && sourceText.contains(')')) 2 else 0
        return tokenCountScore + wordShapeScore + countryScore + seedScore + markerScore
    }

    private fun structuredNameTokenCandidates(baseTokens: List<String>): List<List<String>> {
        val cleaned = cleanupNameTokens(baseTokens)
        if (cleaned.isEmpty()) return emptyList()

        val candidates = linkedSetOf<List<String>>()
        candidates += cleaned

        val splitSegments = Regex("(?i)\\b(?:and|&|/)\\b")
            .split(cleaned.joinToString(" "))
            .map { segment ->
                cleanupNameTokens(
                    segment
                        .split(' ')
                        .map { token -> token.trim() }
                        .filter { token -> token.isNotBlank() },
                )
            }
            .filter { segment -> segment.isNotEmpty() }
        splitSegments.forEach { segment ->
            candidates += segment
        }

        if (cleaned.size >= 4) {
            for (windowSize in 2..4) {
                if (cleaned.size >= windowSize) {
                    candidates += cleaned.take(windowSize)
                    candidates += cleaned.takeLast(windowSize)
                    for (start in 0..(cleaned.size - windowSize)) {
                        candidates += cleaned.subList(start, start + windowSize)
                    }
                }
            }
        }

        return candidates
            .filter { tokens -> tokens.any { token -> token.length >= 2 } }
            .distinct()
    }

    private fun extractConcatenatedPair(raw: String): Pair<ParsedEntrant, ParsedEntrant>? {
        val normalized = raw.normalizeSpaces().trim()
        if (normalized.isBlank()) return null
        if (INLINE_VS_REGEX.containsMatchIn(normalized)) return null

        val markerRegex = Regex("\\(\\s*[A-Za-z0-9|]{2,6}\\s*\\)\\s*(?:\\[[^\\]]*\\])?")
        val markers = markerRegex.findAll(normalized).toList()
        if (markers.size != 2) return null

        val splitIndex = markers.first().range.last + 1
        val leftRaw = normalized.substring(0, splitIndex).trim()
        val rightRaw = normalized.substring(splitIndex).trim().trimStart('-', ':')
        if (leftRaw.isBlank() || rightRaw.isBlank()) return null

        val left = parseEntrant(leftRaw) ?: return null
        val right = parseEntrant(rightRaw) ?: return null
        if (left.name.equals(right.name, ignoreCase = true)) return null
        return sanitizeSeedConflict(left, right, leftRaw, rightRaw)
    }

    private fun bestEntrantRaw(candidates: List<OcrLine>, takeTail: Boolean): String? {
        if (candidates.isEmpty()) return null
        val primary = if (takeTail) candidates.last() else candidates.first()
        val pair = if (takeTail) candidates.takeLast(2) else candidates.take(2)
        if (pair.size < 2) return primary.text
        val verticalGap = abs(pair[1].centerY - pair[0].centerY)
        if (verticalGap > 28f) return primary.text

        val mergedRaw = pair.joinToString(" ") { it.text }.normalizeSpaces().trim()
        val merged = parseEntrant(mergedRaw)
        val single = parseEntrant(primary.text)
        val firstParsed = parseEntrant(pair[0].text)
        val secondParsed = parseEntrant(pair[1].text)
        val bothLookLikeDistinctEntrants =
            firstParsed != null &&
                secondParsed != null &&
                !firstParsed.name.equals(secondParsed.name, ignoreCase = true) &&
                firstParsed.name.split(' ').size >= 2 &&
                secondParsed.name.split(' ').size >= 2 &&
                (firstParsed.countryCode != null || hasCountryMarker(pair[0].text)) &&
                (secondParsed.countryCode != null || hasCountryMarker(pair[1].text))
        if (bothLookLikeDistinctEntrants) return primary.text

        val mergeLikelySplitEntrant =
            (hasCountryMarker(pair[0].text).xor(hasCountryMarker(pair[1].text)) && (
                single?.name?.split(' ')?.size == 1 ||
                    firstParsed?.name?.split(' ')?.size == 1 ||
                    secondParsed?.name?.split(' ')?.size == 1
                )) ||
                (single != null && single.name.split(' ').size == 1)
        return when {
            merged == null -> primary.text
            single == null -> mergedRaw
            !mergeLikelySplitEntrant -> primary.text
            entrantParseConfidence(merged, mergedRaw) >= entrantParseConfidence(single, primary.text) -> mergedRaw
            else -> primary.text
        }
    }

    private fun entrantParseConfidence(entrant: ParsedEntrant, raw: String): Int {
        val words = entrant.name.split(' ').filter { it.isNotBlank() }
        val wordQuality = words.fold(0) { acc, word ->
            acc + when {
                word.length <= 1 -> -3
                word.length in 2..18 -> 2
                else -> 1
            }
        }
        val countryQuality = when {
            entrant.countryCode != null && hasCountryMarker(raw) -> 4
            entrant.countryCode != null -> 2
            else -> 0
        }
        val seedQuality = if (entrant.seed != null && hasExplicitSeedMarker(raw)) 2 else 0
        return wordQuality + countryQuality + seedQuality
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
        if (cellLines.isEmpty()) return ScheduleContext(label = "Followed By", manualNotBefore = null, explicitKeyword = false)
        val sorted = cellLines.sortedBy { it.top }
        val scheduleBoundary = sorted.indexOfFirst { line ->
            isPotentialEntrantLine(line.text) || isVsToken(line.text)
        }
        val scheduleLines = when {
            scheduleBoundary > 0 -> sorted.take(scheduleBoundary)
            else -> sorted.take(3)
        }
        val headerJoined = scheduleLines.joinToString(" ") { it.text }
        val parsedHeader = parseScheduleContextFromText(headerJoined)
        if (parsedHeader.explicitKeyword) return parsedHeader
        val fullJoined = sorted.joinToString(" ") { it.text }
        return parseScheduleContextFromText(fullJoined)
    }

    private fun parseScheduleContextFromRow(rowLines: List<OcrLine>): ScheduleContext {
        if (rowLines.isEmpty()) return ScheduleContext(label = "Followed By", manualNotBefore = null, explicitKeyword = false)
        val sorted = rowLines.sortedBy { it.top }
        val scheduleLikeLines = sorted.filter { line ->
            containsScheduleKeyword(line.text) || extractTime(line.text) != null
        }
        val joined = if (scheduleLikeLines.isEmpty()) {
            sorted.joinToString(" ") { it.text }
        } else {
            scheduleLikeLines.joinToString(" ") { it.text }
        }
        return parseScheduleContextFromText(joined)
    }

    private fun parseScheduleContextFromText(raw: String): ScheduleContext {
        val normalized = raw.normalizeSpaces()
        val keyword = scheduleKeywordLabel(normalized)
        val label = keyword ?: "Followed By"
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
        return ScheduleContext(
            label = scheduleLabel,
            manualNotBefore = manualNotBefore,
            explicitKeyword = keyword != null,
        )
    }

    private fun groupRowLinesByColumn(
        rowLines: List<OcrLine>,
        columns: List<CourtColumnLayout>,
    ): Map<String, List<OcrLine>> {
        if (rowLines.isEmpty() || columns.isEmpty()) return emptyMap()
        val grouped = columns.associate { it.name to mutableListOf<OcrLine>() }
        rowLines.forEach { line ->
            val isEntrantLike = isPotentialEntrantLine(line.text) || isVsToken(line.text)
            val nearestByCenter = columns.minByOrNull { column ->
                abs(line.centerX - ((column.leftBoundary + column.rightBoundary) / 2f))
            }
            val bestByOverlap = columns.maxByOrNull { column -> columnOverlapWidth(line, column) }
            val overlapWidth = bestByOverlap?.let { columnOverlapWidth(line, it) } ?: 0f
            val lineWidth = (line.right - line.left).coerceAtLeast(1f)
            val overlapRatio = overlapWidth / lineWidth
            val bestColumn = when {
                !isEntrantLike -> bestByOverlap ?: nearestByCenter
                bestByOverlap != null && overlapRatio >= 0.16f -> bestByOverlap
                nearestByCenter != null &&
                    line.centerX >= nearestByCenter.leftBoundary - 18f &&
                    line.centerX <= nearestByCenter.rightBoundary + 18f -> nearestByCenter

                else -> bestByOverlap ?: nearestByCenter
            } ?: return@forEach

            if (isEntrantLike && looksLikeCrossColumnEntrantLine(line, bestColumn)) {
                return@forEach
            }
            grouped[bestColumn.name]?.add(line)
        }
        return grouped
    }

    private fun columnOverlapWidth(line: OcrLine, column: CourtColumnLayout): Float {
        val overlapLeft = maxOf(line.left, column.leftBoundary)
        val overlapRight = minOf(line.right, column.rightBoundary)
        return (overlapRight - overlapLeft).coerceAtLeast(0f)
    }

    private fun looksLikeCrossColumnEntrantLine(line: OcrLine, column: CourtColumnLayout): Boolean {
        val columnWidth = (column.rightBoundary - column.leftBoundary).coerceAtLeast(1f)
        val lineWidth = (line.right - line.left).coerceAtLeast(0f)
        if (lineWidth <= columnWidth * 1.22f) return false
        val overlapRatio = columnOverlapWidth(line, column) / lineWidth.coerceAtLeast(1f)
        if (overlapRatio >= 0.72f) return false
        if (containsScheduleKeyword(line.text)) return false
        val tokenCount = Regex("[A-Za-z']+").findAll(line.text).count()
        val markerCount = Regex("\\(\\s*[A-Za-z0-9|]{2,6}\\s*\\)").findAll(line.text).count()
        if (INLINE_VS_REGEX.containsMatchIn(line.text)) return false
        if (markerCount >= 2) return true
        if (markerCount == 1 && tokenCount >= 6 && lineWidth >= columnWidth * 1.35f) return true
        return markerCount == 0 && tokenCount >= 7 && lineWidth >= columnWidth * 1.5f
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
            memberNames = if (existing.memberNames.isNotEmpty()) existing.memberNames else incoming.memberNames,
        )
    }

    private fun buildEntrantDisplayName(name: String, seed: Int?): String {
        return if (seed != null) "$name [$seed]" else name
    }

    private fun splitEntrantIntoPlayerNames(rawName: String): List<String> {
        val normalized = rawName.normalizeSpaces()
        val pieces = Regex("(?i)\\s*(?:/|&|\\band\\b)\\s*")
            .split(normalized)
            .map { it.normalizeSpaces() }
            .filter { token -> token.isNotBlank() }
        return if (pieces.size >= 2) pieces else listOf(normalized)
    }

    private fun teamPlayerKey(name: String, countryCode: String?): String {
        return "${name.normalizeSpaces().lowercase()}|${countryCode?.trim()?.uppercase().orEmpty()}"
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

    private fun inlinePairConfidence(
        left: ParsedEntrant,
        right: ParsedEntrant,
        leftRaw: String,
        rightRaw: String,
    ): Int {
        var score = 0
        if (hasCountryMarker(leftRaw)) score += 3
        if (hasCountryMarker(rightRaw)) score += 3
        if (left.countryCode != null) score += 2
        if (right.countryCode != null) score += 2
        if (left.seed != null) score += 1
        if (right.seed != null) score += 1

        val leftWords = left.name.split(' ').size
        val rightWords = right.name.split(' ').size
        score += when (leftWords) {
            in 2..4 -> 2
            1 -> -2
            else -> 0
        }
        score += when (rightWords) {
            in 2..4 -> 2
            1 -> -2
            else -> 0
        }
        return score
    }

    private fun hasExplicitSeedMarker(raw: String): Boolean {
        if (Regex("\\[(\\d{1,2})\\]").containsMatchIn(raw)) return true
        val paren = Regex("\\((\\d{1,2})\\)").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return paren != null && paren in 1..32
    }

    private fun scheduleKeywordLabel(value: String): String? {
        val compact = value
            .lowercase()
            .replace('|', 'l')
            .replace('0', 'o')
            .replace('1', 'l')
            .replace('5', 's')
            .replace('$', 's')
            .replace(Regex("[^a-z0-9]"), "")
        return when {
            compact.contains("notbefore") ||
                compact.contains("notbefor") ||
                compact.contains("notbefo") ||
                compact.contains("notbeforc") ||
                compact.contains("notbcfore") -> "Not Before"
            compact.contains("startingat") || compact.contains("starting") -> "Starting at"
            compact.contains("followedby") ||
                compact.contains("folowedby") ||
                compact.contains("followed") ||
                compact.contains("folowed") -> "Followed By"
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

        // Do not aggressively trim by country/seed markers:
        // OCR can place "(LAT)" between first and last names, and trimming would drop one side.
        if (takeTail && value.split(' ').size > 8) {
            if (value.contains(')')) {
                val tail = value.substringAfterLast(')').trim()
                if (tail.split(' ').size in 2..5) value = tail
            }
            if (value.contains(']')) {
                val tail = value.substringAfterLast(']').trim()
                if (tail.split(' ').size in 2..5) value = tail
            }
        }

        val words = value.split(' ').filter { it.isNotBlank() }
        if (words.size > 7) {
            value = if (takeTail) {
                words.takeLast(6).joinToString(" ")
            } else {
                words.take(6).joinToString(" ")
            }
        }
        return value.normalizeSpaces().trim()
    }

    private fun detectCountryCode(raw: String): String? {
        val parenthesized = Regex("\\(\\s*([A-Za-z0-9|]{2,6})\\s*\\)")
            .findAll(raw)
            .map { it.groupValues[1] }
            .toList()
        parenthesized.firstNotNullOfOrNull { normalizeCountryCodeToken(it, allowFuzzy = true) }?.let { return it }

        val looseCandidates = Regex("\\b([A-Za-z0-9|]{2,6})\\b")
            .findAll(raw)
            .map { it.groupValues[1] }
            .filter { token -> token == token.uppercase() || token.any { it in "|01258" } }
            .toList()
        return looseCandidates.firstNotNullOfOrNull { normalizeCountryCodeToken(it, allowFuzzy = false) }
    }

    private fun detectSeed(raw: String, countryCode: String?): Int? {
        Regex("\\[(\\d{1,2})\\]").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { value ->
            if (value in 1..32) return value
        }
        Regex("\\((\\d{1,2})\\)").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { value ->
            if (value in 1..32) return value
        }
        Regex("\\[\\s*([A-Za-z0-9|]{1,4})\\s*\\]").findAll(raw)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .forEach { candidate ->
                decodeSeedToken(candidate, countryCode)?.let { return it }
            }
        Regex("\\(\\s*([A-Za-z0-9|]{1,4})\\s*\\)").findAll(raw)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .forEach { candidate ->
                val normalizedCountry = normalizeCountryCodeToken(candidate, allowFuzzy = false)
                if (normalizedCountry != null) {
                    return@forEach
                }
                decodeSeedToken(candidate, countryCode)?.let { return it }
            }
        return null
    }

    private fun decodeSeedToken(token: String, countryCode: String?): Int? {
        if (token.isBlank()) return null
        val compact = token
            .replace('[', 'I')
            .replace(']', 'I')
            .replace('|', 'I')
            .uppercase()

        if (normalizeCountryCodeToken(compact, allowFuzzy = false) != null) return null
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

    private fun normalizeCountryCodeToken(token: String, allowFuzzy: Boolean = true): String? {
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
        val resolved = when {
            direct != null -> direct
            allowFuzzy -> variants.mapNotNull { variant -> bestIso3Match(variant) }.firstOrNull()
            else -> null
        }

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
                !token.equals("AND", ignoreCase = true) &&
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
        return token.length <= 3 &&
            token == token.uppercase() &&
            token.all { it in "IiLlZzSsrR0123456789" }
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
        val memberNames = extractTeamMemberNames(raw, matchedCountry)

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
        if (memberNames.size >= 2) {
            return ParsedEntrant(
                name = memberNames.joinToString(" / "),
                countryCode = matchedCountry,
                seed = seed,
                memberNames = memberNames,
            )
        }
        if (cleaned.isBlank()) return null
        val tokens = cleaned
            .split(' ')
            .map(::normalizeNameToken)
            .filter { token ->
                token.isNotBlank() &&
                    token.any { it.isLetter() } &&
                    token.length > 1 &&
                    !token.equals("AND", ignoreCase = true) &&
                    !tokenMatchesCountry(token, matchedCountry) &&
                    !isLikelySeedArtifactToken(token)
            }
        val normalizedTokens = cleanupNameTokens(tokens)
        if (normalizedTokens.isEmpty()) return null
        if (normalizedTokens.size == 1 && matchedCountry == null) return null
        if (normalizedTokens.size == 1 && normalizedTokens.first().length < 4) return null
        val name = normalizePersonNameCase(normalizedTokens.joinToString(" ").normalizeSpaces())
        if (name.isBlank()) return null
        return ParsedEntrant(
            name = name,
            countryCode = matchedCountry,
            seed = seed,
            memberNames = emptyList(),
        )
    }

    private fun extractTeamMemberNames(raw: String, matchedCountry: String?): List<String> {
        val hasSeparator = Regex("(?i)(/|&|\\band\\b)").containsMatchIn(raw)
        if (!hasSeparator) return emptyList()

        val cleaned = raw
            .replace(Regex("\\[[^\\]]*]"), "")
            .replace(Regex("\\((\\d{1,2})\\)"), " ")
            .replace(Regex("\\(\\s*[A-Za-z0-9|]{2,6}\\s*\\)"), " ")
            .replace(Regex("(?i)\\b(?:GS|BS)\\b"), " ")
            .replace(Regex("(?i)\\b(?:starting\\s+at|followed\\s+by|not\\s+before)\\b"), " ")
            .replace(Regex("(?i)^court\\s+[A-Za-z0-9]+\\s*"), "")
            .replace(Regex("^\\d+\\.?\\s*"), "")
            .replace(Regex("\\b\\d{1,2}:\\d{2}\\b"), " ")
            .replace(Regex("\\s+\\d+[\\-:][0-9()\\-:\\s]*$"), " ")
            .replace(Regex("(^|\\s)[\\d()\\-:]+($|\\s)"), " ")
            .replace("(", " ")
            .replace(")", " ")
            .replace(".", " ")
            .normalizeSpaces()
            .trim()

        val segments = Regex("(?i)\\s*(?:/|&|\\band\\b)\\s*")
            .split(cleaned)
            .map { segment ->
                val tokens = segment
                    .split(' ')
                    .map(::normalizeNameToken)
                    .filter { token ->
                        token.isNotBlank() &&
                            token.any { it.isLetter() } &&
                            token.length > 1 &&
                            !token.equals("AND", ignoreCase = true) &&
                            !tokenMatchesCountry(token, matchedCountry) &&
                            !isLikelySeedArtifactToken(token)
                    }
                val normalized = cleanupNameTokens(tokens)
                normalizePersonNameCase(normalized.joinToString(" ").normalizeSpaces()).ifBlank { "" }
            }
            .filter { it.isNotBlank() }
            .distinct()

        return if (segments.size >= 2) segments else emptyList()
    }

    private fun tokenMatchesCountry(token: String, countryCode: String?): Boolean {
        val matched = countryCode ?: return false
        val cleaned = token.trim().replace(Regex("[^A-Za-z]"), "").uppercase()
        if (cleaned.isBlank()) return false
        val iso2 = ISO3_TO_ISO2[matched]
        if (cleaned == matched || cleaned == iso2) return true
        if (cleaned.length !in 2..4) return false
        val normalized = normalizeCountryCodeToken(cleaned, allowFuzzy = false) ?: return false
        return normalized == matched || normalized == iso2
    }

    private fun isLikelySeedArtifactToken(token: String): Boolean {
        if (token.isBlank()) return false
        if (token.any { it.isDigit() }) return true
        if (token.contains('[') || token.contains(']') || token.contains('|')) return true
        return token.length <= 2 && token.all { it in "IiLlZzSsrR" }
    }

    private fun normalizeNameToken(raw: String): String {
        return raw
            .trim()
            .trim(',', ';', ':', '.', '(', ')', '[', ']', '{', '}', '|')
            .replace('0', 'O')
            .replace('1', 'I')
            .replace('2', 'Z')
            .replace('3', 'E')
            .replace('4', 'A')
            .replace('5', 'S')
            .replace('6', 'G')
            .replace('7', 'T')
            .replace('8', 'B')
            .replace(Regex("[^A-Za-z'\\-]"), "")
    }

    private fun hasCountryMarker(raw: String): Boolean {
        return Regex("\\(\\s*[A-Za-z0-9|]{2,6}\\s*\\)").containsMatchIn(raw)
    }

    private fun findScheduleContext(lines: List<String>, aroundIndex: Int): ScheduleContext {
        val lowerBound = (aroundIndex - 20).coerceAtLeast(0)
        var candidateLabel = "Followed By"
        var candidateTime: String? = null
        var explicitKeyword = false
        for (index in aroundIndex downTo lowerBound) {
            val line = lines[index]
            when {
                line.contains("Not Before", ignoreCase = true) -> {
                    candidateLabel = "Not Before"
                    candidateTime = extractTime(line) ?: findNeighbourTime(lines, index)
                    explicitKeyword = true
                    break
                }

                line.contains("Starting at", ignoreCase = true) -> {
                    candidateLabel = "Starting at"
                    candidateTime = extractTime(line) ?: findNeighbourTime(lines, index)
                    explicitKeyword = true
                    break
                }

                line.contains("Followed By", ignoreCase = true) -> {
                    candidateLabel = "Followed By"
                    explicitKeyword = true
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
        return ScheduleContext(label = label, manualNotBefore = manual, explicitKeyword = explicitKeyword)
    }

    private fun findNeighbourTime(lines: List<String>, index: Int): String? {
        val window = (index - 2).coerceAtLeast(0)..(index + 2).coerceAtMost(lines.lastIndex)
        return window.firstNotNullOfOrNull { candidate ->
            extractTime(lines[candidate])
        }
    }

    private fun extractTime(value: String): String? {
        val normalized = normalizeOcrTimeText(value)
        Regex("\\b([0-2]?\\d)\\s*[:.]\\s*([0-5]\\d)\\b")
            .find(normalized)
            ?.let { match ->
                val hour = match.groupValues[1].toIntOrNull() ?: return@let
                val minute = match.groupValues[2].toIntOrNull() ?: return@let
                if (hour in 0..23 && minute in 0..59) {
                    return "%02d:%02d".format(hour, minute)
                }
            }
        Regex("\\b([0-2]\\d)([0-5]\\d)\\b")
            .find(normalized)
            ?.let { match ->
                val hour = match.groupValues[1].toIntOrNull() ?: return@let
                val minute = match.groupValues[2].toIntOrNull() ?: return@let
                if (hour in 0..23 && minute in 0..59) {
                    return "%02d:%02d".format(hour, minute)
                }
            }
        return null
    }

    private fun normalizeOcrTimeText(value: String): String {
        return value
            .replace('O', '0')
            .replace('o', '0')
            .replace('|', '1')
            .replace('I', '1')
            .replace('l', '1')
            .replace(Regex("\\s+"), " ")
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

    private data class DrawNodeWithMeta(
        val eventCode: String,
        val depth: Int,
        val order: Int,
        val courtSort: Int,
        val node: DrawMatchNode,
    )

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
        val memberNames: List<String> = emptyList(),
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
        val explicitKeyword: Boolean,
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

    private data class InlinePairCandidate(
        val left: ParsedEntrant,
        val right: ParsedEntrant,
        val confidence: Int,
    ) {
        val wordCount: Int get() = left.name.split(' ').size + right.name.split(' ').size
    }

    private data class ParsedEntrantLine(
        val centerY: Float,
        val raw: String,
        val entrant: ParsedEntrant,
    )

    private data class StructuredEntrantCandidate(
        val entrant: ParsedEntrant,
        val centerY: Float,
        val confidence: Int,
        val sourceText: String,
        val nameKey: String,
    )

    private data class CourtPlacement(
        val courtIndex: Int,
        val order: Int,
    )

    private data class ImportedOrderMatch(
        val courtName: String,
        val order: Int,
        val left: ParsedEntrant?,
        val right: ParsedEntrant?,
        val scheduleLabel: String,
        val manualNotBefore: Long?,
        val eventCode: String? = null,
        val leftWinnerOf: ImportedMatchRef? = null,
        val rightWinnerOf: ImportedMatchRef? = null,
        val leftIsBye: Boolean = false,
        val rightIsBye: Boolean = false,
    )

    private data class ImportedMatchRef(
        val courtName: String,
        val order: Int,
    )

    private data class ImportedOrderDocument(
        val tournamentName: String,
        val courts: List<String>,
        val matches: List<ImportedOrderMatch>,
    )

    private data class ReloadSource(
        val type: String,
        val value: String?,
    )

    private companion object {
        const val DEFAULT_PRELOADED_ID = "example"
        const val DEFAULT_REST_MINUTES = 30
        const val REST_PROMPT_MIN_MATCH_MINUTES = 60
        const val ROW_RECOVERY_MARGIN = 20f
        const val PDF_OCR_MAX_PAGES = 2
        const val PDF_OCR_RENDER_SCALE = 2.3f
        const val PDF_OCR_MAX_DIMENSION = 3200
        const val RELOAD_PREFS_NAME = "order_of_play_reload_source"
        const val KEY_RELOAD_SOURCE_TYPE = "type"
        const val KEY_RELOAD_SOURCE_VALUE = "value"
        const val RELOAD_SOURCE_SEED = "seed"
        const val RELOAD_SOURCE_FILE = "file"
        const val RELOAD_SOURCE_PRELOADED = "preloaded"
        val INLINE_VS_REGEX = Regex("(?i)\\b(?:v\\.?[s5$]\\.?|vs\\.?|v\\.)\\b")
        val ISO3_TO_ISO2 = CountryFlags.iso3ToIso2Map
        val ISO2_CODES = CountryFlags.iso2Codes
        val OCR_NOISE_TOKENS = setOf("hii", "hll", "iii", "ii", "iz", "zi", "rs", "sr", "ir", "lr", "la")
    }
}

private fun <T, R> Flow<List<T>>.mapList(transform: (T) -> R): Flow<List<R>> =
    map { list -> list.map(transform) }

private fun String.normalizeSpaces(): String = trim().replace(Regex("\\s+"), " ")
