# Ad placements — closed-test candidate

- Home and cleaner surfaces use Google anchored adaptive Banner ads.
- Cleanup results may use the official Native media surface; when no Native unit is available, the configured result fallback is used.
- A cleanup result is always shown before any optional full-screen monetization.
- Back/system dismissal of the result never requests an Interstitial.
- Only an explicit Done action may hand off to an eligible, already-loaded Interstitial.
- If the result itself displayed an ad, no Interstitial is shown for that same cleanup.
- If no Interstitial is ready or eligible, navigation continues immediately; ads never block cleanup or app entry.
- Returning to the foreground never triggers a full-screen advertisement.
- No ad may imitate a cleaner action, delete button, permission button, result card, or Android confirmation.
