#!/usr/bin/env bash
# Recreate the Termina DS build toolchain on a fresh host.
#
# Exists because the toolchain is host state, not repo state: when wheelhouse
# migrated to a new VM (2026-07-24) the repo came along but docker, adb, and
# the termina-ds-build image did not, and every build script died on
# `docker: command not found`. Running this once on any Debian/Ubuntu host
# makes the repo's tools/*.sh scripts work there. Idempotent — safe to re-run.
#
# Requires sudo. The docker-group grant takes effect on the NEXT login; until
# then run the tools scripts as `sg docker -c './tools/run-unit-tests.sh'`.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${TERMINA_BUILD_IMAGE:-termina-ds-build:latest}"

echo "==> apt: docker.io + adb"
sudo apt-get update -qq
sudo apt-get install -y -qq docker.io adb
sudo systemctl enable --now docker
sudo usermod -aG docker "$(id -un)"

if sudo docker image inspect "${IMAGE}" > /dev/null 2>&1; then
    echo "==> image ${IMAGE} already present, skipping build"
else
    echo "==> building ${IMAGE} from docker/Dockerfile.android (30-60 min on 4 cores)"
    sudo docker build -f "${REPO_ROOT}/docker/Dockerfile.android" \
        -t "${IMAGE}" "${REPO_ROOT}/docker/"
fi

echo "==> verify"
sudo docker run --rm "${IMAGE}" bash -c \
    'sdkmanager --version && "$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm --version | head -1'
adb version | head -1

echo "==> done. Log out and back in for docker-group membership,"
echo "    or prefix scripts with: sg docker -c '<command>'"
