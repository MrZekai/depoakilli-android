# Physical device QA checklist

Record the device model, Android version, app version, and result for every run.

## Storage and memory accuracy

- Compare the app's total/free storage with Android Settings > Storage. Small differences caused by rounding or live writes are acceptable; multi-gigabyte differences are not.
- Capture `adb shell df -B1 /data` and compare the total/available byte values with the app.
- Observe the RAM card three times, one minute apart. Available RAM may change continuously because Android reclaims memory; total RAM should remain stable.
- Run RAM optimization after a completed scan. Confirm the scan result list is released, the progress indicator finishes, and the result reports measured app PSS and available RAM without inventing a fixed saving.
- Repeat RAM optimization with no scan results. It must still finish safely and must not claim that other applications were closed.
- Capture `adb shell dumpsys meminfo | head -n 20` for a diagnostic comparison. Do not expect the value to match consumer “RAM booster” apps.

## Cache tools

- Open “Clear app cache”; verify a success/empty message appears and its displayed size becomes 0 B or near zero.
- Open “App storage details”; verify Android opens this app's own storage/cache page.
- Open “Memory and apps”; verify Android opens the installed-app management screen.
- Open “Storage settings” and “App language”; verify each lands on a relevant Android screen and the Back button returns safely.

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
- Confirm the Home screen shows one 300×250 MREC and no anchored banner at the same time.
- Confirm AI Clean and Tools use the anchored banner and that no ad covers controls.
- Deny UMP consent where applicable and confirm the implementation follows the resulting `canRequestAds` state.

## Localization and resilience

- Switch between English and Turkish on Android 13+ and verify navigation, scan reasons, messages, and tool labels.
- Test a small-screen device and the largest system font; buttons and ad containers must not overlap.
- Rotate only through supported orientation behavior, background/foreground the app repeatedly, and check for crashes or ANRs.
