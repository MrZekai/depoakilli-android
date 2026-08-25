# v0.5.4 — Smart Clean Final Results Template

- versionName: `0.5.4`
- versionCode: `11`
- Smart Clean Results now follows the selected dark neon results template used by the home dashboard family.
- Category cards are stacked vertically while each expanded category uses a left-to-right preview strip with up to four real files plus a `+N` entry.
- Tapping a file opens preview; checkboxes alone change cleanup selection.
- Category-level checkbox supports select-all / clear-all without changing the preview action.
- Photos and supported videos receive lightweight on-device thumbnails; details remain available for audio, APK, archive, document and other files.
- Storage Analyzer stays separate from cleanup selection and opens the real files behind each storage category.
- The sticky footer shows selected cleanable bytes and the main `Review & clean` action directly above the reserved anchored adaptive banner slot.
- Cleanup is guarded against duplicate concurrent executions.
- Final cleanup flow is: user review -> explicit confirmation -> eligible preloaded interstitial -> deletion starts automatically -> Android consent if required for protected media -> rescan after successful deletion.
- If no interstitial is available, cleanup continues instead of trapping the user.
