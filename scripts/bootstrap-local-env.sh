#!/usr/bin/env bash
#
# Smart Cleaner local build bootstrap.
#
# Run this once in a fresh clone (Windows Git Bash, macOS or Linux) before the
# first Gradle command. It never writes machine-specific paths into a tracked
# file: local.properties is git-ignored and is only created when the Android SDK
# location can be discovered from the environment.
#
#   bash scripts/bootstrap-local-env.sh
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
status=0

note() { printf '  %s\n' "$*"; }
ok()   { printf 'OK    %s\n' "$*"; }
warn() { printf 'WARN  %s\n' "$*"; }
bad()  { printf 'ERROR %s\n' "$*"; status=1; }

printf '== Smart Cleaner build environment ==\n'

# ---------------------------------------------------------------- JDK 17 check
parse_feature() {
    # Reads `java -version` output on stdin and prints the Java feature version.
    # Tolerates JVM banner noise such as "Picked up JAVA_TOOL_OPTIONS: ...".
    grep -m1 'version "' \
        | sed -E 's/.*version "([0-9]+)(\.([0-9]+))?.*/\1 \3/' \
        | awk '{ if ($1 == 1) print $2; else print $1 }'
}

java_feature() {
    local home="$1"
    local bin="$home/bin/java"
    [ -x "$bin" ] || bin="$home/bin/java.exe"
    [ -x "$bin" ] || return 1
    "$bin" -version 2>&1 | parse_feature
}

launcher_feature=""
if command -v java >/dev/null 2>&1; then
    launcher_feature="$(java -version 2>&1 | parse_feature)"
fi

if [ -n "${JAVA_HOME:-}" ]; then
    home_feature="$(java_feature "$JAVA_HOME" || true)"
    if [ "$home_feature" = "17" ]; then
        ok "JAVA_HOME points at a Java 17 JDK: $JAVA_HOME"
    elif [ -n "$home_feature" ]; then
        warn "JAVA_HOME is Java $home_feature: $JAVA_HOME"
        note "gradle/gradle-daemon-jvm.properties still forces the daemon onto Java 17,"
        note "but only if a Java 17 JDK is installed and discoverable on this machine."
    else
        warn "JAVA_HOME is set but does not contain a usable java executable: $JAVA_HOME"
    fi
elif [ -n "$launcher_feature" ]; then
    warn "JAVA_HOME is not set; the java on PATH is Java $launcher_feature."
else
    bad "No java found on PATH and JAVA_HOME is not set."
fi

if [ -n "$launcher_feature" ] && [ "$launcher_feature" -ge 25 ] 2>/dev/null; then
    warn "Java $launcher_feature launches Gradle. Gradle 8.13 cannot compile Kotlin DSL"
    note "scripts on Java 25+ (IllegalArgumentException: <version>)."
    note "gradle/gradle-daemon-jvm.properties redirects the daemon to Java 17;"
    note "install Temurin 17 if Gradle reports that it cannot find a matching toolchain."
fi

# ------------------------------------------------------------- Android SDK/dir
sdk_dir=""
for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" \
    "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" \
    "${LOCALAPPDATA:-}/Android/Sdk" "$HOME/AppData/Local/Android/Sdk"
do
    if [ -n "$candidate" ] && [ -d "$candidate/platforms" ]; then
        sdk_dir="$candidate"
        break
    fi
done

local_properties="$ROOT/local.properties"
if [ -f "$local_properties" ]; then
    ok "local.properties already exists (git-ignored, never committed)."
elif [ -n "$sdk_dir" ]; then
    # Java/Gradle on Windows does not reliably understand MSYS paths such as
    # /c/<account>/... inside a .properties file. Convert it to a Windows path,
    # then escape the drive-letter colon according to Java properties syntax.
    sdk_property_path="$sdk_dir"
    if command -v cygpath >/dev/null 2>&1; then
        sdk_property_path="$(cygpath -m "$sdk_dir")"
    fi
    sdk_property_path="${sdk_property_path//\\/\\\\}"
    sdk_property_path="${sdk_property_path//:/\\:}"
    printf 'sdk.dir=%s\n' "$sdk_property_path" > "$local_properties"
    ok "Wrote local.properties for Android SDK: $sdk_dir"
else
    bad "Android SDK not found."
    note "Set ANDROID_HOME (or ANDROID_SDK_ROOT) to your SDK, or open the project once"
    note "in Android Studio so it can create local.properties for you."
fi

if [ -n "$sdk_dir" ] && [ ! -d "$sdk_dir/platforms/android-36" ]; then
    warn "Android SDK platform 36 was not found under $sdk_dir/platforms."
    note "Install 'Android SDK Platform 36' from the SDK Manager (compileSdk/targetSdk 36)."
fi

# ------------------------------------------------------------------ wrapper
if [ -f "$ROOT/gradlew" ] && [ ! -x "$ROOT/gradlew" ]; then
    chmod +x "$ROOT/gradlew" 2>/dev/null && ok "Made ./gradlew executable." \
        || warn "Could not chmod +x gradlew (harmless on Windows)."
fi

printf '\n'
if [ "$status" -eq 0 ]; then
    printf 'Environment looks ready. Next:\n'
    printf '  ./gradlew --no-daemon --stacktrace :app:checkDebugAarMetadata\n'
else
    printf 'Fix the ERROR lines above, then run this script again.\n'
fi
exit "$status"
