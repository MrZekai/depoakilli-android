# Cleaner Engine 0.5.0

## Product definition

Smart Cleaner is a **storage-management and file-maintenance** application. Cleaner Engine 0.5 replaces the narrow MediaStore-only cleaner in v0.4.1.

Core engine capabilities:

- recursive shared-storage indexing after Android grants All files access;
- conservative junk rules for temporary/incomplete artifacts and regeneratable thumbnail files;
- old APK and old Download review;
- large-file discovery across file types, not only videos;
- exact duplicate discovery using size -> multi-point sample SHA-256 -> full streaming SHA-256;
- WhatsApp and WhatsApp Business shared Media scanning inside the app;
- storage analysis by images, videos, audio, documents, archives, APK and other files;
- app cache measurement through Usage Access and StorageStatsManager;
- Android 11+ official `StorageManager.ACTION_CLEAR_APP_CACHE` flow for device app-cache cleanup;
- in-app App Manager for launchable applications, with Android system confirmation for uninstall;

## Important Android boundaries

`MANAGE_EXTERNAL_STORAGE` gives broad access to shared storage, but it does not grant direct access to protected private directories of other apps such as their internal data. The engine explicitly skips `Android/data` and `Android/obb`.

Device app-cache cleanup does not pretend to silently delete another app's private cache. Android 11+ exposes `ACTION_CLEAR_APP_CACHE`; Android displays the required system confirmation and performs the operation.

There is no user-facing RAM optimizer, third-party process-killing action, CPU cooler, or fabricated speed-gain tool.

## Deletion safety

User-created large files, old downloads, screenshots and WhatsApp media are review items and are not automatically selected merely because of age or category. High-confidence temporary artifacts, stale installers and unambiguous exact duplicate copies may be preselected.

The duplicate policy protects a camera original when one copy is in a camera-original path. Ambiguous duplicate groups are not automatically selected.

## Scanner limits

The engine uses a high safety ceiling of 200,000 indexed shared-storage files per pass and reports when this ceiling is reached. WhatsApp scanning has a separate 100,000-file safety ceiling.
