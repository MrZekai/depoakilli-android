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


def require(relative: str) -> pathlib.Path:
    path = ROOT / relative
    if not path.is_file():
        errors.append(f"missing required file: {relative}")
    return path


def read(relative: str) -> str:
    path = require(relative)
    if not path.is_file():
        return ""
    return path.read_text(encoding="utf-8")


# ------------------------------------------------------------------
# Required project surface for v0.5.17-alpha5.
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
    "app/src/main/java/com/mrzekai/depoakilli/ads/AdComponents.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ads/ResultAdComponents.kt",
    "app/src/main/java/com/mrzekai/depoakilli/data/AiCleaningEngine.kt",
    "app/src/main/java/com/mrzekai/depoakilli/data/DeviceRepository.kt",
    "app/src/main/java/com/mrzekai/depoakilli/data/DuplicatePolicy.kt",
    "app/src/main/java/com/mrzekai/depoakilli/data/WhatsAppMediaClassifier.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/CleanerApp.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/CleanerViewModel.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/CleanupResultDialog.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/DashboardSnapshotStore.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/CleanupHistoryStore.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/HomeVisualTokens.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/HomeDashboardComponents.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/DeviceCenterScreen.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/AppCacheManagerScreen.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/NeonDashboardScreen.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/PremiumCleanerToolScreen.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/SmartCleanResultsScreen.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/WhatsAppCleanerScreen.kt",
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-tr/strings.xml",
    "app/src/main/res/xml/locales_config.xml",
    ".github/workflows/android-ci.yml",
    ".github/workflows/release-aab.yml",
    "keystore/depoakilli-ci-qa.jks",
]
for relative in required:
    require(relative)

# XML must remain structurally valid across all resource sets.
for xml_file in ROOT.rglob("*.xml"):
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
    'android:enableOnBackInvokedCallback="false"',
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
for expected in (
    'applicationId = "com.mrzekai.depoakilli"',
    "minSdk = 30",
    "targetSdk = 36",
    "compileSdk = 36",
    "versionCode = 32",
    'versionName = "0.5.17-alpha5"',
    "validateReleaseAds",
    'applicationIdSuffix = ".qa"',
    'liveAdMobAppId = "ca-app-pub-1380972808968213~9043355268"',
    'liveBannerId = "ca-app-pub-1380972808968213/2118175647"',
    'liveInterstitialId = "ca-app-pub-1380972808968213/8492012303"',
    'liveAppOpenId = "ca-app-pub-1380972808968213/8923257140"',
    'sampleResultNativeVideoId = "ca-app-pub-3940256099942544/1044960115"',
    'providers.environmentVariable("ADMOB_RESULT_NATIVE_ID")',
    '"ADMOB_RESULT_NATIVE_ID"',
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
    "sampleFingerprint",
    "fingerprint(file",
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
    "setCriticalTaskActive",
    "showPostTaskInterstitial",
    "onCleanupResultDismissed",
    "onCleanupResultDismissed = ::onCleanupResultDismissed",
    "onPrepareCleanup = ::cleanSelected",
    "onPrepareStorageCleanup = ::cleanStorage",
    "onPrepareWhatsAppCleanup = ::cleanWhatsApp",
    "override fun onTrimMemory(level: Int)",
    "ComponentCallbacks2.TRIM_MEMORY_BACKGROUND",
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

# ------------------------------------------------------------------
# Full-screen session caps and adaptive banner.
# ------------------------------------------------------------------
ads = read("app/src/main/java/com/mrzekai/depoakilli/ads/AdComponents.kt")
result_ads = read("app/src/main/java/com/mrzekai/depoakilli/ads/ResultAdComponents.kt")
for expected in (
    "INTERSTITIAL/SHOW_SKIP session-cap",
    "APP_OPEN/SHOWED_SESSION_ONLY",
    "MIN_ELIGIBLE_RETURNS_BEFORE_FIRST_AD = 1",
    "FULL_SCREEN_SEPARATION_MILLIS = 90L * 1000L",
    "MIN_INTERVAL_MILLIS = 5L * 60L * 1000L",
    "MIN_BACKGROUND_DURATION_MILLIS = 30L * 1000L",
    "MIN_SHOW_INTERVAL_MILLIS = 60L * 60L * 1000L",
    "getCurrentOrientationAnchoredAdaptiveBannerAdSize",
    "Lifecycle.Event.ON_RESUME -> adView.resume()",
    "Lifecycle.Event.ON_PAUSE -> adView.pause()",
    "adView.destroy()",
    "releaseCachedAd",
):
    if expected not in ads:
        errors.append(f"missing v0.5.17 ad invariant: {expected}")
if ads.count("private var shownThisProcess = false") != 2:
    errors.append("exactly one process-session cap is required in each fullscreen ad controller")
if "AdSize.BANNER" in ads:
    errors.append("fixed 320x50 AdSize.BANNER must not return; v0.5.17 uses anchored adaptive banner")
if "setImmersiveMode(true)" in ads:
    errors.append("Interstitial must let Google AdActivity own system UI/insets")

application = read("app/src/main/java/com/mrzekai/depoakilli/DepoAkilliApplication.kt")
for expected in (
    "fullScreenAdSurfaceActive",
    "onAppBackgrounded",
    "beginInterstitialSurface",
    "endInterstitialSurface",
    "suppressNextAppOpenAd",
    "releaseCachedAd",
    "ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN",
):
    if expected not in application:
        errors.append(f"missing app lifecycle/ad invariant: {expected}")
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
dashboard = read("app/src/main/java/com/mrzekai/depoakilli/ui/NeonDashboardScreen.kt")
home_components = read("app/src/main/java/com/mrzekai/depoakilli/ui/HomeDashboardComponents.kt")
home_tokens = read("app/src/main/java/com/mrzekai/depoakilli/ui/HomeVisualTokens.kt")
cleanup_history_store = read("app/src/main/java/com/mrzekai/depoakilli/ui/CleanupHistoryStore.kt")
memory_dialog = ROOT / "app/src/main/java/com/mrzekai/depoakilli/ui/MemoryOptimizationResultDialog.kt"

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
    "onDismiss: (resultAdPresented: Boolean) -> Unit",
    "CleanupResultKind.SYSTEM_CACHE",
    "verticalScroll(rememberScrollState())",
):
    if expected not in cleanup_dialog:
        errors.append(f"missing alpha5 result-dialog invariant: {expected}")

for expected in (
    "beginDeepCacheCleanupMeasurement()",
    "kind = CleanupResultKind.SYSTEM_CACHE",
    "measuredCacheReduction",
):
    if expected not in view_model:
        errors.append(f"missing truthful Android-cache result invariant: {expected}")

if "state.cleanupResult == null" not in cleaner_app:
    errors.append("normal bottom banner must be disabled while cleanup result is visible")

if "eligibleForNaturalBreakAd && !resultAdPresented" not in cleaner_app:
    errors.append("Interstitial must be fallback-only when result ad was not presented")


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
    "!resultAdPresented",
    "state.cleanupResult == null",
    "CleanupResultKind.SYSTEM_CACHE",
    "selectedTabIndex = AppTab.HOME.ordinal",
    "BackHandler(enabled = hasInAppBackTarget && !fullScreenAdActive)",
    "Spacer(Modifier.height(10.dp))",
):
    if expected not in cleaner_app:
        errors.append(f"missing result-first/UI invariant: {expected}")

# Trust surfaces do not carry banners; content surfaces still can.
for expected in (
    "AppTab.HOME,",
    "AppTab.TOOLS -> true",
    "AppTab.ME -> false",
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
    "AppTab.ME -> SettingsDetailScreen(",
    "DetailScreen.ACCESS -> SecurityCenterScreen(",
    "onOpenPrivacyAccess",
):
    if expected not in cleaner_app and expected not in device_center:
        errors.append(f"missing Phase-2 navigation invariant: {expected}")

for expected in (
    "HomeOpportunityCard(",
    "HomeSmartCleanCard(",
    "HomeCleanupProofCard(",
    "HomeSuggestionCard(",
    "HomeToolShortcut(",
    "HomeExploreCard(",
    "onOpenAppCache: () -> Unit",
    "onOpenPrivacyAccess: () -> Unit",
    "onOpenTools: () -> Unit",
):
    if expected not in dashboard:
        errors.append(f"missing alpha3 premium Home invariant: {expected}")

for expected in (
    "HomeBrandHeader",
    "HomeOpportunityCard",
    "HomeSmartCleanCard",
    "HomeCleanupProofCard",
    "HomeSuggestionCard",
    "HomeToolShortcut",
    "HomeExploreCard",
):
    if expected not in home_components:
        errors.append(f"missing alpha3 Home component: {expected}")

for expected in (
    "HeroGradient",
    "PrimaryGradient",
    "ExploreGradient",
    "PageGradient",
):
    if expected not in home_tokens:
        errors.append(f"missing Home visual token: {expected}")

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
    "whatsapp_cleanup_ad_notice",
    "premium_cleanup_ad_notice",
    "home_last_cleanup_title",
    "home_last_cleanup_summary",
    "home_smart_suggestions_title",
    "home_different_tools_title",
    "home_discover_title",
    "cache_modern_total_label",
    "cache_modern_individual_steps",
    "cache_modern_safety_note",
    "cleanup_result_system_cache_title",
    "cleanup_result_system_cache_reduced",
    "cleanup_result_system_cache_unmeasured",
    "cleanup_result_system_cache_note",
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

if default_strings.count("measured result is always shown before any ad") != 3:
    errors.append("all three English cleanup notices must be result-first")
if tr_strings.count("ölçülen sonuç her zaman reklamdan önce gösterilir") != 3:
    errors.append("all three Turkish cleanup notices must be result-first")
if "RAM Optimization releases" in default_strings or "RAM Optimizasyonu" in tr_strings:
    errors.append("Terms/resources must not describe the removed RAM Optimizer feature")

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
for expected in (
    "SmartCleanHero",
    "CleanupConfirmationDialog",
    "StorageDetailPage",
    "StorageCleanupConfirmationDialog",
    "FilePreviewDialog",
    "LazyVerticalGrid(",
    "ExoPlayer.Builder",
    "smartCategoryReview",
):
    if expected not in smart_results and expected not in view_model:
        errors.append(f"missing Smart Clean/review invariant: {expected}")

whatsapp_ui = read("app/src/main/java/com/mrzekai/depoakilli/ui/WhatsAppCleanerScreen.kt")
for expected in (
    "WhatsAppHeroCard",
    "WhatsAppGroupSection",
    "WhatsAppPreviewDialog",
    "ExoPlayer.Builder",
    "whatsapp_cleanup_ad_notice",
):
    if expected not in whatsapp_ui:
        errors.append(f"missing WhatsApp review invariant: {expected}")

premium_tools = read("app/src/main/java/com/mrzekai/depoakilli/ui/PremiumCleanerToolScreen.kt")
for expected in (
    "PremiumCleanerToolScreen",
    "PremiumCleanupConfirmationDialog",
    "premium_cleanup_ad_notice",
    "ExoPlayer.Builder",
):
    if expected not in premium_tools:
        errors.append(f"missing premium cleaner review invariant: {expected}")

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
    "testDebugUnitTest",
    "lintDebug",
    "assembleDebug",
    "Verify stable test signing certificate",
):
    if expected not in workflow:
        errors.append(f"missing CI invariant: {expected}")

if errors:
    print("Project validation failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)

print("Smart Cleaner v0.5.17-alpha5 result-native monetization invariants are valid.")
