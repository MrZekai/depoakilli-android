package com.mrzekai.depoakilli

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrzekai.depoakilli.ads.ConsentManager
import com.mrzekai.depoakilli.ads.InterstitialAdController
import com.mrzekai.depoakilli.data.DeviceRepository
import com.mrzekai.depoakilli.ui.CleanerApp
import com.mrzekai.depoakilli.ui.CleanerViewModel
import com.mrzekai.depoakilli.ui.theme.DepoAkilliTheme
import com.mrzekai.depoakilli.model.ScanFocus

class MainActivity : ComponentActivity() {
    private val cleanerViewModel: CleanerViewModel by viewModels()
    private lateinit var consentManager: ConsentManager
    private lateinit var interstitialAds: InterstitialAdController
    private var permissionRevision by mutableIntStateOf(0)
    private var pendingMediaScanFocus = ScanFocus.SMART

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionRevision++
        if (hasAnyMediaAccess()) {
            cleanerViewModel.scan(
                limitedAccess = hasLimitedMediaAccess(),
                focus = pendingMediaScanFocus,
            )
        }
    }

    private val whatsappFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val persisted = runCatching {
            contentResolver.takePersistableUriPermission(uri, flags)
        }.isSuccess
        if (persisted && cleanerViewModel.connectWhatsAppFolder(uri)) {
            cleanerViewModel.scanWhatsAppLibrary()
        }
    }

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val approved = result.resultCode == Activity.RESULT_OK
        cleanerViewModel.completeCleanup(approved)
        if (approved) interstitialAds.showAfterCleanup(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consentManager = ConsentManager(applicationContext)
        interstitialAds = InterstitialAdController(applicationContext)
        consentManager.gatherConsent(this)

        setContent {
            val canRequestAds by consentManager.canRequestAds.collectAsStateWithLifecycle()
            @Suppress("UNUSED_VARIABLE")
            val revision = permissionRevision

            LaunchedEffect(canRequestAds) {
                interstitialAds.setAdsAllowed(canRequestAds)
                (application as DepoAkilliApplication).setAppOpenAdsAllowed(canRequestAds)
            }

            DepoAkilliTheme {
                CleanerApp(
                    viewModel = cleanerViewModel,
                    hasFullMediaAccess = hasFullMediaAccess(),
                    hasLimitedMediaAccess = hasLimitedMediaAccess(),
                    canRequestAds = canRequestAds,
                    privacyOptionsRequired = consentManager.privacyOptionsRequired,
                    onRequestMediaAccess = ::requestMediaAccess,
                    onRequestWhatsAppAccess = ::requestWhatsAppFolder,
                    onPrepareCleanup = {
                        cleanerViewModel.prepareCleanup(
                            onPlanReady = { plan ->
                                if (plan is DeviceRepository.DeletePlan.RequiresConsent) {
                                    suppressNextAppOpenAd()
                                    deleteLauncher.launch(
                                        IntentSenderRequest.Builder(plan.pendingIntent.intentSender).build(),
                                    )
                                }
                            },
                            onCleanupCompleted = {
                                interstitialAds.showAfterCleanup(this)
                            },
                        )
                    },
                    onOptimizeMemory = {
                        cleanerViewModel.optimizeMemory {
                            interstitialAds.releaseForMemoryOptimization()
                            (application as DepoAkilliApplication).releaseAdMemory()
                        }
                    },
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
        permissionRevision++
        cleanerViewModel.refreshDeviceState()
        cleanerViewModel.refreshAppCaches()
        if (::interstitialAds.isInitialized) interstitialAds.load()
    }

    private fun requestMediaAccess(focus: ScanFocus) {
        pendingMediaScanFocus = focus
        suppressNextAppOpenAd()
        permissionLauncher.launch(requiredMediaPermissions())
    }

    private fun requestWhatsAppFolder() {
        val initialUri = Uri.parse(WHATSAPP_MEDIA_INITIAL_URI)
        suppressNextAppOpenAd()
        runCatching { whatsappFolderLauncher.launch(initialUri) }
            .onFailure { whatsappFolderLauncher.launch(null) }
    }

    private fun requiredMediaPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )

        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun hasFullMediaAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED

        else -> checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLimitedMediaAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED

    private fun hasAnyMediaAccess(): Boolean = hasFullMediaAccess() || hasLimitedMediaAccess()

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
                    Build.MANUFACTURER,
                    Build.MODEL,
                    Build.VERSION.RELEASE,
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
        const val WHATSAPP_MEDIA_INITIAL_URI =
            "content://com.android.externalstorage.documents/document/" +
                "primary%3AAndroid%2Fmedia%2Fcom.whatsapp%2FWhatsApp%2FMedia"
        const val PLAY_PACKAGE_NAME = "com.mrzekai.depoakilli"
    }
}
