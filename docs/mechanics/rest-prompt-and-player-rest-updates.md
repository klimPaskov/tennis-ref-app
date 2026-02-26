# Rest Prompt and Player Rest Updates

## Automatic Rest Prompt
- After final score submission, the app checks:
  - match duration (threshold: `60` minutes),
  - whether each player has an upcoming scheduled/in-progress match.
- If both conditions are met, a rest dialog appears with editable minutes per eligible player.

## Manual Rest Update
- Player detail includes `Rest Minutes` edit + save only when the player is eligible:
  - has an upcoming match, and
  - has at least one completed match meeting rest threshold rules.
- Updating rest recalculates not-before times across the schedule immediately.

## Why It Matters
- Not-before uses per-player rest and last completed match end time.
- Referees can capture requested rest directly after long matches or adjust later from players view.
