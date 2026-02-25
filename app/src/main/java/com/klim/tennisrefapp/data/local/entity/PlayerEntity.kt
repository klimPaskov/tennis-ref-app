package com.klim.tennisrefapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "players",
    foreignKeys = [
        ForeignKey(
            entity = TournamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["tournamentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tournamentId")],
)
data class PlayerEntity(
    @PrimaryKey val id: Long,
    val tournamentId: Long,
    val displayName: String,
    val restMinutes: Int?,
)
