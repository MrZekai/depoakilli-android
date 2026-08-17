# Android cache behavior — v0.5.0

Normal Play applications cannot directly and silently delete protected private cache directories belonging to arbitrary third-party apps.

Cleaner Engine 0.5 therefore separates three concepts:

1. **Cache measurement** — `StorageStatsManager` + optional Usage Access reports device/app cache sizes.
2. **Smart Cleaner's own cache** — can be deleted directly because it belongs to this application.
3. **Device app-cache cleanup** — Android 11+ `StorageManager.ACTION_CLEAR_APP_CACHE` is requested by Smart Cleaner when All files access is granted. Android shows the required system confirmation and performs the cache cleanup.

The Smart Scan cleanable-file total never substitutes Smart Cleaner's own cache for the entire phone's app cache.
