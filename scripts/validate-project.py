#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import pathlib
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
    "app/src/main/java/com/mrzekai/depoakilli/MainActivity.kt",
    "app/src/main/java/com/mrzekai/depoakilli/data/AiCleaningEngine.kt",
    "app/src/main/java/com/mrzekai/depoakilli/data/DeviceRepository.kt",
    "app/src/main/java/com/mrzekai/depoakilli/data/DuplicatePolicy.kt",
    "app/src/main/java/com/mrzekai/depoakilli/data/WhatsAppMediaClassifier.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/CleanerApp.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/CleanerViewModel.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/WhatsAppCleanerScreen.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/DeviceCenterScreen.kt",
    "app/src/main/java/com/mrzekai/depoakilli/ui/NeonDashboardScreen.kt",
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
    "versionCode = 9",
    'versionName = "0.5.2"',
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
):
    if expected not in repository:
        errors.append(f"missing Cleaner Engine repository invariant: {expected}")

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
):
    if expected not in dashboard:
        errors.append(f"missing v0.5.2 dashboard invariant: {expected}")
if "PRO" in dashboard or "Premium" in dashboard:
    errors.append("v0.5.2 dashboard must not advertise a non-existent Pro/Premium tier")

smart_results = (ROOT / "app/src/main/java/com/mrzekai/depoakilli/ui/SmartCleanResultsScreen.kt").read_text(encoding="utf-8")
for expected in (
    "SmartCleanSummaryCard",
    "CleanupConfirmationDialog",
    "StorageDetailDialog",
    "FilePreviewDialog",
    "summary.storagePreviews",
):
    if expected not in smart_results:
        errors.append(f"missing v0.5.2 Smart Clean results invariant: {expected}")

for strings_path in (
    ROOT / "app/src/main/res/values/strings.xml",
    ROOT / "app/src/main/res/values-tr/strings.xml",
):
    visible_strings = strings_path.read_text(encoding="utf-8")
    if "Cleaner Engine" in visible_strings or "CLEANER ENGINE" in visible_strings:
        errors.append(f"user-visible Cleaner Engine branding remains in {strings_path}")

cleaner_app = (ROOT / "app/src/main/java/com/mrzekai/depoakilli/ui/CleanerApp.kt").read_text(encoding="utf-8")
if "TopAppBar(" in cleaner_app:
    if "ExperimentalMaterial3Api" not in cleaner_app or "@OptIn(ExperimentalMaterial3Api::class)" not in cleaner_app:
        errors.append("CleanerApp TopAppBar requires ExperimentalMaterial3Api opt-in")

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

print("Smart Cleaner 0.5.2 project structure, permissions, resources, CI and safety guardrails are valid.")
