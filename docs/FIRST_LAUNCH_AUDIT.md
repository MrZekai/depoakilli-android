# First launch audit (version 1.0.1 / 47)

Reviewed `DepoAkilliApplication.onCreate`, `MainActivity.onCreate`, consent and AdMob initialization, permission launchers, and the ViewModel's initial refresh.

- `Application.onCreate` only initializes local Logcat diagnostics.
- `MainActivity.onCreate` starts UMP consent collection and sets Compose content. UMP uses callbacks; the form can delay the first interaction when consent is required, but the call does not synchronously wait for a network response.
- `MobileAds.initialize` runs on a separate thread after consent permits ads.
- All files and Usage Access screens launch only from user actions. Their result callbacks trigger refreshes after return.
- Initial device storage, memory and own-cache reads use `Dispatchers.Default` or `Dispatchers.IO`. App cache and installed-app queries run in repository `withContext(Dispatchers.IO)`.
- The ViewModel restores a few local snapshots synchronously during construction. These are small app-owned records. No recursive file scan starts automatically on first launch.

No definite startup crash or ANR cause was found in this path, so no app behavior was changed. The reported 16 installs versus 3 first opens cannot be attributed to this code without Play Console Android vitals, pre-launch report or device logs. Check crashes/ANRs by version and device, then reproduce on affected devices before changing initialization or adding instrumentation.
