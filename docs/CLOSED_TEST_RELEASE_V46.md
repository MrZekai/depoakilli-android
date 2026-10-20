# Smart Cleaner v46 - closed-test handoff

- Package: `com.mrzekai.depoakilli`
- Version: `1.0.1` (`versionCode 46`)
- Signing: existing Play upload key from GitHub Actions secrets; no key or alias change. Its SHA-256 is pinned to the successful v45 build: `e15e1d31ea6dda44c7f8333eabdc9f4a4d0e9d8feeb9f321958b496edf4f4afd`.

Run `Play Closed Test AAB + APK` on `main` after Android CI passes. Its separate artifacts are `SmartCleaner-PLAY-CLOSED-TEST-AAB-v46` and `SmartCleaner-CLOSED-TEST-APK-v46`. The binary gate verifies package/version, non-debuggable state, matching AAB/APK certificates, and the v45 certificate fingerprint before uploading either artifact. The QA APK from Android CI uses the separate `.qa` package and test key; it is only for QA, not an update to the installed closed-test app.

## Round 2 QA fixes

- A restored results route without its in-memory scan is discarded after process death.
- If Android's cache activity is cancelled or no reduction is measurable, the app gives a per-app settings fallback instead of showing a false success result.
- The results dialog keeps Done visible while its content and ad area scroll.
- The app-cache sheet keeps its actions above the navigation bar.
- Deep scan and Large Files now use the same 100 MiB threshold.
- Storage tiles use two columns and wrapping labels; narrow file previews separate name and size; small-screen and enlarged-text labels can scroll or wrap.
- Hidden dot-files, including `.database_uuid`, are excluded from indexing and deletion.

The report tested v44. v45 had already improved scanning-label wrapping and the result-ad placeholder. Before Play upload, repeat the report's physical-device cases at font scale 1.3, including process death, the SEGO Android 14 cache action, ads loading/failure, and hidden files. Android OEM cache screens and ad behavior require device verification; CI cannot establish their appearance.
