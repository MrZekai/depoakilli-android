# Home design — v0.5.1 Neon Cleaner Dashboard

The production home screen follows the selected dark blue neon concept.

## Locked visual structure

1. centered Smart Cleaner / Akıllı Temizleyici title with teal accent;
2. total cleanable card + cleaning-score ring;
3. large blue-to-green Smart Clean CTA;
4. first row: WhatsApp Cleaner, Duplicate Files, Large Files;
5. second row: APK Cleaner, Media Cleaner, Deep Cleaner;
6. third row: Deep App Cache + RAM Optimization;
7. Deep Clean recommendation banner;
8. bottom navigation: Home, Tools, Security, Me.

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
- RAM Optimization: releases Smart Cleaner's heavy in-memory scan/ad resources and remeasures RAM.

The cleaning score is derived locally from storage occupancy plus the latest comprehensive scan's cleanable-space ratio. It is not a benchmark or a promise of device-speed improvement.
