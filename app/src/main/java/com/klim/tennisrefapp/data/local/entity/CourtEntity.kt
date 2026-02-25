package com.klim.tennisrefapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "courts",
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
data class CourtEntity(
    @PrimaryKey val id: Long,
    val tournamentId: Long,
    val name: String,
    val sortOrder: Int,
    val isActive: Boolean = true,
)
