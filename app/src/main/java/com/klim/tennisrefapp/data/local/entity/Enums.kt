package com.klim.tennisrefapp.data.local.entity

enum class MatchStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
}

enum class SlotType {
    DIRECT_TEAM,
    WINNER_OF_MATCH,
    BYE,
}

enum class SlotPosition {
    LEFT,
    RIGHT,
}

enum class EventType {
    MATCH_STARTED,
    MATCH_ENDED,
    VIOLATION,
    TIMER,
}

enum class MatchResultTypeEntity {
    NORMAL,
    RETIREMENT,
    WALKOVER,
    DEFAULT,
}
