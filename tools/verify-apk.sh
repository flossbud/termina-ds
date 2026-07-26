#!/usr/bin/env bash
# Verify the packaged native exports and support archive from inside the build
# container. This intentionally depends on ANDROID_NDK_HOME and its llvm-nm;
# the caller is responsible for running the script in that container.
set -euo pipefail

apk=Android/app/build/outputs/apk/release/app-release.apk
extracted_lib=/tmp/apk/lib/arm64-v8a/lib2ship.so
nm_output="$(mktemp)"
trap 'rm -f "${nm_output}"' EXIT

rm -rf /tmp/apk
unzip -q -o -d /tmp/apk "${apk}" "lib/arm64-v8a/lib2ship.so"
"${ANDROID_NDK_HOME}"/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm -D \
    "${extracted_lib}" > "${nm_output}"

required_symbols=(
    Java_com_terminads_mm_NativeBridge_nativeReadSnapshot
    Java_com_terminads_mm_NativeBridge_nativeGetUptimeMillis
    Java_com_terminads_mm_NativeBridge_nativeSubmitCommand
    TerminaDS_LoadVeilFont
    TerminaDS_GetDisplayRefreshHz
    TerminaDS_SetDisplayRefreshHz
)

for symbol in "${required_symbols[@]}"; do
    if ! awk -v required="${symbol}" '
        $NF == required { found = 1 }
        END { exit(found ? 0 : 1) }
    ' "${nm_output}"; then
        echo "FAIL: required symbol missing: ${symbol}" >&2
        exit 1
    fi
    echo "symbol OK: ${symbol}"
done

if unzip -l "${apk}" | grep -q "mm\.o2r"; then
    echo "FAIL: mm.o2r leaked into APK"
    exit 1
fi
echo "2ship.o2r entries: $(unzip -l "${apk}" | grep -c '2ship\.o2r')"
echo "symbols + o2r check OK"
