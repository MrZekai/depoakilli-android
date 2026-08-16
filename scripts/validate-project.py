#!/usr/bin/env python3
from __future__ import annotations

import pathlib
import sys
import xml.etree.ElementTree as ET

import yaml


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
    "gradlew",
    "gradle/wrapper/gradle-wrapper.jar",
    "gradle/wrapper/gradle-wrapper.properties",
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/mrzekai/depoakilli/MainActivity.kt",
    ".github/workflows/android-ci.yml",
    ".github/workflows/release-aab.yml",
]
for relative in required:
    require(relative)

for xml_file in ROOT.rglob("*.xml"):
    try:
        ET.parse(xml_file)
    except ET.ParseError as exc:
        errors.append(f"invalid XML {xml_file.relative_to(ROOT)}: {exc}")

for workflow in (ROOT / ".github/workflows").glob("*.yml"):
    try:
        yaml.safe_load(workflow.read_text(encoding="utf-8"))
    except yaml.YAMLError as exc:
        errors.append(f"invalid YAML {workflow.relative_to(ROOT)}: {exc}")

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

if errors:
    print("Project validation failed:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    raise SystemExit(1)

print("Project structure, XML, workflows, package identity and permission guardrails are valid.")
