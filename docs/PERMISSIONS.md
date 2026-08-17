# Permissions — Cleaner Engine 0.5.0

## INTERNET / ACCESS_NETWORK_STATE
Required for Google Mobile Ads and consent services.

## MANAGE_EXTERNAL_STORAGE
Core storage-management permission on Android 11+. Smart Cleaner uses it to scan and maintain shared storage across folders, find exact duplicates, review large files and downloads/APKs, analyze storage and manage WhatsApp shared Media without forcing a folder picker for every feature.

Android still protects other apps' private internal data and restricted `Android/data` / `Android/obb` areas. Smart Cleaner skips those areas.

This is a restricted Google Play permission and requires a Play Console declaration and approval.

## PACKAGE_USAGE_STATS
Optional special access for app cache/storage totals and App Manager usage information. No personal app content is read.

## Not requested
`QUERY_ALL_PACKAGES`, Accessibility Service, `KILL_BACKGROUND_PROCESSES`, privileged `CLEAR_APP_CACHE`, and legacy write-storage permissions are intentionally absent.
