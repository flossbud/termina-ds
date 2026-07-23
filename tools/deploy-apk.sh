#!/usr/bin/env bash
# Install the built Termina DS APK onto a connected device.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="${REPO_ROOT}/Android/app/build/outputs/apk/release/app-release.apk"

if [ ! -f "${APK}" ]; then
    echo "APK not found at ${APK} — run tools/build-apk.sh first." >&2
    exit 1
fi

if [ -z "$(adb devices | sed -n '2p')" ]; then
    echo "No device connected. Pair over Wi-Fi with:" >&2
    echo "  adb pair <thor-ip>:<pair-port>" >&2
    echo "  adb connect <thor-ip>:<port>" >&2
    exit 1
fi

adb install -r "${APK}"
echo "Installed. Launch with:"
echo "  adb shell monkey -p com.terminads.mm -c android.intent.category.LAUNCHER 1"
