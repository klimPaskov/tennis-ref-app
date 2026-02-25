# Slot Resolution

## Slot types supported
- `DIRECT_TEAM`
- `WINNER_OF_MATCH`
- `BYE`

## Resolution rules
- Direct team resolves immediately to its team.
- Winner-of-match resolves only when referenced match has a winner.
- Bye resolves as a special resolved state without a team.

## Used by
- Court cards and court detail labels
- Start-match validation (requires both sides resolved)

## Main files
- `app/src/main/java/com/klim/tennisrefapp/domain/usecase/ResolveSlotUseCase.kt`
- `app/src/main/java/com/klim/tennisrefapp/data/local/entity/SlotEntity.kt`
- `app/src/main/java/com/klim/tennisrefapp/data/repository/AppRepository.kt`
- `app/src/test/java/com/klim/tennisrefapp/domain/ResolveSlotUseCaseTest.kt`
