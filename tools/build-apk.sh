#!/usr/bin/env bash
# Build the Termina DS release APK inside the Docker toolchain image.
#
# Stage 1 (host):    generate the 2ship.o2r support archive
# Stage 2 (Android): cross-compile the native lib and assemble the APK
#
# Release signing is used when all four ANDROID_KEY* variables are set;
# otherwise Gradle falls back to debug signing (fine for local iteration).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${TERMINA_BUILD_IMAGE:-termina-ds-build:latest}"

docker run --rm \
    -v "${REPO_ROOT}:/workspace" \
    -v "termina-ds-gradle:/root/.gradle" \
    -e ANDROID_KEYSTORE_PATH="${ANDROID_KEYSTORE_PATH:-}" \
    -e ANDROID_KEYSTORE_PASSWORD="${ANDROID_KEYSTORE_PASSWORD:-}" \
    -e ANDROID_KEY_ALIAS="${ANDROID_KEY_ALIAS:-}" \
    -e ANDROID_KEY_PASSWORD="${ANDROID_KEY_PASSWORD:-}" \
    -w /workspace \
    "${IMAGE}" bash -euo pipefail -c '
        echo "==> Stage 1: generating 2ship.o2r support archive"

        # CMakeLists.txt shells out to git (branch/commit/tag) to embed build
        # metadata. The bind-mounted repo is owned by the host user while the
        # container runs as root, which git treats as "dubious ownership" and
        # refuses by default -- silently leaving the metadata blank rather than
        # failing configure. Trust the mount explicitly so it is populated.
        git config --global --add safe.directory /workspace

        # CMakeLists.txt unconditionally calls lsb_release on Linux (only used
        # for a status message and CPACK_SYSTEM_NAME, neither of which affects
        # the Generate2ShipOtr target). The build image intentionally omits the
        # lsb-release package; GitHub Actions ubuntu-latest runners happen to
        # have it preinstalled, which is why CI never hit this. Shim it from
        # /etc/os-release instead of adding a package to the pinned image.
        if ! command -v lsb_release >/dev/null 2>&1; then
            cat > /usr/local/bin/lsb_release <<"LSBSHIM"
#!/bin/sh
. /etc/os-release
case "$2" in
    --id) echo "$NAME" ;;
    --release) echo "$VERSION_ID" ;;
    --codename) echo "$VERSION_CODENAME" ;;
esac
LSBSHIM
            chmod +x /usr/local/bin/lsb_release
        fi

        cmake --no-warn-unused-cli -H. -Bbuild-cmake -GNinja -DCMAKE_BUILD_TYPE:STRING=Release
        cmake --build build-cmake --config Release --target Generate2ShipOtr -j"$(nproc)"

        support_archive=""
        for candidate in 2ship.o2r build-cmake/mm/2ship.o2r build-cmake/2ship.o2r; do
            if [ -f "$candidate" ]; then support_archive="$candidate"; break; fi
        done
        if [ -z "$support_archive" ]; then
            echo "ERROR: 2ship.o2r was not produced." >&2
            find . -name "2ship.o2r" -print >&2
            exit 1
        fi
        mkdir -p Android/app/src/main/assets
        cp "$support_archive" Android/app/src/main/assets/2ship.o2r
        ls -lh Android/app/src/main/assets/2ship.o2r

        echo "==> Stage 2: assembling release APK"
        cd Android
        ./gradlew --no-daemon :app:assembleRelease
    '

echo "==> APK: ${REPO_ROOT}/Android/app/build/outputs/apk/release/app-release.apk"
