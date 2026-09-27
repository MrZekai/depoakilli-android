# Smart Cleaner v44 - Play closed-test handoff

## Immutable identity

- package: `com.mrzekai.depoakilli`
- versionName: `0.5.23-closedtest1`
- versionCode: `44`
- minSdk: 30
- target/compile SDK: 36
- debuggable: no
- R8/resource shrinking: enabled
- signing: existing Play upload key

## Advertising contract

The `closedTest` variant uses Google's official sample App, Banner, Interstitial, and Native IDs. The closedTest variant uses Google's sample Rewarded ID `ca-app-pub-3940256099942544/5224354917`. It never embeds Smart Cleaner's production AdMob publisher ID. App Open is absent. Returning from Android settings, permission, cache, language, or uninstall screens is never gated by a full-screen ad.

## Workflow outputs

Run the manual GitHub Actions workflow `Play Closed Test AAB + APK`.

- `SmartCleaner-PLAY-CLOSED-TEST-AAB-v44` contains only `SmartCleaner-ClosedTest-v44.aab`. This is the only file to upload to Play Console.
- `SmartCleaner-CLOSED-TEST-APK-v44` contains `SmartCleaner-ClosedTest-v44.apk` for direct device installation and smoke testing.
- `SmartCleaner-ClosedTest-Diagnostics-v44` contains mapping, lint, signature/binary audit, hashes, and publication-ready legal Markdown with `SUPPORT_EMAIL` resolved.

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
5. Upload only `SmartCleaner-ClosedTest-v44.aab` to the intended closed-testing track.
6. Record the physical-device checklist before production access or rollout.

## QA report v43 (27 September 2026) - resolution

Device: vivo V2029, Android 12. 16 findings (1 high, 11 medium, 4 low), no crashes or ANRs.

| # | Finding | Resolution |
|---|---------|------------|
| 01 | `.nomedia` and WhatsApp status files preselected | `.nomedia`/`.hidden` marker files are never indexed and are refused at deletion; nothing inside WhatsApp media is preselected; section checkboxes show a partial state |
| 02 | APK installers preselected as safe | APKs are listed for review and never preselected, whatever their age |
| 03 | App cache shown as 0 B without Usage Access | Tools hero and App Cache Manager say "not measured – allow Usage Access" |
| 04 | Home suggestion sizes truncated | Amount on its own full-width line, may wrap, never ellipsized |
| 05 | Layout breaks at large font | Flexible heights for bottom bars and cards, weighted hero stats, gauge text fixed to the ring size |
| 06 | Stale "Old downloads" title on Duplicate Cleaner | Any new scan and every result dismissal close open sub-lists; system Back closes a tool group first |
| 07 | Copies labelled "Original protected" | Cards read "Copy of <original>" |
| 08 | Uninstall offered for system apps | System apps show "App info"; excluded from unused-app removal list |
| 09 | Back from Rate us stays in Play Store | Listing opens as a separate document task, Back returns to the app |
| 10 | Send feedback forces mail setup | Dialog shows the selectable address, Copy button, and an app chooser |
| 11 | Headline counts personal videos | Headline is the safe amount; personal media is a separate "worth reviewing" figure |
| 12 | Totals differ between screens | Duplicates keep their duplicate classification in Deep Clean; WhatsApp card states its scope |
| 13 | Descriptions truncated | Tool descriptions and section titles wrap |
| 14 | Section checkbox / badge inconsistent | Tri-state section checkboxes; badge replaced by one "Selected: X" line |
| 15 | Inactive icons | Home shield is a static badge; WhatsApp sparkle removed |
| 16 | "1 files removed" | Count strings are Android plurals in en, tr, es, pt-BR |

Also: screen recordings are no longer classified as screenshots; every delete confirmation lists file examples; ad disclosure no longer styled like a link; tool names unified; Me list keeps its scroll position; "Select files to clean" hint when nothing is selected.

Not changed on purpose: the bottom banner keeps its reserved space before the ad loads, so content never shifts under the user's finger when the ad appears (accidental-click protection). The ad-consent entry is shown when Google's consent SDK reports it is required, as Google specifies.
