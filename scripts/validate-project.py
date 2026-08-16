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
):
    if forbidden in manifest:
        errors.append(f"forbidden first-release permission present: {forbidden}")

build_file = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
for expected in (
    'applicationId = "com.mrzekai.depoakilli"',
    "targetSdk = 36",
    "compileSdk = 36",
    "validateReleaseAds",
):
    if expected not in build_file:
        errors.append(f"missing build invariant: {expected}")

catalog = (ROOT / "gradle/libs.versions.toml").read_text(encoding="utf-8")
for expected in (
    'agp = "8.13.2"',
    'kotlin = "2.2.21"',
    'compose-bom = "2026.06.01"',
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

workflow_text = (ROOT / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")
for expected in (
    "actions/checkout@v7",
    "actions/setup-java@v5",
    "gradle/actions/setup-gradle@v6",
    "actions/upload-artifact@v6",
    ":app:checkDebugAarMetadata",
):
    if expected not in workflow_text:
        errors.append(f"missing CI invariant: {expected}")

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
