# Fake Seed Data

## Purpose
Populate a usable tournament on first app launch without import/manual setup.

## Seed contents
- 1 tournament
- 3 courts
- 12 players (single-player teams)
- 12 teams
- 6 matches:
  - 3 first matches marked `COMPLETED`
  - 3 follow-up matches marked `SCHEDULED` and not started
- slots including direct, winner-of-match dependency, and bye
- event logs for match start/end on completed matches

## Trigger
`seedIfEmpty()` runs from the courts screen view model; it inserts only if no tournament exists.

## Assumption
The sample seeding is based on `Order of Play.pdf` participant names and a simplified schedule.  
Only first matches are completed, while the next matches are left as not started.

## Main files
- `app/src/main/java/com/klim/tennisrefapp/data/repository/AppRepository.kt`
- `app/src/main/java/com/klim/tennisrefapp/ui/courts/CourtsViewModel.kt`
