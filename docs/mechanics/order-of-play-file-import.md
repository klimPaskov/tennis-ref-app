# Order Of Play File Import

## Supported Inputs
- PDF files (`application/pdf`)
- Plain text files

## Import Flow
- Home screen `New Order Of Play` opens Android file picker.
- Selected file URI is passed to repository import.
- Existing local data is cleared, then tournament/courts/players/matches are rebuilt from parsed content.

## Parsing Strategy
- Detect court columns from `Court X` labels.
- Detect match pairings from lines around `vs.` tokens.
- Parse player names and optional nationality code from `Name(COUNTRY)` patterns.
- Assign parsed pairs across detected courts in order.

## Failure Behavior
- If file cannot be parsed, import returns false and UI shows an error.
- Existing loaded data remains available unless a valid import succeeds.
