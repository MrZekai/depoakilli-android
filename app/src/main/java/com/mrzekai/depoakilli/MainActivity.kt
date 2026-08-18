package com.mrzekai.depoakilli

import android.app.Activity
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
        cleanerViewModel.refreshDeviceState()
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
        cleanerViewModel.refreshDeviceState()
        cleanerViewModel.refreshAppCaches(force = true)
        cleanerViewModel.refreshInstalledApps()
    }

    private val deepCacheLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        cleanerViewModel.onDeepCacheCleanupResult(result.resultCode == Activity.RESULT_OK)
    }

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val approved = result.resultCode == Activity.RESULT_OK
        cleanerViewModel.completeCleanup(approved)
        if (approved) cleanerViewModel.refreshAfterCleanup()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consentManager = ConsentManager(applicationContext)
        interstitialAds = InterstitialAdController(applicationContext)
        consentManager.gatherConsent(this)

        setContent {
            val canRequestAds by consentManager.canRequestAds.collectAsStateWithLifecycle()

            LaunchedEffect(canRequestAds) {
                interstitialAds.setAdsAllowed(canRequestAds)
                (application as DepoAkilliApplication).setAppOpenAdsAllowed(canRequestAds)
            }

            DepoAkilliTheme {
                CleanerApp(
                    viewModel = cleanerViewModel,
                    canRequestAds = canRequestAds,
                    privacyOptionsRequired = consentManager.privacyOptionsRequired,
                    onRequestAllFilesAccess = ::requestAllFilesAccess,
                    onRequestUsageAccess = ::requestUsageAccess,
                    onClearAllAppCaches = ::requestDeepCacheCleanup,
                    onPrepareCleanup = ::showCleanupInterstitialThenDelete,
                    onPrepareStorageCleanup = ::showStorageCleanupInterstitialThenDelete,
                    onPrepareWhatsAppCleanup = ::showWhatsAppCleanupInterstitialThenDelete,
                    onOptimizeMemory = {
                        cleanerViewModel.optimizeMemory {
                            interstitialAds.releaseForMemoryOptimization()
                            (application as DepoAkilliApplication).releaseAdMemory()
                        }
                    },
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
        if (::interstitialAds.isInitialized) interstitialAds.load()
    }

    private fun showCleanupInterstitialThenDelete(itemIds: Set<String>? = null) {
        suppressNextAppOpenAd()
        interstitialAds.showBeforeCleanup(this) {
            executeCleanupPlan(itemIds)
        }
    }

    private fun showStorageCleanupInterstitialThenDelete() {
        suppressNextAppOpenAd()
        interstitialAds.showBeforeCleanup(this) {
            cleanerViewModel.deleteSelectedStorageReview {
                cleanerViewModel.refreshDeviceState()
            }
        }
    }

    private fun showWhatsAppCleanupInterstitialThenDelete(onFinished: (Boolean) -> Unit) {
        suppressNextAppOpenAd()
        interstitialAds.showBeforeCleanup(this) {
            cleanerViewModel.deleteSelectedWhatsApp { changed ->
                cleanerViewModel.refreshDeviceState()
                onFinished(changed)
            }
        }
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
                cleanerViewModel.refreshAfterCleanup()
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
        runCatching {
            deepCacheLauncher.launch(Intent(StorageManager.ACTION_CLEAR_APP_CACHE))
        }.onFailure {
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
    }
}
