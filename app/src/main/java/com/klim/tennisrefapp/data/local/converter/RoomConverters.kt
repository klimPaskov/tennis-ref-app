package com.klim.tennisrefapp.data.local.converter

import androidx.room.TypeConverter
import com.klim.tennisrefapp.data.local.entity.EventType
import com.klim.tennisrefapp.data.local.entity.MatchStatus
import com.klim.tennisrefapp.data.local.entity.SlotPosition
import com.klim.tennisrefapp.data.local.entity.SlotType

class RoomConverters {
    @TypeConverter
    fun fromMatchStatus(value: MatchStatus): String = value.name

    @TypeConverter
    fun toMatchStatus(value: String): MatchStatus = MatchStatus.valueOf(value)

    @TypeConverter
    fun fromSlotType(value: SlotType): String = value.name

    @TypeConverter
    fun toSlotType(value: String): SlotType = SlotType.valueOf(value)

    @TypeConverter
    fun fromSlotPosition(value: SlotPosition): String = value.name

    @TypeConverter
    fun toSlotPosition(value: String): SlotPosition = SlotPosition.valueOf(value)

    @TypeConverter
    fun fromEventType(value: EventType): String = value.name

    @TypeConverter
    fun toEventType(value: String): EventType = EventType.valueOf(value)
}
