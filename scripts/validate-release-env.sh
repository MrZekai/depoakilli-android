#!/usr/bin/env bash
set -euo pipefail

required=(
  KEYSTORE_BASE64
  KEYSTORE_PASSWORD
  KEY_ALIAS
  KEY_PASSWORD
  ADMOB_APP_ID
  ADMOB_BANNER_ID
  ADMOB_INTERSTITIAL_ID
  ADMOB_MEDIUM_RECTANGLE_ID
  ADMOB_APP_OPEN_ID
)

missing=()
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    missing+=("$name")
  fi
done

if (( ${#missing[@]} > 0 )); then
  echo "Release blocked. Missing GitHub secrets: ${missing[*]}" >&2
  exit 1
fi

if [[ "$ADMOB_APP_ID$ADMOB_BANNER_ID$ADMOB_INTERSTITIAL_ID$ADMOB_MEDIUM_RECTANGLE_ID$ADMOB_APP_OPEN_ID" == *"3940256099942544"* ]]; then
  echo "Release blocked. Google sample AdMob IDs cannot be used in production." >&2
  exit 1
fi

echo "Release environment is complete."
