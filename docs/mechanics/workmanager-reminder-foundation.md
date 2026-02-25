# WorkManager Reminder Foundation

## What is implemented
- A `ReminderScheduler` abstraction.
- A WorkManager-backed scheduler implementation.
- A `NotBeforeReminderWorker` with match/court payload input.

## Current scope
This pass wires scheduling/cancellation infrastructure only. User-visible notification behavior is intentionally deferred.

## Main files
- `app/src/main/java/com/klim/tennisrefapp/domain/repository/ReminderScheduler.kt`
- `app/src/main/java/com/klim/tennisrefapp/data/work/WorkManagerReminderScheduler.kt`
- `app/src/main/java/com/klim/tennisrefapp/data/work/NotBeforeReminderWorker.kt`
