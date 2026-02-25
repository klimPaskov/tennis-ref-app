# Not-Before Computation

## Inputs
- Optional `manualNotBefore` value from match.
- Known participants in that match.
- For each known participant:
  - player rest minutes (`player.restMinutes` or tournament default)
  - most recent ended match time for that player

## Formula
`computedNotBefore = max(manualNotBefore, each(lastEndedAt + restMinutes))`

If no constraints are known, result is `null`.

## Notes
- Doubles naturally contributes both players as constraints.
- Unknown participants (unresolved slots) do not add constraints yet.

## Main files
- `app/src/main/java/com/klim/tennisrefapp/domain/usecase/ComputeNotBeforeUseCase.kt`
- `app/src/main/java/com/klim/tennisrefapp/data/repository/AppRepository.kt`
- `app/src/test/java/com/klim/tennisrefapp/domain/ComputeNotBeforeUseCaseTest.kt`
