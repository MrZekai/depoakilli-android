# Play permission rationale — v0.5.0

## MANAGE_EXTERNAL_STORAGE

Smart Cleaner requests All files access because **file management and maintenance are core user-facing functionality**. Without broad shared-storage access, the application cannot perform its core cross-folder operations: junk maintenance, exact duplicate discovery across file types, large-file review, Downloads/APK management, storage analysis, or direct WhatsApp shared-Media management.

Google Play's current All files access policy lists file management as an eligible use when the app's core purpose includes accessing, editing and managing files/folders outside app-specific storage, including maintenance. This permission remains subject to Play Console declaration and review.

Official policy reference:
https://support.google.com/googleplay/android-developer/answer/10467955

Android implementation reference:
https://developer.android.com/training/data-storage/manage-all-files

## PACKAGE_USAGE_STATS

Usage Access is optional and requested only for app storage/cache statistics and App Manager usage metadata. Smart Cleaner does not read another application's personal content through this access.

## Device app-cache cleanup

On Android 11+, Smart Cleaner uses Android's official `StorageManager.ACTION_CLEAR_APP_CACHE` action. The action requires `MANAGE_EXTERNAL_STORAGE`, does not silently clear cache, and shows Android-controlled confirmation.

Reference:
https://developer.android.com/reference/android/os/storage/StorageManager#ACTION_CLEAR_APP_CACHE

## Permissions intentionally not used

- `QUERY_ALL_PACKAGES`: not used. App discovery stays limited to launchable apps visible through the launcher intent.
- Accessibility Service: not used for cleaner automation.
- `KILL_BACKGROUND_PROCESSES`: not used for misleading RAM boosting.
- `CLEAR_APP_CACHE`: not requested because it is a privileged/signature permission unavailable to normal Play apps.
