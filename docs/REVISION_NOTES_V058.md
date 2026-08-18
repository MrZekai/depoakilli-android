# v0.5.8 — WhatsApp Cleaner Premium Review UX

This revision replaces the legacy vertical WhatsApp media list with the selected premium review layout while keeping the existing direct shared-storage scanner and deletion engine.

## Product UX

- WhatsApp results use three lightweight review groups: Images, Videos, and Documents & More.
- Main groups use horizontal lazy preview rails with real thumbnails, file metadata, selection checkboxes, and View all.
- View all opens an in-screen adaptive grid instead of the old plain vertical list.
- File-card tap is preview-only; checkbox is deletion-selection-only.
- Images open a full in-app preview.
- Videos use Jetpack Media3 ExoPlayer with the existing TextureView-backed PlayerView.
- Audio/voice notes get in-app play/pause preview.
- Generic documents remain inside the app with file metadata rather than forcing an external viewer.
- Incoming / Sent / All filters are based on WhatsApp Media path segments; sorting supports Largest and Newest.
- No WhatsApp file is preselected automatically.

## Performance

- Only visible LazyRow/LazyVerticalGrid cards request thumbnails.
- A small 72-entry in-memory LRU cache avoids repeated thumbnail decoding while browsing.
- Full-size images use sampled decoding rather than loading original-resolution bitmaps into memory.
- Main result sections render only four previews plus a +N card.

## Ads and cleanup

- WhatsApp detail screens reserve the same standard 320x50 banner slot used elsewhere.
- Final confirmation routes through the existing interstitial controller before deletion.
- Frequency cap / full-screen separation / no-fill fallback remain unchanged.
- After a successful or partial WhatsApp deletion, the app refreshes device storage, posts the result message, and returns to Home.

## Localization

All new visible copy is Android-resource based. Turkish devices use `values-tr`; English/UK devices use the default English resources. Unsupported locales fall back to English until a complete translation pack is added.
