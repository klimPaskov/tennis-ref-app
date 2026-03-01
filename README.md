# Tennis Referee App

Tennis Referee App is an Android app for running tennis matches on court.  
It helps a referee manage courts, start matches, track events, and review history.

## What You Can Do
- Load an order of play from file.
- Open any court and run the match flow.
- Track warm-up and in-match timers.
- Record violations, score updates, and final results.
- View player list, match history, and draw progress.

## Main Screens
- `Home`: start from file import, presets, or continue to courts.
- `Courts`: all courts with status (scheduled, in progress, completed).
- `Court Detail`: server selection, start match, timers, score, violations.
- `Players`: player search and basic player match info.
- `History`: completed matches and event logs.
- `Draws`: bracket view that updates from match results.

## Quick Start
### Requirements
- Android Studio (latest stable)
- JDK 17
- Android SDK 34

### Run
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Start a Tournament
1. On `Home`, choose `New Order Of Play` to import a file.
2. Or choose `Load Preloaded Order Of Play`.
3. You can save the current tournament as a preset with `Save Current As Preset`.

## Supported Import Files
- Images (`png`, `jpg`, `jpeg`, `webp`)
- `PDF`
- `text/plain`

## Not-Before Time (Simple)
Each match can have a planned start limit (`Not Before HH:mm`).  
The app uses that together with player-availability constraints and picks the earliest valid start time.

## Data Storage
- Room database name: `tennis_ref.db`
- Android path: `/data/data/com.klim.tennisrefapp/databases/tennis_ref.db`
