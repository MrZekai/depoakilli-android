# Smart Cleaner — Play Release Checklist

## Required before signed release AAB
- Configure GitHub secret `SENTRY_DSN`.
- Keep the upload keystore backed up offline.
- Configure the existing Android signing secrets.
- Set the production Native AdMob unit in `ADMOB_RESULT_NATIVE_ID` if Native inventory is desired; blank intentionally falls back to MREC.

## MANAGE_EXTERNAL_STORAGE declaration
Use only the real core storage-manager reasons:
- exact duplicate discovery across accessible shared storage (size → sampled SHA-256 → full SHA-256),
- large-file review across file types,
- APK installer and old-download management,
- Storage Analyzer / user-controlled file management across accessible shared volumes.

Do **not** justify All Files Access with WhatsApp Cleaner, RAM boosting, generic system optimization, CPU cooling, battery boosting, or private app-data claims.

Prepare a short declaration video showing the in-app disclosure, Android permission screen, and Duplicate/Large Files/APK/Downloads/Storage Analyzer flows with explicit review before deletion.

## Data Safety / privacy
Declare, as applicable:
- Advertising ID / advertising data used by Google Mobile Ads.
- Diagnostics/crash data sent to Sentry when the release DSN is configured.
- Files and duplicate fingerprints remain on-device; Smart Cleaner does not upload file names, paths, content, or fingerprints to Sentry.
- Usage Access is optional and used for Android-reported app/cache/last-use metadata.
- Target audience: 13+ unless the store strategy changes.

Publish `PRIVACY_POLICY_EN.md` / `PRIVACY_POLICY_TR.md` at the public Privacy Policy URL used in Play Console.

## Play Console
- Complete Content Rating.
- Confirm "contains ads".
- Confirm Advertising ID use.
- Join/retain Play App Signing and preserve the upload key.
- Finish the required closed-test period before production access.
- Store listing must not use misleading RAM/CPU/battery/AI-optimization claims.

## Release binary gate
The release workflow must pass `scripts/verify-release-aab.sh` and show:
- package `com.mrzekai.depoakilli`,
- release not debuggable,
- no `.qa`,
- no Google sample AdMob IDs,
- no `FOREGROUND_SERVICE`, WorkManager `SystemForegroundService`, or debug `PreviewActivity`,
- measured AAB/universal APK size.

## Closed-test device priorities
1. Result Back: no Interstitial. Explicit Done may show one only when eligible.
2. Real AdMob: Banner, Native/MREC fallback, Interstitial, App Open after manifest cleanup.
3. Turkish/English `İndirilenler` / `INDIRILENLER`.
4. 100k+ files: Analyzer → category review should reuse the recent index.
5. SD card / secondary shared volume: Analyzer, Large Files, APK/Downloads and duplicates should see accessible files.
6. Delete hundreds of media files, open Gallery, and check for stale entries/thumbnails.
7. Storage Change must show live file/folder counters.
8. Screenshots: <30 days excluded; ≥30 days review-only and not preselected.
9. Verify Sentry receives a controlled test exception before production rollout.

## Slim QA APK
The external tester APK is `assembleQa`, not `assembleDebug`.
It is non-debuggable, uses the stable QA signing key and Google sample AdMob IDs,
and runs R8 + resource shrinking like release. Debug-only Compose tooling/test
manifest components must not be packaged. `scripts/verify-qa-apk.sh` is the gate.
Media3/ExoPlayer is intentionally retained because real video preview screens use it.
