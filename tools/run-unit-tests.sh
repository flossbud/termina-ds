#!/usr/bin/env bash
# Run the Termina DS JVM unit tests inside the Docker toolchain image.
#
# These are plain JUnit tests -- no NDK, no device, no APK assembly -- so this
# takes about a minute rather than the 8-19 minutes a full ./tools/build-apk.sh
# costs. Use it for every Kotlin change; save the full build for native changes.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${TERMINA_BUILD_IMAGE:-termina-ds-build:latest}"

docker run --rm \
    -v "${REPO_ROOT}:/workspace" \
    -v "termina-ds-gradle:/root/.gradle" \
    -v "termina-ds-android-home:/root/.android" \
    -w /workspace/Android \
    "${IMAGE}" ./gradlew --no-daemon :app:testReleaseUnitTest "$@"
