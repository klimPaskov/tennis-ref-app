# Event Log Storage

## What is logged
- Match start/end events
- Violation events
- Timer milestone events

## Data shape
Each row stores:
- optional `matchId`
- optional `courtId`
- timestamp
- event type
- optional offender player id
- free-text message

## Current usage
- Seed data inserts example violation and timer events.
- Start match operation appends a `MATCH_STARTED` event.

## Main files
- `app/src/main/java/com/klim/tennisrefapp/data/local/entity/EventLogEntity.kt`
- `app/src/main/java/com/klim/tennisrefapp/data/local/dao/EventLogDao.kt`
- `app/src/main/java/com/klim/tennisrefapp/data/repository/AppRepository.kt`
