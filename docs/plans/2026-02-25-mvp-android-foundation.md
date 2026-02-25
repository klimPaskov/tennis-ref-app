# MVP Android Foundation Implementation Plan

**Goal:** Deliver a runnable first-pass Android MVP for tennis referees with local persistence, core domain rules, and initial Compose screens.

**Description:**
Build a new Android app in this repository using Kotlin, Jetpack Compose, Room, Hilt, and WorkManager. Implement the minimal but cohesive vertical slice: schema for tournament/court/match primitives and logs, domain logic for slot resolution, not-before computation, and violation guidance, and UI for courts list + court detail start flow with seeded fake data. Keep assumptions explicit in README.

**Definition of done:**
- Project builds with Gradle Kotlin DSL and produces debug APK.
- Unit tests cover domain logic for slot resolution, not-before computation, and violation ladder.
- Room entities/DAOs/repositories exist for Tournament, Court, Player, Team, Match, Slot, EventLog.
- Court list and court detail screens work from seeded data, including side swap, initial server selection, and start-match persistence.
- README documents run steps, DB path, and high-level not-before logic plus assumptions.

**Scope (in):**
- Android app scaffolding and dependency setup.
- Local Room schema + repositories + fake seed.
- Pure Kotlin domain services + tests.
- Compose UI for courts list and court detail MVP interactions.
- WorkManager dependency integration (no full notification flow yet).

**Scope (out):**
- Full scoring flow and violation/timer execution UX.
- Import/export flows.
- Bracket auto-advancement orchestration beyond slot resolution utility.
- Notification and foreground service runtime behavior.

**References checked:**
- `/home/klim/tennis-ref-app/SPEC.md`
- `/home/klim/tennis-ref-app/README.md`
- `/home/klim/.codex/skills/writing-plans/SKILL.md`

**Files to touch:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/**`
- Create: `docs/plans/2026-02-25-mvp-android-foundation.md`
- Modify: `README.md`

---

### Task 1: Scaffold Android project and baseline configuration

**Intent:** Establish a clean Android app foundation with required libraries and test setup.

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/**`
- Create: `app/src/main/res/**`

**Steps:**
1. Add top-level Gradle Kotlin DSL files and plugin/dependency management.
2. Configure app module for Compose, Hilt, Room, WorkManager, and test dependencies.
3. Add Application class, Hilt setup, and baseline activity/navigation host.

**Notes:**
- Keep minSdk/targetSdk on modern supported defaults.
- Keep architecture packages explicit: `data`, `domain`, `ui`, `di`.

### Task 2: Implement Room data layer and repository interfaces

**Intent:** Persist tournament/court/match model and event logs with DAOs and repository implementations.

**Files:**
- Create: `app/src/main/java/**/data/local/entity/*.kt`
- Create: `app/src/main/java/**/data/local/dao/*.kt`
- Create: `app/src/main/java/**/data/local/AppDatabase.kt`
- Create: `app/src/main/java/**/data/repository/*.kt`
- Create: `app/src/main/java/**/domain/repository/*.kt`
- Create: `app/src/main/java/**/data/seed/*.kt`

**Steps:**
1. Define entities and enums for required tables, including slot type variants.
2. Create DAOs for CRUD and key UI query shapes.
3. Add repository contracts and Room-backed implementations.
4. Add fake data seeding on first app launch.

**Notes:**
- Store `startedAt`, `endedAt`, and violation/timer events in `EventLog`.
- Keep slot representation explicit for `directTeam`, `winnerOfMatch`, and `bye`.

### Task 3: Implement pure domain rules with tests

**Intent:** Provide deterministic business logic separated from Android framework concerns.

**Files:**
- Create: `app/src/main/java/**/domain/model/*.kt`
- Create: `app/src/main/java/**/domain/usecase/*.kt`
- Create: `app/src/test/java/**/domain/*.kt`

**Steps:**
1. Implement slot resolution for winner dependencies.
2. Implement not-before computation with manual and rest constraints.
3. Implement violation ladder counting and next-step guidance.
4. Add unit tests for edge cases and common scenarios.

**Notes:**
- Keep functions side-effect free and easy to test.
- Prefer immutable inputs/outputs.

### Task 4: Build MVP Compose UI for courts list and court detail

**Intent:** Deliver interactive referee-first flows with clear status and start controls.

**Files:**
- Create: `app/src/main/java/**/ui/navigation/*.kt`
- Create: `app/src/main/java/**/ui/courts/*.kt`
- Create: `app/src/main/java/**/ui/courtdetail/*.kt`
- Create: `app/src/main/java/**/ui/components/*.kt`

**Steps:**
1. Implement courts list screen with status/current match/computed not-before.
2. Implement court detail two-sided layout, swap sides, server selection.
3. Implement start-match action to persist `startedAt`, lock orientation and first server.
4. Render in-progress controls as disabled placeholders.

**Notes:**
- Use large touch targets and clear labels.
- Prevent starting without initial server.

### Task 5: Documentation, build verification, and cleanup

**Intent:** Ensure reproducibility and communicate assumptions.

**Files:**
- Modify: `README.md`

**Steps:**
1. Document setup/run instructions and DB location.
2. Document high-level not-before computation and explicit assumptions.
3. Run unit tests and assemble debug build; fix issues.
4. Commit in small logical chunks with conventional prefixes.

**Notes:**
- If local Android SDK is missing, capture exact blocker and workaround.
