# Ad placements (Faz 5 "Make Room", Faz 5B, Faz 5C)

All limits live in `app/src/main/java/com/mrzekai/depoakilli/ads/AdPolicy.kt`
and are unchanged in Faz 5. Every format requires UMP consent
(`canRequestAds`) and is off during the 60-minute ad-free window earned with a
rewarded video. Debug, QA and closed-test builds use Google's official sample
units; release uses Smart Cleaner's own units (public AdMob identifiers in
`app/build.gradle.kts`). Basic cleaning never depends on watching an ad.

| Screen | Ad | Rule |
|---|---|---|
| App start | App open | From the first launch after consent; waits ≤3 s; every 2 h, ≤4/day; no cover when offline; never after Android settings, permissions, cache dialog, uninstall, Play review or share. |
| Home, Tools, Trash | Banner (anchored adaptive) | Fixed above the bottom menu, 24 dp from it. |
| Access disclosure and confirmation sheets | None | Move to Trash, permanent delete, Trash fallback. |
| Scanning | None | The banner is hidden on every screen while `state.scanning`. |
| Scan finished → Findings | Interstitial (`OPEN_FINDINGS`) | Existing `canShowNow`: ≥120 s apart, ≤8/day, ≥60 s between full-screen ads; never before the first cleanup of the first session. |
| Findings | Small native in the list + banner | Faz 5C: after the 3rd row when there are ≥4 rows (`NATIVE_FINDINGS_AFTER_ROW`, `NATIVE_FINDINGS_MIN_ROWS`). |
| Swipe review | Native break card | Every 8 cards (`SWIPE_BREAK_EVERY`); no banner. |
| Swipe review finished → Result | Interstitial (`SWIPE_FINISHED`) | Same limits; skipped when the 60 s gap has not passed. |
| Result | Large native (MREC 300×250 fallback) | Below the buttons, after a 24 dp gap; `ADMOB_RESULT_NATIVE_ID` if set, else the list native unit. |
| Tool scan finished → tool result | Interstitial (`TOOL_RESULT`) | Faz 5B. Same limits (120 s, 8/day, 60 s between full-screen ads, first-session rule). Also after a finished WhatsApp scan. Not when the result came straight from the cached storage index (the user did not wait), not after a cancelled scan, not after Smart/Deep scans (they have their own triggers). |
| Tool lists | Small native in the list | Faz 5C: after the 3rd row when there are ≥6 rows (`NATIVE_LIST_AFTER_ROW`, `NATIVE_LIST_MIN_ROWS`); no banner. |
| App Manager, App Cache Manager | Small native in the list | After the 3rd app row when there are ≥6 rows; no banner. |
| WhatsApp categories | Native in the list | Faz 5B. After the 2nd category card (`NATIVE_GROUP_LIST_AFTER_CARD`) when a 3rd card follows and the list holds ≥6 files; no banner. |
| Storage Analyzer | Native in the list | Faz 5B. After the 2nd type card when there are ≥6 type cards. |
| Settings | Rewarded | 60 minutes without banner and full-screen ads. Also "Watch and verify" for the deep duplicate check, which runs even when no ad is ready. |

Interstitials also keep their earlier triggers with the same limits: the
result closed with Done, and the return to Home after ≥20 s on a tool or
detail screen.

- Faz 5C: list natives are the small, row-sized card (≈ 96 dp: icon, "Ad"
  label, headline, one line of text, call to action; no media view). The
  large native with media is used only on the result screen. Same ad unit.
- Every native card shows an "Ad" label on the AdLabel colour; the result
  screen shows the label above the card instead of inside it.
- At least 24 dp between an ad and the nearest button.
- Any two full-screen ads (app open, interstitial, rewarded) are at least 60 s apart.
