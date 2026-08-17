# Physical device QA checklist

Record the device model, Android version, app version, and result for every run.

## Storage and memory accuracy

- Compare the app's total/free storage with Android Settings > Storage. Small differences caused by rounding or live writes are acceptable; multi-gigabyte differences are not.
- Capture `adb shell df -B1 /data` and compare the total/available byte values with the app.
- Observe the RAM card three times, one minute apart. Available RAM may change continuously because Android reclaims memory; total RAM should remain stable.
- Run app-memory cleanup after a completed scan. Confirm scan results and selections remain intact, the progress indicator finishes, and the result reports measured app PSS and available RAM without inventing a fixed saving.
- Repeat RAM optimization with no scan results. It must still finish safely and must not claim that other applications were closed.
- Capture `adb shell dumpsys meminfo | head -n 20` for a diagnostic comparison. Do not expect the value to match consumer “RAM booster” apps.

## Cache tools

- Run Smart Scan with several gigabytes of other-app cache reported by Android. Verify the large protected-cache value is shown separately and is never added to the green cleanable total or the Clean button.
- Select Smart Cleaner’s own cache and accessible files. Confirm the Clean button equals exactly the sum of those selected items.
- Approve cleanup and verify the result never claims that another app’s private cache was deleted.
- Revoke Usage Access and confirm Smart Scan still works without a fake other-app cache total.

## WhatsApp Cleaner

- With no folder connected, open WhatsApp Cleaner and verify the in-app explanation appears before Android’s folder picker.
- Test both WhatsApp and WhatsApp Business Media trees. Reject an unrelated directory and verify a clear error message.
- Scan a Media tree containing at least one image, video, document, audio file, voice note, sticker/GIF, and unknown file. Verify every item appears under the correct category.
- Confirm the progress UI reaches 100% only after traversal and classification finish.
- Confirm no item is preselected and image/video thumbnails load without blocking scrolling.
- Select a whole category, deselect one item, open the deletion confirmation, and cancel. No file may disappear.
- Confirm deletion and verify only selected documents are removed from the provider and the displayed/free-space totals update.
- Include a read-only or provider-rejected document; verify partial cleanup reports the failed count without hiding that row.

## Scan and deletion

- Test full media permission, limited photo access (Android 14+), denial, and permission revocation.
- Run the scan with at least 2,000 media items; the UI must remain responsive.
- Confirm every suggested duplicate is actually identical before deletion.
- Cancel Android's deletion confirmation; no item may disappear from the results.
- Approve deletion; confirm only selected files disappear and free storage increases plausibly.

## Ads and consent

- Clear app data, launch twice, and confirm no App Open ad is shown.
- On the third foreground transition, confirm a Google test App Open ad may appear if loaded; app startup must continue if it is unavailable.
- Confirm a second App Open ad cannot appear within two hours.
- Confirm Home, Clean, and Tools show one anchored 320×50 banner above navigation and no 300×250 MREC.
- Start and finish a scan while watching the bottom bar. Banner space and navigation must not jump; no new banner request should be caused solely by scan state.
- Return from media permission, WhatsApp SAF selection, Android Settings, and the system delete confirmation. App Open must be suppressed and cleanup may evaluate at most one interstitial.
- On a 5,000+ item media library, confirm the oldest/large-file pass finds eligible old screenshots or large videos and displays the bounded-scan note.
- For an exact copy in `DCIM/Camera` and `Download`, confirm only the downloaded copy is preselected and the Camera original is named as protected.
- Deny UMP consent where applicable and confirm the implementation follows the resulting `canRequestAds` state.

## Localization and resilience

- Switch between English and Turkish on Android 13+ and verify navigation, scan reasons, messages, and tool labels.
- Test a small-screen device and the largest system font; buttons and ad containers must not overlap.
- Rotate only through supported orientation behavior, background/foreground the app repeatedly, and check for crashes or ANRs.
- Open Device Center and compare storage/RAM with Android Settings; verify battery, Android/API, CPU ABI/core count, resolution, and app version are populated.
- Open Settings and verify rate, feedback, share, Privacy Policy, Terms of Service, About, and available ad privacy controls.
