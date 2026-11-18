# Cleaner engine

Code: `data/AiCleaningEngine.kt` (rules), `data/CleaningThresholds.kt` (every threshold), `model/CleanupPlan.kt` (plan), `data/TrashStore.kt` (Trash). Byte values use base 1000.

## Principles
- Every candidate falls into exactly one `CleanupGroup`, by the first matching rule.
- Only Auto groups are preselected: `APK_INSTALLED`, `DUPLICATE_SAFE`, `THUMBNAILS`, `TEMP_FILES`, `PARTIAL_DOWNLOADS`. A unit test keeps this set fixed.
- Everything else is "Worth a look": not selected, moved to Trash for 7 days.
- Group checkboxes and "select all" never select groups that are not group-selectable, nor review items from the camera folder (`DCIM/…`, except screenshots).
- Age for review groups: the capture date in the file name (`IMG_20240105…`), otherwise the modification time. Auto groups use the modification time only.

## Rules (first match wins)
| # | Rule | Group | Auto | Delete |
|---|---|---|---|---|
| 1 | WhatsApp databases/backups, `.crypt*`, `msgstore*` | never a candidate | | |
| 1b | `.trashed-<time>-name` (Android's own trash) | `SYSTEM_TRASH` | no | permanent |
| 1c | `.pending-<time>-name` ≥ 7 days, not in Android/media | `TEMP_FILES` | yes | permanent |
| 2 | WhatsApp: Status ≥ 2 d → Sent ≥ 30 d → video ≥ 25 MB → voice note ≥ 180 d → video ≥ 90 d → image ≥ 180 d → Documents ≥ 90 d → audio ≥ 180 d (stickers, profile photos never) | `WHATSAPP_*` | no | Trash |
| 3 | `.thumbnails` in DCIM/Pictures/Movies/Music | `THUMBNAILS` | yes | permanent |
| 4 | `.crdownload/.part/.download` in Downloads ≥ 14 d | `PARTIAL_DOWNLOADS` | yes | Trash |
| 5 | `.tmp/.temp` ≥ 7 d outside Android/media | `TEMP_FILES` | yes | permanent |
| 6 | APK: same/newer version installed → `APK_INSTALLED`, older of several → `APK_OLD_VERSION`, else `APK_OTHER` | APK groups | installed only | |
| 7 | Screen recordings ≥ 30 d | `SCREEN_RECORDING` | no | Trash |
| 8 | Screenshots ≥ 30 d | `SCREENSHOT` | no | Trash |
| 9 | Video ≥ 100 MB and ≥ 90 d | `LARGE_VIDEO` | no | Trash |
| 9b | Video ≥ 20 MB and ≥ 180 d | `OLD_VIDEO` | no | Trash |
| 9c | Photo/video in `Android/media/<pkg>/` (not WhatsApp) or `Telegram/`, ≥ 5 MB, ≥ 90 d | `OTHER_APP_MEDIA` (not group-selectable) | no | Trash |
| 10 | Downloads ≥ 90 d (documents not group-selectable) | `DOWNLOAD_FILE` / `DOWNLOAD_DOCUMENT` | no | Trash |
| 11 | Anything ≥ 100 MB | `LARGE_OTHER` | no | Trash |
| — | `.dng` with a `.jpg/.jpeg/.heic/.heif` partner in the same folder | `RAW_COPY` | no | Trash |
| — | Duplicates: full SHA-256 ≤ 32 MB in the quick scan, larger ones stay "look alike" until the optional deep check | `DUPLICATE_SAFE` / `_PERSONAL` / `_UNVERIFIED` | safe locations only | |

## Plan
- Card: "Up to X can be freed" = Auto + every review group (small groups merged into "Smaller groups to check"). App cache is never part of it. The button only shows selected bytes.
- Review groups ≥ 50 MB get their own row; Auto groups < 50 MB merge into "Small items".
- "Biggest folders" (top 5, three levels deep) and, with Usage Access, "Largest apps" (top 5 user apps; opens App info).
- The user's own choices apply to the Smart/Deep plan only: files kept in the swipe review (`kept_items_v1`, max 5,000) and excluded folders (`excluded_folders_v1`, max 50, never a volume root).
- A saved summary younger than 6 hours avoids a full scan at launch.

## Trash
- `.SmartCleanerTrash` on each volume, `.nomedia` + README. Each file gets a sidecar record before it moves, so a lost index can be rebuilt.
- Kept 7 days, purged at launch and daily (WorkManager). Undo and Restore return files to their original path.
- When Trash cannot be used (storage full, or the folder cannot be created), the user is asked separately before a permanent delete.
- Trash index and the user-choice stores are excluded from backup and device transfer.
