#!/usr/bin/env python3
from __future__ import annotations

import pathlib
import sys
import xml.etree.ElementTree as ET
import hashlib
import struct


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
    "app/src/main/res/values/styles.xml",
    "app/src/main/res/values-v27/styles.xml",
    "app/src/main/res/xml/backup_rules.xml",
    "app/src/main/res/xml/data_extraction_rules.xml",
    "app/src/main/res/xml/locales_config.xml",
    "app/src/main/java/com/mrzekai/depoakilli/MainActivity.kt",
    ".github/workflows/android-ci.yml",
    ".github/workflows/release-aab.yml",
    "docs/AD_PLACEMENTS.md",
    "docs/HOME_DESIGN.md",
    "docs/APP_ICON.md",
    "docs/QA_SIGNING.md",
    "scripts/verify-qa-signing.sh",
    "keystore/depoakilli-ci-qa.jks",
    "app/src/debug/res/values/strings.xml",
    "app/src/debug/res/values-tr/strings.xml",
    "app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground_art.png",
    "app/src/main/res/drawable-xxxhdpi/ic_launcher_legacy_art.png",
    "app/src/main/res/drawable-xxxhdpi/ic_launcher_monochrome_art.png",
    "app/src/main/res/drawable/ic_launcher_background.xml",
    "app/src/main/res/drawable/ic_launcher_foreground.xml",
    "app/src/main/res/drawable/ic_launcher_monochrome.xml",
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
    "app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml",
    "app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml",
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
        length_bytes = stream.read(4)
        chunk_type = stream.read(4)
        if len(length_bytes) != 4 or chunk_type != b"IHDR":
            errors.append(f"launcher PNG has no valid IHDR: {path.relative_to(ROOT)}")
            return None
        width, height = struct.unpack(">II", stream.read(8))
        return width, height


for relative, expected_size in (
    ("app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground_art.png", (432, 432)),
    ("app/src/main/res/drawable-xxxhdpi/ic_launcher_legacy_art.png", (432, 432)),
    ("app/src/main/res/drawable-xxxhdpi/ic_launcher_monochrome_art.png", (432, 432)),
    ("store-assets/icon-512.png", (512, 512)),
    ("store-assets/icon-master-1536.png", (1536, 1536)),
):
    dimensions = png_dimensions(ROOT / relative)
    if dimensions is not None and dimensions != expected_size:
        errors.append(
            f"incorrect launcher asset dimensions for {relative}: "
            f"expected {expected_size[0]}x{expected_size[1]}, got "
            f"{dimensions[0]}x{dimensions[1]}",
        )

for xml_file in ROOT.rglob("*.xml"):
    try:
        ET.parse(xml_file)
    except ET.ParseError as exc:
        errors.append(f"invalid XML {xml_file.relative_to(ROOT)}: {exc}")

manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for forbidden in (
    "MANAGE_EXTERNAL_STORAGE",
    "QUERY_ALL_PACKAGES",
    "BIND_ACCESSIBILITY_SERVICE",
    "KILL_BACKGROUND_PROCESSES",
):
    if forbidden in manifest:
        errors.append(f"forbidden first-release permission present: {forbidden}")

if 'android:localeConfig="@xml/locales_config"' not in manifest:
    errors.append("manifest must declare the English/Turkish locale configuration")

for expected in (
    'android.permission.PACKAGE_USAGE_STATS',
    'xmlns:tools="http://schemas.android.com/tools"',
    'tools:ignore="ProtectedPermissions"',
    'android.intent.category.LAUNCHER',
    '<queries>',
):
    if expected not in manifest:
        errors.append(f"missing scoped app-cache visibility invariant: {expected}")

default_strings_file = ROOT / "app/src/main/res/values/strings.xml"
turkish_strings_file = ROOT / "app/src/main/res/values-tr/strings.xml"
if default_strings_file.is_file() and turkish_strings_file.is_file():
    default_names = {
        node.attrib["name"]
        for node in ET.parse(default_strings_file).getroot().findall("string")
    }
    turkish_names = {
        node.attrib["name"]
        for node in ET.parse(turkish_strings_file).getroot().findall("string")
    }
    if default_names != turkish_names:
        missing_tr = sorted(default_names - turkish_names)
        missing_default = sorted(turkish_names - default_names)
        if missing_tr:
            errors.append(f"Turkish translations missing keys: {missing_tr}")
        if missing_default:
            errors.append(f"default translations missing keys: {missing_default}")

build_file = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
for expected in (
    'applicationId = "com.mrzekai.depoakilli"',
    "targetSdk = 36",
    "compileSdk = 36",
    "versionCode = 4",
    'versionName = "0.3.0"',
    "validateReleaseAds",
    'buildConfigField("String", "ADMOB_MEDIUM_RECTANGLE_ID"',
    'buildConfigField("String", "ADMOB_APP_OPEN_ID"',
    'applicationIdSuffix = ".qa"',
    'storeFile = qaKeystore',
    'signingConfig = signingConfigs.getByName("debug")',
    'signingConfig = signingConfigs.findByName("release")',
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

for forbidden in (
    'compose-bom = "2026.08.00"',
    'lifecycle = "2.11.0"',
):
    if forbidden in catalog or forbidden in build_file:
        errors.append(f"API 37 dependency must not be used in the API 36 build: {forbidden}")

if "enforcedPlatform(libs.androidx.compose.bom)" not in build_file:
    errors.append("Compose BOM must be enforced to block transitive Compose 1.12 upgrades")

if "implementation(libs.androidx.fragment)" not in build_file:
    errors.append("Fragment must be explicit to keep Activity Result APIs on Fragment 1.3.0 or newer")

if "implementation(libs.androidx.lifecycle.process)" not in build_file:
    errors.append("lifecycle-process is required for foreground-aware App Open ads")

if 'applicationIdSuffix = ".debug"' in build_file:
    errors.append("ephemeral .debug package must be replaced by the stable .qa test package")

qa_keystore = ROOT / "keystore/depoakilli-ci-qa.jks"
if qa_keystore.is_file():
    qa_keystore_sha256 = hashlib.sha256(qa_keystore.read_bytes()).hexdigest()
    if qa_keystore_sha256 != "d6e453480cd6e99fb7bbfd7192eef1719328979f09d6dba383249dbc46b5eac8":
        errors.append("QA keystore bytes changed; CI update signing continuity would be broken")

gitignore_text = (ROOT / ".gitignore").read_text(encoding="utf-8")
if "!keystore/depoakilli-ci-qa.jks" not in gitignore_text:
    errors.append("stable QA keystore must be explicitly unignored for CI checkout")

for expected in (
    "abortOnError = true",
    "checkDependencies = true",
    "warningsAsErrors = false",
    "textReport = true",
    'textOutput = file("build/reports/lint-results-debug.txt")',
):
    if expected not in build_file:
        errors.append(f"missing lint reporting invariant: {expected}")

valid_backup_domains = {
    "root",
    "file",
    "database",
    "sharedpref",
    "external",
    "device_root",
    "device_file",
    "device_database",
    "device_sharedpref",
}
for relative in (
    "app/src/main/res/xml/backup_rules.xml",
    "app/src/main/res/xml/data_extraction_rules.xml",
):
    rules_file = ROOT / relative
    if not rules_file.is_file():
        continue
    rules_root = ET.parse(rules_file).getroot()
    for rule in rules_root.iter():
        if rule.tag not in {"include", "exclude"}:
            continue
        domain = rule.attrib.get("domain")
        if domain not in valid_backup_domains:
            errors.append(f"invalid Android backup domain in {relative}: {domain!r}")

workflow_text = (ROOT / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")
for expected in (
    "actions/checkout@v7",
    "actions/setup-java@v5",
    "gradle/actions/setup-gradle@v6",
    "actions/upload-artifact@v6",
    ":app:checkDebugAarMetadata",
    ":app:dependencyInsight --configuration debugRuntimeClasspath --dependency androidx.fragment:fragment",
    "continue-on-error: true",
    "app/build/reports/lint-results-debug.txt",
    "Enforce lint and APK build results",
    "Verify stable test signing certificate",
    "id: signing",
    "bash scripts/verify-qa-signing.sh app/build/outputs/apk/debug/app-debug.apk",
    "SIGNING_OUTCOME: ${{ steps.signing.outcome }}",
    "depoakilli-test-apk-${{ github.run_number }}",
):
    if expected not in workflow_text:
        errors.append(f"missing CI invariant: {expected}")

qa_signing_script_text = (ROOT / "scripts/verify-qa-signing.sh").read_text(encoding="utf-8")
for expected in (
    'apksigner" verify --print-certs',
    "===== RAW APKSIGNER OUTPUT =====",
    "keytool -exportcert",
    "508e012197da76d516bad24880b67c6f067fcd646c51a043a11c0c345e6ead54",
    'if [[ "$keystore_digest" != "$pinned_digest" ]]',
    'if [[ "$actual_digest" != "$keystore_digest" ]]',
):
    if expected not in qa_signing_script_text:
        errors.append(f"missing stable QA signing verification invariant: {expected}")

release_workflow_text = (ROOT / ".github/workflows/release-aab.yml").read_text(encoding="utf-8")
release_script_text = (ROOT / "scripts/validate-release-env.sh").read_text(encoding="utf-8")
for expected in (
    "ADMOB_MEDIUM_RECTANGLE_ID",
    "ADMOB_APP_OPEN_ID",
):
    if expected not in release_workflow_text:
        errors.append(f"release workflow missing live ad ID: {expected}")
    if expected not in release_script_text:
        errors.append(f"release secret validation missing live ad ID: {expected}")

source_text = "\n".join(
    path.read_text(encoding="utf-8")
    for path in (ROOT / "app/src/main/java").rglob("*.kt")
)

application_text = (
    ROOT / "app/src/main/java/com/mrzekai/depoakilli/DepoAkilliApplication.kt"
).read_text(encoding="utf-8")
for expected in (
    "private val processObserver = object : DefaultLifecycleObserver",
    "addObserver(processObserver)",
    "super.onCreate()",
):
    if expected not in application_text:
        errors.append(f"missing collision-free Application lifecycle invariant: {expected}")

if "Application.ActivityLifecycleCallbacks,\n    DefaultLifecycleObserver" in application_text:
    errors.append(
        "DepoAkilliApplication must keep DefaultLifecycleObserver in a separate "
        "processObserver object to avoid ambiguous Android lifecycle super calls",
    )
for expected in (
    "class AppOpenAdController",
    "AdSize.MEDIUM_RECTANGLE",
    "MIN_FOREGROUNDS_BEFORE_FIRST_AD = 3",
    "MIN_SHOW_INTERVAL_MILLIS = 2L * 60L * 60L * 1000L",
    "fun clearAppCache()",
    "fun optimizeMemory(",
    "Debug.MemoryInfo()",
    "releaseForMemoryOptimization",
    "Settings.ACTION_APPLICATION_DETAILS_SETTINGS",
    "Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS",
    "StorageStatsManager",
    "queryStatsForPackage",
    "AppOpsManager.OPSTR_GET_USAGE_STATS",
    "Settings.ACTION_USAGE_ACCESS_SETTINGS",
    "fun refreshAppCaches()",
):
    if expected not in source_text:
        errors.append(f"missing global tools/ads invariant: {expected}")

for forbidden in (
    "StorageManager.ACTION_CLEAR_APP_CACHE",
    "KILL_BACKGROUND_PROCESSES",
    "onOpenSystemCache",
    "onClick = {},",
):
    if forbidden in source_text:
        errors.append(f"obsolete or non-functional tool action present: {forbidden}")

cleaner_app_text = (
    ROOT / "app/src/main/java/com/mrzekai/depoakilli/ui/CleanerApp.kt"
).read_text(encoding="utf-8")
for expected in (
    "val showAnchoredBanner = adsCanBeShown",
    "ModernHomeHero(",
    "HomeToolMasonry(",
    "HomeToolRow(",
    "R.string.smart_scan_home_action",
    "R.string.whatsapp_home_subtitle",
    "CacheManagerPanel(",
    "AppCacheRow(",
):
    if expected not in cleaner_app_text:
        errors.append(f"missing visible app-cache/banner UI invariant: {expected}")

if "MediumRectangleAd(" in cleaner_app_text:
    errors.append("Home must use the visible anchored banner instead of a 300x250 MREC")

styles_text = (ROOT / "app/src/main/res/values/styles.xml").read_text(encoding="utf-8")
styles_v27_text = (ROOT / "app/src/main/res/values-v27/styles.xml").read_text(encoding="utf-8")
if '<item name="android:windowLightStatusBar">false</item>' not in styles_text:
    errors.append("base theme must keep light status-bar content on the dark background")
if '<item name="android:windowLightNavigationBar">' in styles_text:
    errors.append("API 27 navigation-bar appearance must not be in the base theme")
if '<item name="android:windowLightNavigationBar">true</item>' not in styles_v27_text:
    errors.append("values-v27 theme must use dark navigation-bar icons on the white navigation surface")

for debug_strings in (
    ROOT / "app/src/debug/res/values/strings.xml",
    ROOT / "app/src/debug/res/values-tr/strings.xml",
):
    if debug_strings.is_file() and "QA" in debug_strings.read_text(encoding="utf-8"):
        errors.append(f"visible debug app label must not contain QA: {debug_strings.relative_to(ROOT)}")

for kotlin_file in (ROOT / "app/src").rglob("*.kt"):
    kotlin_text = kotlin_file.read_text(encoding="utf-8")
    if "import androidx.compose.foundation.layout.weight" in kotlin_text:
        errors.append(
            "forbidden Compose scope-member import in "
            f"{kotlin_file.relative_to(ROOT)}: remove the weight import and call "
            "Modifier.weight only inside a RowScope or ColumnScope",
        )

if errors:
    print("Project validation failed:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    raise SystemExit(1)

print("Project structure, XML, workflows, package identity and permission guardrails are valid.")
