# Fake Seed Data

## Purpose
Populate a usable tournament on first app launch without import/manual setup.

## Seed contents
- 1 tournament
- 7 courts (`Court 2` .. `Court 8`)
- players/teams generated from all seeded order-of-play entries
- 28 matches (4 per court):
  - match 1 on each court is `COMPLETED`
  - matches 2-4 are `SCHEDULED` and not started
- direct-team slots for each side
- event logs for match start/end on completed matches

## Trigger
`seedIfEmpty()` runs from the courts screen view model; it inserts only if no tournament exists.

## Assumption
The sample seeding is based on `Order of Play.pdf` participant names and court order.  
For usability in MVP, each court keeps exactly one completed first match and queued scheduled matches.

## Main files
- `app/src/main/java/com/klim/tennisrefapp/data/repository/AppRepository.kt`
- `app/src/main/java/com/klim/tennisrefapp/ui/courts/CourtsViewModel.kt`
