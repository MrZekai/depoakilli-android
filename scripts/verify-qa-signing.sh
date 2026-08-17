#!/usr/bin/env bash

set -uo pipefail
export LC_ALL=C

apk_path="${1:-app/build/outputs/apk/debug/app-debug.apk}"
qa_keystore="keystore/depoakilli-ci-qa.jks"
qa_alias="depoakilliQa"
qa_store_password="depoakilli-qa"
pinned_digest="508e012197da76d516bad24880b67c6f067fcd646c51a043a11c0c345e6ead54"

fail() {
    echo "::error::$1"
    exit 1
}

[[ -f "$apk_path" ]] || fail "QA APK bulunamadi: $apk_path"
[[ -f "$qa_keystore" ]] || fail "QA keystore bulunamadi: $qa_keystore"
[[ -n "${ANDROID_HOME:-}" ]] || fail "ANDROID_HOME tanimli degil."

build_tools="$(
    find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d -print 2>/dev/null \
        | sort -V \
        | tail -n 1
)"
[[ -n "$build_tools" ]] || fail "Android build-tools klasoru bulunamadi."

apksigner="$build_tools/apksigner"
[[ -x "$apksigner" ]] || fail "apksigner calistirilabilir degil: $apksigner"

echo "Using build-tools: $build_tools"
if ! raw_output="$("$apksigner" verify --print-certs "$apk_path" 2>&1)"; then
    echo "===== RAW APKSIGNER OUTPUT ====="
    printf '%s\n' "$raw_output"
    echo "===== END RAW OUTPUT ====="
    fail "apksigner QA APK dogrulamasini tamamlayamadi."
fi

echo "===== RAW APKSIGNER OUTPUT ====="
printf '%s\n' "$raw_output"
echo "===== END RAW OUTPUT ====="

actual_digest="$(
    printf '%s\n' "$raw_output" \
        | grep -iEo 'SHA-256 digest:[[:space:]]*([0-9a-fA-F]{2}:?){32}' \
        | head -n 1 \
        | sed -E 's/^[^:]*:[[:space:]]*//' \
        | tr -d ':' \
        | tr '[:upper:]' '[:lower:]' \
        || true
)"

if [[ ! "$actual_digest" =~ ^[0-9a-f]{64}$ ]]; then
    fail "Sertifika parmak izi apksigner ciktisindan okunamadi; ham cikti yukarida."
fi

keytool_error_file="$(mktemp)"
if ! keystore_digest="$(
    keytool -exportcert \
        -keystore "$qa_keystore" \
        -alias "$qa_alias" \
        -storepass "$qa_store_password" \
        2>"$keytool_error_file" \
        | sha256sum \
        | awk '{print tolower($1)}'
)"; then
    echo "===== KEYTOOL ERROR OUTPUT ====="
    cat "$keytool_error_file"
    echo "===== END KEYTOOL OUTPUT ====="
    rm -f "$keytool_error_file"
    fail "QA keystore sertifikasi okunamadi."
fi

if [[ -s "$keytool_error_file" ]]; then
    echo "===== KEYTOOL DIAGNOSTIC OUTPUT ====="
    cat "$keytool_error_file"
    echo "===== END KEYTOOL OUTPUT ====="
fi
rm -f "$keytool_error_file"

if [[ ! "$keystore_digest" =~ ^[0-9a-f]{64}$ ]]; then
    fail "QA keystore SHA-256 parmak izi hesaplanamadi."
fi

echo "pinned:   $pinned_digest"
echo "keystore: $keystore_digest"
echo "apk:      $actual_digest"

if [[ "$keystore_digest" != "$pinned_digest" ]]; then
    fail "Depodaki QA keystore sabitlenen sertifikadan farkli. Imza surekliligi korunmadi."
fi

if [[ "$actual_digest" != "$keystore_digest" ]]; then
    fail "QA APK depodaki kalici QA keystore ile imzalanmamis."
fi

echo "Stable QA signing certificate verified."
