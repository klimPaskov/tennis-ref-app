package com.klim.tennisrefapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.klim.tennisrefapp.data.local.converter.RoomConverters
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
import com.klim.tennisrefapp.data.local.entity.MatchEntity
import com.klim.tennisrefapp.data.local.entity.PlayerEntity
import com.klim.tennisrefapp.data.local.entity.SlotEntity
import com.klim.tennisrefapp.data.local.entity.TeamEntity
import com.klim.tennisrefapp.data.local.entity.TeamPlayerCrossRefEntity
import com.klim.tennisrefapp.data.local.entity.TournamentEntity

@Database(
    entities = [
        TournamentEntity::class,
        CourtEntity::class,
        PlayerEntity::class,
        TeamEntity::class,
        TeamPlayerCrossRefEntity::class,
        MatchEntity::class,
        SlotEntity::class,
        EventLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tournamentDao(): TournamentDao
    abstract fun courtDao(): CourtDao
    abstract fun playerDao(): PlayerDao
    abstract fun teamDao(): TeamDao
    abstract fun teamPlayerDao(): TeamPlayerDao
    abstract fun matchDao(): MatchDao
    abstract fun slotDao(): SlotDao
    abstract fun eventLogDao(): EventLogDao
}
