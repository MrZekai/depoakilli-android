# Smart Cleaner / Akıllı Temizleyici

Android 11+ storage-management and file-maintenance app. Current version: **1.0.4 (versionCode 50)**.

## Product scope

- Smart Scan and Deep Clean across accessible shared storage
- conservative junk review with explicit reasons
- exact duplicate discovery using sampled and full streaming SHA-256
- large-file, Downloads/APK, Media, and Screenshot review
- WhatsApp and WhatsApp Business shared-media management inside the app
- Storage Analyzer by file type
- Android-reported app-cache measurement with optional Usage Access
- Android's official device-cache cleanup action
- App Manager with Android-confirmed uninstall

User-created files remain review-first. Smart Cleaner does not claim silent private-cache deletion, protected third-party app termination, device-wide RAM boosting, CPU cooling, or fabricated performance gains.

## Ads and local diagnostics

- anchored adaptive Banner ads
- Native cleanup-result ad with MREC fallback
- Interstitial after a cleanup result is closed with Done, or after 20 s on a tool screen when returning to Clean (2 min apart, at most 8 per day)
- App Open ad on the loading cover from the first launch (after consent), and after the user left the app for 30 s via Home/Recents; never after Android settings, permission, cache, uninstall, review or share screens
- UMP consent and in-app advertising privacy options where required
- local-only Android logging; no third-party crash-reporting service is included

## Build environment

**The build JDK is Java 17.** `gradle/gradle-daemon-jvm.properties` pins the
Gradle daemon to a Java 17 toolchain, so the JVM that happens to launch
`./gradlew` no longer decides the outcome. Gradle 8.13 embeds Kotlin 2.0.21,
which cannot parse Java 25+ and fails while compiling `settings.gradle.kts`
(`IllegalArgumentException: 25.0.4`).

In a fresh clone, run the bootstrap once before the first Gradle command:

```bash
bash scripts/bootstrap-local-env.sh
```

It reports the JDK situation and writes the git-ignored `local.properties` from
`ANDROID_HOME` / `ANDROID_SDK_ROOT`; no personal path is ever committed. Full
root-cause analysis: `docs/BUILD_ENVIRONMENT_JDK17.md`.

## Build identities

| Variant | Package | Ads | Signing | Purpose |
|---|---|---|---|---|
| `debug` | `com.mrzekai.depoakilli.debug` | Google sample | Stable repository QA key | Local development only |
| `qa` | `com.mrzekai.depoakilli.qa` | Google sample | Stable repository QA key | Installable smoke-test APK |
| `closedTest` | `com.mrzekai.depoakilli` | Google sample | Play upload key | Google Play closed testing |
| `release` | `com.mrzekai.depoakilli` | Production | Play upload key | Production rollout |

All release-like variants are non-debuggable and use R8 plus resource shrinking.

## Closed-test build

The manual `Play Closed Test AAB + APK` workflow requires:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `SUPPORT_EMAIL`

It runs project validation, dependency metadata checks, unit tests, `lintClosedTest`, `bundleClosedTest`, `assembleClosedTest`, signature comparison, package/version inspection, merged-manifest checks, and sample-ad verification.

Upload the signed AAB generated for version 1.0.4 (versionCode 50) to the appropriate Play track. The APK is for direct device verification; mapping and audit files are diagnostics.

See `docs/DEVICE_QA_CHECKLIST.md`, `docs/PERMISSIONS.md`, `docs/CLEANER_ENGINE.md` and `PLAY_RELEASE_CHECKLIST.md`.
