# Match Start and Side Locking

## What it does
- `Start Match` is available only for scheduled matches with resolved participants.
- Referee must select an initial server before starting.
- On start:
  - `startedAt` is set
  - match status changes to `IN_PROGRESS`
  - current left/right resolved teams are copied to `initialLeftTeamId` and `initialRightTeamId`
  - `initialServerPlayerId` is stored
  - an event log row is created (`MATCH_STARTED`)

## Why it matters
This preserves a stable start-state snapshot even if UI orientation changes later.

## Main files
- `app/src/main/java/com/klim/tennisrefapp/domain/repository/MatchRepository.kt`
- `app/src/main/java/com/klim/tennisrefapp/data/repository/AppRepository.kt`
- `app/src/main/java/com/klim/tennisrefapp/ui/courtdetail/CourtDetailViewModel.kt`
