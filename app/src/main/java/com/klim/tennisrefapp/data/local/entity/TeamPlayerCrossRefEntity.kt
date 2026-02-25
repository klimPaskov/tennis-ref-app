package com.klim.tennisrefapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "team_player_cross_ref",
    primaryKeys = ["teamId", "playerId"],
    foreignKeys = [
        ForeignKey(
            entity = TeamEntity::class,
            parentColumns = ["id"],
            childColumns = ["teamId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["playerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playerId")],
)
data class TeamPlayerCrossRefEntity(
    val teamId: Long,
    val playerId: Long,
    val playerOrder: Int,
)
