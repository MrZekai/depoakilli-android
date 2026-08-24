#!/usr/bin/env bash
set -euo pipefail

AAB="${1:-app/build/outputs/bundle/release/app-release.aab}"
OUT="release-verification"
BUNDLETOOL_VERSION="1.18.3"
BUNDLETOOL_SHA256="a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29"
BUNDLETOOL="$OUT/bundletool-all-$BUNDLETOOL_VERSION.jar"
APKS="$OUT/release.apks"
UNIVERSAL="$OUT/release-universal.apk"
REPORT="$OUT/release-verification.txt"

fail() {
  echo "RELEASE VERIFY FAILED: $*" >&2
  exit 1
}

[ -f "$AAB" ] || fail "AAB missing: $AAB"
mkdir -p "$OUT"

if [ ! -f "$BUNDLETOOL" ]; then
  curl -fL --retry 4 --retry-delay 2 \
    "https://github.com/google/bundletool/releases/download/$BUNDLETOOL_VERSION/bundletool-all-$BUNDLETOOL_VERSION.jar" \
    -o "$BUNDLETOOL"
fi
printf '%s  %s\n' "$BUNDLETOOL_SHA256" "$BUNDLETOOL" | sha256sum -c -

rm -f "$APKS" "$UNIVERSAL"
java -jar "$BUNDLETOOL" build-apks \
  --bundle="$AAB" \
  --output="$APKS" \
  --mode=universal \
  --overwrite

unzip -p "$APKS" universal.apk > "$UNIVERSAL"
[ -s "$UNIVERSAL" ] || fail "universal.apk extraction failed"

AAPT="$(find "${ANDROID_HOME:?ANDROID_HOME missing}/build-tools" -type f -name aapt | sort -V | tail -n1)"
[ -x "$AAPT" ] || fail "aapt not found"

BADGING="$OUT/badging.txt"
PERMS="$OUT/permissions.txt"
MANIFEST="$OUT/manifest-tree.txt"
DEX_STRINGS="$OUT/dex-strings.txt"

"$AAPT" dump badging "$UNIVERSAL" > "$BADGING"
"$AAPT" dump permissions "$UNIVERSAL" > "$PERMS"
"$AAPT" dump xmltree "$UNIVERSAL" AndroidManifest.xml > "$MANIFEST"

: > "$DEX_STRINGS"
while IFS= read -r dex; do
  unzip -p "$UNIVERSAL" "$dex" | strings >> "$DEX_STRINGS"
done < <(unzip -Z1 "$UNIVERSAL" | grep -E '^classes([0-9]+)?\.dex$')

grep -q "package: name='com.mrzekai.depoakilli'" "$BADGING" || fail "release applicationId mismatch"
if grep -q "application-debuggable" "$BADGING"; then fail "release APK is debuggable"; fi
if grep -q "com.mrzekai.depoakilli.qa" "$BADGING"; then fail "QA applicationId leaked"; fi

for forbidden in \
  android.permission.FOREGROUND_SERVICE \
  androidx.work.impl.foreground.SystemForegroundService \
  androidx.compose.ui.tooling.PreviewActivity
do
  if grep -Fq "$forbidden" "$PERMS" || grep -Fq "$forbidden" "$MANIFEST"; then
    fail "forbidden merged-release surface remains: $forbidden"
  fi
done

grep -Fq "ca-app-pub-1380972808968213" "$DEX_STRINGS" || fail "live AdMob publisher id not found"
if grep -Fq "ca-app-pub-3940256099942544" "$DEX_STRINGS"; then
  fail "Google sample AdMob id leaked into release"
fi

{
  echo "Smart Cleaner release verification: PASS"
  echo "AAB: $AAB"
  echo "Universal APK: $UNIVERSAL"
  echo "AAB bytes: $(wc -c < "$AAB")"
  echo "Universal APK bytes: $(wc -c < "$UNIVERSAL")"
  echo
  echo "Bundletool size:"
  java -jar "$BUNDLETOOL" get-size total --apks="$APKS" || true
  echo
  grep "^package:" "$BADGING" || true
  grep -E "sdkVersion|targetSdkVersion" "$BADGING" || true
  echo
  cat "$PERMS"
} | tee "$REPORT"

sha256sum "$AAB" "$UNIVERSAL" > "$OUT/SHA256SUMS.txt"
echo "RELEASE_BINARY_GATE_PASS"
