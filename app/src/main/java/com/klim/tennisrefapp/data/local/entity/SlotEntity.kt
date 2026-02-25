package com.klim.tennisrefapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "slots",
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["matchId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TeamEntity::class,
            parentColumns = ["id"],
            childColumns = ["directTeamId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("matchId"), Index("directTeamId"), Index("winnerOfMatchId")],
)
data class SlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val position: SlotPosition,
    val slotType: SlotType,
    val directTeamId: Long?,
    val winnerOfMatchId: Long?,
)
