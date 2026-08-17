# Review resolution status

The v0.4.1 safety fixes remain the historical baseline, but Cleaner Engine 0.5 intentionally changes the storage architecture after device testing showed that the previous product was too narrow for a real phone-cleaner promise.

Resolved in v0.5.0:

- Smart Scan no longer substitutes Smart Cleaner's own cache for phone-wide junk.
- WhatsApp no longer uses `OpenDocumentTree()` / a persistent folder-picker flow.
- duplicate discovery no longer excludes files over 40 MB, groups over 20 files, or all but 100 hash groups;
- large-file cleaning is no longer video-only;
- Tools no longer uses battery/RAM/storage status cards as primary cleaner tools;
- All files access is introduced as a declared core file-management/maintenance permission;
- device cache cleanup uses Android's official `ACTION_CLEAR_APP_CACHE` flow rather than pretending private third-party cache was directly deleted;
- user-created files remain conservative review items; high-confidence disposable artifacts may be preselected.

See `CLEANER_ENGINE_V050.md` and `PLAY_PERMISSIONS_V050.md` for the current architecture.
