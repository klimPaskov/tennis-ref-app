# Timer, Violations, Score, and Court Actions

## Timer
- In-progress view keeps a compact timer control.
- The UI no longer defaults to showing a `5:00` warm-up label in match-progress context.

## Violations
- Violations open from a small icon button.
- Violation counts are shown only inside the violation dialog.
- Counts are displayed only for players that already have at least one recorded violation.
- Undo removes the latest violation event.
- If there is nothing to undo, feedback is shown as a short snackbar (no persistent undo action).

## Final score / match end
- Winner is selected by participant name (not left/right labels).
- Result type is automatic:
  - empty score -> `WALKOVER`
  - incomplete score (for example `4:3`) -> `RETIREMENT`
  - complete score -> `NORMAL`
- Submitting score records `endedAt`, sets winner, marks match completed, and logs `MATCH_ENDED`.

## Court assignment actions
- Court detail supports moving match to another court or unassigned.
- Court detail supports swapping matches between courts.
