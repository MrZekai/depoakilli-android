# Smart Cleaner — Play Release Checklist

## Required before signed release AAB
- Configure GitHub secret `SUPPORT_EMAIL` with the public privacy/support address.
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
- Diagnostics remain on-device; Smart Cleaner does not upload file names, paths, content, fingerprints, or crash logs to a third-party crash-reporting service.
- Usage Access is optional and used for Android-reported app/cache/last-use metadata.
- Target audience: 13+ unless the store strategy changes.

Publish the resolved `PRIVACY_POLICY_EN.md` / `PRIVACY_POLICY_TR.md` content at a public, non-geofenced HTTPS webpage used in Play Console. Google Play does not accept a PDF as the policy URL. Keep the in-app contact, Play developer contact and policy address consistent.

## Play closed-test binary gate
Run the manual `Play Closed Test AAB + APK` workflow. It must show:
- package `com.mrzekai.depoakilli`, version `0.5.18-closedtest1` / `39`,
- AAB and APK signed by the same existing Play upload certificate,
- non-debuggable R8/resource-shrunk binaries,
- Google sample Banner, Interstitial and Native IDs,
- no production AdMob publisher ID or App Open ID,
- no `FOREGROUND_SERVICE`, WorkManager `SystemForegroundService`, or debug `PreviewActivity`.

Upload only `SmartCleaner-ClosedTest-v39.aab` from `SmartCleaner-PLAY-CLOSED-TEST-AAB-v39`. The APK and diagnostics artifacts are not Play uploads.

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
2. Ads: Banner, result Native/fallback and Interstitial only; returning to the foreground must never be ad-gated.
3. Turkish/English `İndirilenler` / `INDIRILENLER`.
4. 100k+ files: Analyzer → category review should reuse the recent index.
5. SD card / secondary shared volume: Analyzer, Large Files, APK/Downloads and duplicates should see accessible files.
6. Delete hundreds of media files, open Gallery, and check for stale entries/thumbnails.
7. Storage Change must show live file/folder counters.
8. Screenshots: recent and old accessible screenshots are visible; all remain review-only and not preselected.
9. Verify Android Vitals after the first Play closed-test sessions.

## Slim QA APK
The external tester APK is `assembleQa`, not `assembleDebug`.
It is non-debuggable, uses the stable QA signing key and Google sample AdMob IDs,
and runs R8 + resource shrinking like release. Debug-only Compose tooling/test
manifest components must not be packaged. `scripts/verify-qa-apk.sh` is the gate.
Media3/ExoPlayer is intentionally retained because real video preview screens use it.
