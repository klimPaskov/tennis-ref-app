# Timer, Violations, Score, and Court Actions

## Timer
- Warm-up timer starts automatically once initial server is selected on a scheduled match.
- Match can auto-start when warm-up reaches 0, or be manually started with `Start Match Now`.
- In-progress timer control opens referee timing presets:
  - side switch
  - set break
  - toilet break
  - off-court allowance
  - medical timeout
  - equipment fix
- Active warm-up and in-progress timers stay running when leaving court detail and are visible on the courts list card.
- Timer start/stop/finish actions are logged as `EventLog` rows with `TIMER` type.
- In-progress matches show a live elapsed stopwatch (from `startedAt` until completion).

## Violations
- Violations open from a small icon button.
- Violation counts are shown only inside the violation dialog.
- Counts are displayed only for players that already have at least one recorded violation.
- Violations are selected by violation name only, then confirmed in a second dialog that explains:
  - current offence number
  - penalty applied now
  - next-step penalty guidance
- Undo removes the latest violation event.
- If there is nothing to undo, feedback is shown as a short snackbar (no persistent undo action).

## Final score / match end
- Winner is selected by participant name (not left/right labels).
- Result type is automatic:
  - empty score -> `WALKOVER`
  - incomplete score (for example `4:3`) -> `RETIREMENT`
  - complete score -> `NORMAL`
- Submitting score records `endedAt`, sets winner, marks match completed, and logs `MATCH_ENDED`.
- Completed match header shows played duration from `startedAt` and `endedAt`.

## Court assignment actions
- Side swap in court detail is available before match start to correct left/right orientation.
