# v0.5.9 Premium Cleaner Tool Review UX

This revision upgrades the remaining home cleaning tools to the same product-quality bar as the WhatsApp Cleaner without changing the underlying cleanup safety rules.

## Upgraded tools

- Duplicate Cleaner
- Large Files
- APK Packages
- Media Cleaner
- Junk Cleaner
- Downloads
- Deep Clean

## Interaction model

- Main tool pages use horizontal lazy preview rails instead of plain vertical file rows.
- `View all` opens an adaptive visual grid with sorting and select-all controls.
- Tapping a file previews it. Only the checkbox changes deletion selection.
- Images use sampled decoding; media thumbnails are cached in a small in-memory LRU.
- Video/audio preview stays in-app with Jetpack Media3.
- The bottom action always shows selected bytes and item count before cleanup.

## Tool-specific behavior

### Duplicate Cleaner
Exact duplicates are grouped by the protected original name. The original is never added to the deletion list by this screen.

### Large Files
Candidates are split into videos, images, documents/archives and other files so users can review storage-heavy content visually.

### APK Packages
Installer files are split into older and recent packages. Deleting an APK only removes the downloaded installer file; it does not uninstall an installed application.

### Media / Junk / Downloads / Deep Clean
These screens share the same fast review components but keep their existing conservative engine categories and selection rules.

## Ads and cleanup

The existing single standard 320x50 banner shell remains outside the tool content. Cleanup continues through the existing eligible interstitial-before-delete controller; if an ad cannot be shown, the cleanup action is not blocked.

## Localization

All new copy is stored in Android resources. Turkish devices use Turkish, English/UK devices use English, and unsupported locales fall back to English until dedicated translations are added.
