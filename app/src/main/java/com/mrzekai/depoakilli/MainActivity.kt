package com.mrzekai.depoakilli

import android.app.Activity
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.play.core.review.ReviewManagerFactory
import com.mrzekai.depoakilli.billing.BillingEvent
import com.mrzekai.depoakilli.billing.BillingProducts
import com.mrzekai.depoakilli.billing.ProDemo
import com.mrzekai.depoakilli.billing.ProPlanOption
import com.mrzekai.depoakilli.pro.CleanupOrigin
import com.mrzekai.depoakilli.pro.PhotoStats
import com.mrzekai.depoakilli.data.DeviceRepository
import com.mrzekai.depoakilli.diagnostics.AppDiagnostics
import com.mrzekai.depoakilli.ui.CleanerApp
import com.mrzekai.depoakilli.ui.CleanerViewModel
import com.mrzekai.depoakilli.ui.theme.DepoAkilliTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val cleanerViewModel: CleanerViewModel by viewModels()
    private val billing by lazy { (application as DepoAkilliApplication).billing }
    private var pendingDeepCacheAfterStorageAccess = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Faz 5: one theme regardless of the device's light/dark setting.
        // Lime screens use dark status-bar icons; the black bottom menu uses
        // light navigation icons. Screens adjust them with SystemBarIcons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // Phones stay portrait; tablets, foldables and desktop windows are
        // free to rotate and resize (Play large-screen quality guideline).
        if (resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        // Billing events (purchase done, pending, restore) become one short message.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                billing.events.collect(::showBillingMessage)
            }
        }
        // Debug builds: demo Pro state for screenshots of the Pro screens (ProDemo).
        if (BuildConfig.DEBUG) ProDemo.mode = intent?.getStringExtra("demo_pro")
        // The free-tier gates follow Google Play's answer on Pro.
        lifecycleScope.launch {
            billing.state.collect { cleanerViewModel.setPro(ProDemo.apply(applicationContext, it).isPro) }
        }
        // Plans (and whether a trial is offered) for the Y1 card and the paywall.
        billing.loadProducts()

        setContent {
            val realPro by billing.state.collectAsStateWithLifecycle()
            val pro = ProDemo.apply(applicationContext, realPro)
            val photoBytes by averagePhotoBytes.collectAsStateWithLifecycle()
            DepoAkilliTheme {
                CleanerApp(
                    viewModel = cleanerViewModel,
                    pro = pro,
                    onRequestAllFilesAccess = ::requestAllFilesAccess,
                    onRequestUsageAccess = ::requestUsageAccess,
                    onClearAllAppCaches = ::requestDeepCacheCleanup,
                    onOpenAppDetails = ::openAppCacheSettings,
                    onPrepareCleanup = ::cleanSelected,
                    onPrepareStorageCleanup = ::cleanStorage,
                    onPrepareWhatsAppCleanup = ::cleanWhatsApp,
                    onCleanupResultDismissed = ::onCleanupResultDismissed,
                    onCleanupSucceeded = ::onCleanupSucceeded,
                    onDeepDuplicateCheck = cleanerViewModel::runDeepDuplicateCheck,
                    onUninstallApp = ::uninstallApp,
                    onOpenAppInfo = ::openAppInfo,
                    onOpenLanguageSettings = ::openLanguageSettings,
                    onOpenPro = billing::loadProducts,
                    onBuyPlan = ::buyPlan,
                    onRestorePurchases = { billing.refreshPurchases(userRequested = true) },
                    onManageSubscription = ::openSubscriptionManagement,
                    averagePhotoBytes = photoBytes,
                    onPaywallOpened = ::measurePhotos,
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
        // E1: back from Android's uninstall or app settings, the apps section
        // shows fresh sizes and a removed app's row disappears.
        cleanerViewModel.refreshInstalledAppsIfAllowed()
        // Re-read purchases on every start and return (plan §1.2); offline keeps the last known state.
        billing.refreshPurchases()
    }

    private fun cleanSelected(itemIds: Set<String>? = null) {
        val origin = cleanerViewModel.takeNextCleanupOrigin()
        if (origin == null) {
            executeCleanupPlan(itemIds)
            return
        }
        // Faz 2: the free first "My pick" cleanup is spent once it deletes something;
        // what Pro features clear counts toward this month's figure (Y5).
        val wasPro = billing.state.value.isPro
        val firstUsed = cleanerViewModel.state.value.freeTier.firstCleanupUsed
        val proSwipeBytes = if (origin == CleanupOrigin.SWIPE && itemIds != null) cleanerViewModel.proDecisionBytes(itemIds) else 0L
        cleanerViewModel.prepareCleanup(itemIds = itemIds) { deletedAny ->
            if (!deletedAny) return@prepareCleanup
            val result = cleanerViewModel.state.value.cleanupResult
            val cleared = (result?.deletedBytes ?: 0L) + (result?.trashedBytes ?: 0L)
            when (origin) {
                CleanupOrigin.ONE_TAP_PICK ->
                    if (firstUsed && wasPro) cleanerViewModel.addProBytes(cleared) else cleanerViewModel.markFirstCleanupUsed()
                CleanupOrigin.SWIPE -> if (wasPro) cleanerViewModel.addProBytes(proSwipeBytes.coerceAtMost(cleared))
            }
        }
    }

    /** Average photo size for the paywall's "about N photos" line (MediaStore, off the main thread). */
    private val averagePhotoBytes = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)

    private fun measurePhotos() {
        if (averagePhotoBytes.value != null) return
        lifecycleScope.launch {
            averagePhotoBytes.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                PhotoStats.averagePhotoBytes(applicationContext)
            }
        }
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
        cleanerViewModel.refreshDeviceState(force = true)
    }

    /**
     * Google Play in-app review, requested right after a successful cleanup.
     * Play decides whether the sheet is actually shown (its own quota); this
     * app additionally asks at most once every [REVIEW_PROMPT_INTERVAL_MILLIS].
     */
    private fun onCleanupSucceeded() {
        maybeAskForReview()
    }

    private fun maybeAskForReview() {
        val prefs = getSharedPreferences("review_prompt", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_asked", 0L) < REVIEW_PROMPT_INTERVAL_MILLIS) return
        val manager = ReviewManagerFactory.create(this)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful && !isFinishing && !isDestroyed) {
                prefs.edit().putLong("last_asked", now).apply()
                manager.launchReviewFlow(this, request.result)
            }
        }
    }

    private fun executeCleanupPlan(itemIds: Set<String>? = null) {
        cleanerViewModel.prepareCleanup(
            itemIds = itemIds,
            onCleanupCompleted = {
                // Result state is already produced by CleanerViewModel.
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

    private fun buyPlan(plan: ProPlanOption) {
        billing.launchPurchase(this, plan)
    }

    /** Google Play's own subscription page (plan §1.2 "Aboneliği yönet"). */
    private fun openSubscriptionManagement() {
        startFirstAvailable(
            Intent(Intent.ACTION_VIEW, Uri.parse(BillingProducts.MANAGE_SUBSCRIPTIONS_URL)),
        )
    }

    private fun showBillingMessage(event: BillingEvent) {
        cleanerViewModel.showMessage(
            when (event) {
                BillingEvent.PRO_ACTIVATED -> R.string.pro_message_activated
                BillingEvent.PENDING -> R.string.pro_message_pending
                BillingEvent.RESTORED -> R.string.pro_message_restored
                BillingEvent.NOTHING_TO_RESTORE -> R.string.pro_message_nothing_to_restore
                BillingEvent.RESTORE_FAILED -> R.string.pro_message_restore_failed
                BillingEvent.FAILED -> R.string.pro_message_failed
                BillingEvent.UNAVAILABLE -> R.string.pro_message_unavailable
            },
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
        const val REVIEW_PROMPT_INTERVAL_MILLIS = 30L * 24L * 60L * 60L * 1000L
    }
}
