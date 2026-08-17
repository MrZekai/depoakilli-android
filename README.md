# Smart Cleaner / Akıllı Temizleyici

Android storage-management and file-maintenance app. Current development line: **v0.5.4 Smart Clean Final Results Template**.

## Cleaner Engine 0.5

## v0.5.4 Smart Clean results

- selected neon results template aligned with the home dashboard;
- vertical category stack with horizontal real-file preview strips;
- file preview and checkbox selection are separate actions;
- category select-all and full-category review;
- sticky selected-size + Review & clean footer above the anchored adaptive banner;
- final review -> interstitial (when eligible/loaded) -> automatic deletion -> Android consent when required -> automatic rescan;
- duplicate cleanup execution guard.


## v0.5.1 dashboard

- dark blue / cyan / green neon home dashboard;
- real Smart Clean, Deep Clean, duplicates, large files, APK, media, WhatsApp, app-cache and RAM actions;
- no Pro/Premium UI because no paid tier exists;
- four-tab navigation: Home, Tools, Security, Me;
- home cleaning score is a local storage-health heuristic, not a device-speed benchmark.


- recursive shared-storage Smart Scan after Android grants All files access;
- junk maintenance for conservative temporary/incomplete artifacts;
- exact duplicate discovery across accessible file types using sample + full streaming SHA-256;
- large-file discovery across videos, archives, APKs, audio, documents and other files;
- Downloads & APK review;
- Media Cleaner for screenshots and storage-heavy media;
- WhatsApp / WhatsApp Business shared-Media cleaner fully browsed inside Smart Cleaner;
- Storage Analyzer by file type;
- app-cache measurement via Usage Access;
- Android 11+ official device cache cleanup request using `StorageManager.ACTION_CLEAR_APP_CACHE`;
- App Manager with storage/cache information and Android-confirmed uninstall;
- honest RAM Optimization that releases Smart Cleaner's temporary resources without fake third-party process claims.

## Android / Play boundaries

Smart Cleaner requests `MANAGE_EXTERNAL_STORAGE` because file management and maintenance are core features. Google Play approval and the Permissions Declaration Form are required before production release. Android still protects other applications' private internal data and restricted `Android/data` / `Android/obb` areas.

See:

- `docs/CLEANER_ENGINE_V050.md`
- `docs/PLAY_PERMISSIONS_V050.md`
- `docs/PERMISSIONS.md`
- `docs/ANDROID_CACHE_LIMITS.md`

## Build identity

- package: `com.mrzekai.depoakilli`
- minSdk: 30 (Android 11)
- target/compile SDK: 36
- versionName: `0.5.4`
- versionCode: `11`
- debug package: `com.mrzekai.depoakilli.qa`

GitHub Actions runs project validation, dependency checks, unit tests, lint, debug APK build and stable QA-signing verification. The debug artifact is named `depoakilli-test-apk-*`.
