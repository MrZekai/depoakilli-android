# Smart Cleaner v39 - Play closed-test handoff

## Immutable identity

- package: `com.mrzekai.depoakilli`
- versionName: `0.5.18-closedtest1`
- versionCode: `39`
- minSdk: 30
- target/compile SDK: 36
- debuggable: no
- R8/resource shrinking: enabled
- signing: existing Play upload key

## Advertising contract

The `closedTest` variant uses Google's official sample App, Banner, Interstitial, and Native IDs. It never embeds Smart Cleaner's production AdMob publisher ID. App Open is absent. Returning from Android settings, permission, cache, language, or uninstall screens is never gated by a full-screen ad.

## Workflow outputs

Run the manual GitHub Actions workflow `Play Closed Test AAB + APK`.

- `SmartCleaner-PLAY-CLOSED-TEST-AAB-v39` contains only `SmartCleaner-ClosedTest-v39.aab`. This is the only file to upload to Play Console.
- `SmartCleaner-CLOSED-TEST-APK-v39` contains `SmartCleaner-ClosedTest-v39.apk` for direct device installation and smoke testing.
- `SmartCleaner-ClosedTest-Diagnostics-v39` contains mapping, lint, signature/binary audit, hashes, and publication-ready legal Markdown with `SUPPORT_EMAIL` resolved.

The binary gate fails unless the AAB and APK have the same upload-certificate SHA-256, exact package/version, sample ad IDs, non-debuggable state, and no forbidden foreground/debug surfaces.

## Required GitHub secrets

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `SENTRY_DSN`
- `SUPPORT_EMAIL`

The workflow must stop if any required value is missing. Never add keystore material, passwords, the Sentry DSN, or support-contact substitutions to the repository.

## Play Console handoff

1. Publish the final privacy policy at a public, non-geofenced HTTPS webpage; do not use a PDF URL.
2. Ensure the policy, in-app contact, Play developer contact, and Data Safety answers are consistent.
3. Complete the All files access declaration using only the real core storage-manager functionality documented in `docs/PLAY_PERMISSIONS_V050.md`.
4. Declare that the app contains ads and uses Advertising ID where applicable.
5. Upload only `SmartCleaner-ClosedTest-v39.aab` to the intended closed-testing track.
6. Record the physical-device checklist before production access or rollout.
