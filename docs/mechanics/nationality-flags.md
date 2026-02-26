# Nationality and Flags

## Purpose
Show player nationality consistently in court detail and dialogs.

## Data
- `PlayerEntity` stores `countryCode` as an abbreviation from the source order-of-play data (for example `EST`, `FIN`, `CHN`).
- `PlayerRef` carries `countryCode` into UI models.

## Display
- UI converts ISO-3 abbreviations to ISO-2 and then to a flag emoji.
- Flag is rendered directly next to the player name.
- Unknown or unmapped country codes are shown without a flag (name only).

## Main files
- `app/src/main/java/com/klim/tennisrefapp/data/local/entity/PlayerEntity.kt`
- `app/src/main/java/com/klim/tennisrefapp/domain/model/CourtModels.kt`
- `app/src/main/java/com/klim/tennisrefapp/data/repository/AppRepository.kt`
- `app/src/main/java/com/klim/tennisrefapp/ui/courtdetail/CourtDetailScreen.kt`
