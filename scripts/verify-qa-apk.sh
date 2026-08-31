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
  'android.permission.FOREGROUND_SERVICE' \
  'androidx.work.impl.foreground.SystemForegroundService' \
  'androidx.compose.ui.tooling.PreviewActivity'
do
  if grep -Fq "$forbidden" "$PERMS" || grep -Fq "$forbidden" "$MANIFEST"; then
    fail "packaged QA manifest still contains: $forbidden"
  fi
done

# DEX printable-string scanning is valid for Compose PreviewActivity.
for forbidden in \
  'androidx/compose/ui/tooling/PreviewActivity'
do
  if grep -Fq "$forbidden" "$DEX_STRINGS"; then
    fail "unwanted packaged runtime remains: $forbidden"
  fi
done

grep -Fq 'ca-app-pub-3940256099942544' "$DEX_STRINGS" \
  || fail "Google sample AdMob IDs not found in QA binary"
if grep -Fq 'ca-app-pub-1380972808968213' "$DEX_STRINGS"; then
  fail "production AdMob publisher ID leaked into QA binary"
fi

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
