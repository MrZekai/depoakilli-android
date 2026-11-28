#!/usr/bin/env bash
set -euo pipefail

APK="${1:-app/build/outputs/apk/qa/app-qa.apk}"
OUT="${2:-app/build/reports/qa-apk-audit}"
mkdir -p "$OUT"

fail() {
  echo "QA APK VERIFY FAILED: $*" >&2
  exit 1
}

[ -f "$APK" ] || fail "APK missing: $APK"

AAPT="$(find "${ANDROID_HOME:?ANDROID_HOME missing}/build-tools" -type f \( -name aapt -o -name aapt.exe \) | sort -V | tail -n1)"
[ -f "$AAPT" ] || fail "aapt not found"

BADGING="$OUT/badging.txt"
PERMS="$OUT/permissions.txt"
MANIFEST="$OUT/manifest-tree.txt"
DEX_STRINGS="$OUT/dex-strings.txt"
ZIP_LIST="$OUT/zip-list.txt"
LARGEST="$OUT/largest-entries.txt"
REPORT="$OUT/summary.txt"

"$AAPT" dump badging "$APK" > "$BADGING"
"$AAPT" dump permissions "$APK" > "$PERMS"
"$AAPT" dump xmltree "$APK" AndroidManifest.xml > "$MANIFEST"
unzip -l "$APK" > "$ZIP_LIST"

: > "$DEX_STRINGS"
while IFS= read -r dex; do
  unzip -p "$APK" "$dex" | LC_ALL=C tr -c '[:print:]' '\n' >> "$DEX_STRINGS"
done < <(unzip -Z1 "$APK" | grep -E '^classes([0-9]+)?\.dex$')

unzip -l "$APK" \
  | awk 'NF >= 4 && $1 ~ /^[0-9]+$/ {print $1 "\t" $4}' \
  | sort -nr \
  | head -n 40 > "$LARGEST"

grep -q "package: name='com.mrzekai.depoakilli.qa'" "$BADGING" \
  || fail "QA package mismatch"
if grep -q "application-debuggable" "$BADGING"; then
  fail "Slim QA APK must not be debuggable"
fi

for forbidden in \
  'androidx.work.impl.foreground.SystemForegroundService' \
  'androidx.compose.ui.tooling.PreviewActivity'
do
  if grep -Fq "$forbidden" "$PERMS" || grep -Fq "$forbidden" "$MANIFEST"; then
    fail "packaged QA manifest still contains: $forbidden"
  fi
done
# Faz 3A-2: a foreground service only for shrinking videos (user tap):
# exactly FOREGROUND_SERVICE + _DATA_SYNC + _MEDIA_PROCESSING, our own service.
# VIDEO_COMPRESSION_FGS off (task 2.4): no foreground service at all.
fgs_on="$(grep -c 'const val VIDEO_COMPRESSION_FGS = true' "$(dirname "$0")/../app/src/main/java/com/mrzekai/depoakilli/video/VideoRunMode.kt" || true)"
fgs_expected="android.permission.FOREGROUND_SERVICE android.permission.FOREGROUND_SERVICE_DATA_SYNC android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING "
[ "$fgs_on" = "1" ] || fgs_expected=""
fgs_perms="$(grep -Eo 'android\.permission\.FOREGROUND_SERVICE[A-Z_]*' "$PERMS" | sort -u | tr '\n' ' ' || true)"
if [ "$fgs_perms" != "$fgs_expected" ]; then
  fail "QA APK foreground-service permissions differ: $fgs_perms"
fi
# Fable review: Google's usage-log uploader has no sender (CCT backend unregistered).
if grep -Fq "CctBackendFactory" "$MANIFEST"; then
  fail "the datatransport CCT backend is still registered (would try the network)"
fi
# Faz 3C: the app needs no internet (Play Billing talks to the Play Store app).
if grep -Eq 'android\.permission\.INTERNET([^_A-Z]|$)' "$PERMS"; then
  fail "QA APK still asks for the internet permission"
fi
if [ "$fgs_on" = "1" ]; then
  grep -Fq "com.mrzekai.depoakilli.video.VideoCompressionService" "$MANIFEST" \
    || fail "QA APK is missing the video shrinking service"
elif grep -Fq "com.mrzekai.depoakilli.video.VideoCompressionService" "$MANIFEST"; then
  fail "the video service is declared although VIDEO_COMPRESSION_FGS is off"
fi

# DEX printable-string scanning is valid for Compose PreviewActivity.
for forbidden in \
  'androidx/compose/ui/tooling/PreviewActivity'
do
  if grep -Fq "$forbidden" "$DEX_STRINGS"; then
    fail "unwanted packaged runtime remains: $forbidden"
  fi
done

# 2.0: no ad SDK in the QA binary either.
if grep -Fq 'ca-app-pub-' "$DEX_STRINGS"; then
  fail "AdMob id found in QA binary"
fi
for forbidden in 'Lcom/google/android/gms/ads/' 'Lcom/google/android/ump/'; do
  if grep -Fq "$forbidden" "$DEX_STRINGS"; then
    fail "ad SDK classes found in QA binary: $forbidden"
  fi
done

APK_BYTES="$(wc -c < "$APK" | tr -d ' ')"
{
  echo "Smart Cleaner slim QA APK audit: PASS"
  echo "APK: $APK"
  echo "APK bytes: $APK_BYTES"
  echo
  grep "^package:" "$BADGING" || true
  grep -E "sdkVersion|targetSdkVersion" "$BADGING" || true
  echo
  echo "Permissions:"
  cat "$PERMS"
  echo
  echo "Largest packaged entries:"
  cat "$LARGEST"
} | tee "$REPORT"

sha256sum "$APK" > "$OUT/SHA256SUMS.txt"
echo "SLIM_QA_APK_BINARY_GATE_PASS"
