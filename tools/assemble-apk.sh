#!/usr/bin/env bash
# Assemble the release APK WITHOUT the full pipeline -- for Kotlin/resource
# iteration only.
#
# tools/build-apk.sh clears Android/app/.cxx on purpose so CMake's GLOB_RECURSE
# re-freezes the native source list; that is mandatory when .c/.cpp files were
# added or removed, and costs 8-19 minutes. This script skips the clear and the
# o2r stage entirely, so Gradle reuses the existing native build. NEVER use it
# after native source changes -- the stale glob would silently ship an old
# native lib (docs/HANDOFF.md, the GLOB_RECURSE trap).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${TERMINA_BUILD_IMAGE:-termina-ds-build:latest}"

docker run --rm \
    -v "${REPO_ROOT}:/workspace" \
    -v "termina-ds-gradle:/root/.gradle" \
    -v "termina-ds-android-home:/root/.android" \
    -e ANDROID_KEYSTORE_PATH="${ANDROID_KEYSTORE_PATH:-}" \
    -e ANDROID_KEYSTORE_PASSWORD="${ANDROID_KEYSTORE_PASSWORD:-}" \
    -e ANDROID_KEY_ALIAS="${ANDROID_KEY_ALIAS:-}" \
    -e ANDROID_KEY_PASSWORD="${ANDROID_KEY_PASSWORD:-}" \
    -w /workspace/Android \
    "${IMAGE}" ./gradlew --no-daemon :app:assembleRelease

echo "==> APK: ${REPO_ROOT}/Android/app/build/outputs/apk/release/app-release.apk"
