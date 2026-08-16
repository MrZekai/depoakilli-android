#!/usr/bin/env python3
from __future__ import annotations

import pathlib
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
    "app/src/main/res/xml/backup_rules.xml",
    "app/src/main/res/xml/data_extraction_rules.xml",
    "app/src/main/res/xml/locales_config.xml",
    "app/src/main/java/com/mrzekai/depoakilli/MainActivity.kt",
    ".github/workflows/android-ci.yml",
    ".github/workflows/release-aab.yml",
    "docs/AD_PLACEMENTS.md",
]
for relative in required:
    require(relative)

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
    "validateReleaseAds",
    'buildConfigField("String", "ADMOB_MEDIUM_RECTANGLE_ID"',
    'buildConfigField("String", "ADMOB_APP_OPEN_ID"',
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
):
    if expected not in workflow_text:
        errors.append(f"missing CI invariant: {expected}")

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
