# Home, History, and Player Insights

## Home Screen
- Entry point of the app.
- Actions:
  - `Courts`: opens courts.
  - `New Order Of Play`: reloads seeded schedule from the sample order-of-play set.
  - `Players`: opens player list.
  - `History`: opens completed-match history grouped by court.

## History Screens
- `History` shows finished matches grouped by court.
- Tapping a match opens `Match Log`, which shows:
  - match label,
  - score/result label,
  - ordered event timeline (start/end/violations/timers).

## Player Insights
- `Players` list shows:
  - player name + nationality flag,
  - completed match count,
  - next match summary (court + time if known).
- `Player Detail` shows:
  - next upcoming match (if any),
  - completed matches with score and end time.

## Data Source
- All screens read from Room through repository projections:
  - completed matches by court,
  - match history detail with events,
  - per-player completed and next match snapshots.
