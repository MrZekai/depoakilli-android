package com.mrzekai.depoakilli

import android.app.Activity
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.play.core.review.ReviewManagerFactory
import com.mrzekai.depoakilli.ads.ConsentManager
import com.mrzekai.depoakilli.ads.AdFrequencyStore
import com.mrzekai.depoakilli.ads.AdPolicy
import com.mrzekai.depoakilli.ads.AppOpenCover
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import com.mrzekai.depoakilli.ads.AppOpenAdController
import com.mrzekai.depoakilli.ads.InterstitialAdController
import com.mrzekai.depoakilli.ads.InterstitialTrigger
import com.mrzekai.depoakilli.ads.RewardedAdController
import com.mrzekai.depoakilli.data.DeviceRepository
import com.mrzekai.depoakilli.diagnostics.AppDiagnostics
import com.mrzekai.depoakilli.ui.CleanerApp
import com.mrzekai.depoakilli.ui.CleanerViewModel
import com.mrzekai.depoakilli.ui.theme.DepoAkilliTheme

class MainActivity : ComponentActivity() {
    private val cleanerViewModel: CleanerViewModel by viewModels()
    private lateinit var consentManager: ConsentManager
    private lateinit var interstitialAds: InterstitialAdController
    private lateinit var adFrequency: AdFrequencyStore
    private lateinit var appOpenAds: AppOpenAdController
    private lateinit var rewardedAds: RewardedAdController
    private var pendingDeepCacheAfterStorageAccess = false
    private var reviewPromptInFlight = false

    private val allFilesAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        cleanerViewModel.refreshDeviceState(force = true)
        if (Environment.isExternalStorageManager()) {
            AppDiagnostics.breadcrumb("permission_granted", mapOf("type" to "all_files"))
            cleanerViewModel.showMessage(R.string.message_all_files_granted)
            cleanerViewModel.resumePendingScanAfterPermission()
            if (pendingDeepCacheAfterStorageAccess) {
                pendingDeepCacheAfterStorageAccess = false
                requestDeepCacheCleanup()
            }
        } else {
            pendingDeepCacheAfterStorageAccess = false
        }
    }

    private val usageAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (DeviceRepository(applicationContext).hasUsageAccess()) {
            AppDiagnostics.breadcrumb("permission_granted", mapOf("type" to "usage_access"))
        }
        cleanerViewModel.refreshDeviceState(force = true)
        cleanerViewModel.refreshAppCaches(force = true)
        cleanerViewModel.refreshInstalledApps()
    }

    private val appDetailsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        cleanerViewModel.onIndividualAppCacheSettingsReturned()
    }

    private val deepCacheLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val approved = result.resultCode == Activity.RESULT_OK
        cleanerViewModel.onDeepCacheCleanupResult(approved)
    }

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val approved = result.resultCode == Activity.RESULT_OK
        cleanerViewModel.completeCleanup(approved)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The UI is always dark, so both system bars use light icons on a
        // transparent bar regardless of the device's light/dark setting.
        val darkBars = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = darkBars, navigationBarStyle = darkBars)
        // Phones stay portrait; tablets, foldables and desktop windows are
        // free to rotate and resize (Play large-screen quality guideline).
        if (resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        consentManager = ConsentManager(applicationContext)
        adFrequency = AdFrequencyStore(applicationContext)
        if (savedInstanceState == null) adFrequency.recordLaunch()
        interstitialAds = InterstitialAdController(applicationContext, adFrequency)
        rewardedAds = RewardedAdController(applicationContext)
        appOpenAds = AppOpenAdController(applicationContext, adFrequency)
        consentManager.gatherConsent(this)
        if (savedInstanceState == null) {
            val app = application as DepoAkilliApplication
            appOpenAds.onColdStart(
                activity = this,
                adsAllowedNow = consentManager.canRequestAds.value && !app.adFreeWindow.isActiveNow(),
                otherFullScreenActive = { app.fullScreenAdSurfaceActive.value },
            )
        }

        setContent {
            val canRequestAds by consentManager.canRequestAds.collectAsStateWithLifecycle()
            val privacyOptionsRequired by consentManager.privacyOptionsRequired.collectAsStateWithLifecycle()
            val app = application as DepoAkilliApplication
            val adFreeRemainingMillis by app.adFreeWindow.remainingMillis.collectAsStateWithLifecycle()
            val rewardedReady by rewardedAds.isReady.collectAsStateWithLifecycle()
            val fullScreenAdActive by app.fullScreenAdSurfaceActive.collectAsStateWithLifecycle()
            val appOpenCover by appOpenAds.coverVisible.collectAsStateWithLifecycle()
            val adFreeActive = adFreeRemainingMillis > 0L
            LaunchedEffect(canRequestAds, adFreeActive) {
                interstitialAds.setAdsAllowed(canRequestAds && !adFreeActive)
                appOpenAds.setAdsAllowed(canRequestAds && !adFreeActive)
                rewardedAds.setAdsAllowed(canRequestAds && !adFreeActive)
            }

            DepoAkilliTheme {
                Box(Modifier.fillMaxSize()) {
                    CleanerApp(
                        viewModel = cleanerViewModel,
                        // A rewarded ad promises an ad-free window. Pass one
                        // effective UI flag to every ad surface, including the
                        // cleanup-result Native/MREC placement.
                        canRequestAds =
                            canRequestAds &&
                                adFreeRemainingMillis <= 0L &&
                                !fullScreenAdActive,
                        fullScreenAdActive = fullScreenAdActive,
                        appOpenCoverVisible = appOpenCover,
                        privacyOptionsRequired = privacyOptionsRequired,
                        adFreeRemainingMillis = adFreeRemainingMillis,
                        rewardedAdReady = rewardedReady,
                        onRequestAllFilesAccess = ::requestAllFilesAccess,
                        onRequestUsageAccess = ::requestUsageAccess,
                        onClearAllAppCaches = ::requestDeepCacheCleanup,
                        onOpenAppDetails = ::openAppCacheSettings,
                        onPrepareCleanup = ::cleanSelected,
                        onPrepareStorageCleanup = ::cleanStorage,
                        onPrepareWhatsAppCleanup = ::cleanWhatsApp,
                        onCleanupResultDismissed = ::onCleanupResultDismissed,
                        onCleanupSucceeded = ::onCleanupSucceeded,
                        onReturnedToClean = ::onReturnedToClean,
                        onUninstallApp = ::uninstallApp,
                        onOpenAppInfo = ::openAppInfo,
                        onOpenLanguageSettings = ::openLanguageSettings,
                        onShowPrivacyOptions = ::showPrivacyOptions,
                        onShowRewardedAd = ::showRewardedAd,
                        onRateApp = ::rateApp,
                        onSendFeedback = ::sendFeedback,
                        onShareApp = ::shareApp,
                    )
                    if (appOpenCover) AppOpenCover()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        cleanerViewModel.refreshDeviceState()
        cleanerViewModel.refreshAppCaches()
        if (::interstitialAds.isInitialized) interstitialAds.onHostResumed(this)
        if (::rewardedAds.isInitialized) rewardedAds.onHostResumed(this)
        val app = application as DepoAkilliApplication
        app.adFreeWindow.refresh()
        if (::appOpenAds.isInitialized) {
            appOpenAds.onReturnedToForeground(
                activity = this,
                otherFullScreenActive = app.fullScreenAdSurfaceActive.value,
                blocked = reviewPromptInFlight || app.adFreeWindow.isActiveNow(),
            )
        }
    }

    /** Home/Recents pressed by the user; screens the app opens itself do not call this. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (::appOpenAds.isInitialized) appOpenAds.onUserLeftApp()
    }

    override fun onPause() {
        if (::interstitialAds.isInitialized) interstitialAds.onHostPaused(this)
        if (::rewardedAds.isInitialized) rewardedAds.onHostPaused(this)
        super.onPause()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND &&
            ::interstitialAds.isInitialized &&
            (application as DepoAkilliApplication).isSystemUnderMemoryPressure()
        ) {
            interstitialAds.releaseCachedAd()
            if (::rewardedAds.isInitialized) rewardedAds.releaseCachedAd()
        }
    }

    private fun cleanSelected(itemIds: Set<String>? = null) {
        executeCleanupPlan(itemIds)
    }

    private fun cleanStorage() {
        cleanerViewModel.deleteSelectedStorageReview {
            cleanerViewModel.refreshDeviceState(force = true)
        }
    }

    private fun cleanWhatsApp(onFinished: (Boolean) -> Unit) {
        cleanerViewModel.deleteSelectedWhatsApp { changed ->
            cleanerViewModel.refreshDeviceState(force = true)
            onFinished(changed)
        }
    }

    private fun onCleanupResultDismissed() {
        // The destructive operation and measured result are already complete.
        // Monetization happens only after the user closes the result.
        cleanerViewModel.refreshDeviceState(force = true)
        // Never stack a full-screen ad on top of the Play rating sheet.
        if (reviewPromptInFlight) return
        showPostTaskInterstitial()
    }

    /**
     * Google Play in-app review, requested right after a successful cleanup.
     * Play decides whether the sheet is actually shown (its own quota); this
     * app additionally asks at most once every [REVIEW_PROMPT_INTERVAL_MILLIS].
     */
    private fun onCleanupSucceeded() {
        adFrequency.recordCleanupCompleted()
        maybeAskForReview()
    }

    /** Trigger (b): back on the Clean tab after at least 20 s on a tool or detail screen. */
    private fun onReturnedToClean(dwellMillis: Long) {
        if (reviewPromptInFlight) return
        showPostTaskInterstitial(trigger = InterstitialTrigger.RETURN_TO_CLEAN, dwellMillis = dwellMillis)
    }

    private fun maybeAskForReview() {
        val prefs = getSharedPreferences("review_prompt", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_asked", 0L) < REVIEW_PROMPT_INTERVAL_MILLIS) return
        reviewPromptInFlight = true
        val manager = ReviewManagerFactory.create(this)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful && !isFinishing && !isDestroyed) {
                prefs.edit().putLong("last_asked", now).apply()
                manager.launchReviewFlow(this, request.result)
                    .addOnCompleteListener { reviewPromptInFlight = false }
            } else {
                reviewPromptInFlight = false
            }
        }
    }

    private fun showPostTaskInterstitial(
        trigger: InterstitialTrigger = InterstitialTrigger.CLEANUP_DONE,
        dwellMillis: Long = Long.MAX_VALUE,
        onFinished: () -> Unit = {},
    ) {
        if ((application as DepoAkilliApplication).adFreeWindow.isActiveNow()) {
            onFinished()
            return
        }
        val app = application as DepoAkilliApplication

        // Short neutral settle surface prevents the cleanup-confirm tap from
        // carrying into a suddenly appearing full-screen ad.
        app.beginInterstitialSurface()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            {
                interstitialAds.showAtNaturalBreak(
                    activity = this,
                    trigger = trigger,
                    dwellMillis = dwellMillis,
                    onWillShow = {},
                    onFinished = {
                        app.endInterstitialSurface()
                        onFinished()
                    },
                )
            },
            POST_TASK_AD_SETTLE_MILLIS,
        )
    }

    private fun executeCleanupPlan(itemIds: Set<String>? = null) {
        cleanerViewModel.prepareCleanup(
            itemIds = itemIds,
            onPlanReady = { plan ->
                if (plan is DeviceRepository.DeletePlan.RequiresConsent) {
                    runCatching {
                        deleteLauncher.launch(
                            IntentSenderRequest.Builder(plan.pendingIntent.intentSender).build(),
                        )
                    }.onFailure {
                        cleanerViewModel.completeCleanup(false)
                        cleanerViewModel.showMessage(R.string.message_screen_unavailable)
                    }
                }
            },
            onCleanupCompleted = {
                // Result state is already produced by CleanerViewModel.
                // Never gate that result on a Google Mobile Ads callback.
            },
        )
    }

    private fun requestAllFilesAccess() {
        val appIntent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        if (runCatching { allFilesAccessLauncher.launch(appIntent) }.isFailure) {
            runCatching { allFilesAccessLauncher.launch(fallback) }
                .onFailure { cleanerViewModel.showMessage(R.string.message_screen_unavailable) }
        }
    }

    private fun requestUsageAccess() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { usageAccessLauncher.launch(intent) }
            .onFailure {
                runCatching { usageAccessLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    .onFailure { cleanerViewModel.showMessage(R.string.message_screen_unavailable) }
            }
    }

    private fun requestDeepCacheCleanup() {
        if (!Environment.isExternalStorageManager()) {
            pendingDeepCacheAfterStorageAccess = true
            cleanerViewModel.showMessage(R.string.message_all_files_required_for_cache)
            requestAllFilesAccess()
            return
        }
        cleanerViewModel.beginDeepCacheCleanupMeasurement()
        runCatching {
            deepCacheLauncher.launch(Intent(StorageManager.ACTION_CLEAR_APP_CACHE))
        }.onFailure {
            cleanerViewModel.onDeepCacheCleanupResult(false)
            cleanerViewModel.showMessage(R.string.message_screen_unavailable)
        }
    }

    private fun openAppCacheSettings(packageName: String) {
        cleanerViewModel.beginIndividualAppCacheMeasurement(packageName)

        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName"),
        )

        runCatching {
            appDetailsLauncher.launch(intent)
        }.onFailure {
            cleanerViewModel.cancelIndividualAppCacheMeasurement()
            cleanerViewModel.showMessage(R.string.message_screen_unavailable)
        }
    }

    private fun uninstallApp(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }
            .onFailure { cleanerViewModel.showMessage(R.string.message_screen_unavailable) }
    }

    private fun openAppInfo(packageName: String) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName"),
        )
        runCatching { startActivity(intent) }
            .onFailure { cleanerViewModel.showMessage(R.string.message_screen_unavailable) }
    }

    private fun openLanguageSettings() {
        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Intent(
                Settings.ACTION_APP_LOCALE_SETTINGS,
                Uri.parse("package:$packageName"),
            )
        } else {
            Intent(Settings.ACTION_LOCALE_SETTINGS)
        }
        runCatching { startActivity(intent) }
            .onFailure { cleanerViewModel.showMessage(R.string.message_screen_unavailable) }
    }

    private fun rateApp() {
        // The listing opens as its own document task that is dropped from
        // history when left. Back then closes it and returns here, instead of
        // landing on the Play Store home page (QA v43 #09).
        val storeFlags = Intent.FLAG_ACTIVITY_NO_HISTORY or
            Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$PLAY_PACKAGE_NAME"),
        ).apply {
            setPackage(PLAY_STORE_PACKAGE)
            addFlags(storeFlags)
        }
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$PLAY_PACKAGE_NAME"),
        ).apply {
            addFlags(storeFlags)
        }
        startFirstAvailable(marketIntent, webIntent)
    }

    private fun sendFeedback() {
        val supportEmail = BuildConfig.SUPPORT_EMAIL.trim()
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = if (supportEmail.isBlank()) {
                Uri.parse("mailto:")
            } else {
                Uri.fromParts("mailto", supportEmail, null)
            }
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject))
            putExtra(
                Intent.EXTRA_TEXT,
                getString(
                    R.string.feedback_template,
                    android.os.Build.MANUFACTURER,
                    android.os.Build.MODEL,
                    android.os.Build.VERSION.RELEASE,
                ),
            )
        }
        // An app chooser instead of the default mail app, which on a phone
        // without a configured account opened straight into account setup
        // (QA v43 #10). The address itself is shown in the feedback dialog.
        startFirstAvailable(Intent.createChooser(intent, getString(R.string.send_feedback)))
    }

    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                getString(
                    R.string.share_app_message,
                    "https://play.google.com/store/apps/details?id=$PLAY_PACKAGE_NAME",
                ),
            )
        }
        runCatching {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_app)))
        }.onFailure {
            cleanerViewModel.showMessage(R.string.message_screen_unavailable)
        }
    }

    private fun showPrivacyOptions() {
        consentManager.showPrivacyOptions(this)
    }

    private fun showRewardedAd() {
        val adFreeWindow = (application as DepoAkilliApplication).adFreeWindow
        if (adFreeWindow.isActiveNow()) return
        rewardedAds.show(
            activity = this,
            onWillShow = {
                adFrequency.update { AdPolicy.recordFullScreen(it, System.currentTimeMillis()) }
                (application as DepoAkilliApplication).beginInterstitialSurface()
            },
            onUserEarnedReward = {
                adFreeWindow.grant()
                cleanerViewModel.showMessage(R.string.rewarded_ad_earned)
            },
            onFinished = { (application as DepoAkilliApplication).endInterstitialSurface() },
            onUnavailable = { cleanerViewModel.showMessage(R.string.rewarded_ad_unavailable) },
        )
    }

    private fun startFirstAvailable(vararg intents: Intent) {
        for (intent in intents) {
            if (runCatching { startActivity(intent) }.isSuccess) return
        }
        cleanerViewModel.showMessage(R.string.message_screen_unavailable)
    }

    private companion object {
        const val PLAY_PACKAGE_NAME = "com.mrzekai.depoakilli"
        const val PLAY_STORE_PACKAGE = "com.android.vending"
        const val POST_TASK_AD_SETTLE_MILLIS = 350L
        const val REVIEW_PROMPT_INTERVAL_MILLIS = 30L * 24L * 60L * 60L * 1000L
    }
}
