package com.mrzekai.depoakilli

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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

class MainActivity : ComponentActivity() {
    private val cleanerViewModel: CleanerViewModel by viewModels()
    private lateinit var consentManager: ConsentManager
    private lateinit var interstitialAds: InterstitialAdController
    private var permissionRevision by mutableIntStateOf(0)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionRevision++
        if (hasAnyMediaAccess()) {
            cleanerViewModel.scan(limitedAccess = !hasFullMediaAccess())
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
            }

            DepoAkilliTheme {
                CleanerApp(
                    viewModel = cleanerViewModel,
                    hasFullMediaAccess = hasFullMediaAccess(),
                    hasLimitedMediaAccess = hasLimitedMediaAccess(),
                    canRequestAds = canRequestAds,
                    privacyOptionsRequired = consentManager.privacyOptionsRequired,
                    onRequestMediaAccess = ::requestMediaAccess,
                    onPrepareCleanup = {
                        cleanerViewModel.prepareCleanup(
                            onPlanReady = { plan ->
                                if (plan is DeviceRepository.DeletePlan.RequiresConsent) {
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
                    onOpenSystemCache = ::openSystemCache,
                    onOpenStorageSettings = ::openStorageSettings,
                    onShowPrivacyOptions = { consentManager.showPrivacyOptions(this) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionRevision++
        cleanerViewModel.refreshDeviceState()
        if (::interstitialAds.isInitialized) interstitialAds.load()
    }

    private fun requestMediaAccess() {
        permissionLauncher.launch(requiredMediaPermissions())
    }

    private fun requiredMediaPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )

        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
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

    private fun openSystemCache() {
        val intent = cleanerViewModel.systemCacheIntent()
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            openStorageSettings()
        } catch (_: SecurityException) {
            openStorageSettings()
        }
    }

    private fun openStorageSettings() {
        val intents = listOf(
            Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        intents.firstOrNull { it.resolveActivity(packageManager) != null }?.let(::startActivity)
    }
}
