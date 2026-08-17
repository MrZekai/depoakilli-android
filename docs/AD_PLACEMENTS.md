# Ad placements — v0.5.0

- Anchored banner remains above the bottom navigation with reserved layout space.
- App Open remains subject to consent and foreground guards.
- Interstitial remains post-cleanup only; it must never interrupt scanning, permission education, Android All files access, Android cache-clean confirmation, WhatsApp review, or deletion confirmation.
- Cleaner Engine permission/system-control transitions suppress the next App Open ad where appropriate.
- No ad may visually imitate a cleaner action, delete button, permission button, result card or system confirmation.
