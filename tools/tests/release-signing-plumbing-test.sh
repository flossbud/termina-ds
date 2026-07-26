#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "${TEST_ROOT}"' EXIT

TEST_HOME="${TEST_ROOT}/home"
MOCK_BIN="${TEST_ROOT}/bin"
mkdir -p \
    "${TEST_HOME}/.termina-ds" \
    "${MOCK_BIN}" \
    "${TEST_ROOT}/custom" \
    "${TEST_ROOT}/links"
printf '%s' 'file-password' > "${TEST_HOME}/.termina-ds/pass"
touch "${TEST_HOME}/.termina-ds/release-keystore.jks" "${TEST_ROOT}/custom/own.jks"
ln -s "${TEST_ROOT}/custom/own.jks" "${TEST_ROOT}/links/linked.jks"

printf '%s\n' \
    '#!/usr/bin/env bash' \
    'set -euo pipefail' \
    'for argument in "$@"; do' \
    '    if [[ "${argument}" == *"${MOCK_EXPECTED_PASSWORD}"* ]]; then' \
    '        echo "password leaked into docker argv" >&2' \
    '        exit 1' \
    '    fi' \
    'done' \
    '[[ "${ANDROID_KEYSTORE_PATH}" == "${MOCK_EXPECTED_CONTAINER_PATH}" ]]' \
    '[[ "${ANDROID_KEYSTORE_PASSWORD}" == "${MOCK_EXPECTED_PASSWORD}" ]]' \
    '[[ "${ANDROID_KEY_ALIAS}" == "${MOCK_EXPECTED_ALIAS}" ]]' \
    '[[ "${ANDROID_KEY_PASSWORD}" == "${MOCK_EXPECTED_PASSWORD}" ]]' \
    'argv=" $* "' \
    '[[ "${argv}" == *" -v ${MOCK_EXPECTED_HOST_DIR}:/keystore:ro "* ]]' \
    'for name in ANDROID_KEYSTORE_PATH ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do' \
    '    [[ "${argv}" == *" -e ${name} "* ]]' \
    '    [[ "${argv}" != *" -e ${name}="* ]]' \
    'done' \
    > "${MOCK_BIN}/docker"
chmod +x "${MOCK_BIN}/docker"

assert_no_password_output() {
    local output="$1"
    local password="$2"
    if [[ "${output}" == *"${password}"* ]]; then
        echo "password leaked into build output" >&2
        return 1
    fi
}

run_with_defaults() {
    local script="$1"
    local output
    output="$(env \
        -u ANDROID_KEYSTORE_DIR \
        -u ANDROID_KEYSTORE_PATH \
        -u ANDROID_KEYSTORE_PASSWORD \
        -u ANDROID_KEY_ALIAS \
        -u ANDROID_KEY_PASSWORD \
        HOME="${TEST_HOME}" \
        PATH="${MOCK_BIN}:${PATH}" \
        MOCK_EXPECTED_HOST_DIR="${TEST_HOME}/.termina-ds" \
        MOCK_EXPECTED_CONTAINER_PATH="/keystore/release-keystore.jks" \
        MOCK_EXPECTED_PASSWORD="file-password" \
        MOCK_EXPECTED_ALIAS="termina-ds" \
        "${REPO_ROOT}/${script}" 2>&1)"
    assert_no_password_output "${output}" "file-password"
}

run_with_defaults tools/build-apk.sh
run_with_defaults tools/assemble-apk.sh

run_with_explicit_path() {
    local script="$1"
    local output
    output="$(env \
        -u ANDROID_KEYSTORE_DIR \
        HOME="${TEST_HOME}" \
        PATH="${MOCK_BIN}:${PATH}" \
        ANDROID_KEYSTORE_PATH="${TEST_ROOT}/custom/own.jks" \
        ANDROID_KEYSTORE_PASSWORD="explicit-password" \
        ANDROID_KEY_ALIAS="own-alias" \
        ANDROID_KEY_PASSWORD="explicit-password" \
        MOCK_EXPECTED_HOST_DIR="${TEST_ROOT}/custom" \
        MOCK_EXPECTED_CONTAINER_PATH="/keystore/own.jks" \
        MOCK_EXPECTED_PASSWORD="explicit-password" \
        MOCK_EXPECTED_ALIAS="own-alias" \
        "${REPO_ROOT}/${script}" 2>&1)"
    assert_no_password_output "${output}" "explicit-password"
}

run_with_explicit_path tools/build-apk.sh
run_with_explicit_path tools/assemble-apk.sh

run_with_symlink_path() {
    local script="$1"
    local output
    output="$(env \
        -u ANDROID_KEYSTORE_DIR \
        HOME="${TEST_HOME}" \
        PATH="${MOCK_BIN}:${PATH}" \
        ANDROID_KEYSTORE_PATH="${TEST_ROOT}/links/linked.jks" \
        ANDROID_KEYSTORE_PASSWORD="symlink-password" \
        ANDROID_KEY_ALIAS="symlink-alias" \
        ANDROID_KEY_PASSWORD="symlink-password" \
        MOCK_EXPECTED_HOST_DIR="${TEST_ROOT}/custom" \
        MOCK_EXPECTED_CONTAINER_PATH="/keystore/own.jks" \
        MOCK_EXPECTED_PASSWORD="symlink-password" \
        MOCK_EXPECTED_ALIAS="symlink-alias" \
        "${REPO_ROOT}/${script}" 2>&1)"
    assert_no_password_output "${output}" "symlink-password"
}

run_with_symlink_path tools/build-apk.sh
run_with_symlink_path tools/assemble-apk.sh

negative_output="$(
    HOME="${TEST_HOME}" \
    PATH="${MOCK_BIN}:${PATH}" \
    ANDROID_KEYSTORE_PATH="${TEST_HOME}/.termina-ds/nope.jks" \
    ANDROID_KEYSTORE_PASSWORD="negative-test-password" \
    ANDROID_KEY_ALIAS="negative-test-alias" \
    ANDROID_KEY_PASSWORD="negative-test-password" \
    MOCK_EXPECTED_HOST_DIR="${TEST_HOME}/.termina-ds" \
    MOCK_EXPECTED_CONTAINER_PATH="/keystore/nope.jks" \
    MOCK_EXPECTED_PASSWORD="negative-test-password" \
    MOCK_EXPECTED_ALIAS="negative-test-alias" \
    "${REPO_ROOT}/tools/assemble-apk.sh" 2>&1
)"
assert_no_password_output "${negative_output}" "negative-test-password"

grep -Fq 'if (releaseKeystorePath && (!releaseKeystoreFile.isFile() || !releaseKeystoreFile.canRead()))' \
    "${REPO_ROOT}/Android/app/build.gradle"
grep -Fq "Refusing to fall back to debug signing silently" \
    "${REPO_ROOT}/Android/app/build.gradle"
grep -Fq 'reads the password from' "${REPO_ROOT}/docs/HANDOFF.md"
grep -Fq '`~/.termina-ds/pass`' "${REPO_ROOT}/docs/HANDOFF.md"

echo "release signing plumbing checks passed"
