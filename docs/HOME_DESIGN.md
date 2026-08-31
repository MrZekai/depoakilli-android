# Home design - v0.5.18 closed-test candidate

The production home screen follows the selected dark blue neon concept.

## Locked visual structure

1. centered Smart Cleaner / Akıllı Temizleyici title with teal accent;
2. total cleanable card + cleaning-score ring;
3. large blue-to-green Smart Clean CTA;
4. dynamic scan findings and a short set of real storage actions;
5. Deep Clean recommendation banner;
6. bottom navigation: Home, Tools, Me.

There is no Pro/Premium badge because the app has no paid tier.

## Functional rule

No two home cards are aliases for the same action.

- Smart Clean: standard comprehensive cleaner scan.
- Deep Clean: broader review thresholds and exact duplicates.
- WhatsApp Cleaner: dedicated shared-media browser and deletion UI.
- Duplicate Files: exact content verification.
- Large Files: files >= 100 MB.
- APK Cleaner: installer packages only.
- Media Cleaner: screenshots, large photo/video files and media duplicates.
- Deep App Cache: StorageStats measurement + Android official cache-clean action.

Permissions &amp; privacy lives under Me instead of competing with the three primary navigation destinations. The full tool catalogue lives in Tools; Home does not duplicate it as a static grid.

The cleaning score is derived locally from storage occupancy plus the latest comprehensive scan's cleanable-space ratio. It is not a benchmark or a promise of device-speed improvement.
