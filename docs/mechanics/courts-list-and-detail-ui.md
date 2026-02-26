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
- Renders two equal side panels with a compact swap icon between them.
- Uses larger participant rows for readability.
- Lets referee tap an existing participant row to select initial server (row highlight indicates selected server).
- Removes duplicate left/right labels in participant content.
- In-progress layout uses compact icon controls for violations/undo and a full-width score action.

## Main files
- `app/src/main/java/com/klim/tennisrefapp/ui/courts/CourtsScreen.kt`
- `app/src/main/java/com/klim/tennisrefapp/ui/courts/CourtsViewModel.kt`
- `app/src/main/java/com/klim/tennisrefapp/ui/courtdetail/CourtDetailScreen.kt`
- `app/src/main/java/com/klim/tennisrefapp/ui/courtdetail/CourtDetailViewModel.kt`
