#!/usr/bin/env bash
set -euo pipefail

required=(
  KEYSTORE_BASE64
  KEYSTORE_PASSWORD
  KEY_ALIAS
  KEY_PASSWORD
  SUPPORT_EMAIL
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

if [[ ! "$SUPPORT_EMAIL" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]]; then
  echo "Release blocked. SUPPORT_EMAIL must be a valid public contact address." >&2
  exit 1
fi

echo "Release signing and public support contact are complete; AdMob IDs are variant-pinned in Gradle."
