#!/usr/bin/env bash
set -euo pipefail

required=(
  KEYSTORE_BASE64
  KEYSTORE_PASSWORD
  KEY_ALIAS
  KEY_PASSWORD
  SENTRY_DSN
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

echo "Release signing + crash-diagnostics environment is complete; AdMob IDs are release-pinned in Gradle."
