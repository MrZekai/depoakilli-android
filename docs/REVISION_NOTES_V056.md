# v0.5.6 — Safe cleanup accounting & Media3 video preview

## Cleanup accounting

The home dashboard no longer labels every review candidate as immediately cleanable.

- **Safe to clean / Güvenle temizlenebilir** = items preselected by conservative rules.
- **Review suggestions / İnceleme önerileri** = candidates shown for user review but not preselected.
- Smart Clean results continue to let the user manually add/remove review items before cleanup.

This removes the misleading case where the dashboard could show ~5 GB "cleanable" while only ~32 MB was actually selected for deletion.

## Junk safety

Smart junk auto-selection now stays conservative:

- incomplete download/temp extensions remain eligible;
- hidden `.thumbnails` cache remains eligible after age threshold;
- ordinary image/video files in generic `Temp` or `Thumbnails` folders are not auto-classified as junk;
- the review UI shows the rule/reason for each suggested item.

## Video preview

`VideoView` was replaced with Jetpack Media3 ExoPlayer. The player is hosted in a `PlayerView` configured with `surface_type="texture_view"` and `resize_mode="fit"` to improve video rendering reliability inside Compose dialogs on OEM devices.

## Unchanged

- 320x50 banner slot;
- interstitial before deletion;
- Android consent when required;
- automatic rescan after cleanup;
- duplicate safety policy.
