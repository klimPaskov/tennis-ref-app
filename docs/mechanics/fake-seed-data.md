# Fake Seed Data

## Purpose
Populate a usable tournament on first app launch without import/manual setup.

## Seed contents
- 1 tournament
- 3 courts
- 6 players
- 5 teams (including one doubles team)
- 4 matches across statuses (`COMPLETED`, `SCHEDULED`, `IN_PROGRESS`)
- slots including direct, winner-of-match dependency, and bye
- event logs for start/violation/end and timer milestones

## Trigger
`seedIfEmpty()` runs from the courts screen view model; it inserts only if no tournament exists.

## Main files
- `app/src/main/java/com/klim/tennisrefapp/data/repository/AppRepository.kt`
- `app/src/main/java/com/klim/tennisrefapp/ui/courts/CourtsViewModel.kt`
