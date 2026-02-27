# Order Of Play File Import

## Supported Inputs
- PDF files (`application/pdf`)
- Image files (`.png`, `.jpg`, `.jpeg`, `.webp`) via on-device OCR
- Plain text files

## Import Flow
- Home screen `New Order Of Play` opens Android file picker.
- Selected file URI is passed to repository import.
- Existing local data is cleared, then tournament/courts/players/matches are rebuilt from parsed content.
- On successful import, that source URI is stored as the active reload source.
- Home screen `Reload` uses the stored source and rebuilds the same order-of-play with all matches reset to scheduled.

## Parsing Strategy
- Detect court columns from `Court X` labels.
- Detect match pairings from lines around `vs.` tokens.
- Parse player names and optional nationality code from `Name(COUNTRY)` patterns.
- Detect doubles teams automatically when entrant text contains separators (`/`, `&`, or `and`) and create two-player teams.
- Assign parsed pairs across detected courts in order.
- Imported matches are created as scheduled (no pre-completed results/scores).

## Failure Behavior
- If file cannot be parsed, import returns false and UI shows an error.
- Existing loaded data remains available unless a valid import succeeds.
