# Persistent Court Timer Alerts

## Goal
Keep referee timers reliable when navigating away from a court or when the app is backgrounded.

## How It Works
- Starting a timer:
  - stores timer state in local shared preferences,
  - starts in-app countdown updates for current UI,
  - schedules a `WorkManager` one-time job for timer completion,
  - schedules `AlarmManager` warning/finish alarms for screen-off reliability.
- Stopping/restarting a timer:
  - cancels the scheduled completion job,
  - removes persisted timer state,
  - logs a timer stop event.

## Completion Path
- `CourtTimerFinishedWorker` runs at timer expiry and:
  - posts a high-priority local notification,
  - vibrates device,
  - logs `TIMER|<TYPE>|DONE` event,
  - clears persisted timer entry.
- For `WARMUP`, worker also attempts automatic match start using the selected initial server.

## Recovery Behavior
- On app process restart, active persisted timers are restored:
  - active timers resume countdown in UI,
  - already expired timers are immediately completed and logged.

## Notes
- Warm-up timer uses a stronger vibration pattern.
- Android 13+ notification permission is requested from `MainActivity`.
- Android 12+ exact alarm permission is requested for precise background alarms.
- Android 6+ battery optimization exception is requested to reduce delayed alerts when device is idle.
