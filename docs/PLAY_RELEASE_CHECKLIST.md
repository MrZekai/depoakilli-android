# Google Play release checklist — Cleaner Engine 0.5

## Build / QA gate

- [ ] `python scripts/validate-project.py` passes.
- [ ] GitHub Actions passes project validation, `testDebugUnitTest`, `lintDebug`, `assembleQa`, QA signing and packaged-binary verification.
- [ ] QA APK reports version `0.5.18-closedtest1-qa` / versionCode `39`.
- [ ] Manual `Play Closed Test AAB + APK` workflow passes `lintClosedTest`, signed AAB/APK build and binary verification.
- [ ] Play AAB reports package `com.mrzekai.depoakilli`, version `0.5.18-closedtest1` / `39`, sample ads, non-debuggable state and the existing upload certificate.
- [ ] Physical-device checklist in `docs/DEVICE_QA_CHECKLIST.md` is recorded before Production.
- [ ] Android 11–16 permission return paths are tested where devices are available.
- [ ] No scan/delete path causes ANR or silently drops failed items.

## Storage-management core functionality

- [ ] Smart Scan, Junk, Duplicates, Large Files, Media, WhatsApp, Downloads/APK and Storage Analyzer are demonstrably functional on shared storage.
- [ ] `MANAGE_EXTERNAL_STORAGE` is genuinely core to file management / maintenance rather than an advertising or convenience permission.
- [ ] `/Android/data` and `/Android/obb` belonging to other apps are not presented as directly accessible/cleanable.
- [ ] WhatsApp cleaning remains inside Smart Cleaner after All Files Access; there is no recurring SAF folder-picker workflow.
- [ ] Duplicate scanning works above the former 40 MB limit and does not auto-select an uncertain original.
- [ ] User-created old Downloads, large personal files and screenshots remain review-first instead of silent auto-delete.

## App cache / app manager

- [ ] Usage Access rationale explains that it is used to measure available app usage/storage/cache information.
- [ ] Deep App Cache uses Android's official system cache-cleanup action and does not claim silent deletion of protected private caches.
- [ ] Smart Cleaner's own cache is labelled separately from device-wide cache tools.
- [ ] App Manager only lists apps visible through the launcher query; `QUERY_ALL_PACKAGES` is not requested.
- [ ] Uninstall uses Android's official confirmation flow.

## Performance claims

- [ ] No user-facing RAM Optimizer or third-party process-killing claim exists.
- [ ] No fixed “phone X% faster”, fake recovered-RAM total, CPU cooler or inaccessible-private-cache claim exists in app or store metadata.

## Permission / Play declarations

- [ ] All Files Access declaration in Play Console matches `docs/PLAY_PERMISSIONS_V050.md` and actual app behavior.
- [ ] Usage Access disclosure is consistent in-app, in privacy policy and in store text.
- [ ] Data Safety answers match Google Mobile Ads/UMP SDK behavior and local file processing.
- [ ] `QUERY_ALL_PACKAGES`, Accessibility Service, `KILL_BACKGROUND_PROCESSES`, privileged `CLEAR_APP_CACHE`, legacy write-storage permissions are absent.
- [ ] Privacy policy is published over HTTPS before release.
- [ ] Published privacy policy is a public, non-geofenced webpage (not a PDF) and contains the same `SUPPORT_EMAIL` shown in the app and Play developer contact.

## Ads / consent

- [ ] UMP consent is configured for applicable regions.
- [ ] Banner, result-ad and Interstitial placement follows `docs/AD_PLACEMENTS.md`.
- [ ] Ads never cover permission rationales, cleanup confirmation or primary selection controls.
- [ ] Returning from Android permission/settings/cache/uninstall screens immediately restores the app without a foreground-return advertisement.
- [ ] Result Native/fallback behavior is intentional and does not block cleanup or navigation.

## Localization / settings

- [ ] English remains the default/fallback resource language.
- [ ] Turkish resources stay key-compatible with English.
- [ ] Android 13+ app-language settings open correctly; Android 11–12 fall back to device language settings.
- [ ] Unsupported locales fall back to English without broken keys. Additional production locale packs are added only when translated/reviewed, not fabricated.

## Identity / signing

- [ ] Package name is `com.mrzekai.depoakilli` for Play and QA suffix behavior remains intentional.
- [ ] Closed-test AAB and APK are signed by the same existing Play upload certificate.
- [ ] QA certificate SHA-256 is verified by CI: `50:8E:01:21:97:DA:76:D5:16:BA:D2:48:80:B6:7C:6F:06:7F:CD:64:6C:51:A0:43:A1:1C:0C:34:5E:6E:AD:54`.
- [ ] Play upload keystore and passwords exist only in approved secret storage and are backed up securely.

## Store assets / metadata

- [ ] 512×512 icon, 1024×500 feature graphic and at least four phone screenshots represent the real 0.5 behavior.
- [ ] English store listing is complete; localized listings are published only for supported/reviewed locales.
- [ ] Store copy clearly positions Smart Cleaner as storage management / file maintenance and does not promise inaccessible Android operations.
