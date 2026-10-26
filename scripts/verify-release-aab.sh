#!/usr/bin/env bash
set -euo pipefail

AAB="${1:-app/build/outputs/bundle/release/app-release.aab}"
APK="${2:-app/build/outputs/apk/release/app-release.apk}"
OUT="release-verification"
BUNDLETOOL_VERSION="1.18.3"
BUNDLETOOL_SHA256="a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29"
EXPECTED_UPLOAD_CERT_SHA256="e15e1d31ea6dda44c7f8333eabdc9f4a4d0e9d8feeb9f321958b496edf4f4afd"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
BUNDLETOOL="$TMP_DIR/bundletool-all-$BUNDLETOOL_VERSION.jar"
APKS="$TMP_DIR/release.apks"
UNIVERSAL="$TMP_DIR/release-universal.apk"

fail() {
  echo "RELEASE VERIFY FAILED: $*" >&2
  exit 1
}

[ -f "$AAB" ] || fail "AAB missing: $AAB"
[ -f "$APK" ] || fail "APK missing: $APK"
[[ "${SUPPORT_EMAIL:-}" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]] \
  || fail "SUPPORT_EMAIL is missing or invalid"
mkdir -p "$OUT"

curl -fL --retry 4 --retry-delay 2 \
  "https://github.com/google/bundletool/releases/download/$BUNDLETOOL_VERSION/bundletool-all-$BUNDLETOOL_VERSION.jar" \
  -o "$BUNDLETOOL"
printf '%s  %s\n' "$BUNDLETOOL_SHA256" "$BUNDLETOOL" | sha256sum -c -

jarsigner -verify -certs "$AAB" > "$OUT/aab-signature.txt" 2>&1 \
  || fail "AAB JAR signature verification failed"
grep -Fq "jar verified" "$OUT/aab-signature.txt" || fail "AAB is not JAR-signed"
keytool -printcert -jarfile "$AAB" > "$OUT/aab-certificate.txt" 2>&1 \
  || fail "AAB signer certificate could not be read"

AAPT="$(find "${ANDROID_HOME:?ANDROID_HOME missing}/build-tools" -type f -name aapt | sort -V | tail -n1)"
APKSIGNER="$(find "${ANDROID_HOME}/build-tools" -type f -name apksigner | sort -V | tail -n1)"
[ -x "$AAPT" ] || fail "aapt not found"
[ -f "$APKSIGNER" ] || fail "apksigner not found"
"$APKSIGNER" verify --verbose --print-certs "$APK" > "$OUT/apk-signature.txt" 2>&1 \
  || fail "release APK signature verification failed"

AAB_CERT="$(sed -nE 's/^[[:space:]]*SHA256:[[:space:]]*//p' "$OUT/aab-certificate.txt" | head -n1 | tr -d ':' | tr '[:upper:]' '[:lower:]')"
APK_CERT="$(sed -nE 's/.*SHA-256 digest:[[:space:]]*//p' "$OUT/apk-signature.txt" | head -n1 | tr -d ':' | tr '[:upper:]' '[:lower:]')"
[[ "$AAB_CERT" =~ ^[0-9a-f]{64}$ ]] || fail "AAB signer SHA-256 could not be parsed"
[[ "$APK_CERT" =~ ^[0-9a-f]{64}$ ]] || fail "APK signer SHA-256 could not be parsed"
[ "$AAB_CERT" = "$APK_CERT" ] || fail "AAB and APK use different upload certificates"
[ "$AAB_CERT" = "$EXPECTED_UPLOAD_CERT_SHA256" ] || fail "upload certificate differs from closed test"

java -jar "$BUNDLETOOL" build-apks \
  --bundle="$AAB" --output="$APKS" --mode=universal --overwrite
unzip -p "$APKS" universal.apk > "$UNIVERSAL"
[ -s "$UNIVERSAL" ] || fail "universal.apk extraction failed"

audit_binary() {
  local label="$1" binary="$2" prefix="$3"
  local badging="$OUT/$prefix-badging.txt"
  local perms="$OUT/$prefix-permissions.txt"
  local manifest="$OUT/$prefix-manifest-tree.txt"
  local dex_strings="$TMP_DIR/$prefix-dex-strings.txt"

  "$AAPT" dump badging "$binary" > "$badging"
  "$AAPT" dump permissions "$binary" > "$perms"
  "$AAPT" dump xmltree "$binary" AndroidManifest.xml > "$manifest"
  : > "$dex_strings"
  while IFS= read -r dex; do
    unzip -p "$binary" "$dex" | LC_ALL=C tr -c '[:print:]' '\n' >> "$dex_strings"
  done < <(unzip -Z1 "$binary" | grep -E '^classes([0-9]+)?\.dex$')

  grep -Fq "package: name='com.mrzekai.depoakilli'" "$badging" || fail "$label package mismatch"
  grep -Fq "versionCode='47'" "$badging" || fail "$label versionCode mismatch"
  grep -Fq "versionName='1.0.1'" "$badging" || fail "$label versionName mismatch"
  grep -Fq "targetSdkVersion:'36'" "$badging" || fail "$label target API mismatch"
  ! grep -Fq 'application-debuggable' "$badging" || fail "$label is debuggable"
  grep -Fq 'ca-app-pub-1380972808968213~9043355268' "$manifest" \
    || fail "$label live AdMob app ID missing"
  for id in \
    'ca-app-pub-1380972808968213/2118175647' \
    'ca-app-pub-1380972808968213/8492012303' \
    'ca-app-pub-1380972808968213/4039180256'
  do
    grep -Fq "$id" "$dex_strings" || fail "$label missing live AdMob unit: $id"
  done
  if [ -n "${ADMOB_RESULT_NATIVE_ID:-}" ]; then
    grep -Fq "$ADMOB_RESULT_NATIVE_ID" "$dex_strings" \
      || fail "$label missing configured Native ad unit"
  fi
  if grep -Fq 'ca-app-pub-3940256099942544' "$manifest" || \
     grep -Fq 'ca-app-pub-3940256099942544' "$dex_strings"; then
    fail "$label contains Google sample AdMob ID"
  fi
  grep -Fq "$SUPPORT_EMAIL" "$dex_strings" || fail "$label support contact missing"
  for forbidden in \
    android.permission.FOREGROUND_SERVICE \
    androidx.work.impl.foreground.SystemForegroundService \
    androidx.compose.ui.tooling.PreviewActivity
  do
    if grep -Fq "$forbidden" "$perms" || grep -Fq "$forbidden" "$manifest"; then
      fail "$label contains forbidden surface: $forbidden"
    fi
  done
}

audit_binary "release APK" "$APK" "apk"
audit_binary "AAB universal APK" "$UNIVERSAL" "universal"

{
  echo "Smart Cleaner production release verification: PASS"
  echo "Package: com.mrzekai.depoakilli"
  echo "Version: 1.0.1 (47)"
  echo "Target API: 36"
  echo "Upload certificate SHA-256: $AAB_CERT"
  echo "AdMob: live app, banner, interstitial and rewarded IDs; no Google sample IDs"
  echo "AAB: $AAB ($(wc -c < "$AAB" | tr -d ' ') bytes)"
  echo "APK: $APK ($(wc -c < "$APK" | tr -d ' ') bytes)"
  echo "AAB universal APK: $UNIVERSAL ($(wc -c < "$UNIVERSAL" | tr -d ' ') bytes)"
} | tee "$OUT/release-verification.txt"
sha256sum "$AAB" "$APK" > "$OUT/SHA256SUMS.txt"
echo "RELEASE_BINARY_GATE_PASS"
