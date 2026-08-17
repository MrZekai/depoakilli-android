# Smart Cleaner 0.5.0 — Cleaner Engine Rewrite

Base source: the complete user-provided `depoakilli-android-main (3).zip` project. This is a full-source cumulative revision, not an overlay built from an older conversation package.

## Product architecture changes

- Smart Scan no longer substitutes Smart Cleaner's own cache for phone-wide junk/cache cleanup.
- Shared-storage scanning is recursive after Android All Files Access is granted.
- Other apps' protected `Android/data` and `Android/obb` areas are intentionally excluded.
- Smart Scan can surface conservative junk, exact duplicates, large files, old Downloads, APK installers, screenshots/media review candidates and WhatsApp shared media.
- Storage Analyzer classifies indexed files into images, video, audio, documents, archives, APK and other.

## Duplicate engine

- Removed the old 40 MB hash ceiling.
- Removed the old 20-files-per-group and 100-hash-group limits.
- Candidate pipeline is exact-size grouping → sampled SHA-256 → full streaming SHA-256.
- DuplicatePolicy protects likely originals such as Camera files and avoids unsafe automatic selection for ambiguous groups.

## WhatsApp

- Removed persistent `OpenDocumentTree` / SAF folder-connection flow.
- WhatsApp and WhatsApp Business shared Media are discovered directly under supported shared-storage locations after All Files Access.
- Cleaner remains in Smart Cleaner UI for scan, classification, thumbnail review, selection and direct deletion.
- Images, videos, documents, audio, voice notes, stickers/GIFs, statuses and other files are categorized.

## App cache / app manager

- Usage Access is used only to query Android-provided app usage/storage/cache stats where available.
- Deep App Cache launches Android's official cache-cleanup system action; Smart Cleaner does not claim privileged silent deletion of protected private caches.
- Smart Cleaner's own cache is a separate explicit action.
- App Manager uses launcher-visible apps without requesting `QUERY_ALL_PACKAGES` and uses Android's uninstall confirmation.

## Tools / UI

Primary tools are now actionable cleaner/storage functions:

- Deep Cleaner
- Junk Cleaner
- Duplicate Cleaner
- Large Files
- Media Cleaner
- WhatsApp Cleaner
- Downloads & APK
- Deep App Cache
- App Manager
- Storage Analyzer
- RAM Optimization
- Settings

Battery/CPU/live-device status is no longer presented as a cleaning tool.

## RAM

RAM Optimization remains honest: it can release Smart Cleaner's own heavy/ad resources and report measured memory state, but it does not claim to force-stop third-party apps or invent a fixed phone-speed percentage.

## Permissions / release posture

- Added `MANAGE_EXTERNAL_STORAGE` because storage management / file maintenance is now explicit core functionality.
- Kept `PACKAGE_USAGE_STATS` for optional app/cache measurement.
- Did not add `QUERY_ALL_PACKAGES`, Accessibility Service, privileged `CLEAR_APP_CACHE`, `KILL_BACKGROUND_PROCESSES` or legacy write-storage permission.
- Added Play permission/declaration notes and a new physical-device QA matrix.

## Version

- `versionName = 0.5.0`
- `versionCode = 7`
- minimum Android remains API 30 / Android 11.

## Validation completed before packaging

- Project validator: PASS.
- XML/resources parse: PASS.
- English/Turkish string-key parity: PASS (342/342 at validation time).
- No missing Kotlin `R.string` references: PASS.
- Pure Kotlin cleaner core compilation (`CleanModels`, `AiCleaningEngine`, `DuplicatePolicy`, `WhatsAppMediaClassifier`): PASS.
- Cleaner core behavioral smoke checks: PASS.
- Full Gradle/Compose build could not be executed in the artifact environment because the Gradle 8.13 distribution was not locally cached and outbound network access was unavailable. GitHub Actions remains the authoritative full compile/test/lint/APK gate.
