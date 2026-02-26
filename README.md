# Tennis Referee App

Tennis Referee App is an Android app for on-court tournament refereeing.  
It is designed for fast, low-friction match control: view courts, open a court, confirm first server, and start matches with clear state tracking.

## What This App Does
- Starts from a Home screen to continue or reload the order of play.
- Shows all courts with current match and status.
- Supports unresolved draw slots (`Winner of Match X`, `TBD`, `BYE`).
- Lets a referee set sides, choose initial server, and start a match.
- Tracks match timeline data (`startedAt`, `endedAt`) and event history.
- Computes “not before” time using rest constraints and optional manual limits.
- Shows a History view (completed matches by court) and per-player match insights.

## Quick Start
### Prerequisites
- Android Studio (latest stable)
- JDK 17
- Android SDK 34

### Run in Android Studio
1. Open this repository.
2. Let Gradle sync complete.
3. Run the `app` configuration on an emulator or device.

### Run from CLI
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## First Launch
The app seeds a small sample tournament automatically on first run, so you can explore screens immediately without importing data.

Seed assumptions for this sample:
- Player names are taken from `Order of Play.pdf`.
- Courts `2` to `8` are seeded from the order-of-play day.
- Match `#1` on each court is marked completed.
- The following matches are scheduled and not started.
- Player nationality is stored as an abbreviation (for example, `EST`, `FIN`) and shown in UI with a flag emoji.

## Data Storage
- Local Room database file: `tennis_ref.db`
- Device path: `/data/data/com.klim.tennisrefapp/databases/tennis_ref.db`

## Core Rule: Not-Before Time
For each match, the app computes the earliest valid start as the maximum of:
- `manualNotBefore` (if set), and
- each known player's `lastEndedAt + restMinutes`.

For doubles, both players constrain availability. For unresolved slots, only known participants are considered until dependencies resolve.
`restMinutes` can be updated per player and recalculates not-before times dynamically.

## Recording a Match Result
- Pick the winner by player/team name.
- Enter score text if available (optional).
- Result type is inferred automatically:
  - no score -> Walkover
  - incomplete score (for example `4:3`) -> Retirement
  - complete score -> Normal
- If match duration is at least `60` minutes and a player has an upcoming match, the app prompts for requested rest minutes.
- Rest can also be edited later from player detail.

## Match Timing
- Warm-up starts automatically after the initial server is selected.
- Match can start automatically when warm-up ends, or manually with `Start Match Now`.
- Warming-up courts are pushed to the top of the courts list and show live warm-up remaining time.
- In-progress timer menu includes referee timing presets:
  - side switch
  - set break
  - toilet break
  - off-court allowance
  - medical timeout
  - equipment fix
- Timer completion can alert by vibration + notification, including when the app is in background.

## Court Controls
- `Swap` (between left/right player panels) swaps sides before match start.
- New order-of-play action opens a file picker, then refreshes current loaded schedule.

## Project Docs
- Contributor guide: `AGENTS.md`
- Mechanics details: `docs/mechanics/`
