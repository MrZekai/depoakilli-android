# v0.5.7 — Storage Review & Monetization UX

## Storage Analysis

Storage Analysis is no longer read-only. Tapping Videos, Images, Audio, Documents, APK, Archives or Other opens an in-screen full review page.

- media categories use thumbnail grids;
- non-media categories use readable file rows;
- files are never auto-selected;
- tapping content previews it;
- checkbox controls deletion selection;
- select-all / clear-all is available;
- selected count and bytes remain visible;
- Review & clean is available inside the category;
- selected storage files are deleted for real and the storage totals are reconciled after deletion.

A category review scans shared storage on demand and is capped at 50,000 displayed files as a memory-safety guard. The global shared-storage index remains capped at 200,000 files.

## Smart Clean category details

"View all" is now an in-screen review page instead of a modal list. It keeps the same preview/selection semantics and adds a scoped Review & clean action for that category.

## Ads

There is still only one visible 320x50 banner placement on the Clean Results shell. Full review pages render inside that shell so a second hidden banner is not created.

A deletion initiated from Smart Clean category review or Storage Analysis follows the same disclosed flow:

1. user selects files;
2. user taps Review & clean;
3. app shows a final irreversible-delete confirmation that discloses the possible interstitial;
4. if an eligible preloaded interstitial is available and cooldown permits, it is shown;
5. dismiss/failure callback continues deletion; if no ad is available, deletion continues without blocking.

This keeps the ad at an explicit task transition and preserves the existing five-minute interstitial cooldown.
