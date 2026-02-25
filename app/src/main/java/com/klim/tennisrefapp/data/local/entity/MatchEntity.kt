package com.klim.tennisrefapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "matches",
    foreignKeys = [
        ForeignKey(
            entity = TournamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["tournamentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CourtEntity::class,
            parentColumns = ["id"],
            childColumns = ["courtId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("tournamentId"), Index("courtId")],
)
data class MatchEntity(
    @PrimaryKey val id: Long,
    val tournamentId: Long,
    val courtId: Long?,
    val roundName: String?,
    val status: MatchStatus,
    val manualNotBefore: Long?,
    val computedNotBefore: Long?,
    val startedAt: Long?,
    val endedAt: Long?,
    val winnerTeamId: Long?,
    val initialLeftTeamId: Long?,
    val initialRightTeamId: Long?,
    val initialServerPlayerId: Long?,
)
