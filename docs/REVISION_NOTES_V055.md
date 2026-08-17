# v0.5.5 — Smart Clean Review & Preview UX

- versionName: `0.5.5`
- versionCode: `12`
- based on GitHub main commit `98a890b75f641ae7275b272257207d413e79d0d2`

## UX fixes

- Rebuilt the live Smart Clean scanning screen to match the neon home dashboard.
- Replaced the legacy full-category vertical list with a two-column visual review grid.
- Every review tile shows thumbnail or file-type icon, name, location, size and cleanup state.
- Tapping a tile opens preview; only the checkbox changes deletion selection.
- Video previews now expose an explicit Play/Pause control and error state.
- File preview cleanup-selection control uses a dark high-contrast surface instead of white-on-white text.
- Category detail has select-all/clear-all, selected count/bytes and a clear Done action.
- Home/results banner reserve a standard 320x50 mobile banner area instead of the oversized large adaptive slot.

## Cleanup safety

The existing v0.5.4 flow remains unchanged:
final review -> eligible/loaded interstitial -> ad dismissal/fallback -> delete plan -> Android media consent when required -> actual deletion -> automatic rescan.
