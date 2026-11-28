#!/usr/bin/env bash
set -euo pipefail

AAB="${1:-app/build/outputs/bundle/closedTest/app-closedTest.aab}"
APK="${2:-app/build/outputs/apk/closedTest/app-closedTest.apk}"
OUT="${3:-app/build/reports/closed-test-binary-audit}"
BUNDLETOOL_VERSION="1.18.3"
BUNDLETOOL_SHA256="a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29"
EXPECTED_UPLOAD_CERT_SHA256="e15e1d31ea6dda44c7f8333eabdc9f4a4d0e9d8feeb9f321958b496edf4f4afd"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
BUNDLETOOL="$TMP_DIR/bundletool-all-$BUNDLETOOL_VERSION.jar"
APKS="$TMP_DIR/closed-test.apks"
UNIVERSAL="$TMP_DIR/closed-test-universal.apk"

fail() {
  echo "CLOSED TEST VERIFY FAILED: $*" >&2
  exit 1
}

# Version comes from app/build.gradle.kts so a version bump needs no script edit.
EXPECTED_VERSION_CODE="$(grep -oP 'versionCode = \K\d+' app/build.gradle.kts)"
EXPECTED_VERSION_NAME="$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts | head -n 1)"
[ -n "$EXPECTED_VERSION_CODE" ] && [ -n "$EXPECTED_VERSION_NAME" ] || fail "cannot read version from app/build.gradle.kts"

[ -f "$AAB" ] || fail "AAB missing: $AAB"
[ -f "$APK" ] || fail "APK missing: $APK"
[[ "${SUPPORT_EMAIL:-}" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]] \
  || fail "SUPPORT_EMAIL is missing or invalid"
mkdir -p "$OUT"

if [ ! -f "$BUNDLETOOL" ]; then
  curl -fL --retry 4 --retry-delay 2 \
    "https://github.com/google/bundletool/releases/download/$BUNDLETOOL_VERSION/bundletool-all-$BUNDLETOOL_VERSION.jar" \
    -o "$BUNDLETOOL"
fi
printf '%s  %s\n' "$BUNDLETOOL_SHA256" "$BUNDLETOOL" | sha256sum -c -

JARSIGNER_REPORT="$OUT/aab-signature.txt"
APK_SIGNER_REPORT="$OUT/apk-signature.txt"
AAB_CERT_REPORT="$OUT/aab-certificate.txt"
jarsigner -verify -certs "$AAB" > "$JARSIGNER_REPORT" 2>&1 \
  || fail "AAB JAR signature verification failed"
grep -Fq "jar verified" "$JARSIGNER_REPORT" || fail "AAB is not JAR-signed"

AAPT="$(find "${ANDROID_HOME:?ANDROID_HOME missing}/build-tools" -type f \( -name aapt -o -name aapt.exe \) | sort -V | tail -n1)"
APKSIGNER="$(find "${ANDROID_HOME:?ANDROID_HOME missing}/build-tools" -type f \( -name apksigner -o -name apksigner.bat \) | sort -V | tail -n1)"
[ -f "$AAPT" ] || fail "aapt not found"
[ -f "$APKSIGNER" ] || fail "apksigner not found"

"$APKSIGNER" verify --verbose --print-certs "$APK" > "$APK_SIGNER_REPORT" 2>&1 \
  || fail "closed-test APK signature verification failed"
keytool -printcert -jarfile "$AAB" > "$AAB_CERT_REPORT" 2>&1 \
  || fail "AAB signer certificate could not be read"

APK_CERT="$({ sed -nE 's/.*SHA-256 digest:[[:space:]]*//p' "$APK_SIGNER_REPORT" || true; } | head -n1 | tr -d ':' | tr '[:upper:]' '[:lower:]')"
AAB_CERT="$({ sed -nE 's/^[[:space:]]*SHA256:[[:space:]]*//p' "$AAB_CERT_REPORT" || true; } | head -n1 | tr -d ':' | tr '[:upper:]' '[:lower:]')"
[[ "$APK_CERT" =~ ^[0-9a-f]{64}$ ]] || fail "APK signer SHA-256 could not be parsed"
[[ "$AAB_CERT" =~ ^[0-9a-f]{64}$ ]] || fail "AAB signer SHA-256 could not be parsed"
[ "$APK_CERT" = "$AAB_CERT" ] || fail "AAB and APK are not signed by the same Play upload certificate"
[ "$AAB_CERT" = "$EXPECTED_UPLOAD_CERT_SHA256" ] || fail "Play upload certificate differs from v45"

rm -f "$APKS" "$UNIVERSAL"
java -jar "$BUNDLETOOL" build-apks \
  --bundle="$AAB" \
  --output="$APKS" \
  --mode=universal \
  --overwrite
unzip -p "$APKS" universal.apk > "$UNIVERSAL"
[ -s "$UNIVERSAL" ] || fail "universal APK extraction failed"

audit_binary() {
  local label="$1"
  local binary="$2"
  local prefix="$3"
  local badging="$OUT/$prefix-badging.txt"
  local permissions="$OUT/$prefix-permissions.txt"
  local manifest="$OUT/$prefix-manifest-tree.txt"
  local dex_strings="$TMP_DIR/$prefix-dex-strings.txt"

  "$AAPT" dump badging "$binary" > "$badging"
  "$AAPT" dump permissions "$binary" > "$permissions"
  "$AAPT" dump xmltree "$binary" AndroidManifest.xml > "$manifest"
  : > "$dex_strings"
  while IFS= read -r dex; do
    unzip -p "$binary" "$dex" | LC_ALL=C tr -c '[:print:]' '\n' >> "$dex_strings"
  done < <(unzip -Z1 "$binary" | grep -E '^classes([0-9]+)?\.dex$')

  grep -q "package: name='com.mrzekai.depoakilli'" "$badging" \
    || fail "$label package mismatch"
  grep -q "versionCode='$EXPECTED_VERSION_CODE'" "$badging" || fail "$label versionCode mismatch"
  grep -q "versionName='$EXPECTED_VERSION_NAME'" "$badging" || fail "$label versionName mismatch"
  if grep -q "application-debuggable" "$badging"; then
    fail "$label must not be debuggable"
  fi
  if grep -Fq "com.mrzekai.depoakilli.qa" "$badging"; then
    fail "$label contains the QA applicationId"
  fi

  for forbidden in \
    androidx.work.impl.foreground.SystemForegroundService \
    androidx.compose.ui.tooling.PreviewActivity
  do
    if grep -Fq "$forbidden" "$permissions" || grep -Fq "$forbidden" "$manifest"; then
      fail "$label contains forbidden merged surface: $forbidden"
    fi
  done
  # Faz 3A-2: a foreground service only for shrinking videos (user tap):
  # exactly FOREGROUND_SERVICE + _DATA_SYNC + _MEDIA_PROCESSING, our own service.
  # VIDEO_COMPRESSION_FGS off (task 2.4): no foreground service at all.
  fgs_on="$(grep -c 'const val VIDEO_COMPRESSION_FGS = true' "$(dirname "$0")/../app/src/main/java/com/mrzekai/depoakilli/video/VideoRunMode.kt" || true)"
  fgs_expected="android.permission.FOREGROUND_SERVICE android.permission.FOREGROUND_SERVICE_DATA_SYNC android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING "
  [ "$fgs_on" = "1" ] || fgs_expected=""
  fgs_perms="$(grep -Eo 'android\.permission\.FOREGROUND_SERVICE[A-Z_]*' "$permissions" | sort -u | tr '\n' ' ')"
  if [ "$fgs_perms" != "$fgs_expected" ]; then
    fail "$label foreground-service permissions differ: $fgs_perms"
  fi
  # Fable review: Google's usage-log uploader has no sender (CCT backend unregistered).
  if grep -Fq "CctBackendFactory" "$manifest"; then
    fail "the datatransport CCT backend is still registered (would try the network)"
  fi
  # Faz 3C: the app needs no internet (Play Billing talks to the Play Store app).
  if grep -Eq 'android\.permission\.INTERNET([^_A-Z]|$)' "$permissions"; then
    fail "$label still asks for the internet permission"
  fi
  if [ "$fgs_on" = "1" ]; then
    grep -Fq "com.mrzekai.depoakilli.video.VideoCompressionService" "$manifest" \
      || fail "$label is missing the video shrinking service"
  elif grep -Fq "com.mrzekai.depoakilli.video.VideoCompressionService" "$manifest"; then
    fail "the video service is declared although VIDEO_COMPRESSION_FGS is off"
  fi

  # 2.0: no ad SDK. No AdMob id, no advertising-id permission, no ads classes;
  # Google Play Billing is present.
  if grep -Fq 'ca-app-pub-' "$manifest" || grep -Fq 'ca-app-pub-' "$dex_strings"; then
    fail "$label contains an AdMob id"
  fi
  for forbidden in \
    com.google.android.gms.ads \
    com.google.android.gms.permission.AD_ID \
    android.permission.ACCESS_ADSERVICES_AD_ID
  do
    if grep -Fq "$forbidden" "$permissions" || grep -Fq "$forbidden" "$manifest"; then
      fail "$label contains ad surface: $forbidden"
    fi
  done
  for forbidden in 'Lcom/google/android/gms/ads/' 'Lcom/google/android/ump/'; do
    if grep -Fq "$forbidden" "$dex_strings"; then
      fail "$label contains ad SDK classes: $forbidden"
    fi
  done
  grep -Fq 'com.android.vending.BILLING' "$permissions" \
    || fail "$label is missing the Google Play Billing permission"
  grep -Fq "$SUPPORT_EMAIL" "$dex_strings" \
    || fail "$label does not contain the configured support contact"
}

audit_binary "assembled APK" "$APK" "assembled"
audit_binary "AAB universal APK" "$UNIVERSAL" "universal"

{
  echo "Smart Cleaner Play closed-test binary audit: PASS"
  echo "Package: com.mrzekai.depoakilli"
  echo "Version: $EXPECTED_VERSION_NAME ($EXPECTED_VERSION_CODE)"
  echo "AAB: $AAB ($(wc -c < "$AAB" | tr -d ' ') bytes)"
  echo "APK: $APK ($(wc -c < "$APK" | tr -d ' ') bytes)"
  echo "AAB universal APK: $UNIVERSAL ($(wc -c < "$UNIVERSAL" | tr -d ' ') bytes)"
  echo "Upload certificate SHA-256: $AAB_CERT"
  echo "Ads: none (no AdMob id, no AD_ID, no ads classes); Google Play Billing present"
  echo "Debuggable: no"
} | tee "$OUT/summary.txt"

{
  sha256sum "$AAB" "$APK"
  printf '%s  %s\n' "$(sha256sum "$UNIVERSAL" | awk '{print $1}')" "closed-test-universal.apk"
} > "$OUT/SHA256SUMS.txt"
echo "PLAY_CLOSED_TEST_BINARY_GATE_PASS"
