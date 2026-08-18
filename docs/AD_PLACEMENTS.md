# Ad placements — v0.5.8

- The home shell, Clean Results shell, and WhatsApp Cleaner shell reserve one standard `AdSize.BANNER` 320x50 mobile banner slot.
- Smart Clean category "View all" and Storage Analysis review pages render inside the Clean Results shell; they do **not** create a second banner or a hidden ad behind a modal dialog.
- App Open remains subject to consent and foreground guards.
- Smart Clean, Storage Analysis, and WhatsApp Cleaner interstitials are eligible only after the user explicitly selects files, opens the final irreversible-delete confirmation, and confirms cleanup.
- The confirmation discloses that an eligible interstitial can appear before deletion. The interstitial dismissal/failure callback continues the delete action. If no ad is loaded, the delete action proceeds without blocking.
- The existing interstitial cooldown and App Open separation remain active.
- No ad may visually imitate a cleaner action, delete button, permission button, result card or system confirmation.
- No ad is shown simply because a user opened a Storage Analysis/WhatsApp category or previewed a file.

## Banner sizing

The app reserves a standard `AdSize.BANNER` 320x50 mobile banner slot. It does not reserve the taller large-adaptive container on phones.
