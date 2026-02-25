# Tennis Referee App (MVP)

Android MVP for court referees built with Kotlin, Jetpack Compose, Room, Hilt, and WorkManager.

## Implemented in this pass
- New Android Studio/Gradle Kotlin DSL project in this repository.
- Room entities/DAOs/repositories for `Tournament`, `Court`, `Player`, `Team`, `Match`, `Slot`, and `EventLog`.
- Slot modeling supports:
  - direct team
  - winner of another match
  - bye
- Match start data model includes `startedAt` and `endedAt`.
- Violations and timer milestones are stored as `EventLog` rows.
- Pure Kotlin domain rules with unit tests for:
  - slot resolution
  - not-before computation
  - violation ladder guidance
- Compose UI:
  - Courts list with status, match label, and computed not-before
  - Court detail with left/right layout, side swap, initial server selection, and Start Match
  - In-progress action buttons (`Timer`, `Violations`, `Score`) shown as disabled placeholders
- Fake seed data so the app is usable immediately.

## How to run
1. Open the repo in Android Studio.
2. Ensure Android SDK 34 and JDK 17 are installed.
3. Sync Gradle and run the `app` configuration on an emulator/device.

CLI equivalents:
```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Database location
Room database file name: `tennis_ref.db`

On device/emulator, the database is at:
`/data/data/com.klim.tennisrefapp/databases/tennis_ref.db`

## Not-before computation (high level)
For a match, `computedNotBefore` is calculated as:
- `manualNotBefore` (if present)
- and each known player's rest-based earliest start:
  - `lastEndedAt(player) + restMinutes(player)`

Final value is the maximum of those constraints.
For doubles, both players contribute constraints.
For TBD slots, only currently known participants are used until dependencies resolve.

## Assumptions for ambiguous parts
- Court card "current match" priority is: `In progress` > `Scheduled` > `Completed`.
- A match can start only when both sides resolve to concrete teams (BYE does not start a live match in this MVP).
- Initial server must be one of the visible players and must be selected before `Start Match`.
- WorkManager integration is included (worker + scheduler), but actual user-facing reminder notifications are deferred to the next pass.
- Fake seed includes example violation and timer `EventLog` entries for immediate UI/testing context.
