# v0.5.2 — Smart Clean Results UX

This revision changes only the Smart Clean result/review experience and the scan-result data needed by that screen.

- User-visible "Cleaner Engine" branding is removed in favor of Smart Cleaner / Akıllı Temizleyici.
- Smart Clean no longer dumps every category and item into one endless result list.
- Cleanup categories are shown as horizontal review cards with four preview items and a dedicated "View all" dialog.
- Tapping a file opens an in-app preview instead of silently changing its cleanup selection.
- Images open in a large in-app preview, video uses an in-app VideoView, audio can be played/paused, and other files show detailed metadata.
- The cleanable-space card and bottom action open an explicit confirmation dialog showing exactly which categories, counts and sizes will be deleted.
- Storage Analyzer categories are tappable and open a list of up to 80 of the largest files found in each type. These analyzer previews are informational and are never auto-selected for deletion.
- Storage analyzer preview metadata is retained in ScanSummary without changing the full storage totals.
- Version: 0.5.2 (versionCode 9).
