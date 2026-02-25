package com.klim.tennisrefapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tournaments")
data class TournamentEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val defaultRestMinutes: Int,
    val createdAt: Long,
)
