package com.mrzekai.depoakilli

import android.app.Activity
import android.content.ComponentCallbacks2
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrzekai.depoakilli.ads.ConsentManager
import com.mrzekai.depoakilli.ads.InterstitialAdController
import com.mrzekai.depoakilli.data.DeviceRepository
import com.mrzekai.depoakilli.ui.CleanerApp
import com.mrzekai.depoakilli.ui.CleanerViewModel
import com.mrzekai.depoakilli.ui.theme.DepoAkilliTheme

class MainActivity : ComponentActivity() {
    private val cleanerViewModel: CleanerViewModel by viewModels()
    private lateinit var consentManager: ConsentManager
    private lateinit var interstitialAds: InterstitialAdController
    private var pendingDeepCacheAfterStorageAccess = false

    private val allFilesAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        cleanerViewModel.refreshDeviceState(force = true)
        if (Environment.isExternalStorageManager()) {
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
        enableEdgeToEdge()
        consentManager = ConsentManager(applicationContext)
        interstitialAds = InterstitialAdController(applicationContext)
        consentManager.gatherConsent(this)

        setContent {
            val canRequestAds by consentManager.canRequestAds.collectAsStateWithLifecycle()
            val app = application as DepoAkilliApplication
            val fullScreenAdActive by app.fullScreenAdSurfaceActive.collectAsStateWithLifecycle()
            val cleanerState by cleanerViewModel.state.collectAsStateWithLifecycle()

            LaunchedEffect(canRequestAds) {
                interstitialAds.setAdsAllowed(canRequestAds)
                app.setAppOpenAdsAllowed(canRequestAds)
            }

            LaunchedEffect(
                cleanerState.scanning,
                cleanerState.dashboardRefreshing,
                cleanerState.whatsAppScanning,
                cleanerState.cleanupInProgress,
            ) {
                app.setCriticalTaskActive(
                    cleanerState.scanning ||
                        cleanerState.dashboardRefreshing ||
                        cleanerState.whatsAppScanning ||
                        cleanerState.cleanupInProgress,
                )
            }

            DepoAkilliTheme {
                CleanerApp(
                    viewModel = cleanerViewModel,
                    canRequestAds = canRequestAds && !fullScreenAdActive,
                    fullScreenAdActive = fullScreenAdActive,
                    privacyOptionsRequired = consentManager.privacyOptionsRequired,
                    onRequestAllFilesAccess = ::requestAllFilesAccess,
                    onRequestUsageAccess = ::requestUsageAccess,
                    onClearAllAppCaches = ::requestDeepCacheCleanup,
                    onOpenAppDetails = ::openAppCacheSettings,
                    onPrepareCleanup = ::cleanSelected,
                    onPrepareStorageCleanup = ::cleanStorage,
                    onPrepareWhatsAppCleanup = ::cleanWhatsApp,
                    onCleanupResultDismissed = ::onCleanupResultDismissed,
                    onUninstallApp = ::uninstallApp,
                    onOpenLanguageSettings = ::openLanguageSettings,
                    onShowPrivacyOptions = ::showPrivacyOptions,
                    onRateApp = ::rateApp,
                    onSendFeedback = ::sendFeedback,
                    onShareApp = ::shareApp,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        cleanerViewModel.refreshDeviceState()
        cleanerViewModel.refreshAppCaches()
        if (::interstitialAds.isInitialized) interstitialAds.onHostResumed(this)
    }

    override fun onPause() {
        if (::interstitialAds.isInitialized) interstitialAds.onHostPaused(this)
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
        showPostTaskInterstitial()
    }

    private fun showPostTaskInterstitial(onFinished: () -> Unit = {}) {
        val app = application as DepoAkilliApplication

        // Short neutral settle surface prevents the cleanup-confirm tap from
        // carrying into a suddenly appearing full-screen ad.
        app.beginInterstitialSurface()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            {
                interstitialAds.showAtNaturalBreak(
                    activity = this,
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
                    suppressNextAppOpenAd()
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
        suppressNextAppOpenAd()
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
        suppressNextAppOpenAd()
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
        suppressNextAppOpenAd()
        cleanerViewModel.beginDeepCacheCleanupMeasurement()
        runCatching {
            deepCacheLauncher.launch(Intent(StorageManager.ACTION_CLEAR_APP_CACHE))
        }.onFailure {
            cleanerViewModel.onDeepCacheCleanupResult(false)
            cleanerViewModel.showMessage(R.string.message_screen_unavailable)
        }
    }

    private fun openAppCacheSettings(packageName: String) {
        suppressNextAppOpenAd()
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
        suppressNextAppOpenAd()
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }
            .onFailure { cleanerViewModel.showMessage(R.string.message_screen_unavailable) }
    }

    private fun openLanguageSettings() {
        suppressNextAppOpenAd()
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
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$PLAY_PACKAGE_NAME"),
        )
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$PLAY_PACKAGE_NAME"),
        )
        startFirstAvailable(marketIntent, webIntent)
    }

    private fun sendFeedback() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
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
        startFirstAvailable(intent)
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
        suppressNextAppOpenAd()
        runCatching {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_app)))
        }.onFailure {
            cleanerViewModel.showMessage(R.string.message_screen_unavailable)
        }
    }

    private fun showPrivacyOptions() {
        suppressNextAppOpenAd()
        consentManager.showPrivacyOptions(this)
    }

    private fun startFirstAvailable(vararg intents: Intent) {
        for (intent in intents) {
            suppressNextAppOpenAd()
            if (runCatching { startActivity(intent) }.isSuccess) return
        }
        cleanerViewModel.showMessage(R.string.message_screen_unavailable)
    }

    private fun suppressNextAppOpenAd() {
        (application as DepoAkilliApplication).suppressNextAppOpenAd()
    }

    private companion object {
        const val PLAY_PACKAGE_NAME = "com.mrzekai.depoakilli"
        const val POST_TASK_AD_SETTLE_MILLIS = 350L
    }
}
