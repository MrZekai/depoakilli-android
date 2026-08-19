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


def require(path: str) -> pathlib.Path:
    target = ROOT / path
    if not target.is_file():
        errors.append(f"missing required file: {path}")
    return target


required = [
    "settings.gradle.kts",
    "build.gradle.kts",
    "gradle/libs.versions.toml",
    "gradlew",
    "gradle/wrapper/gradle-wrapper.jar",
    "gradle/wrapper/gradle-wrapper.properties",
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-tr/strings.xml",
    "app/src/main/res/xml/locales_config.xml",
    "app/src/main/res/layout/smart_video_player.xml",
    "app/src/main/java/com/mrzekai/depoakilli/MainActivity.kt",
    "app/src/main/java/com/mrzekai/depoakilli/data/AiCleaningEngine.kt",
    "app/src/main/java/com/mrzekai/depoakilli/data/DeviceRepository.kt",
    "app/src/main/java/com/mrzekai/depoakilli/data/DuplicatePolicy.kt",
    "app/src/main/java/com/mrzekai/depoakilli/data/WhatsAppMediaClassifier.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/CleanerApp.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/CleanerViewModel.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/WhatsAppCleanerScreen.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/PremiumCleanerToolScreen.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/DeviceCenterScreen.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/NeonDashboardScreen.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/MemoryOptimizationResultDialog.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/CleanupResultDialog.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/SmartCleanResultsScreen.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/SecurityCenterScreen.kt",
    "app/src/test/java/com/mrzekai/depoakilli/data/AiCleaningEngineTest.kt",
    "app/src/test/java/com/mrzekai/depoakilli/data/WhatsAppMediaClassifierTest.kt",
    "app/src/test/java/com/mrzekai/depoakilli/data/DuplicatePolicyTest.kt",
    ".github/workflows/android-ci.yml",
    ".github/workflows/release-aab.yml",
    "docs/CLEANER_ENGINE_V050.md",
    "docs/NEON_DASHBOARD_V051.md",
    "docs/REVISION_NOTES_V051.md",
    "docs/REVISION_NOTES_V057.md",
    "docs/REVISION_NOTES_V058.md",
    "docs/REVISION_NOTES_V059.md",
    "docs/PLAY_PERMISSIONS_V050.md",
    "docs/PERMISSIONS.md",
    "docs/ANDROID_CACHE_LIMITS.md",
    "docs/FEATURE_MATRIX.md",
    "docs/QA_SIGNING.md",
    "scripts/verify-qa-signing.sh",
    "keystore/depoakilli-ci-qa.jks",
    "app/src/debug/res/values/strings.xml",
    "app/src/debug/res/values-tr/strings.xml",
    "app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground_art.png",
    "app/src/main/res/drawable-xxxhdpi/ic_launcher_legacy_art.png",
    "app/src/main/res/drawable-xxxhdpi/ic_launcher_monochrome_art.png",
    "store-assets/icon-512.png",
    "store-assets/icon-master-1536.png",
]
for relative in required:
    require(relative)


def png_dimensions(path: pathlib.Path) -> tuple[int, int] | None:
    if not path.is_file():
        return None
    with path.open("rb") as stream:
        if stream.read(8) != b"\x89PNG\r\n\x1a\n":
            errors.append(f"launcher asset is not a real PNG: {path.relative_to(ROOT)}")
            return None
        stream.read(4)
        chunk_type = stream.read(4)
        if chunk_type != b"IHDR":
            errors.append(f"launcher PNG has no valid IHDR: {path.relative_to(ROOT)}")
            return None
        return struct.unpack(">II", stream.read(8))


for relative, expected in (
    ("app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground_art.png", (432, 432)),
    ("app/src/main/res/drawable-xxxhdpi/ic_launcher_legacy_art.png", (432, 432)),
    ("app/src/main/res/drawable-xxxhdpi/ic_launcher_monochrome_art.png", (432, 432)),
    ("store-assets/icon-512.png", (512, 512)),
    ("store-assets/icon-master-1536.png", (1536, 1536)),
):
    dimensions = png_dimensions(ROOT / relative)
    if dimensions is not None and dimensions != expected:
        errors.append(f"incorrect launcher dimensions for {relative}: expected {expected}, got {dimensions}")

for xml_file in ROOT.rglob("*.xml"):
    try:
        ET.parse(xml_file)
    except ET.ParseError as exc:
        errors.append(f"invalid XML {xml_file.relative_to(ROOT)}: {exc}")

manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for expected in (
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.PACKAGE_USAGE_STATS",
    'android:localeConfig="@xml/locales_config"',
    '<queries>',
    'android.intent.category.LAUNCHER',
):
    if expected not in manifest:
        errors.append(f"missing v0.5 manifest invariant: {expected}")

for forbidden in (
    "QUERY_ALL_PACKAGES",
    "BIND_ACCESSIBILITY_SERVICE",
    "KILL_BACKGROUND_PROCESSES",
    "WRITE_EXTERNAL_STORAGE",
    "CLEAR_APP_CACHE\"",
):
    if forbidden in manifest:
        errors.append(f"forbidden permission/automation present: {forbidden}")

build_file = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
for expected in (
    'applicationId = "com.mrzekai.depoakilli"',
    "minSdk = 30",
    "targetSdk = 36",
    "compileSdk = 36",
    "versionCode = 20",
    'versionName = "0.5.13"',
    "validateReleaseAds",
    'applicationIdSuffix = ".qa"',
    'storeFile = qaKeystore',
):
    if expected not in build_file:
        errors.append(f"missing build invariant: {expected}")

catalog = (ROOT / "gradle/libs.versions.toml").read_text(encoding="utf-8")
for expected in (
    'agp = "8.13.2"',
    'kotlin = "2.2.21"',
    'compose-bom = "2026.06.01"',
    'fragment = "1.9.0"',
    'lifecycle = "2.10.0"',
    'media3 = "1.10.1"',
):
    if expected not in catalog:
        errors.append(f"missing compatible dependency pin: {expected}")

if "enforcedPlatform(libs.androidx.compose.bom)" not in build_file:
    errors.append("Compose BOM must remain enforced")

repository = (ROOT / "app/src/main/java/com/mrzekai/depoakilli/data/DeviceRepository.kt").read_text(encoding="utf-8")
for expected in (
    "Environment.isExternalStorageManager()",
    "indexSharedStorage",
    "indexWhatsAppFiles",
    "sampleFingerprint",
    "fingerprint(file",
    "MAX_INDEXED_FILES = 200_000",
    "MAX_WHATSAPP_FILES = 100_000",
    "CleanCategory.LARGE_FILE",
    "ScanFocus.DEEP",
    "ScanFocus.APKS",
    "ScanFocus.ANALYZE",
    "assessDeep(file",
    "SCAN_PROGRESS_THROTTLE_MILLIS = 120L",
):
    if expected not in repository:
        errors.append(f"missing Cleaner Engine repository invariant: {expected}")

for expected in (
    "scanStorageReview",
    "deleteStorageReviewItems",
    "MAX_STORAGE_REVIEW_ITEMS = 50_000",
):
    if expected not in repository:
        errors.append(f"missing v0.5.8 storage review repository invariant: {expected}")

for expected in (
    "smartMediaTypeStats",
    "isWhatsAppSharedMedia",
    "smartMediaTypes = smartMediaTypeStats(indexed)",
):
    if expected not in repository:
        errors.append(f"missing v0.5.12 no-extra-scan media summary invariant: {expected}")

for forbidden in (
    "MAX_HASH_BYTES",
    "MAX_DUPLICATE_GROUP",
    "MAX_HASH_GROUPS",
    "KEY_WHATSAPP_TREE_URI",
    "DocumentsContract",
    "APP_CACHE_URI",
):
    if forbidden in repository:
        errors.append(f"legacy v0.4 cleaner restriction still present: {forbidden}")

main_activity = (ROOT / "app/src/main/java/com/mrzekai/depoakilli/MainActivity.kt").read_text(encoding="utf-8")
for expected in (
    "ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
    "StorageManager.ACTION_CLEAR_APP_CACHE",
    "Settings.ACTION_USAGE_ACCESS_SETTINGS",
    "runCleanupAdGate",
    "showMemoryOptimizationInterstitialThenOptimize",
    "releaseWhatsAppThumbnailMemory",
    "releasePremiumToolThumbnailMemory",
):
    if expected not in main_activity:
        errors.append(f"missing Android system flow: {expected}")
if "OpenDocumentTree" in main_activity:
    errors.append("WhatsApp must not use OpenDocumentTree in v0.5")

# Compile-guard invariants caught by the first real Android CI run.
if "setContent {" in main_activity and "import androidx.activity.compose.setContent" not in main_activity:
    errors.append("MainActivity uses setContent without androidx.activity.compose.setContent import")
if "distinctBy(File::absolutePath)" in repository:
    errors.append("DeviceRepository must not use unsupported synthetic Java property reference File::absolutePath")
dashboard = (ROOT / "app/src/main/java/com/mrzekai/depoakilli/ui/NeonDashboardScreen.kt").read_text(encoding="utf-8")
for expected in (
    "smart_clean_primary",
    "DashboardToolTile",
    "DashboardHero",
    "CleaningScoreRing",
    "RamOptimizationStrip",
):
    if expected not in dashboard:
        errors.append(f"missing v0.5.11 dashboard invariant: {expected}")
if "onOpenAppCache" in dashboard:
    errors.append("v0.5.11 home dashboard must not show the old paired deep-app-cleanup card")
if "PRO" in dashboard or "Premium" in dashboard:
    errors.append("v0.5.11 dashboard must not advertise a non-existent Pro/Premium tier")

view_model = (ROOT / "app/src/main/java/com/mrzekai/depoakilli/ui/CleanerViewModel.kt").read_text(encoding="utf-8")
for expected in (
    "MemoryOptimizationResult",
    "memoryOptimizationResult",
    "dismissMemoryOptimizationResult",
    "CleanupResult",
    "cleanupResult",
    "dismissCleanupResult",
):
    if expected not in view_model:
        errors.append(f"missing v0.5.11 RAM result invariant: {expected}")
for forbidden in (
    "summary = it.summary.copy(items = emptyList())",
    "whatsAppSummary = it.whatsAppSummary.copy(items = emptyList())",
):
    if forbidden in view_model:
        errors.append(f"RAM optimization must preserve user scan selections: {forbidden}")

memory_result_ui = (ROOT / "app/src/main/java/com/mrzekai/depoakilli/ui/MemoryOptimizationResultDialog.kt").read_text(encoding="utf-8")
for expected in (
    "MemoryOptimizationResultDialog",
    "ram_result_measured_note",
    "appMemoryReleasedBytes",
    "availableRamGainBytes",
):
    if expected not in memory_result_ui:
        errors.append(f"missing v0.5.11 RAM result UI invariant: {expected}")

smart_results = (ROOT / "app/src/main/java/com/mrzekai/depoakilli/ui/SmartCleanResultsScreen.kt").read_text(encoding="utf-8")
for expected in (
    "SmartCleanHero",
    "SmartCategoryStripCard",
    "SmartHorizontalPreviewTile",
    "SmartCleanBottomAction",
    "CleanupConfirmationDialog",
    "CategoryDetailPage",
    "StorageDetailPage",
    "StorageReviewGridItem",
    "StorageReviewListItem",
    "ReviewCleanupFooter",
    "StorageCleanupConfirmationDialog",
    "FilePreviewDialog",
    "CategoryGridItem",
    "LazyVerticalGrid(",
    "VideoFilePreview",
    "smart_video_play",
    "LazyRow(",
):
    if expected not in smart_results:
        errors.append(f"missing v0.5.8 Smart Clean/storage review invariant: {expected}")
if "import androidx.compose.foundation.layout.weight" in smart_results:
    errors.append("SmartCleanResultsScreen must not import the internal Compose layout weight symbol")
if "Smart Clean scan in progress" not in (ROOT / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8"):
    errors.append("v0.5.8 must use the upgraded Smart Clean scan-progress copy")
if "smart_detail_grid_hint" not in smart_results:
    errors.append("v0.5.8 category detail must use the visual review grid guidance")
if "ExoPlayer.Builder" not in smart_results or "MediaItem.fromUri" not in smart_results:
    errors.append("v0.5.8 video preview must use Jetpack Media3 ExoPlayer")
if "VideoView" in smart_results:
    errors.append("legacy VideoView must not remain in v0.5.8 Smart Clean preview")
if "R.layout.smart_video_player" not in smart_results:
    errors.append("v0.5.8 video preview must use the TextureView-backed PlayerView layout")
for expected in (
    "SmartStorageSuggestionCard",
    "UnusedAppsSectionCard",
    "UnusedAppsReviewDialog",
    "smartVisibleCategoryItems",
    "smartMediaPreviews",
    "UNUSED_APP_DAYS = 60L",
    "smart_clean_suggestions_subtitle_v0512",
):
    if expected not in smart_results:
        errors.append(f"missing v0.5.12 Smart priority-review invariant: {expected}")
if "orderedSmartCategories" in smart_results:
    errors.append("v0.5.12 Smart Clean must not restore the old generic category ordering")
for strings_path in (
    ROOT / "app/src/main/res/values/strings.xml",
    ROOT / "app/src/main/res/values-tr/strings.xml",
):
    visible_strings = strings_path.read_text(encoding="utf-8")
    if "Cleaner Engine" in visible_strings or "CLEANER ENGINE" in visible_strings:
        errors.append(f"user-visible Cleaner Engine branding remains in {strings_path}")

cleaner_app = (ROOT / "app/src/main/java/com/mrzekai/depoakilli/ui/CleanerApp.kt").read_text(encoding="utf-8")
ads_source = (ROOT / "app/src/main/java/com/mrzekai/depoakilli/ads/AdComponents.kt").read_text(encoding="utf-8")
view_model_source = (ROOT / "app/src/main/java/com/mrzekai/depoakilli/ui/CleanerViewModel.kt").read_text(encoding="utf-8")
if "dashboardReviewBytes" not in view_model_source or "summary.safeSuggestedBytes" not in view_model_source or "summary.reviewBytes" not in view_model_source:
    errors.append("dashboard must separate safely selected cleanup bytes from review-only candidate bytes")
if "dashboard_safe_cleanable" not in dashboard or "dashboard_review_ready" not in dashboard:
    errors.append("dashboard must label safe cleanup and review candidates separately")
ai_engine_source = (ROOT / "app/src/main/java/com/mrzekai/depoakilli/data/AiCleaningEngine.kt").read_text(encoding="utf-8")
if 'path.contains("/thumbnails/")' in ai_engine_source:
    errors.append("generic user Thumbnails folders must not be auto-classified as junk")
if 'val generatedThumbnail = path.contains("/.thumbnails/")' not in ai_engine_source:
    errors.append("hidden generated thumbnail cache rule is missing")
if "detailScreen == DetailScreen.CLEAN_RESULTS" not in cleaner_app or "BannerAd(canRequestAds = canRequestAds)" not in cleaner_app:
    errors.append("Smart Clean results must reserve an anchored banner slot")
if "AdSize.BANNER" not in ads_source:
    errors.append("BannerAd must use the standard 320x50 mobile banner size in v0.5.8")
if "getLargeAnchoredAdaptiveBannerAdSize" in ads_source:
    errors.append("v0.5.8 must not reserve the oversized large adaptive banner")
main_activity_source = (ROOT / "app/src/main/java/com/mrzekai/depoakilli/MainActivity.kt").read_text(encoding="utf-8")
if "showBeforeCleanup" not in ads_source or "onFinished: () -> Unit" not in ads_source:
    errors.append("cleanup interstitial must run before deletion and expose a completion callback")
if "showCleanupInterstitialThenDelete" not in main_activity_source or "executeCleanupPlan" not in main_activity_source:
    errors.append("MainActivity must show the cleanup interstitial before starting the delete plan")
if "cleanupInProgress: Boolean = false" not in view_model_source or "if (_state.value.cleanupInProgress) return" not in view_model_source:
    errors.append("cleanup must guard against duplicate concurrent delete executions")
if "refreshAfterCleanup" not in view_model_source:
    errors.append("cleanup must refresh results after deletion completes")
if "smartCategoryReview" not in view_model_source or "openSmartCategoryReview" not in view_model_source:
    errors.append("Smart Clean View all must use in-screen category review navigation")
for expected in (
    "smartCategoryReviewIds",
    "openSmartCategoryReview(category: CleanCategory, itemIds: Set<String>? = null)",
):
    if expected not in view_model_source:
        errors.append(f"v0.5.12 filtered Smart category review invariant missing: {expected}")
if "StorageReviewSummary" not in view_model_source or "openStorageReview" not in view_model_source:
    errors.append("Storage Analysis must load a full selectable review state")
for expected in (
    "excludeWhatsAppMedia",
    "scanStorageReview(type, excludeWhatsAppMedia)",
):
    if expected not in view_model_source:
        errors.append(f"v0.5.12 unique Smart media review invariant missing: {expected}")
if "toggleStorageReviewItem" not in view_model_source or "toggleAllStorageReviewItems" not in view_model_source:
    errors.append("Storage Analysis must support per-file and select-all selection")
if "deleteSelectedStorageReview" not in view_model_source:
    errors.append("Storage Analysis must support real selected-file deletion")
if "showStorageCleanupInterstitialThenDelete" not in main_activity_source:
    errors.append("Storage Analysis deletion must reuse the disclosed interstitial-before-delete flow")
if "storage_analyzer_action_hint" not in smart_results or "storage_review_manual_reason" not in smart_results:
    errors.append("Storage Analysis must explain manual review and non-automatic selection")
if "TopAppBar(" in cleaner_app:
    if "ExperimentalMaterial3Api" not in cleaner_app or "@OptIn(ExperimentalMaterial3Api::class)" not in cleaner_app:
        errors.append("CleanerApp TopAppBar requires ExperimentalMaterial3Api opt-in")

whatsapp_ui = (ROOT / "app/src/main/java/com/mrzekai/depoakilli/ui/WhatsAppCleanerScreen.kt").read_text(encoding="utf-8")
for expected in (
    "WhatsAppHeroCard",
    "WhatsAppGroupSection",
    "LazyRow(",
    "WhatsAppGroupDetailPage",
    "LazyVerticalGrid(",
    "WhatsAppMediaCard",
    "WhatsAppPreviewDialog",
    "WhatsAppVideoPreview",
    "ExoPlayer.Builder",
    "WhatsAppThumbnailCache",
    "whatsapp_filter_incoming",
    "whatsapp_review_and_clean",
):
    if expected not in whatsapp_ui:
        errors.append(f"missing v0.5.8 WhatsApp premium review invariant: {expected}")
if "WhatsAppMediaRow" in whatsapp_ui:
    errors.append("v0.5.8 WhatsApp UI must not fall back to the legacy plain vertical media rows")
if "VideoView" in whatsapp_ui:
    errors.append("v0.5.8 WhatsApp video preview must use Media3, not legacy VideoView")
if "detailScreen == DetailScreen.CLEAN_RESULTS || detailScreen == DetailScreen.WHATSAPP" not in cleaner_app:
    errors.append("WhatsApp Cleaner must reserve the single standard banner shell")
if "onPrepareWhatsAppCleanup" not in cleaner_app:
    errors.append("WhatsApp cleanup must route through the pre-delete interstitial callback")
if "showWhatsAppCleanupInterstitialThenDelete" not in main_activity_source:
    errors.append("MainActivity must route WhatsApp cleanup through the interstitial-before-delete flow")
if "deleteSelectedWhatsApp(onCompleted: (Boolean) -> Unit = {})" not in view_model_source:
    errors.append("WhatsApp delete must expose completion state for post-cleanup navigation")
if "whatsapp_cleanup_ad_notice" not in whatsapp_ui:
    errors.append("WhatsApp final confirmation must disclose the eligible pre-delete interstitial")



premium_tools = (ROOT / "app/src/main/java/com/mrzekai/depoakilli/ui/PremiumCleanerToolScreen.kt").read_text(encoding="utf-8")
for expected in (
    "PremiumCleanerToolScreen",
    "PremiumCleanupConfirmationDialog",
    "premium_cleanup_confirm_action",
    "PremiumToolHero",
    "PremiumToolSectionCard",
    "PremiumToolDetailPage",
    "PremiumToolPreviewDialog",
    "LazyRow(",
    "LazyVerticalGrid(",
    "ExoPlayer.Builder",
    "ToolThumbnailCache",
    "premium_duplicates_original_safe",
    "premium_apk_installer_note",
):
    if expected not in premium_tools:
        errors.append(f"missing v0.5.9 premium tool review invariant: {expected}")
for focus in (
    "ScanFocus.DUPLICATES",
    "ScanFocus.LARGE_FILES",
    "ScanFocus.APKS",
    "ScanFocus.MEDIA",
    "ScanFocus.JUNK",
    "ScanFocus.DOWNLOADS",
    "ScanFocus.DEEP",
):
    if focus not in premium_tools and focus not in cleaner_app:
        errors.append(f"premium tool routing missing for {focus}")
if "PremiumCleanerToolScreen(" not in cleaner_app:
    errors.append("non-Smart cleaner tools must route through PremiumCleanerToolScreen")
if "CleanupResultDialog(" not in cleaner_app:
    errors.append("v0.5.13 cleanup completion must show the measured result dialog")
if 'onClean = { onClean(null) }' in premium_tools:
    errors.append("premium cleaner tools must never bypass the final confirmation dialog")
if "setItemsSelected" not in view_model_source or "onSetItemsSelected" not in cleaner_app:
    errors.append("premium tool select-all must use one batched state update instead of per-file toggles")
if "FileResultRow(item" in cleaner_app and "PremiumCleanerToolScreen(" not in cleaner_app:
    errors.append("legacy generic cleaner rows must not be the primary UI for premium tools")
for forbidden_visible in ("Kopyaları Sil", "Büyük Dosyalar", "APK Paketleri", "İncele ve temizle"):
    if forbidden_visible in premium_tools:
        errors.append(f"premium tool UI must not hard-code Turkish copy: {forbidden_visible}")

for forbidden_visible in ("Görseller", "Gönderilenler", "İncele ve temizle", "Temizliğe dahil"):
    if forbidden_visible in whatsapp_ui:
        errors.append(f"WhatsApp UI must not hard-code Turkish copy: {forbidden_visible}")
locales_config = (ROOT / "app/src/main/res/xml/locales_config.xml").read_text(encoding="utf-8")
if 'android:name="en"' not in locales_config or 'android:name="tr"' not in locales_config:
    errors.append("global locale config must expose English and Turkish resources")

strings_default = ROOT / "app/src/main/res/values/strings.xml"
strings_tr = ROOT / "app/src/main/res/values-tr/strings.xml"
if strings_default.is_file() and strings_tr.is_file():
    default_names = {node.attrib["name"] for node in ET.parse(strings_default).getroot().findall("string")}
    tr_names = {node.attrib["name"] for node in ET.parse(strings_tr).getroot().findall("string")}
    if default_names != tr_names:
        if missing := sorted(default_names - tr_names):
            errors.append(f"Turkish translations missing keys: {missing}")
        if missing := sorted(tr_names - default_names):
            errors.append(f"default translations missing keys: {missing}")

    # Compile-time guard: every Kotlin R.string reference must exist in the default
    # resource set. This catches unresolved R.string symbols before Gradle/Kotlin CI.
    kotlin_string_refs: dict[str, set[str]] = {}
    for kotlin_file in (ROOT / "app/src/main/java").rglob("*.kt"):
        source = kotlin_file.read_text(encoding="utf-8")
        for resource_name in re.findall(r"R\.string\.([A-Za-z0-9_]+)", source):
            kotlin_string_refs.setdefault(resource_name, set()).add(str(kotlin_file.relative_to(ROOT)))
    for resource_name in sorted(set(kotlin_string_refs) - default_names):
        locations = ", ".join(sorted(kotlin_string_refs[resource_name]))
        errors.append(f"Kotlin references missing default string resource R.string.{resource_name}: {locations}")

qa_keystore = ROOT / "keystore/depoakilli-ci-qa.jks"
if qa_keystore.is_file():
    digest = hashlib.sha256(qa_keystore.read_bytes()).hexdigest()
    if digest != "d6e453480cd6e99fb7bbfd7192eef1719328979f09d6dba383249dbc46b5eac8":
        errors.append("QA keystore bytes changed; CI update signing continuity would be broken")

workflow = (ROOT / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")
for expected in (
    "actions/checkout@v7",
    "actions/setup-java@v5",
    "gradle/actions/setup-gradle@v6",
    "actions/upload-artifact@v6",
    "testDebugUnitTest",
    "lintDebug",
    "assembleDebug",
    "depoakilli-test-apk-",
    "Verify stable test signing certificate",
):
    if expected not in workflow:
        errors.append(f"missing CI invariant: {expected}")

if errors:
    print("Project validation failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)

print("Smart Cleaner 0.5.9 project structure, permissions, resources, CI and safety guardrails are valid.")
