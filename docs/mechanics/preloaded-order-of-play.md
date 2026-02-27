# Preloaded Order Of Play

## Purpose
Provide a deterministic import path that does not depend on OCR quality.

## Behavior
- Home screen has `Load Preloaded Order Of Play`.
- Tapping it clears current tournament data and loads a curated tournament dataset.
- Dataset includes courts, players, country codes, selected seeds, and scheduled matches.
- Schedule labels are explicit (`Starting at 09:00`, `Followed By`, `Not Before 11:30`).
- `manualNotBefore` is set for fixed-time rows so not-before values are always available.

## Why this exists
OCR import can still misread names, seeds, or times on some files/images.  
This preloaded option gives a reliable fallback flow for demos and real use when file OCR is unstable.

## Main files
- `app/src/main/java/com/klim/tennisrefapp/ui/home/HomeScreen.kt`
- `app/src/main/java/com/klim/tennisrefapp/ui/home/HomeViewModel.kt`
- `app/src/main/java/com/klim/tennisrefapp/domain/repository/TournamentRepository.kt`
- `app/src/main/java/com/klim/tennisrefapp/data/repository/AppRepository.kt`
