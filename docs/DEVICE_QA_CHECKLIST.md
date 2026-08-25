# Cleaner Engine 0.5 physical-device QA checklist

Record device model, Android version, app version, available storage, WhatsApp/Business presence, and pass/fail evidence for every run.

## 1. First-run access and recovery

- Launch with All Files Access disabled. Home and Tools must explain why storage-management access is needed; Smart Scan must not fabricate results.
- Grant All Files Access through Android's official settings screen, return to Smart Cleaner, and confirm the app resumes to its own UI without a folder picker.
- Revoke All Files Access while the app is backgrounded, return, and confirm scans are gated safely rather than crashing.
- Grant and revoke Usage Access. Cache/App Manager sizes may become unavailable, but file cleaning must continue to work.
- On Android 13+, open app-language settings from Smart Cleaner. On Android 11–12, the fallback must open device language settings instead of crashing.

## 2. Smart Scan / Deep Cleaner

- Seed the device with temp files, old Downloads, APK installers, screenshots, large files, exact duplicates, documents, archives, audio and video.
- Run Smart Scan from Home. Progress must show real scanned file/folder counts and remain responsive on a large library.
- Verify the cleanable total contains only accessible selected files; another application's protected/private cache must never be counted as directly deletable storage.
- Verify categories match the actual files found: Junk, Duplicates, Screenshots/Media, Large Files, Old Downloads, APK packages and WhatsApp media where applicable.
- Cancel a cleanup confirmation and verify no file is deleted.
- Approve cleanup and verify only selected files are removed and the free-space change is plausible.
- Run the same scan again and verify deleted items do not reappear from stale state.

## 3. Junk / Downloads / APK

- Test `.tmp`, `.temp`, `.part`, `.crdownload`, `.download`, `.cache` and old temp-folder files. Only conservative, old temporary files may be preselected.
- Put old and new APK files in shared storage. Old installers should be discoverable; recent installers must not be silently auto-deleted.
- Put old personal documents in Downloads. They may be recommended for review but must not be preselected merely because they are old.
- Verify protected paths such as other apps' private `/Android/data` and `/Android/obb` are not traversed or represented as directly cleanable.

## 4. Duplicate Cleaner

- Create byte-identical copies in Camera, Download, Documents and a large-file folder, including files larger than 40 MB.
- Verify the scanner finds large duplicates; the old 40 MB, 20-file group and 100-hash-group limits must not exist.
- For identical Camera and Download files, verify the Camera/original candidate is protected and a safer duplicate is selected.
- Create same-size but different-content files; they must not be reported as exact duplicates.
- Test very large files and confirm streaming hashing does not load the whole file into memory or freeze the UI.

## 5. WhatsApp Cleaner — entirely in Smart Cleaner after access

- With All Files Access granted, open WhatsApp Cleaner. It must NOT launch `OpenDocumentTree`, Android's folder picker or SAF directory navigation.
- Test WhatsApp and WhatsApp Business under supported `Android/media` layouts.
- Scan a library containing Images, Videos, Documents, Audio, Voice Notes, Stickers/GIFs, Sent media, Status media and large files.
- Progress must reach 100% only after direct traversal/classification completes.
- Image, sticker/GIF and video rows should show in-app thumbnails when the local format supports Android thumbnail decoding; other files use category icons.
- Open category selection, deselect individual items, cancel deletion, then confirm deletion. Only selected files may disappear.
- Include an unreadable or deletion-failed file; partial failure must be reported rather than pretending the entire cleanup succeeded.
- Reopen WhatsApp Cleaner after app restart. It must remain usable without reconnecting a folder as long as All Files Access remains granted.

## 6. Deep App Cache

- Grant Usage Access and compare measured app cache/storage sizes with Android Settings for several launcher apps. Small timing differences are acceptable; fabricated totals are not.
- Trigger Deep App Cache cleanup. Android's official `ACTION_CLEAR_APP_CACHE` confirmation may appear; Smart Cleaner must not claim silent deletion of protected internal caches.
- Cancel the system action and confirm Smart Cleaner does not report bytes as deleted.
- Complete the supported system action and refresh cache measurements.
- Clear Smart Cleaner's own cache separately and verify it is explicitly labelled as this app's cache, never as the phone-wide cache cleaner.

## 7. Large Files / Media Cleaner / Storage Analyzer

- Place large ZIP, PDF, APK, audio, video and backup files in shared storage. Large Files must not be video-only.
- Storage Analyzer totals by Images, Videos, Audio, Documents, Archives, APK and Other should reconcile plausibly with indexed shared-storage bytes.
- Media Cleaner must identify review candidates without automatically deleting personal photos merely for age or size.
- Screenshots must list accessible recent and old screenshots, including a 1-day-old sample, and none may be preselected.
- Test a 10,000+ file library and watch for ANR, runaway memory or permanently stuck scan progress.

## 8. App Manager

- With Usage Access granted, verify visible launcher apps show package/name and available storage/cache/last-use data where Android permits it.
- The implementation must not depend on `QUERY_ALL_PACKAGES`.
- Trigger uninstall and verify Android's official uninstall confirmation handles the operation; cancellation must return safely to Smart Cleaner.

## 9. RAM Optimization

- Run RAM Optimization before and after a scan. It may release Smart Cleaner's own heavy/ad resources and report measured state, but must not claim it killed other apps or recovered fabricated gigabytes.
- Repeat multiple times and verify no fixed percentage speedup or fake RAM saving is shown.
- Background/foreground the app after optimization and ensure ads, scan state and navigation recover normally.

## 10. UI / tools / localization

- Home must prominently expose Smart Scan plus real cleaner modules: Junk, Duplicates, Large Files, Media, WhatsApp, Deep App Cache and Downloads/APK.
- Tools must contain actionable cleaner/storage modules, not battery/CPU/RAM status cards disguised as cleaning tools.
- Test Home, Clean, Tools, Settings, App Manager, App Cache and WhatsApp at largest system font and on a small screen.
- English is the default/fallback language. Turkish devices use Turkish resources. Unsupported locales must fall back cleanly to English without broken resource keys.
- Verify RTL/system layout remains usable even when the text falls back to English.

## 11. Ads / consent / navigation

- Clear app data and verify consent flow, banner placement and Interstitial cooldown behavior remain intact.
- No ad should cover a cleanup confirmation, system permission rationale, selection checkbox or primary cleaner CTA.
- Return from All Files Access, Usage Access, Deep Cache system action, language settings and uninstall confirmation. The app must resume immediately without a foreground-return advertisement.
- Confirm no obsolete 300x250 MREC configuration exists.

## 12. Regression / release gate

- `python scripts/validate-project.py` must pass.
- `testDebugUnitTest`, `lintDebug`, `assembleDebug` and QA signing verification must all pass in GitHub Actions.
- Install the generated `depoakilli-test-apk-*` artifact over the prior QA build and verify version `0.5.1` / versionCode `8`.
- Do not promote to Production until the physical-device cases above are recorded on Android 11, 12, 13, 14, 15 and 16 where devices are available.
