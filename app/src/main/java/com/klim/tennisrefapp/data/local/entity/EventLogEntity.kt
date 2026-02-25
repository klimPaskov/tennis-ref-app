package com.klim.tennisrefapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "event_logs",
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["matchId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CourtEntity::class,
            parentColumns = ["id"],
            childColumns = ["courtId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = PlayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["offenderPlayerId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("matchId"), Index("courtId"), Index("offenderPlayerId"), Index("timestamp")],
)
data class EventLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long?,
    val courtId: Long?,
    val timestamp: Long,
    val eventType: EventType,
    val offenderPlayerId: Long?,
    val message: String?,
)
