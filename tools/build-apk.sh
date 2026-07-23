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
    -v "termina-ds-android-home:/root/.android" \
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

        # mm/CMakeLists.txt collects native sources with file(GLOB_RECURSE ...) and
        # no CONFIGURE_DEPENDS, so the source list is frozen at CMake configure time.
        # AGP re-runs the native build only when a CMakeLists/gradle input changes --
        # NOT when a new .cpp appears on disk -- so a newly added native file (e.g.
        # under mm/2s2h/TerminaDS/) is silently dropped from an incremental build:
        # it compiles green but ships without the code. Removing the .cxx config dir
        # forces a full CMake reconfigure + re-glob every build, which reliably picks
        # up new files. (CONFIGURE_DEPENDS and mtime-touching do NOT work here because
        # AGP's own up-to-date check short-circuits before ninja's glob check runs.)
        # Cost: native objects recompile each build; correctness over speed.
        rm -rf Android/app/.cxx

        cd Android
        ./gradlew --no-daemon :app:assembleRelease
    '

echo "==> APK: ${REPO_ROOT}/Android/app/build/outputs/apk/release/app-release.apk"
