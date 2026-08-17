# Ad placements — v0.5.5

- Anchored adaptive banner has dedicated reserved layout space. On Smart Clean Results it sits below the sticky selected-size / Review & clean action; on the home shell it remains above bottom navigation.
- App Open remains subject to consent and foreground guards.
- Smart Clean interstitial is eligible only after the user finishes review and explicitly confirms cleanup. If a preloaded ad is available, it is shown at that transition; deletion starts from the dismissal/failure callback. If no ad is available, cleanup proceeds without blocking the user.
- Cleaner Engine permission/system-control transitions suppress the next App Open ad where appropriate.
- No ad may visually imitate a cleaner action, delete button, permission button, result card or system confirmation.

## v0.5.5 banner sizing

Home and Smart Clean result screens reserve a standard `AdSize.BANNER` 320x50 mobile banner slot. The app no longer reserves the taller large-adaptive container on phones.
