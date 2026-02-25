# Courts List and Court Detail UI

## What it does
- Shows all active courts in a vertical list.
- Each court card shows:
  - court name
  - current match label
  - status (`Empty`, `Scheduled`, `In progress`, `Completed`)
  - computed not-before time when available
- Tapping a card opens the court detail screen.

## Court detail behavior
- Renders left and right sides separately.
- Supports a one-tap `Swap sides` action for scheduled matches.
- Lets referee tap a player name to select initial server.
- Shows disabled placeholders for in-progress actions (`Timer`, `Violations`, `Score`) once the match is started.

## Main files
- `app/src/main/java/com/klim/tennisrefapp/ui/courts/CourtsScreen.kt`
- `app/src/main/java/com/klim/tennisrefapp/ui/courts/CourtsViewModel.kt`
- `app/src/main/java/com/klim/tennisrefapp/ui/courtdetail/CourtDetailScreen.kt`
- `app/src/main/java/com/klim/tennisrefapp/ui/courtdetail/CourtDetailViewModel.kt`
