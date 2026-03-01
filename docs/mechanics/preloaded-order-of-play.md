# Preloaded Order Of Play

## Purpose
Provide a deterministic import path that does not depend on OCR quality.

## Behavior
- Home screen has `Load Preloaded Order Of Play`.
- Preloaded list is dynamic:
  - locally bootstrapped `Fictional Qualifiers` preset (boys + girls qualification draws),
  - local asset presets from `app/src/main/assets/local_presets/*.json` (gitignored),
  - locally saved presets created from imports (`Save Current As Preset`).
- Presets are editable from the load dialog and can be deleted with confirmation.
- Tapping a preset clears current tournament data and loads the selected dataset.
- Presets include courts, players, country codes, seeds, and scheduled matches.
- Schedule labels are explicit (`Starting at 09:00`, `Followed By`, `Not Before 11:30`).
- `manualNotBefore` is set for fixed-time rows so not-before values are always available.
- Presets can include unresolved slots as `winnerOfMatch` dependencies so later matches fill
  automatically when earlier matches are completed.
- After importing a file, use `Save Current As Preset` to store it locally on-device.

## Why this exists
OCR import can still misread names, seeds, or times on some files/images.  
This preloaded option gives a reliable fallback flow for demos and real use when file OCR is unstable.

## Privacy / Git
- Presets are stored in app-internal storage (`files/saved_presets`) and optional local asset files (`assets/local_presets`), both outside tracked Git content.
- This keeps personal tournament presets usable on phone builds without publishing them on GitHub.

## Main files
- `app/src/main/java/com/klim/tennisrefapp/ui/home/HomeScreen.kt`
- `app/src/main/java/com/klim/tennisrefapp/ui/home/HomeViewModel.kt`
- `app/src/main/java/com/klim/tennisrefapp/domain/repository/TournamentRepository.kt`
- `app/src/main/java/com/klim/tennisrefapp/data/repository/AppRepository.kt`
