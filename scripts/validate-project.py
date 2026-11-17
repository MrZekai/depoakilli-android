#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import pathlib
import re
import struct
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
errors: list[str] = []


# T3.4: large UI files were split into packages. A check written against the
# old file reads every file that now holds its code, so no check is lost.
J = "app/src/main/java/com/mrzekai/depoakilli/"
SPLIT_SOURCES = {
    J + "ui/WhatsAppCleanerScreen.kt": [J + "ui/whatsapp/"],
    J + "ui/PremiumCleanerToolScreen.kt": [J + "ui/tools/"],
    J + "ui/AppCacheManagerScreen.kt": [J + "ui/AppCacheComponents.kt"],
    J + "ui/DeviceCenterScreen.kt": [J + "ui/SettingsScreen.kt", J + "ui/AppManagerScreen.kt"],
    J + "ui/CleanerApp.kt": [J + "ui/AppBottomBar.kt", J + "ui/CleanScreenRouter.kt", J + "ui/AppDialogs.kt"],
    J + "ui/SmartCleanResultsScreen.kt": [
        J + "ui/smart/SmartScanStates.kt",
        J + "ui/smart/UnusedAppsSection.kt",
        J + "ui/smart/CategoryDetailPage.kt",
        J + "ui/smart/StorageDetailPage.kt",
        J + "ui/smart/SmartFilePreview.kt",
    ],
    J + "data/DeviceRepository.kt": [
        J + "data/StorageIndexer.kt",
        J + "data/SharedStorageRemover.kt",
        J + "data/ScanFocusRules.kt",
        J + "data/StorageTypeStats.kt",
        J + "data/AppStatsSource.kt",
        J + "data/DeviceInfoSource.kt",
    ],
    J + "ui/CleanerViewModel.kt": [
        J + "ui/CleanerUiState.kt",
        J + "ui/CleanerViewModelCore.kt",
        J + "ui/CleanerTrashLayer.kt",
        J + "ui/CleanerDeviceLayer.kt",
        J + "ui/CleanerAppCacheLayer.kt",
        J + "ui/CleanerScanLayer.kt",
        J + "ui/CleanerWhatsAppLayer.kt",
        J + "ui/CleanerStorageReviewLayer.kt",
        J + "ui/CleanerStateReducers.kt",
    ],
}


def split_files(relative: str) -> list[pathlib.Path]:
    files: list[pathlib.Path] = []
    for part in SPLIT_SOURCES.get(relative, []):
        target = ROOT / part
        files += sorted(target.rglob("*.kt")) if target.is_dir() else [target]
    if (ROOT / relative).is_file():
        files.insert(0, ROOT / relative)
    return files


def require(relative: str) -> pathlib.Path:
    path = ROOT / relative
    if not path.is_file() and not (relative in SPLIT_SOURCES and split_files(relative)):
        errors.append(f"missing required file: {relative}")
    return path


def read(relative: str) -> str:
    if relative in SPLIT_SOURCES:
        files = split_files(relative)
        if not files:
            errors.append(f"missing required file: {relative}")
        return "\n".join(f.read_text(encoding="utf-8") for f in files)
    path = require(relative)
    if not path.is_file():
        return ""
    return path.read_text(encoding="utf-8")


# ------------------------------------------------------------------
# Required project surface for the v41 Play closed-test candidate.
# Deliberately excludes MemoryOptimizationResultDialog.kt.
# ------------------------------------------------------------------
required = [
    "settings.gradle.kts",
    "build.gradle.kts",
    "gradle/libs.versions.toml",
    "gradlew",
    "gradle/wrapper/gradle-wrapper.jar",
    "gradle/wrapper/gradle-wrapper.properties",
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/mrzekai/depoakilli/MainActivity.kt",
    "app/src/main/java/com/mrzekai/depoakilli/DepoAkilliApplication.kt",
    "app/src/main/java/com/mrzekai/depoakilli/diagnostics/AppDiagnostics.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ads/AdComponents.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ads/ResultAdComponents.kt",
    "app/src/main/java/com/mrzekai/depoakilli/data/AiCleaningEngine.kt",
    "app/src/main/java/com/mrzekai/depoakilli/data/StoragePathRules.kt",
    "app/src/main/java/com/mrzekai/depoakilli/data/DeviceRepository.kt",
    "app/src/main/java/com/mrzekai/depoakilli/model/CleanModels.kt",
    "app/src/main/java/com/mrzekai/depoakilli/data/DuplicatePolicy.kt",
    "app/src/main/java/com/mrzekai/depoakilli/data/WhatsAppMediaClassifier.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/CleanerApp.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/CleanerViewModel.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/CleanupResultDialog.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/DashboardSnapshotStore.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/CleanupHistoryStore.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/StorageChangeStore.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/StorageChangeScreen.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/StorageAnalyzerScreen.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/SecurityCenterScreen.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/HomeVisualTokens.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/DeviceCenterScreen.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/AppCacheManagerScreen.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/PremiumCleanerToolScreen.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/SmartCleanResultsScreen.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/WhatsAppCleanerScreen.kt",
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-tr/strings.xml",
    "app/src/main/res/xml/locales_config.xml",
    ".github/workflows/android-ci.yml",
    ".github/workflows/closed-test-build.yml",
    ".github/workflows/release-aab.yml",
    "scripts/verify-closed-test-binaries.sh",
    "scripts/verify-release-aab.sh",
    "scripts/verify-qa-apk.sh",
    "scripts/verify-qa-signing.sh",
    "PLAY_RELEASE_CHECKLIST.md",
    "PRIVACY_POLICY_EN.md",
    "PRIVACY_POLICY_TR.md",
    "TERMS_OF_SERVICE_EN.md",
    "TERMS_OF_SERVICE_TR.md",
    "keystore/depoakilli-ci-qa.jks",
]
for relative in required:
    require(relative)

# XML must remain structurally valid across checked-in Android source/resource
# sets. Never parse generated build/intermediate XML: lint/AGP outputs are not
# project source and may use internal encodings/schema content not intended for
# ElementTree validation.
for xml_file in (ROOT / "app/src").rglob("*.xml"):
    try:
        ET.parse(xml_file)
    except ET.ParseError as exc:
        errors.append(f"invalid XML {xml_file.relative_to(ROOT)}: {exc}")

# ------------------------------------------------------------------
# Manifest / Android platform contract.
# ------------------------------------------------------------------
manifest = read("app/src/main/AndroidManifest.xml")
for expected in (
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.PACKAGE_USAGE_STATS",
    'android:localeConfig="@xml/locales_config"',
    "<queries>",
    "android.intent.category.LAUNCHER",
):
    if expected not in manifest:
        errors.append(f"missing manifest invariant: {expected}")
if manifest.count('android:enableOnBackInvokedCallback="true"') != 1:
    errors.append("MainActivity must be the only activity explicitly opting into predictive back")
for forbidden in (
    "QUERY_ALL_PACKAGES",
    "BIND_ACCESSIBILITY_SERVICE",
    "KILL_BACKGROUND_PROCESSES",
    "WRITE_EXTERNAL_STORAGE",
):
    if forbidden in manifest:
        errors.append(f"forbidden manifest capability present: {forbidden}")

# ------------------------------------------------------------------
# Build / production-vs-QA AdMob contract.
# ------------------------------------------------------------------
build_file = read("app/build.gradle.kts")
# T3.6: version constants come from Gradle, not from this script.
_version_code = re.search(r"versionCode = (\d+)", build_file)
_version_name = re.search(r'versionName = "([^"]+)"', build_file)
if not _version_code or not _version_name:
    errors.append("versionCode/versionName missing in app/build.gradle.kts")
VERSION_CODE = _version_code.group(1) if _version_code else "?"
VERSION_NAME = _version_name.group(1) if _version_name else "?"
for expected in (
    'applicationId = "com.mrzekai.depoakilli"',
    "minSdk = 30",
    "targetSdk = 36",
    "compileSdk = 36",
    f"versionCode = {VERSION_CODE}",
    f'versionName = "{VERSION_NAME}"',
    "validateReleaseAds",
    'applicationIdSuffix = ".qa"',
    'liveAdMobAppId = "ca-app-pub-1380972808968213~9043355268"',
    'liveBannerId = "ca-app-pub-1380972808968213/2118175647"',
    'liveInterstitialId = "ca-app-pub-1380972808968213/8492012303"',
    'sampleResultNativeVideoId = "ca-app-pub-3940256099942544/1044960115"',
    'providers.environmentVariable("ADMOB_RESULT_NATIVE_ID")',
    'providers.environmentVariable("SUPPORT_EMAIL")',
    '"ADMOB_RESULT_NATIVE_ID"',
    '"SUPPORT_EMAIL"',
    'create("closedTest") {',
    "validateClosedTestConfiguration",
    'tasks.matching { it.name == "preClosedTestBuild" }',
    'manifestPlaceholders["ADMOB_APP_ID"] = sampleAdMobAppId',
    'manifestPlaceholders["ADMOB_APP_ID"] = liveAdMobAppId',
):
    if expected not in build_file:
        errors.append(f"missing build/AdMob invariant: {expected}")

# ------------------------------------------------------------------
# Core storage-manager engine remains real.
# ------------------------------------------------------------------
repository = read("app/src/main/java/com/mrzekai/depoakilli/data/DeviceRepository.kt")
for expected in (
    "Environment.isExternalStorageManager()",
    "indexSharedStorage",
    "indexWhatsAppFiles",
    "MAX_INDEXED_FILES = 200_000",
    "MAX_WHATSAPP_FILES = 100_000",
    "scanStorageReview",
    "deleteStorageReviewItems",
    "deleteWhatsAppItems",
    "MAX_STORAGE_REVIEW_ITEMS = 50_000",
    "smartMediaTypeStats",
    "isWhatsAppSharedMedia",
):
    if expected not in repository:
        errors.append(f"missing storage engine invariant: {expected}")
duplicate_finder = read("app/src/main/java/com/mrzekai/depoakilli/data/DuplicateFinder.kt")
for expected in ("sampleFingerprint", "fingerprint(file", "ensureActive()"):
    if expected not in duplicate_finder:
        errors.append(f"missing duplicate finder invariant: {expected}")
for forbidden in (
    "MAX_HASH_BYTES",
    "MAX_DUPLICATE_GROUP",
    "MAX_HASH_GROUPS",
    "KEY_WHATSAPP_TREE_URI",
    "DocumentsContract",
    "APP_CACHE_URI",
    "distinctBy(File::absolutePath)",
):
    if forbidden in repository:
        errors.append(f"forbidden/legacy storage implementation remains: {forbidden}")

# ------------------------------------------------------------------
# Result-first cleanup monetization.
# The only direct no-argument showPostTaskInterstitial() call must be the
# result-dismiss handoff. Destructive work itself must not call the ad.
# ------------------------------------------------------------------
main_activity = read("app/src/main/java/com/mrzekai/depoakilli/MainActivity.kt")
for expected in (
    "ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
    "StorageManager.ACTION_CLEAR_APP_CACHE",
    "Settings.ACTION_USAGE_ACCESS_SETTINGS",
    "interstitialAds.onHostResumed(this)",
    "interstitialAds.onHostPaused(this)",
    "showPostTaskInterstitial",
    "onCleanupResultDismissed",
    "onCleanupResultDismissed = ::onCleanupResultDismissed",
    "onPrepareCleanup = ::cleanSelected",
    "onPrepareStorageCleanup = ::cleanStorage",
    "onPrepareWhatsAppCleanup = ::cleanWhatsApp",
    "override fun onTrimMemory(level: Int)",
    "ComponentCallbacks2.TRIM_MEMORY_BACKGROUND",
    "BuildConfig.SUPPORT_EMAIL.trim()",
    'Uri.fromParts("mailto", supportEmail, null)',
):
    if expected not in main_activity:
        errors.append(f"missing v0.5.17 activity flow: {expected}")

for forbidden in (
    "optimizeMemoryThenShowInterstitial",
    "cleanSelectedThenShowInterstitial",
    "cleanStorageThenShowInterstitial",
    "cleanWhatsAppThenShowInterstitial",
    "onOptimizeMemory",
    "cleanerViewModel.optimizeMemory",
):
    if forbidden in main_activity:
        errors.append(f"obsolete RAM/ad-gating flow remains in MainActivity: {forbidden}")

if main_activity.count("showPostTaskInterstitial()") != 1:
    errors.append(
        "v0.5.17 requires exactly one direct no-argument Interstitial handoff "
        "(after CleanupResult dismissal)"
    )
result_handoff = (
    "private fun onCleanupResultDismissed() {",
    "cleanerViewModel.refreshDeviceState()",
    "showPostTaskInterstitial()",
)
if not all(token in main_activity for token in result_handoff):
    errors.append("cleanup-result dismissal must own the natural-break Interstitial handoff")
if "setContent {" in main_activity and "import androidx.activity.compose.setContent" not in main_activity:
    errors.append("MainActivity uses setContent without the required Compose import")

diagnostics = read("app/src/main/java/com/mrzekai/depoakilli/diagnostics/AppDiagnostics.kt")
for expected in (
    "Local-only diagnostic logging",
    'Log.e(TAG, if (details.isBlank()) stage else "$stage [$details]", throwable)',
):
    if expected not in diagnostics:
        errors.append(f"missing local-only diagnostics invariant: {expected}")

# ------------------------------------------------------------------
# Full-screen session caps and adaptive banner.
# ------------------------------------------------------------------
ads = read("app/src/main/java/com/mrzekai/depoakilli/ads/AdComponents.kt")
result_ads = read("app/src/main/java/com/mrzekai/depoakilli/ads/ResultAdComponents.kt")
for expected in (
    "INTERSTITIAL/SHOW_SKIP policy",
    "AdPolicy.canShowInterstitial(",
    "AdPolicy.recordInterstitial(",
    "getCurrentOrientationAnchoredAdaptiveBannerAdSize",
    "Lifecycle.Event.ON_RESUME -> adView.resume()",
    "Lifecycle.Event.ON_PAUSE -> adView.pause()",
    "adView.destroy()",
    "releaseCachedAd",
):
    if expected not in ads:
        errors.append(f"missing closed-test ad invariant: {expected}")
ad_policy = read("app/src/main/java/com/mrzekai/depoakilli/ads/AdPolicy.kt")
for expected in (
    "INTERSTITIAL_MIN_INTERVAL_SEC = 120L",
    "INTERSTITIAL_MAX_PER_DAY = 8",
    "FULLSCREEN_GAP_SEC = 60L",
    "s.cleanupsCompleted == 0 && s.launchCount <= 1",
):
    if expected not in ad_policy:
        errors.append(f"missing full-screen ad limit: {expected}")
if "shownThisProcess" in ads:
    errors.append("the per-process interstitial cap is replaced by AdPolicy limits")
if "AdSize.BANNER" in ads:
    errors.append("fixed 320x50 banner must not return; use anchored adaptive banner")
if "setImmersiveMode(true)" in ads:
    errors.append("Interstitial must let Google AdActivity own system UI/insets")

application = read("app/src/main/java/com/mrzekai/depoakilli/DepoAkilliApplication.kt")
for expected in (
    "fullScreenAdSurfaceActive",
    "beginInterstitialSurface",
    "endInterstitialSurface",
    "ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN",
):
    if expected not in application:
        errors.append(f"missing app lifecycle/ad invariant: {expected}")

# App open ad (T1B.2): allowed only behind the loading cover, never after a
# return from a screen the app opened itself (settings, permissions, cache,
# uninstall, review, share).
app_open = read("app/src/main/java/com/mrzekai/depoakilli/ads/AppOpenAdController.kt")
for expected in (
    "AdPolicy.canShowAppOpen(",
    "AdPolicy.APP_OPEN_MAX_WAIT_MS",
    "MIN_BACKGROUND_MILLIS",
    "MAX_AD_AGE_MILLIS",
    "adUnitId.isNotBlank()",
):
    if expected not in app_open:
        errors.append(f"missing app open safety invariant: {expected}")
for expected in ("override fun onUserLeaveHint()", "appOpenAds.onUserLeftApp()", "AppOpenCover()"):
    if expected not in main_activity:
        errors.append(f"app open must only follow a user-initiated absence: {expected}")
if 'sampleAppOpenId = "ca-app-pub-3940256099942544/9257395921"' not in build_file:
    errors.append("debug/QA/closed-test builds must use Google's sample app open unit")

if "releaseForMemoryOptimization" in application:
    errors.append("obsolete RAM-optimizer-named ad release API remains in Application")

# ------------------------------------------------------------------
# User-facing RAM Optimizer must be gone, while read-only MemorySnapshot
# support may remain for Android/device information.
# ------------------------------------------------------------------
view_model = read("app/src/main/java/com/mrzekai/depoakilli/ui/CleanerViewModel.kt")
cleaner_app = read("app/src/main/java/com/mrzekai/depoakilli/ui/CleanerApp.kt")
device_center = read("app/src/main/java/com/mrzekai/depoakilli/ui/DeviceCenterScreen.kt")
app_cache_ui = read("app/src/main/java/com/mrzekai/depoakilli/ui/AppCacheManagerScreen.kt")
# T3.1: the old dashboard Home is gone; the Clean tab (plan) is Home. The
# remaining Home checks run on the new surface.
for removed_home in ("NeonDashboardScreen.kt", "HomeDashboardComponents.kt"):
    if (ROOT / "app/src/main/java/com/mrzekai/depoakilli/ui" / removed_home).exists():
        errors.append(f"dead dashboard file must stay deleted: {removed_home}")
dashboard = read("app/src/main/java/com/mrzekai/depoakilli/ui/CleanTab.kt") + read(
    "app/src/main/java/com/mrzekai/depoakilli/ui/smart/CleanupPlanScreen.kt"
)
home_components = dashboard
home_tokens = read("app/src/main/java/com/mrzekai/depoakilli/ui/HomeVisualTokens.kt")
cleanup_history_store = read("app/src/main/java/com/mrzekai/depoakilli/ui/CleanupHistoryStore.kt")
storage_change_store = read("app/src/main/java/com/mrzekai/depoakilli/ui/StorageChangeStore.kt")
storage_change_screen = read("app/src/main/java/com/mrzekai/depoakilli/ui/StorageChangeScreen.kt")
storage_analyzer = read("app/src/main/java/com/mrzekai/depoakilli/ui/StorageAnalyzerScreen.kt")
security_center = read("app/src/main/java/com/mrzekai/depoakilli/ui/SecurityCenterScreen.kt")
memory_dialog = ROOT / "app/src/main/java/com/mrzekai/depoakilli/ui/MemoryOptimizationResultDialog.kt"

# ------------------------------------------------------------------
# Alpha7 storage-change differentiation + permissions/privacy.
# ------------------------------------------------------------------
for expected in (
    "StorageChangeReport",
    "storageChangeStore",
    "restoreStorageChange",
    "recordFileSnapshot",
    "captureAppCacheForStorageChange",
    "updateCurrentAppCaches",
):
    if expected not in view_model:
        errors.append(f"missing alpha7 storage-change ViewModel invariant: {expected}")

for expected in (
    "PREVIOUS_PREFIX",
    "CURRENT_PREFIX",
    "StorageFileType.entries",
    "MAX_TRACKED_APP_CACHES = 50",
    "appCacheChanges",
    "storageTypeChanges",
):
    if expected not in storage_change_store:
        errors.append(f"missing alpha7 StorageChangeStore invariant: {expected}")

for expected in (
    "StorageChangeHero",
    "StorageTypeChangeRow",
    "AppCacheChangeRow",
    "storage_change_truth_note",
    "AsyncAppIcon",
):
    if expected not in storage_change_screen:
        errors.append(f"missing alpha7 StorageChangeScreen invariant: {expected}")

for expected in (
    "STORAGE_CHANGE(R.string.storage_change_title)",
    "StorageChangeScreen(",
    # Faz 5C (C4): storage change is the second tab of the Storage tool.
    "detailScreen = DetailScreen.STORAGE_CHANGE",
    "DetailScreen.STORAGE_CHANGE -> true",
):
    if expected not in cleaner_app:
        errors.append(f"missing alpha7 storage-change navigation invariant: {expected}")

if "onPrivacyAccess: () -> Unit" in home_components:
    errors.append("Home header privacy action must be removed")

for forbidden in (
    "onClick = if (state.hasAllFilesAccess) null else",
    "onClick = if (state.hasUsageAccess) null else",
):
    if forbidden in security_center:
        errors.append(f"permission card must remain manageable when ready: {forbidden}")

for expected in (
    "onManageAllFilesAccess",
    "onManageUsageAccess",
    "permissions_manage_android",
    "permissions_privacy_policy_title",
):
    if expected not in security_center:
        errors.append(f"missing useful permissions/privacy invariant: {expected}")


# ------------------------------------------------------------------
# Alpha8 Tools audit: distinct tools, truthful copy, read-only analyzer.
# ------------------------------------------------------------------
# Faz 5C (C4): six tiles. APK packages live in Downloads; app cache and
# storage change are the second tabs of Apps and Storage (HubTabs).
for expected in (
    "ScanFocus.DOWNLOADS",
    "ScanFocus.LARGE_FILES",
    "ScanFocus.ANALYZE",
    "onOpenAppManager",
    "onOpenWhatsApp",
    "ToolSuggestion.suggestedBytes",
):
    if expected not in device_center:
        errors.append(f"missing alpha8 Tools invariant: {expected}")
for expected in ("HubKind.APPS", "HubKind.STORAGE", "DetailScreen.APP_CACHE", "DetailScreen.STORAGE_CHANGE"):
    if expected not in cleaner_app:
        errors.append(f"missing Faz 5C tool tab invariant: {expected}")

for forbidden in (
    "R.string.downloads_apk_title",
    "onOpenSettings: () -> Unit",
    "R.string.deep_cache_title",
):
    if forbidden in device_center:
        errors.append(f"obsolete/misleading Tools surface remains: {forbidden}")

for expected in (
    "state.scanFocus == ScanFocus.ANALYZE",
    "StorageAnalyzerScreen(",
):
    if expected not in cleaner_app:
        errors.append(f"missing alpha8 analyzer routing invariant: {expected}")

for expected in (
    "summary.storageTypes",
    "summary.storagePreviews",
    "storage_analyzer_truth_note",
    "AnalyzerTypeCard",
):
    if expected not in storage_analyzer:
        errors.append(f"missing alpha8 read-only analyzer invariant: {expected}")

for forbidden in (
    "onClean:",
    "cleanupInProgress",
):
    if forbidden in storage_analyzer:
        errors.append(f"Storage Analyzer must remain read-only: {forbidden}")

download_block_start = repository.find("val DOWNLOAD_TOOL_GROUPS = setOf(")
if download_block_start < 0:
    errors.append("DOWNLOAD_TOOL_GROUPS missing")
else:
    download_block = repository[download_block_start:repository.find(")", download_block_start)]
    if "CleanupGroup.APK_" in download_block:
        errors.append("Downloads tool must not overlap the dedicated APK tool")

# ------------------------------------------------------------------
# Alpha5 result Native/MREC monetization and Android cache result.
# ------------------------------------------------------------------
for expected in (
    "AdLoader.Builder",
    "MediaView",
    "NativeAdView",
    "AdSize.MEDIUM_RECTANGLE",
    "CleanupResultAdSurface",
    "ADMOB_RESULT_NATIVE_ID",
):
    if expected not in result_ads:
        errors.append(f"missing alpha5 result-ad invariant: {expected}")

for forbidden in ("VAST", "ExoPlayer", "Media3"):
    if forbidden in result_ads:
        errors.append(f"custom/VAST ad playback must not be implemented: {forbidden}")

cleanup_dialog = read(
    "app/src/main/java/com/mrzekai/depoakilli/ui/CleanupResultDialog.kt"
)
for expected in (
    "CleanupResultAdSurface(",
    "onAdPresented = { resultAdPresented = true }",
    "onSystemDismiss: () -> Unit",
    "onDone: (resultAdPresented: Boolean) -> Unit",
    "dismissOnClickOutside = false",
    "Spacer(Modifier.height(24.dp))",
    "CleanupResultKind.SYSTEM_CACHE",
    "verticalScroll(rememberScrollState())",
):
    if expected not in cleanup_dialog:
        errors.append(f"missing alpha5 result-dialog invariant: {expected}")

if cleanup_dialog.find("R.string.result_done") > cleanup_dialog.rfind("CleanupResultAdSurface("):
    errors.append("result Done button must precede the ad surface")

for expected in (
    "beginDeepCacheCleanupMeasurement()",
    "kind = CleanupResultKind.SYSTEM_CACHE",
    "measuredCacheReduction",
):
    if expected not in view_model:
        errors.append(f"missing truthful Android-cache result invariant: {expected}")

if "state.cleanupResult == null" not in cleaner_app:
    errors.append("normal bottom banner must be disabled while cleanup result is visible")

if "if (eligibleForNaturalBreakAd) {\n                        onCleanupResultDismissed()" not in cleaner_app:
    errors.append("Interstitial follows result Done for successful cleanups (frequency limited by AdPolicy)")

# ------------------------------------------------------------------
# Alpha6 measurable cache results + readable Home tool cards.
# ------------------------------------------------------------------
for expected in (
    "CleanupResultKind.APP_CACHE",
    "subjectLabel",
    "beginIndividualAppCacheMeasurement",
    "onIndividualAppCacheSettingsReturned",
    "cancelIndividualAppCacheMeasurement",
    "measuredReduction",
):
    if expected not in view_model:
        errors.append(f"missing alpha6 app-cache result invariant: {expected}")

for expected in (
    "beginIndividualAppCacheMeasurement(packageName)",
    "onIndividualAppCacheSettingsReturned()",
    "cancelIndividualAppCacheMeasurement()",
):
    if expected not in main_activity:
        errors.append(f"missing alpha6 individual-cache activity invariant: {expected}")

for expected in (
    "result.deletedBytes > 0L",
    "result.kind != CleanupResultKind.FILES",
):
    if expected not in cleaner_app:
        errors.append(f"missing alpha6 result coverage invariant: {expected}")

for expected in (
    "CleanupResultKind.APP_CACHE",
    "cleanup_result_app_cache_title",
    # Faz 5C (C6): cache results drop the explanatory paragraph; the headline says it.
    "R.string.result_cache_cleared",
):
    if expected not in cleanup_dialog:
        errors.append(f"missing alpha6 app-cache dialog invariant: {expected}")



for forbidden in (
    "MemoryOptimizationResult",
    "memoryOptimizationResult",
    "dismissMemoryOptimizationResult",
    "fun optimizeMemory(",
    "optimizingMemory",
):
    if forbidden in view_model:
        errors.append(f"RAM optimizer logic remains in CleanerViewModel: {forbidden}")
for source_name, source in (
    ("CleanerApp", cleaner_app),
    ("DeviceCenterScreen", device_center),
    ("NeonDashboardScreen", dashboard),
):
    for forbidden in (
        "onOptimizeMemory",
        "RamOptimizerCard",
        "MemoryOptimizationResultDialog",
        "R.string.ram_optimizer",
        "R.string.ram_result",
    ):
        if forbidden in source:
            errors.append(f"user-facing RAM optimizer remains in {source_name}: {forbidden}")
if memory_dialog.exists():
    errors.append("MemoryOptimizationResultDialog.kt must be deleted in v0.5.17")

# Cleanup result remains measured and independent of ads.
for expected in (
    "data class CleanupResult",
    "cleanupResult",
    "dismissCleanupResult",
    "beforeAvailableBytes",
    "afterAvailableBytes",
    "deleteSelectedStorageReview",
    "deleteSelectedWhatsApp",
):
    if expected not in view_model:
        errors.append(f"missing measured cleanup invariant: {expected}")

for expected in (
    "CleanupResultDialog(",
    "onCleanupResultDismissed()",
    "eligibleForNaturalBreakAd",
    "resultAdPresented",
    "state.cleanupResult == null",
    "CleanupResultKind.SYSTEM_CACHE",
    "selectedTabIndex = AppTab.HOME.ordinal",
    "BackHandler(enabled = hasInAppBackTarget && !fullScreenAdActive)",
    "Spacer(Modifier.height(24.dp))",
):
    if expected not in cleaner_app:
        errors.append(f"missing result-first/UI invariant: {expected}")

# Trust surfaces do not carry banners; content surfaces still can.
# Faz 5 ad table: Home, Tools and Trash carry the banner above the menu.
for expected in (
    "AppTab.HOME,",
    "AppTab.TOOLS,",
    "AppTab.TRASH -> true",
    "DetailScreen.ACCESS,",
    "DetailScreen.SETTINGS,",
):
    if expected not in cleaner_app:
        errors.append(f"missing banner trust-surface gate: {expected}")

for forbidden in (
    "AppTab.SECURITY",
    "AppTab.PROFILE",
    "onOpenProfile =",
    "private fun HomeScreen(",
):
    if forbidden in cleaner_app:
        errors.append(f"obsolete four-tab/dead-home architecture remains: {forbidden}")

for expected in (
    "DetailScreen.SETTINGS -> SettingsDetailScreen(",
    "AppTab.TRASH -> TrashScreen(",
    "Icons.Outlined.Settings",
    "DetailScreen.ACCESS -> SecurityCenterScreen(",
):
    if expected not in cleaner_app and expected not in device_center:
        errors.append(f"missing Phase-2 navigation invariant: {expected}")

# Faz 5: no gradients and no navy pages; Home tokens map to the Lime/black palette.
for expected in ("val Page = Lime", "val Surface = Black"):
    if expected not in home_tokens:
        errors.append(f"missing Home visual token: {expected}")
for kotlin_file in (ROOT / "app/src/main/java").rglob("*.kt"):
    gradient_source = kotlin_file.read_text(encoding="utf-8")
    if re.search(r"Brush\.(linear|vertical|horizontal|radial|sweep)Gradient", gradient_source):
        errors.append(f"gradients are removed in the Faz 5 design: {kotlin_file.relative_to(ROOT)}")

for expected in (
    "data class CleanupHistorySnapshot",
    "totalDeletedBytes",
    "lastDeletedBytes",
    'PREFS_NAME = "cleanup_history_v1"',
):
    if expected not in cleanup_history_store:
        errors.append(f"missing verified cleanup-history invariant: {expected}")

for expected in (
    "cleanupHistoryStore",
    "recordCleanupHistory",
):
    if expected not in view_model:
        errors.append(f"missing cleanup proof-layer ViewModel invariant: {expected}")

if not re.search(
    r"val\s+cleanupHistory\s*:\s*CleanupHistorySnapshot\s*=\s*CleanupHistorySnapshot\(\)",
    view_model,
):
    errors.append(
        "missing typed cleanupHistory: CleanupHistorySnapshot ViewModel state invariant"
    )

for forbidden in (
    "DashboardToolTile(",
    "DeepCleanPromo(",
    "DashboardDeepCleanRow(",
    "onOpenProfile:",
    "onDeepClean:",
    "onJunk:",
    "onMedia:",
):
    if forbidden in dashboard:
        errors.append(f"duplicated/obsolete Home UI remains: {forbidden}")

for forbidden in (
    "R.string.sponsored",
    "premium",
    "Premium",
):
    if forbidden in dashboard or forbidden in home_components:
        errors.append(f"Home must not fake a premium/sponsored product surface: {forbidden}")
if cleaner_app.count("BannerAd(canRequestAds = true)") < 2:
    errors.append("content shells must retain gated banner placements")


# ------------------------------------------------------------------
# Alpha4 cache UX + runtime-speed invariants.
# ------------------------------------------------------------------
for expected in (
    "CacheManagerHero(",
    "CacheSystemActionCard(",
    "CacheAppRow(",
    "AppCacheActionSheet(",
    "AsyncAppIcon(",
    "cache_modern_individual_steps",
):
    if expected not in app_cache_ui:
        errors.append(f"missing alpha4 cache-manager UI invariant: {expected}")

if "internal fun AppCacheManagerScreen(" in device_center:
    errors.append("legacy AppCacheManagerScreen must not remain inside DeviceCenterScreen")

for expected in (
    "onOpenAppDetails: (String) -> Unit",
    "onOpenAppDetails = onOpenAppDetails",
):
    if expected not in cleaner_app:
        errors.append(f"missing per-app cache settings routing: {expected}")

for expected in (
    "Settings.ACTION_APPLICATION_DETAILS_SETTINGS",
    "appDetailsLauncher",
    "onOpenAppDetails = ::openAppCacheSettings",
    "POST_TASK_AD_SETTLE_MILLIS = 350L",
):
    if expected not in main_activity:
        errors.append(f"missing alpha4 app-details/speed activity invariant: {expected}")

for expected in (
    "DeviceRefreshSnapshot",
    "deviceRefreshJob",
    "lastDeviceRefreshAt",
    "DEVICE_REFRESH_INTERVAL_MILLIS = 1_500L",
    "coroutineScope",
    "async(Dispatchers.Default)",
    "WHATSAPP_COMPLETION_DELAY_MILLIS = 80L",
):
    if expected not in view_model:
        errors.append(f"missing alpha4 ViewModel speed invariant: {expected}")

scan_start = view_model.find("    fun scan(focus: ScanFocus = ScanFocus.SMART) {")
scan_end = view_model.find("    fun scanWhatsAppLibrary() {", scan_start)
if scan_start >= 0 and scan_end > scan_start:
    scan_section = view_model[scan_start:scan_end]
    if "refreshDeviceState()" in scan_section:
        errors.append("scan() must not repeat a device refresh before storage traversal")
    if "refreshInstalledApps()" in scan_section:
        errors.append("Smart Scan must not eagerly load the full App Manager list")
else:
    errors.append("unable to inspect scan() speed invariant section")

if "val usageAccessGranted = hasUsageAccess()" not in repository:
    errors.append("installedAppsSnapshot must cache Usage Access state once per refresh")

# ------------------------------------------------------------------
# Legal/resource truthfulness and translation parity.
# ------------------------------------------------------------------
default_strings_path = ROOT / "app/src/main/res/values/strings.xml"
tr_strings_path = ROOT / "app/src/main/res/values-tr/strings.xml"
default_strings = default_strings_path.read_text(encoding="utf-8")
tr_strings = tr_strings_path.read_text(encoding="utf-8")

for required_name in (
    "privacy_policy_body_v050",
    "terms_of_service_body_v050",
    "about_app_body_v050",
    "smart_cleanup_ad_notice",


    "cache_modern_total_label",
    "cache_modern_individual_steps",
    "cache_modern_safety_note",
    "cleanup_result_system_cache_title",
    "result_cache_cleared",
    "cleanup_result_system_cache_unmeasured",
    "cleanup_result_app_cache_title",
    "message_individual_cache_no_change",
):
    if f'name="{required_name}"' not in default_strings:
        errors.append(f"missing default legal/ad resource: {required_name}")
    if f'name="{required_name}"' not in tr_strings:
        errors.append(f"missing Turkish legal/ad resource: {required_name}")

obsolete_resource_names = {
    "privacy_policy_body",
    "terms_of_service_body",
    "about_app_body",
    "memory_optimizer",
    "memory_optimizer_subtitle",
    "memory_optimizing",
    "message_memory_optimized",
    "message_memory_optimized_stable",
    "ram_booster_title",
    "ram_booster_subtitle",
    "ram_release_action",
    "ram_optimize_action",
    "ram_optimizer_policy_note",
    "ram_optimizer_subtitle_v050",
    "ram_optimizer_title_v050",
    "honest_memory_note",
    "memory_tools_quick",
    "memory_tools_quick_subtitle",
    "dashboard_ram_optimizer_subtitle",
    "dashboard_ram_available_now",
    "dashboard_ram_optimize_action",
    "dashboard_ram_optimizing",
    "dashboard_ram_optimizer_subtitle_v0515",
    "dashboard_ram_pressure_warning_v0515",
    "ram_result_title",
    "ram_result_app_released",
    "ram_result_available_gain",
    "ram_result_stable",
    "ram_result_available_ram",
    "ram_result_app_memory",
    "ram_result_before",
    "ram_result_after",
    "ram_result_measured_note",
    "ram_result_done",
    "ram_result_rebuildable_released_title_v0515",
    "ram_result_rebuildable_released_v0515",
    "ram_result_measured_note_v0515",
}
for strings_path in (ROOT / "app/src/main/res").glob("values*/strings.xml"):
    body = strings_path.read_text(encoding="utf-8")
    for resource_name in obsolete_resource_names:
        if f'name="{resource_name}"' in body:
            errors.append(
                f"obsolete legal/RAM resource remains in {strings_path.relative_to(ROOT)}: {resource_name}"
            )
    for stale_copy in (
        "before the measured result",
        "ölçülen sonuçtan önce",
        "Android’s system picker",
        "Android’in sistem seçicisi",
    ):
        if stale_copy in body:
            errors.append(
                f"stale/misleading copy remains in {strings_path.relative_to(ROOT)}: {stale_copy}"
            )

# Ad disclosure lives on the About screen, not inside deletion confirmations,
# and names every format in use (T0.6, T1B.7).
for name, body in (("English", default_strings), ("Turkish", tr_strings)):
    notice = re.search(r'<string name="smart_cleanup_ad_notice">(.*?)</string>', body, re.S)
    text = notice.group(1) if notice else ""
    for token in (("app open", "native", "full-screen", "60 minutes") if name == "English"
                  else ("açılış reklamı", "yerel reklam", "tam ekran", "60 dakika")):
        if token not in text:
            errors.append(f"{name} ad disclosure must mention: {token}")
if "RAM Optimization releases" in default_strings or "RAM Optimizasyonu" in tr_strings:
    errors.append("Terms/resources must not describe the removed RAM Optimizer feature")
for expected in (
    'name="legal_contact"',
    "aggregate scan/cleanup history",
    # The privacy text must describe the 7-day Trash truthfully.
    "can be restored there for 7 days",
    "cannot be restored.",
):
    if expected not in default_strings:
        errors.append(f"missing English privacy/contact invariant: {expected}")
for expected in (
    'name="legal_contact"',
    "toplu tarama/temizlik geçmişini",
    "geri getirilemez",
):
    if expected not in tr_strings:
        errors.append(f"missing Turkish privacy/contact invariant: {expected}")
for expected in (
    "BuildConfig.SUPPORT_EMAIL.trim()",
    "R.string.legal_contact",
):
    if expected not in device_center:
        errors.append(f"in-app legal contact is not build-bound: {expected}")

for legal_document in (
    "PRIVACY_POLICY_EN.md",
    "PRIVACY_POLICY_TR.md",
    "TERMS_OF_SERVICE_EN.md",
    "TERMS_OF_SERVICE_TR.md",
):
    legal_body = read(legal_document)
    for expected in ("MrZekai", "{{SUPPORT_EMAIL}}"):
        if expected not in legal_body:
            errors.append(f"{legal_document} is missing publication invariant: {expected}")
for privacy_document in ("PRIVACY_POLICY_EN.md", "PRIVACY_POLICY_TR.md"):
    privacy_body = read(privacy_document)
    if "retention" not in privacy_body.lower() and "saklama" not in privacy_body.lower():
        errors.append(f"{privacy_document} is missing a retention/deletion section")

# English/Turkish key parity and Kotlin compile-resource guard.
default_names = {node.attrib["name"] for node in ET.parse(default_strings_path).getroot().findall("string")}
tr_names = {node.attrib["name"] for node in ET.parse(tr_strings_path).getroot().findall("string")}
if default_names != tr_names:
    if missing := sorted(default_names - tr_names):
        errors.append(f"Turkish translations missing keys: {missing}")
    if extra := sorted(tr_names - default_names):
        errors.append(f"default translations missing keys: {extra}")

kotlin_refs: dict[str, set[str]] = {}
for kotlin_file in (ROOT / "app/src/main/java").rglob("*.kt"):
    source = kotlin_file.read_text(encoding="utf-8")
    for resource_name in re.findall(r"R\.string\.([A-Za-z0-9_]+)", source):
        kotlin_refs.setdefault(resource_name, set()).add(str(kotlin_file.relative_to(ROOT)))
for resource_name in sorted(set(kotlin_refs) - default_names):
    locations = ", ".join(sorted(kotlin_refs[resource_name]))
    errors.append(f"Kotlin references missing default R.string.{resource_name}: {locations}")

# ------------------------------------------------------------------
# Existing key feature UI must remain intact.
# ------------------------------------------------------------------
smart_results = read("app/src/main/java/com/mrzekai/depoakilli/ui/SmartCleanResultsScreen.kt")
cleanup_plan_ui = read("app/src/main/java/com/mrzekai/depoakilli/ui/smart/CleanupPlanScreen.kt")
preview_sheet_ui = read("app/src/main/java/com/mrzekai/depoakilli/ui/smart/SelectedItemsPreviewSheet.kt")
# Faz 5: the plan is split into Home (giant figure, safe clean, "easy part
# done") and the findings list (T5.4: rows, start with the biggest, system
# steps). The same invariants are checked on the new surfaces.
clean_tab_ui = read("app/src/main/java/com/mrzekai/depoakilli/ui/CleanTab.kt")
for expected in ("fun findingsRows(", "plan-start-biggest", "plan-system-cache", "plan-kept-note"):
    if expected not in cleanup_plan_ui:
        errors.append(f"missing cleanup plan invariant: {expected}")
for expected in ("state.planFigures()", "HomeLogic.mode(", "DeleteConfirmDialog("):
    if expected not in clean_tab_ui:
        errors.append(f"missing Home plan invariant: {expected}")
for expected in ("ModalBottomSheet", "plan_preview_cloud", "plan_preview_whatsapp", "onRemoveItem"):
    if expected not in preview_sheet_ui:
        errors.append(f"missing single-confirmation preview invariant: {expected}")
if "SelectedItemsPreviewSheet(" not in clean_tab_ui:
    errors.append("Smart plan must confirm through SelectedItemsPreviewSheet")
for expected in (
    "DeleteConfirmDialog(",
    "StorageDetailPage",
    "FilePreviewDialog",
    "MediaPreviewContent(",
    "LazyVerticalGrid(",
    "smartCategoryReview",
):
    if expected not in smart_results and expected not in view_model:
        errors.append(f"missing Smart Clean/review invariant: {expected}")

# T2.3: one delete confirmation and one media preview/thumbnail path.
delete_confirm_ui = read("app/src/main/java/com/mrzekai/depoakilli/ui/common/DeleteConfirmDialog.kt")
for expected in ("confirm_trash_title", "confirm_see_files", "confirm_yes_clean", "R.string.cancel", "confirm_trash_body", "confirm_split_permanent", "confirm_split_trash", "confirm_permanent_action", "PillStyle.WHITE"):
    if expected not in delete_confirm_ui:
        errors.append(f"missing shared delete confirmation invariant: {expected}")
media_preview_ui = read("app/src/main/java/com/mrzekai/depoakilli/ui/media/FilePreviewDialog.kt")
media_thumbnails = read("app/src/main/java/com/mrzekai/depoakilli/ui/media/MediaThumbnails.kt")
if "ExoPlayer.Builder" not in media_preview_ui or "MediaPlayer" in media_preview_ui:
    errors.append("audio and video previews must share the ExoPlayer path")
if "override fun sizeOf" not in media_thumbnails:
    errors.append("thumbnail cache must be sized in bytes")
for kotlin_file in (ROOT / "app/src/main/java").rglob("*.kt"):
    source = kotlin_file.read_text(encoding="utf-8")
    if kotlin_file.name not in {"MediaThumbnails.kt"} and ("LruCache<" in source or "BitmapFactory.decodeFile" in source):
        errors.append(f"thumbnail decoding/caching must go through MediaThumbnails: {kotlin_file.relative_to(ROOT)}")

whatsapp_ui = read("app/src/main/java/com/mrzekai/depoakilli/ui/WhatsAppCleanerScreen.kt")
for expected in (
    "WhatsAppHeroCard",
    "WhatsAppGroupSection",
    "WhatsAppPreviewDialog",
    "DeleteConfirmDialog(",
    "MediaPreviewContent(",

):
    if expected not in whatsapp_ui:
        errors.append(f"missing WhatsApp review invariant: {expected}")

premium_tools = read("app/src/main/java/com/mrzekai/depoakilli/ui/PremiumCleanerToolScreen.kt")
for expected in (
    "PremiumCleanerToolScreen",
    "DeleteConfirmDialog(",
    "MediaPreviewContent(",
):
    if expected not in premium_tools:
        errors.append(f"missing premium cleaner review invariant: {expected}")


# ------------------------------------------------------------------
# Alpha9 Core UX: return context, truthful ads, targeted tools,
# screenshots, analyzer drill-down, and real ANALYZE snapshots.
# ------------------------------------------------------------------
clean_models_alpha9 = read("app/src/main/java/com/mrzekai/depoakilli/model/CleanModels.kt")

for expected in (
    "SCREENSHOTS,",
    "ScanFocus.SCREENSHOTS",
):
    if expected not in clean_models_alpha9 and expected not in repository:
        errors.append(f"missing alpha9 Screenshots invariant: {expected}")

for expected in (

    "ScanFocus.DUPLICATES",
    "SuggestionTool.DUPLICATES",
):
    if expected not in device_center:
        errors.append(f"missing alpha9 Tools UX invariant: {expected}")

junk_start = repository.find("ScanFocus.JUNK -> {")
junk_end = repository.find("ScanFocus.DUPLICATES", junk_start)
downloads_start = repository.find("ScanFocus.DOWNLOADS -> {")
downloads_end = repository.find("fun ageDays(", downloads_start)
if junk_start < 0 or junk_end <= junk_start or downloads_start < 0 or downloads_end <= downloads_start:
    errors.append("unable to inspect alpha10 Junk/Downloads partition")
else:
    junk_section = repository[junk_start:junk_end]
    downloads_section = repository[downloads_start:downloads_end]
    if "StoragePathRules.isDownloadPath(file.relativePath)" not in junk_section:
        errors.append("Junk must exclude the shared Download-path matcher")
    if "StoragePathRules.isDownloadPath(file.relativePath)" not in downloads_section:
        errors.append("Downloads must include the same shared Download-path matcher")

if "ScanFocus.SCREENSHOTS -> aiEngine.assessFocusedScreenshot(file)" not in repository:
    errors.append("dedicated Screenshots tool must use the conservative focused policy")

for expected in (
    "val recordsStorageChange = comprehensive || focus == ScanFocus.ANALYZE",
    "if (recordsStorageChange)",
    "withContext(Dispatchers.Default) { repository.hasUsageAccess() }",
    "                    recordsStorageChange &&\n",
):
    if expected not in view_model:
        errors.append(f"ANALYZE must create a real Storage Change snapshot: {expected}")

for expected in (
    "onAnalyze: () -> Unit",
    "storage_change_create_baseline",
    "storage_change_analysis_scope",
):
    if expected not in storage_change_screen:
        errors.append(f"missing in-place Storage Change analysis invariant: {expected}")

for expected in (
    "onReviewType: (StorageFileType) -> Unit",
    "storage_analyzer_review_action",
):
    if expected not in storage_analyzer:
        errors.append(f"missing Analyzer drill-down invariant: {expected}")

for forbidden in (
    "onClean:",
    "cleanupInProgress",
):
    if forbidden in storage_analyzer:
        errors.append(f"Analyzer must remain read-only in alpha9: {forbidden}")

for expected in (
    "val returnScreen = detailScreen",
    "val returnTabIndex = selectedTabIndex",
    "returnScreen == DetailScreen.WHATSAPP",
    "returnTabIndex == AppTab.TOOLS.ordinal",
    "detailScreen = DetailScreen.CLEAN_RESULTS",
):
    if expected not in cleaner_app:
        errors.append(f"cleanup result must return to its real source surface: {expected}")

if "onPrepareWhatsAppCleanup { changed ->" in cleaner_app:
    errors.append("WhatsApp cleanup must not navigate Home before the measured result")

for expected in (
    "state.scanFocus == ScanFocus.ANALYZE && state.storageReview.type != null",
    "onReviewType = { type -> onOpenStorageReview(type, false) }",
    # Screenshots and every other tool focus route to the tool screen (T3.1).
    "Every remaining focus is a tool scan",
):
    if expected not in cleaner_app:
        errors.append(f"missing alpha9 CleanerApp routing invariant: {expected}")

for expected in (
    "ScanFocus.SCREENSHOTS -> ToolVisualConfig",
    "R.string.screenshots_tool_subtitle",
):
    if expected not in premium_tools:
        errors.append(f"missing Screenshots premium-tool UX invariant: {expected}")

if "R.string.storage_analyzer_action_hint" in smart_results:
    errors.append("destructive storage review must not use the read-only Analyzer resource prefix")

for required_copy in (
    "Find exact duplicates verified by content; one original is always kept",
    "Measure real Android-reported cache per app and verify the actual reduction after cleanup",
):
    if required_copy not in default_strings:
        errors.append(f"missing alpha9 English product-truth copy: {required_copy}")

for required_copy in (
    "İçeriği doğrulanmış birebir kopyaları bul; her grupta bir orijinal her zaman korunur",
):
    if required_copy not in tr_strings:
        errors.append(f"missing alpha9 Turkish product-truth copy: {required_copy}")


# ------------------------------------------------------------------
# Alpha10 release-safety
# ------------------------------------------------------------------
storage_path_rules = read("app/src/main/java/com/mrzekai/depoakilli/data/StoragePathRules.kt")
ai_engine = read("app/src/main/java/com/mrzekai/depoakilli/data/AiCleaningEngine.kt")
duplicate_policy = read("app/src/main/java/com/mrzekai/depoakilli/data/DuplicatePolicy.kt")
whatsapp_classifier = read("app/src/main/java/com/mrzekai/depoakilli/data/WhatsAppMediaClassifier.kt")

for expected in (
    "Normalizer.Form.NFD",
    "lowercase(Locale.ROOT)",
    "combiningMarks",
    ".replace('ı', 'i')",
    '"/download/"',
    '"/downloads/"',
    '"/indirilen/"',
    '"/indirilenler/"',
    "fun isDownloadPath(path: String)",
):
    if expected not in storage_path_rules:
        errors.append(f"missing alpha10 locale-stable path invariant: {expected}")

for expected in (
    "StoragePathRules.isDownloadPath(file.relativePath)",
    "StoragePathRules.normalizePath(file.relativePath)",
    "StoragePathRules.normalizeText(file.mimeType)",
    "typedFiles.size",
):
    if expected not in repository:
        errors.append(f"missing alpha10 repository path/review invariant: {expected}")

for expected in (
    "StoragePathRules.normalizePath(file.relativePath)",
    "StoragePathRules.normalizeText(file.name)",
    "StoragePathRules.isDownloadPath(file.relativePath)",
    "fun assessFocusedScreenshot(file: IndexedFile)",
    "R.string.reason_screenshot_review",
    'path.contains("/screenshots/")',
    'path.contains("/screenshot/")',
):
    if expected not in ai_engine:
        errors.append(f"missing closed-test AI matching invariant: {expected}")
if "FOCUSED_SCREENSHOT_DAYS" in ai_engine:
    errors.append("Dedicated Screenshots tool must not age-filter accessible screenshots")
if ".lowercase()" in ai_engine:
    errors.append("AiCleaningEngine must not use locale-sensitive lowercase()")

for expected in (
    "StoragePathRules.isWhatsAppPath(relativePath)",
    "storageReviewGeneration",
    "val reviewGeneration = ++storageReviewGeneration",
    "reviewGeneration == storageReviewGeneration",
    "loading = true",
    "scannedFileCount = immediateItems.size",
    "storageReviewJob?.cancel()",
    "summary = if (focus == ScanFocus.ANALYZE)",
):
    if expected not in view_model:
        errors.append(f"missing alpha10 ViewModel safety invariant: {expected}")

if "review.loading && review.items.isEmpty()" not in smart_results:
    errors.append("Storage Review previews must remain visible while background expansion loads")
path_rules = read("app/src/main/java/com/mrzekai/depoakilli/data/StoragePathRules.kt")
whatsapp_path_rule = path_rules.split("fun isWhatsAppPath(path: String)", 1)[-1].split("fun isProtectedUserDataPath", 1)[0]
if ("StoragePathRules.isWhatsAppPath(relativePath)" not in smart_results or
        "fun isWhatsAppPath(path: String)" not in path_rules or
        "val normalized = normalizePath(path)" not in whatsapp_path_rule):
    errors.append("Smart review WhatsApp path matching must be locale-stable")

for expected in (
    "onSystemDismiss: () -> Unit",
    "onDone: (resultAdPresented: Boolean) -> Unit",
    "onDismissRequest = onSystemDismiss",
    "dismissOnClickOutside = false",
    "onDone(resultAdPresented)",
    "Spacer(Modifier.height(24.dp))",
):
    if expected not in cleanup_dialog:
        errors.append(f"missing alpha10 result-dialog safety invariant: {expected}")

# v41 accidental-click safety: the gap between the sponsored surface and the
# primary action must be constant, so the Done button cannot move downwards at
# the moment the result ad finishes loading.
if "if (resultAdPresented) {" in cleanup_dialog:
    errors.append(
        "result-dialog ad/action separation must not be conditional on the ad "
        "having loaded (accidental-click layout shift)"
    )

system_start = cleaner_app.find("onSystemDismiss = {")
done_start = cleaner_app.find("onDone = { resultAdPresented ->", system_start)
if system_start < 0 or done_start <= system_start:
    errors.append("Cleanup result must expose separate system-dismiss and Done handlers")
else:
    if "onCleanupResultDismissed()" in cleaner_app[system_start:done_start]:
        errors.append("Back/system dismissal must never request an Interstitial")
    if "onCleanupResultDismissed()" not in cleaner_app[done_start:done_start + 1300]:
        errors.append("Only explicit Done may own the cleanup Interstitial fallback")

for expected in (
    "StoragePathRules.normalizeText(mimeType)",
    "StoragePathRules.normalizeText(name)",
    "StoragePathRules.normalizePath(relativePath)",
):
    if expected not in whatsapp_classifier:
        errors.append(f"WhatsApp classifier must use stable matching: {expected}")

if "StoragePathRules.normalizePath(file.relativePath)" not in duplicate_policy:
    errors.append("Duplicate original-path policy must use stable path normalization")

if "Tüm erişilebilir ekran görüntülerini" not in tr_strings:
    errors.append("Turkish Screenshots copy must disclose all-accessible review behavior")
if "Review all accessible screenshots" not in default_strings:
    errors.append("English Screenshots copy must disclose all-accessible review behavior")
if "hiçbir öğe otomatik seçilmez" not in tr_strings:
    errors.append("Turkish Screenshots copy must disclose that nothing is preselected")
if "nothing is preselected" not in default_strings:
    errors.append("English Screenshots copy must disclose that nothing is preselected")

# ------------------------------------------------------------------
# Release-readiness / slim-QA contract.
workflow_alpha11 = read(".github/workflows/android-ci.yml")
# ------------------------------------------------------------------
for expected in (
    'create("qa") {',
    'isDebuggable = false',
    'isMinifyEnabled = true',
    'isShrinkResources = true',
    'getDefaultProguardFile("proguard-android-optimize.txt")',
):
    if expected not in build_file:
        errors.append(f"missing alpha11 slim-QA/build invariant: {expected}")
if "implementation(libs.androidx.compose.ui.tooling.preview)" in build_file:
    errors.append("unused Compose tooling-preview dependency must not be packaged")
for expected in (
    "assembleQa",
    "scripts/verify-qa-apk.sh",
    "app/build/outputs/apk/qa/app-qa.apk",
):
    if expected not in workflow_alpha11:
        errors.append(f"missing alpha11 slim-QA CI invariant: {expected}")

# ------------------------------------------------------------------
# Play closed-test build and binary gates.
# ------------------------------------------------------------------
closed_test_workflow = read(".github/workflows/closed-test-build.yml")
for expected in (
    "Play Closed Test AAB + APK",
    "bundleClosedTest",
    "assembleClosedTest",
    "lintClosedTest",
    "scripts/verify-closed-test-binaries.sh",
    "SmartCleaner-PLAY-CLOSED-TEST-AAB-v${{ env.VERSION_CODE }}",
    "SmartCleaner-CLOSED-TEST-APK-v${{ env.VERSION_CODE }}",
    "SmartCleaner-ClosedTest-Diagnostics-v${{ env.VERSION_CODE }}",
    "SmartCleaner-ClosedTest-v${{ env.VERSION_CODE }}.aab",
    "SUPPORT_EMAIL",
):
    if expected not in closed_test_workflow:
        errors.append(f"missing Play closed-test workflow invariant: {expected}")

closed_test_verifier = read("scripts/verify-closed-test-binaries.sh")
for expected in (
    "com.mrzekai.depoakilli",
    "EXPECTED_VERSION_NAME",
    "versionCode='$EXPECTED_VERSION_CODE'",
    "ca-app-pub-3940256099942544/6300978111",
    "ca-app-pub-3940256099942544/1033173712",
    "ca-app-pub-3940256099942544/1044960115",
    "ca-app-pub-3940256099942544/5224354917",
    "ca-app-pub-1380972808968213/4039180256",
    "ca-app-pub-1380972808968213",
    "android.permission.FOREGROUND_SERVICE",
    "androidx.work.impl.foreground.SystemForegroundService",
    "androidx.compose.ui.tooling.PreviewActivity",
    "AAB and APK are not signed by the same Play upload certificate",
    "Play upload certificate differs from v45",
    "PLAY_CLOSED_TEST_BINARY_GATE_PASS",
):
    if expected not in closed_test_verifier:
        errors.append(f"missing Play closed-test binary invariant: {expected}")

release_workflow = read(".github/workflows/release-aab.yml")
for expected in (
    "Play Production AAB + APK",
    "SUPPORT_EMAIL",
    "ADMOB_RESULT_NATIVE_ID",
    "testDebugUnitTest",
    "lintRelease",
    "bundleRelease assembleRelease",
    "scripts/verify-release-aab.sh",
    "SmartCleaner-PLAY-PRODUCTION-AAB-v${{ env.VERSION_CODE }}",
    "SmartCleaner-PRODUCTION-APK-v${{ env.VERSION_CODE }}",
):
    if expected not in release_workflow:
        errors.append(f"missing production workflow invariant: {expected}")

release_verifier = read("scripts/verify-release-aab.sh")
for expected in (
    "versionCode='$EXPECTED_VERSION_CODE'",
    "e15e1d31ea6dda44c7f8333eabdc9f4a4d0e9d8feeb9f321958b496edf4f4afd",
    "ca-app-pub-1380972808968213~9043355268",
    "ca-app-pub-1380972808968213/2118175647",
    "ca-app-pub-1380972808968213/8492012303",
    "ca-app-pub-1380972808968213/4039180256",
    "ca-app-pub-3940256099942544",
    "RELEASE_BINARY_GATE_PASS",
):
    if expected not in release_verifier:
        errors.append(f"missing production binary gate: {expected}")

release_environment = read("scripts/validate-release-env.sh")
for expected in ("SUPPORT_EMAIL", "ANDROID_KEYSTORE_BASE64"):
    normalized = "KEYSTORE_BASE64" if expected == "ANDROID_KEYSTORE_BASE64" else expected
    if normalized not in release_environment:
        errors.append(f"release environment validator is missing: {expected}")

# ------------------------------------------------------------------
# QA signing + CI contract.
# ------------------------------------------------------------------
qa_keystore = ROOT / "keystore/depoakilli-ci-qa.jks"
if qa_keystore.is_file():
    digest = hashlib.sha256(qa_keystore.read_bytes()).hexdigest()
    if digest != "d6e453480cd6e99fb7bbfd7192eef1719328979f09d6dba383249dbc46b5eac8":
        errors.append("QA keystore bytes changed; update-signing continuity would break")

workflow = read(".github/workflows/android-ci.yml")
for expected in (
    "actions/checkout@v7",
    "actions/setup-java@v5",
    "gradle/actions/setup-gradle@v6",
    "run: ./gradlew --stacktrace testDebugUnitTest",
    "run: ./gradlew --stacktrace lintDebug",
    "assembleQa",
    "Verify stable test signing certificate",
):
    if expected not in workflow:
        errors.append(f"missing CI invariant: {expected}")

phase_check = workflow.split("  full-checks:", 1)
if len(phase_check) != 2 or not all(
    expected in phase_check[1]
    for expected in ("./gradlew --stacktrace test", "./gradlew --stacktrace lint")
):
    errors.append("full-checks CI must run full test and lint tasks")
if len(phase_check) == 2 and ("ANDROID_KEYSTORE" in phase_check[1] or "head.ref ==" in phase_check[1]):
    errors.append("full-checks CI must run on every PR without the production upload key")

qa_signing_verifier = read("scripts/verify-qa-signing.sh")
for expected in ("apksigner", "apksigner.bat", "apksigner.exe"):
    if expected not in qa_signing_verifier:
        errors.append(f"QA signing verifier is missing cross-platform tool support: {expected}")

# ------------------------------------------------------------------
# v41 build-environment contract.
#
# Root cause of the v41 BUILD FAILED: Gradle 8.13 embeds Kotlin 2.0.21, whose
# bundled IntelliJ JavaVersion parser rejects Java 25+ while compiling
# settings.gradle.kts ("IllegalArgumentException: 25.0.4"). The daemon must run
# on Java 17 regardless of which JVM launched the wrapper.
# ------------------------------------------------------------------
daemon_jvm = read("gradle/gradle-daemon-jvm.properties")
if "toolchainVersion=17" not in daemon_jvm:
    errors.append("Gradle daemon must be pinned to the Java 17 toolchain")

settings_file = read("settings.gradle.kts")
for expected in (
    "JavaVersion.current()",
    "feature in 17..24",
):
    if expected not in settings_file:
        errors.append(f"missing build-JDK guard invariant: {expected}")

bootstrap = read("scripts/bootstrap-local-env.sh")
for expected in ("local.properties", "ANDROID_HOME", "cygpath -m"):
    if expected not in bootstrap:
        errors.append(f"clean-clone bootstrap is missing: {expected}")
if "sdk.dir=C:" in bootstrap or "/Users/" in bootstrap.replace("$HOME/Users", ""):
    errors.append("bootstrap script must not hard-code a personal SDK path")

gradle_properties = read("gradle.properties")
if "org.gradle.java.home" in gradle_properties:
    errors.append("gradle.properties must not pin a machine-specific JDK path")
for expected in (
    "org.gradle.jvmargs=-Xmx2g -Xss768k",
    "org.gradle.parallel=false",
    "org.gradle.workers.max=2",
    "kotlin.daemon.jvmargs=-Xmx768m -Xss768k",
):
    if expected not in gradle_properties:
        errors.append(f"missing memory-safe Gradle invariant: {expected}")

gitignore = read(".gitignore")
if "local.properties" not in gitignore:
    errors.append("local.properties must stay git-ignored")

# ------------------------------------------------------------------
# v41 destructive-path safety.
# ------------------------------------------------------------------
for expected in (
    "visitedDirectoryPaths",
    "visitedFilePaths",
    "canonicalPathOf",
    "canonicalPathWithinRoot",
    "isDeletableSharedStorageFile",
):
    if expected not in repository:
        errors.append(f"missing v41 storage-safety invariant: {expected}")

delete_start = repository.find("    fun removeFile(")
if delete_start < 0:
    errors.append("removeFile is missing")
else:
    delete_section = repository[delete_start:delete_start + 1400]
    if "isDeletableSharedStorageFile(file, roots)" not in delete_section:
        errors.append("direct file deletion must be confined to shared storage roots")

for expected in (
    "ioStorageSnapshot()",
    "ioOwnCacheBytes()",
    "CancellationException",
):
    if expected not in view_model:
        errors.append(f"missing v41 ViewModel main-thread/cancellation invariant: {expected}")

for forbidden in (
    "= repository.storageSnapshot()",
    "repository.ownCacheSize(),",
):
    if forbidden in view_model.replace(
        "withContext(Dispatchers.IO) { repository.storageSnapshot() }", ""
    ).replace("async(Dispatchers.IO) { repository.ownCacheSize() }", ""):
        errors.append(
            f"storage/cache probing must stay off the main dispatcher: {forbidden}"
        )

# Debug and the external-test QA build must not share an applicationId.
if 'applicationIdSuffix = ".debug"' not in build_file:
    errors.append("debug build type must use its own .debug applicationId suffix")
if build_file.count('applicationIdSuffix = ".qa"') != 1:
    errors.append("exactly one build type may claim the .qa applicationId suffix")

# ------------------------------------------------------------------
# v45 QA-report safety contract (QA v43, 27 Sep 2026).
# ------------------------------------------------------------------
if "fun isSystemMarkerFile(name: String)" not in storage_path_rules or 'normalizeText(name.trim()).startsWith(".")' not in storage_path_rules:
    errors.append("hidden dot-files must be recognised by StoragePathRules")
# V4: only Android's trash (.trashed-…) and unfinished writes (.pending-…) are
# exempt; every other dot-file is skipped by both indexers and refused at deletion.
if repository.count("StoragePathRules.isSkippedHiddenFile(") < 2 or repository.count("StoragePathRules.isSystemMarkerFile(") < 1:
    errors.append("marker files must be skipped by both indexers and refused at deletion")
if r'Regex("""^\.trashed-\d+-.+""")' not in storage_path_rules or r'Regex("""^\.pending-\d+-.+""")' not in storage_path_rules:
    errors.append("only the MediaStore .trashed-/.pending- patterns may be exempt from the dot-file rule")
if "recommended = isWhatsAppStatus" in ai_engine or "recommended = autoSelect" in ai_engine:
    errors.append("WhatsApp media and APK installers must never be preselected")
if "isStillImage(" not in ai_engine:
    errors.append("screen recordings must not be classified as screenshots")
if "summary.plan" not in smart_results or "plan.recommendedBytes" not in read("app/src/main/java/com/mrzekai/depoakilli/model/CleanModels.kt"):
    errors.append("Smart Clean headline must lead with the measured plan amount")
if "Modifier.width(92.dp)" in smart_results:
    errors.append("hero stats must use weighted columns (large-font overlap)")
if "premium_duplicates_kept_short" in premium_tools:
    errors.append("duplicate copies must not be labelled as the protected original")
if "BackHandler(enabled = activeDetail != null)" not in premium_tools:
    errors.append("system Back must close an open tool group first")
if "smartCategoryReview = null" not in view_model:
    errors.append("a new scan must close stale sub-lists")
if "app.systemApp" not in device_center or ".filterNot { it.systemApp }" not in smart_results:
    errors.append("system apps must not be offered for uninstall")
if "FLAG_ACTIVITY_NEW_DOCUMENT" not in main_activity or "Intent.createChooser(intent, getString(R.string.send_feedback))" not in main_activity:
    errors.append("rate/feedback external flows must return to the app / offer a chooser")
if "FeedbackDialog(" not in device_center:
    errors.append("feedback must show the support address in-app")

# T2.2 / T2.5: button text contrast and minimum text size are checked in source.
for kotlin_file in (ROOT / "app/src/main/java").rglob("*.kt"):
    source = kotlin_file.read_text(encoding="utf-8")
    relative = kotlin_file.relative_to(ROOT)
    if re.search(r"buttonColors\((?:[^()]|\([^()]*\))*?contentColor\s*=\s*Color\.White", source, re.S):
        errors.append(f"white text on a coloured button fails contrast; use ContrastTokens.OnBrightAction: {relative}")
    for size in re.findall(r"(?<![\w.])(\d+(?:\.\d+)?)\.sp", source):
        if float(size) < 12:
            errors.append(f"text below 12 sp in {relative}: {size}.sp")
            break
if "labelSmall = base.labelSmall.copy(fontSize = 12.sp" not in read("app/src/main/java/com/mrzekai/depoakilli/ui/theme/Theme.kt"):
    errors.append("Material labelSmall (11 sp) must be raised to 12 sp in the theme")

for strings_path in sorted((ROOT / "app/src/main/res").glob("values*/strings.xml")):
    names = [node.attrib.get("name") for node in ET.parse(strings_path).getroot()]
    duplicates = sorted({name for name in names if names.count(name) > 1})
    if duplicates:
        errors.append(f"duplicate resource names in {strings_path.parent.name}: {duplicates}")

# T2.7: sizes follow the phone's language through Formatter, with a root fallback.
byte_formatter_models = read("app/src/main/java/com/mrzekai/depoakilli/model/CleanModels.kt")
if "Formatter.formatShortFileSize" not in byte_formatter_models or "ByteFormatter.install(this)" not in read("app/src/main/java/com/mrzekai/depoakilli/DepoAkilliApplication.kt"):
    errors.append("ByteFormatter must use Formatter.formatShortFileSize with the app context installed at start")

if '"$progress%"' in read("app/src/main/java/com/mrzekai/depoakilli/ui/WhatsAppCleanerScreen.kt"):
    errors.append("WhatsApp progress percent must come from a string resource")
if "unicodeWrap" not in read("app/src/main/java/com/mrzekai/depoakilli/ui/common/BidiText.kt"):
    errors.append("paths and sizes must be bidi-wrapped for right-to-left languages")

for rules_name in ("backup_rules.xml", "data_extraction_rules.xml"):
    rules = read(f"app/src/main/res/xml/{rules_name}")
    for excluded in ("dashboard_snapshot_v1.xml", "storage_change_v1.xml", "cleanup_history_v1.xml", "trash_index.json", "kept_items_v1.xml", "excluded_folders_v1.xml"):
        if f'path="{excluded}"' not in rules:
            errors.append(f"{rules_name} must exclude device-specific state: {excluded}")

plural_sets = {}
for strings_path in sorted((ROOT / "app/src/main/res").glob("values*/strings.xml")):
    root = ET.parse(strings_path).getroot()
    plural_sets[strings_path.parent.name] = {node.attrib["name"] for node in root.findall("plurals")}
    for node in root.findall("plurals"):
        quantities = {item.attrib.get("quantity") for item in node.findall("item")}
        required_quantities = {
            "values-in": {"other"},
            "values-ru": {"one", "few", "many", "other"},
            "values-ar": {"zero", "one", "two", "few", "many", "other"},
        }.get(strings_path.parent.name, {"one", "other"})
        if not required_quantities.issubset(quantities):
            errors.append(f"plural {node.attrib['name']} in {strings_path.parent.name} needs {sorted(required_quantities)}")
default_plurals = plural_sets.get("values", set())
for folder, names in plural_sets.items():
    if names != default_plurals:
        errors.append(f"plural keys differ between values and {folder}: {sorted(names ^ default_plurals)}")
for kotlin_file in (ROOT / "app/src/main/java").rglob("*.kt"):
    source = kotlin_file.read_text(encoding="utf-8")
    for resource_name in re.findall(r"R\.plurals\.([A-Za-z0-9_]+)", source):
        if resource_name not in default_plurals:
            errors.append(f"Kotlin references missing R.plurals.{resource_name}: {kotlin_file.relative_to(ROOT)}")
for kotlin_file in (ROOT / "app/src/main/java").rglob("*.kt"):
    source = kotlin_file.read_text(encoding="utf-8")
    for resource_name in re.findall(r"R\.string\.([A-Za-z0-9_]+)", source):
        if resource_name in default_plurals:
            errors.append(f"{resource_name} is a plural; use pluralStringResource in {kotlin_file.relative_to(ROOT)}")

# T3.6: every workflow names its artifacts from the Gradle version.
for workflow in (".github/workflows/android-ci.yml", ".github/workflows/closed-test-build.yml", ".github/workflows/release-aab.yml"):
    workflow_text = read(workflow)
    if "Read app version from Gradle" not in workflow_text or "-v50" in workflow_text:
        errors.append(f"{workflow} must read the version from app/build.gradle.kts")
if "validate-translations.py" not in read(".github/workflows/android-ci.yml"):
    errors.append("android-ci.yml must run scripts/validate-translations.py")

if errors:
    print("Project validation failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)

print(f"Smart Cleaner v{VERSION_NAME} ({VERSION_CODE}) production + QA invariants are valid.")
