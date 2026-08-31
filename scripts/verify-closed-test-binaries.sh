#!/usr/bin/env bash
set -euo pipefail

AAB="${1:-app/build/outputs/bundle/closedTest/app-closedTest.aab}"
APK="${2:-app/build/outputs/apk/closedTest/app-closedTest.apk}"
OUT="${3:-app/build/reports/closed-test-binary-audit}"
BUNDLETOOL_VERSION="1.18.3"
BUNDLETOOL_SHA256="a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
BUNDLETOOL="$TMP_DIR/bundletool-all-$BUNDLETOOL_VERSION.jar"
APKS="$TMP_DIR/closed-test.apks"
UNIVERSAL="$TMP_DIR/closed-test-universal.apk"

fail() {
  echo "CLOSED TEST VERIFY FAILED: $*" >&2
  exit 1
}

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
  grep -q "versionCode='39'" "$badging" || fail "$label versionCode mismatch"
  grep -q "versionName='0.5.18-closedtest1'" "$badging" || fail "$label versionName mismatch"
  if grep -q "application-debuggable" "$badging"; then
    fail "$label must not be debuggable"
  fi
  if grep -Fq "com.mrzekai.depoakilli.qa" "$badging"; then
    fail "$label contains the QA applicationId"
  fi

  for forbidden in \
    android.permission.FOREGROUND_SERVICE \
    androidx.work.impl.foreground.SystemForegroundService \
    androidx.compose.ui.tooling.PreviewActivity
  do
    if grep -Fq "$forbidden" "$permissions" || grep -Fq "$forbidden" "$manifest"; then
      fail "$label contains forbidden merged surface: $forbidden"
    fi
  done

  for sample_id in \
    'ca-app-pub-3940256099942544~3347511713' \
    'ca-app-pub-3940256099942544/6300978111' \
    'ca-app-pub-3940256099942544/1033173712' \
    'ca-app-pub-3940256099942544/1044960115'
  do
    if ! grep -Fq "$sample_id" "$manifest" && ! grep -Fq "$sample_id" "$dex_strings"; then
      fail "$label is missing Google sample ad ID: $sample_id"
    fi
  done
  if grep -Fq 'ca-app-pub-1380972808968213' "$manifest" || grep -Fq 'ca-app-pub-1380972808968213' "$dex_strings"; then
    fail "$label contains a production AdMob publisher ID"
  fi
  if grep -Fq 'ca-app-pub-3940256099942544/9257395921' "$dex_strings"; then
    fail "$label contains the Google sample App Open ad ID"
  fi
  grep -Fq "$SUPPORT_EMAIL" "$dex_strings" \
    || fail "$label does not contain the configured support contact"
}

audit_binary "assembled APK" "$APK" "assembled"
audit_binary "AAB universal APK" "$UNIVERSAL" "universal"

{
  echo "Smart Cleaner Play closed-test binary audit: PASS"
  echo "Package: com.mrzekai.depoakilli"
  echo "Version: 0.5.18-closedtest1 (39)"
  echo "AAB: $AAB ($(wc -c < "$AAB" | tr -d ' ') bytes)"
  echo "APK: $APK ($(wc -c < "$APK" | tr -d ' ') bytes)"
  echo "AAB universal APK: $UNIVERSAL ($(wc -c < "$UNIVERSAL" | tr -d ' ') bytes)"
  echo "Upload certificate SHA-256: $AAB_CERT"
  echo "Ads: Google sample Banner + Interstitial + Native; App Open absent"
  echo "Debuggable: no"
} | tee "$OUT/summary.txt"

{
  sha256sum "$AAB" "$APK"
  printf '%s  %s\n' "$(sha256sum "$UNIVERSAL" | awk '{print $1}')" "closed-test-universal.apk"
} > "$OUT/SHA256SUMS.txt"
echo "PLAY_CLOSED_TEST_BINARY_GATE_PASS"
