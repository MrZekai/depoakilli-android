# Permissions

Smart Cleaner requests only the permissions below. No permission was added in phases 0–4.

## INTERNET / ACCESS_NETWORK_STATE
Google Mobile Ads and the consent (UMP) service. File names, paths and contents never leave the device.

## MANAGE_EXTERNAL_STORAGE (All files access)
File management and maintenance are the app's core purpose: the cleanup plan, junk and unfinished downloads, exact duplicates across file types, large files, Downloads/APKs, storage analysis, WhatsApp shared media, and the in-app Trash (`.SmartCleanerTrash`) all work across shared-storage folders. Google Play lists file management, including maintenance, as an eligible use; the permission still needs the Play Console declaration and review.

- Policy: https://support.google.com/googleplay/android-developer/answer/10467955
- Android: https://developer.android.com/training/data-storage/manage-all-files

Android keeps protecting other apps' private data; `Android/data` and `Android/obb` are skipped, as are WhatsApp databases and backups and every hidden file except Android's own MediaStore trash (`.trashed-…`) and unfinished writes (`.pending-…`).

The app explains the access in its own dialog before Android's settings screen opens, and shows "Open settings" when access was not granted.

## PACKAGE_USAGE_STATS (Usage Access, optional)
App storage/cache totals, last-used times (unused apps), and the "Largest apps" list. No personal app content is read. Without it, those rows lead to an explanation instead.

## Device app-cache cleanup
Android 11+: the official `StorageManager.ACTION_CLEAR_APP_CACHE` action, which requires All files access and always shows Android's own confirmation.
https://developer.android.com/reference/android/os/storage/StorageManager#ACTION_CLEAR_APP_CACHE

## Not requested
`QUERY_ALL_PACKAGES`, `MANAGE_MEDIA`, `POST_NOTIFICATIONS`, location, `READ_MEDIA_*`, Accessibility Service, `KILL_BACKGROUND_PROCESSES`, privileged `CLEAR_APP_CACHE`, and legacy write-storage permissions.
