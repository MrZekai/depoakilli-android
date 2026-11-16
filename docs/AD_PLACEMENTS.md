# Ad placements (Faz 1B)

All limits live in `app/src/main/java/com/mrzekai/depoakilli/ads/AdPolicy.kt`.
Every format requires UMP consent (`canRequestAds`) and is off during the
60-minute ad-free window earned with a rewarded video. Debug, QA and
closed-test builds use Google's official sample units; release uses
Smart Cleaner's own units (public AdMob identifiers in `app/build.gradle.kts`).

| Format | Unit | Where | Limits |
|---|---|---|---|
| Banner (anchored adaptive) | existing banner unit | Clean and Tools tabs, detail screens | ≥24 dp from any action; not on Trash, settings, legal or permission screens; hidden during scans, cleanup and results |
| App open | `ca-app-pub-1380972808968213/1593948887` | loading cover on cold start; return after the user left via Home/Recents for ≥30 s | from the first launch (after consent); waits ≤3 s then content opens; every 4 h, ≤3/day; never after Android settings, permissions, cache dialog, uninstall, Play review or share |
| Interstitial | existing interstitial unit | (a) result closed with Done; (b) back on Clean after ≥20 s on a tool/detail screen | ≥180 s apart, ≤6/day, never before the first cleanup of the first session, never during scans, confirmation, preview or review flows |
| Native (list) | `ca-app-pub-1380972808968213/6870326296` | Clean: after the 2nd "Worth a look" row (≥6 rows); Tools: before the 2nd group; swipe review break card | at most 1 per screen; "Sponsored" badge, own card style; no gap when not filled |
| Native (result) | `ADMOB_RESULT_NATIVE_ID` if set, else the list native unit | cleanup result, below Done after a divider and 24 dp | — |
| MREC 300x250 (result fallback) | existing banner unit at `AdSize.MEDIUM_RECTANGLE` | cleanup result when native does not fill | — |
| Rewarded | existing rewarded unit | (1) 60 minutes without banner/full-screen ads; (2) "Watch and verify" deep duplicate check | the check also runs when no ad is ready; closing the ad early does not start it |

Any two full-screen ads (app open, interstitial, rewarded) are at least 60 s apart.
