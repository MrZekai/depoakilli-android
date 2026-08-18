# Smart Cleaner / Akıllı Temizleyici

Android storage-management and file-maintenance app. Current development line: **v0.5.9 Premium Cleaner Tool Review UX**.

## Cleaner Engine 0.5


## v0.5.9 premium cleaner tool review UX

- Duplicate Cleaner, Large Files, APK Packages, Media Cleaner, Junk Cleaner, Downloads and Deep Clean no longer fall back to the legacy generic vertical result list;
- each tool gets a focused premium review workspace with its own grouping logic and visual accent;
- duplicate copies are grouped around the protected original; large files are split into media/document/other rails; APKs are split into older and recent installers;
- horizontal lazy preview rails on tool home screens and adaptive View all grids keep browsing fast;
- tapping a card previews the file while the checkbox alone changes delete selection;
- sampled image decoding, a small thumbnail LRU cache and Media3 video/audio preview keep memory use bounded;
- APK cleanup explicitly removes installer files only and never uninstalls an installed app;
- all tool cleanup CTAs reuse the existing disclosed interstitial-before-delete flow and the single standard 320x50 banner shell;
- all new user-visible copy remains resource-based: Turkish locale -> Turkish, English/UK -> English, unsupported locales -> English fallback.

## v0.5.8 WhatsApp Cleaner premium review UX

- selected premium WhatsApp Cleaner layout implemented as real Compose UI;
- three lightweight review groups: Images, Videos, Documents & More;
- horizontal lazy preview rails on the main page and left-to-right adaptive grids in View all;
- tap-to-preview is separated from checkbox-to-delete selection;
- Media3 video preview, audio preview, sampled image preview and generic file details stay inside the app;
- Incoming / Sent / All filters and Largest / Newest sort modes;
- small in-memory thumbnail cache and sampled decoding keep browsing responsive;
- WhatsApp cleanup now follows final review -> eligible interstitial -> real deletion -> result message -> Home;
- a single standard 320x50 banner is reserved on WhatsApp screens;
- all new copy uses Android resources: Turkish locale -> Turkish, English/UK -> English, unsupported locales -> English fallback.

## v0.5.7 storage review & monetization UX

- every Storage Analysis category opens a full selectable review page instead of a read-only preview;
- images/videos use thumbnail grids; audio/documents/APKs/archives/other files use readable review rows;
- storage files are never auto-selected; preview and deletion selection remain separate actions;
- full-category selection, selected-byte summary, direct review-and-clean CTA and post-delete reconciliation;
- "View all" Smart Clean category pages also get their own review-and-clean CTA;
- the single standard 320x50 banner remains visible below all in-screen review pages;
- both Smart Clean category deletion and Storage Analysis deletion reuse the disclosed interstitial-before-delete flow with the existing cooldown.

## v0.5.6 safe cleanup accounting & video preview

- dashboard separates automatically selected safe cleanup bytes from review-only candidate bytes;
- Smart Clean hero reports selected cleanup and review candidates as two different values;
- junk auto-selection is tightened so ordinary photos/videos in generic user folders are not classified as junk;
- junk review shows the concrete reason a file was suggested, including regeneratable hidden thumbnails;
- video preview moved from `VideoView` to Jetpack Media3 ExoPlayer rendered through a TextureView-backed `PlayerView`;
- failed/unsupported playback shows an explicit error instead of silent audio-only black playback;
- previous interstitial-before-delete, duplicate safety and 320x50 banner behavior remain unchanged.

## v0.5.5 Smart Clean review & preview

- premium live scanning screen aligned with the neon dashboard;
- "View all" redesigned from a plain vertical list into a visual two-column review grid;
- real photo/video thumbnails in category review, clear file metadata and cleanup selection state;
- explicit video Play/Pause control with playback error handling;
- high-contrast cleanup inclusion control in file preview;
- standard 320x50 mobile banner slot instead of an oversized large adaptive banner;
- all Smart Clean category cards remain preview-first: tapping content previews it, checkbox changes deletion selection.

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
- versionName: `0.5.9`
- versionCode: `16`
- debug package: `com.mrzekai.depoakilli.qa`

GitHub Actions runs project validation, dependency checks, unit tests, lint, debug APK build and stable QA-signing verification. The debug artifact is named `depoakilli-test-apk-*`.
