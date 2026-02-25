# Timer, Violations, Score, and Court Actions

## Timer (5:00)
- Court detail now has a live timer button.
- Milestones trigger at 3:00, 4:00, and 5:00 with:
  - vibration (1x, 1x, 2x)
  - local notification naming the court
  - `EventLog` entries with `TIMER` type

## Violations
- In-progress matches can open a violation dialog.
- Referee selects offender and violation type, then applies.
- Ladder guidance is computed and shown in snackbar text.
- Undo removes the latest violation event.
- Per-player counts are shown on court detail.

## Final score / match end
- In-progress matches can submit final score with:
  - winner side (left/right)
  - result type (`NORMAL`, `RETIREMENT`, `WALKOVER`, `DEFAULT`)
  - score text
- Submitting score records `endedAt`, sets winner, marks match completed, and logs `MATCH_ENDED`.

## Court assignment actions
- Courts list now exposes quick actions:
  - assign first unassigned scheduled match
  - swap with next court
  - clear current court assignment
- Court detail supports moving match to another court or unassigned.
