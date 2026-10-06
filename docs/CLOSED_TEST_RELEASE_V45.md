# Smart Cleaner v45 - Play closed-test handoff

## Immutable identity

- package: `com.mrzekai.depoakilli`
- versionName: `1.0.0`
- versionCode: `45`
- minSdk: 30
- target/compile SDK: 36
- debuggable: no
- R8/resource shrinking: enabled
- signing: existing Play upload key

## Advertising contract

The `closedTest` variant uses Google's official sample App, Banner, Interstitial, and Native IDs. The closedTest variant uses Google's sample Rewarded ID `ca-app-pub-3940256099942544/5224354917`. It never embeds Smart Cleaner's production AdMob publisher ID. App Open is absent. Returning from Android settings, permission, cache, language, or uninstall screens is never gated by a full-screen ad.

## Workflow outputs

Run the manual GitHub Actions workflow `Play Closed Test AAB + APK`.

- `SmartCleaner-PLAY-CLOSED-TEST-AAB-v45` contains only `SmartCleaner-ClosedTest-v45.aab`. This is the only file to upload to Play Console.
- `SmartCleaner-CLOSED-TEST-APK-v45` contains `SmartCleaner-ClosedTest-v45.apk` for direct device installation and smoke testing.
- `SmartCleaner-ClosedTest-Diagnostics-v45` contains mapping, lint, signature/binary audit, hashes, and publication-ready legal Markdown with `SUPPORT_EMAIL` resolved.

The binary gate fails unless the AAB and APK have the same upload-certificate SHA-256, exact package/version, sample ad IDs, non-debuggable state, and no forbidden foreground/debug surfaces.

## Required GitHub secrets

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `SUPPORT_EMAIL`

The workflow must stop if any required value is missing. Never add keystore material, passwords, or support-contact substitutions to the repository.

## Play Console handoff

1. Publish the final privacy policy at a public, non-geofenced HTTPS webpage; do not use a PDF URL.
2. Ensure the policy, in-app contact, Play developer contact, and Data Safety answers are consistent.
3. Complete the All files access declaration using only the real core storage-manager functionality documented in `docs/PLAY_PERMISSIONS_V050.md`.
4. Declare that the app contains ads and uses Advertising ID where applicable.
5. Upload only `SmartCleaner-ClosedTest-v45.aab` to the intended closed-testing track.
6. Record the physical-device checklist before production access or rollout.

## QA audit v44 (6 October 2026) fixes

- Y-01: temp/tmp/temporary directory names do not auto-select personal documents. Only aged .tmp/.temp/.cache files and generated thumbnails qualify; interrupted downloads stay review-only.
- O-01: duplicate scanning returns protected original URIs. The shared result merger excludes those originals from every category, for exact and sampled groups.
- O-02: the result-ad slot reserves the same height while loading, displaying Native, or falling back to MREC; its size accounts for font scale.
- O-03: production version is 1.0.0 (45); closed-test artifact names and binary gates match.
- D-01/D-02: scanning labels wrap; duplicate tool titles use the same localized name.
- D-03/D-04: WhatsApp folder progress counts directories; R8 strips Log.d/i.

Regression tests cover personal documents in temporary directories, disposable extensions, interrupted downloads, and protected-original category merging.

## Physical-device verification still required

- Repeat Y-01, O-01 and O-02; select every result category and confirm the retained original survives.
- Check font scale 1.3, Turkish/Spanish text, and Native loading/failure/MREC transitions with no Done-button movement.
- Repeat QA #14-16 and rapid navigation/backgrounding during cleanup.
- Rate-us Back behavior remains dependent on Play Store/device behavior (O-04).
- Trash/undo and a new support address are future changes, not part of this patch.

Use the Release AAB workflow for production, with the existing upload key and required repository secrets. QA APK/AAB files use the .qa package and sample ads; do not upload them to production.
