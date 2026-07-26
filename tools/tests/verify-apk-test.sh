#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "${TEST_ROOT}"' EXIT

TEST_REPO="${TEST_ROOT}/repo"
MOCK_BIN="${TEST_ROOT}/bin"
MOCK_NDK="${TEST_ROOT}/ndk"
mkdir -p \
    "${TEST_REPO}/Android/app/build/outputs/apk/release" \
    "${MOCK_BIN}" \
    "${MOCK_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin"
touch "${TEST_REPO}/Android/app/build/outputs/apk/release/app-release.apk"

cat > "${MOCK_BIN}/unzip" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [ "$1" = "-q" ]; then
    mkdir -p /tmp/apk/lib/arm64-v8a
    touch /tmp/apk/lib/arm64-v8a/lib2ship.so
    exit 0
fi
cat <<'LISTING'
Archive: app-release.apk
       42  2026-01-01 00:00   assets/2ship.o2r
LISTING
EOF
chmod +x "${MOCK_BIN}/unzip"

NM="${MOCK_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm"
write_nm() {
    local omitted="${1:-}"
    cat > "${NM}" <<EOF
#!/usr/bin/env bash
set -euo pipefail
echo "llvm-nm diagnostic remains visible" >&2
for symbol in \
    Java_com_terminads_mm_NativeBridge_nativeReadSnapshot \
    Java_com_terminads_mm_NativeBridge_nativeGetUptimeMillis \
    Java_com_terminads_mm_NativeBridge_nativeSubmitCommand \
    TerminaDS_LoadVeilFont \
    TerminaDS_GetDisplayRefreshHz \
    TerminaDS_SetDisplayRefreshHz; do
    if [ "\${symbol}" != "${omitted}" ]; then
        printf '0000000000000000 T %s\\n' "\${symbol}"
    fi
done
EOF
    chmod +x "${NM}"
}

run_gate() {
    (
        cd "${TEST_REPO}"
        PATH="${MOCK_BIN}:${PATH}" ANDROID_NDK_HOME="${MOCK_NDK}" \
            "${REPO_ROOT}/tools/verify-apk.sh"
    ) 2>&1
}

test -x "${REPO_ROOT}/tools/verify-apk.sh"
write_nm
passing_output="$(run_gate)"
grep -Fq 'llvm-nm diagnostic remains visible' <<<"${passing_output}"
for symbol in \
    Java_com_terminads_mm_NativeBridge_nativeReadSnapshot \
    Java_com_terminads_mm_NativeBridge_nativeGetUptimeMillis \
    Java_com_terminads_mm_NativeBridge_nativeSubmitCommand \
    TerminaDS_LoadVeilFont \
    TerminaDS_GetDisplayRefreshHz \
    TerminaDS_SetDisplayRefreshHz; do
    grep -Fq "symbol OK: ${symbol}" <<<"${passing_output}"
done

missing_symbol="TerminaDS_SetDisplayRefreshHz"
write_nm "${missing_symbol}"
if missing_output="$(run_gate)"; then
    echo "APK gate passed with ${missing_symbol} absent" >&2
    exit 1
fi
grep -Fq "FAIL: required symbol missing: ${missing_symbol}" <<<"${missing_output}"
grep -Fq 'llvm-nm diagnostic remains visible' <<<"${missing_output}"

echo "APK verification gate checks passed"
