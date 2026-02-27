# Violation Ladder Guidance

## Categories
- Code violations
- Time violation (server)
- Time violation (receiver)

## Ladder logic
- Code:
  - 1st Warning
  - 2nd Point penalty
  - 3rd Game penalty
  - 4th+ Supervisor decision
- Time (server): first Warning, then Fault
- Time (receiver): first Warning, then Point penalty

## Output
Applying a violation returns:
- updated counts
- current penalty text
- next-step penalty text
- a short, concrete example shown in confirmation dialog (to help quick on-court decisions)

## Main files
- `app/src/main/java/com/klim/tennisrefapp/domain/usecase/ApplyViolationUseCase.kt`
- `app/src/main/java/com/klim/tennisrefapp/domain/model/ViolationModels.kt`
- `app/src/test/java/com/klim/tennisrefapp/domain/ApplyViolationUseCaseTest.kt`
