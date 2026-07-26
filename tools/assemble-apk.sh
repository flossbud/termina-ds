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
PASSWORD_FILE="${HOME}/.termina-ds/pass"

KEYSTORE_DIR="${ANDROID_KEYSTORE_DIR:-${HOME}/.termina-ds}"

if [[ -f "${PASSWORD_FILE}" ]]; then
    if [[ -z "${ANDROID_KEYSTORE_PATH+x}" ]]; then
        ANDROID_KEYSTORE_PATH="${KEYSTORE_DIR}/release-keystore.jks"
        export ANDROID_KEYSTORE_PATH
    fi
    if [[ -z "${ANDROID_KEYSTORE_PASSWORD+x}" ]]; then
        ANDROID_KEYSTORE_PASSWORD="$(<"${PASSWORD_FILE}")"
        export ANDROID_KEYSTORE_PASSWORD
    fi
    if [[ -z "${ANDROID_KEY_ALIAS+x}" ]]; then
        ANDROID_KEY_ALIAS="termina-ds"
        export ANDROID_KEY_ALIAS
    fi
    if [[ -z "${ANDROID_KEY_PASSWORD+x}" ]]; then
        ANDROID_KEY_PASSWORD="$(<"${PASSWORD_FILE}")"
        export ANDROID_KEY_PASSWORD
    fi
fi

if [[ -n "${ANDROID_KEYSTORE_PATH:-}" ]]; then
    # Resolve symlinks before deriving the mount and the container path.
    # dirname/basename on an unresolved symlink can name one file on the host
    # and a different one inside the container, because the container follows
    # the link with its own filesystem underneath.
    ANDROID_KEYSTORE_PATH="$(readlink -f "${ANDROID_KEYSTORE_PATH}")"
    export ANDROID_KEYSTORE_PATH
    KEYSTORE_DIR="$(dirname "${ANDROID_KEYSTORE_PATH}")"
    ANDROID_KEYSTORE_PATH="/keystore/$(basename "${ANDROID_KEYSTORE_PATH}")"
    export ANDROID_KEYSTORE_PATH
fi

# The keystore lives outside the repo (tools/make-keystore.sh writes it to
# ~/.termina-ds). Gradle runs inside the container, so the directory has to be
# mounted and the path rewritten -- forwarding ANDROID_KEYSTORE_PATH alone
# hands Gradle a host path that does not exist in the container.
docker run --rm \
    -v "${REPO_ROOT}:/workspace" \
    -v "termina-ds-gradle:/root/.gradle" \
    -v "termina-ds-android-home:/root/.android" \
    -v "${KEYSTORE_DIR}:/keystore:ro" \
    -e ANDROID_KEYSTORE_PATH \
    -e ANDROID_KEYSTORE_PASSWORD \
    -e ANDROID_KEY_ALIAS \
    -e ANDROID_KEY_PASSWORD \
    -w /workspace/Android \
    "${IMAGE}" ./gradlew --no-daemon :app:assembleRelease

echo "==> APK: ${REPO_ROOT}/Android/app/build/outputs/apk/release/app-release.apk"
