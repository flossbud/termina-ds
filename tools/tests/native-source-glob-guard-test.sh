#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "${TEST_ROOT}"' EXIT

TEST_REPO="${TEST_ROOT}/repo"
MOCK_BIN="${TEST_ROOT}/bin"
mkdir -p \
    "${TEST_REPO}/tools" \
    "${TEST_REPO}/mm/2s2h/TerminaDS" \
    "${TEST_REPO}/Android/app" \
    "${MOCK_BIN}"
cp "${REPO_ROOT}/tools/run-unit-tests.sh" "${TEST_REPO}/tools/run-unit-tests.sh"
touch "${TEST_REPO}/mm/2s2h/TerminaDS/Existing.cpp"

cat > "${TEST_REPO}/Android/gradlew" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
results="app/build/test-results/testDebugUnitTest"
mkdir -p "${results}"
cat > "${results}/TEST-glob-guard.xml" <<'XML'
<testsuite name="glob guard" tests="1" failures="0" errors="0" skipped="0"></testsuite>
XML
EOF
chmod +x "${TEST_REPO}/Android/gradlew"

cat > "${MOCK_BIN}/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

while [ "$#" -gt 0 ]; do
    case "$1" in
        run|--rm)
            shift
            ;;
        -v|-w)
            shift 2
            ;;
        *)
            shift
            break
            ;;
    esac
done

(
    cd "${MOCK_REPO}/Android"
    "$@"
)
EOF
chmod +x "${MOCK_BIN}/docker"

run_suite() {
    env PATH="${MOCK_BIN}:${PATH}" MOCK_REPO="${TEST_REPO}" \
        "${TEST_REPO}/tools/run-unit-tests.sh" 2>&1
}

# With no .cxx tree, the next CMake configuration is fresh. Establish a stamp
# without reporting a clear.
first_output="$(run_suite)"
if grep -Fq 'native source list changed -- clearing .cxx to force a CMake re-glob' <<<"${first_output}"; then
    echo "fresh .cxx setup unexpectedly reported a clear" >&2
    exit 1
fi
test -s "${TEST_REPO}/Android/app/.cxx/termina-native-source-list.sha256"

# An unchanged list must retain the existing native build tree.
touch "${TEST_REPO}/Android/app/.cxx/keep-me"
unchanged_output="$(run_suite)"
if grep -Fq 'native source list changed -- clearing .cxx to force a CMake re-glob' <<<"${unchanged_output}"; then
    echo "unchanged native source list triggered a clear" >&2
    exit 1
fi
test -e "${TEST_REPO}/Android/app/.cxx/keep-me"

# Additions and removals both invalidate the frozen CMake glob. A path with a
# space also verifies that the list hash does not rely on word splitting.
touch "${TEST_REPO}/mm/2s2h/TerminaDS/New source.cpp"
added_output="$(run_suite)"
grep -Fq 'native source list changed -- clearing .cxx to force a CMake re-glob' <<<"${added_output}"
test ! -e "${TEST_REPO}/Android/app/.cxx/keep-me"

touch "${TEST_REPO}/Android/app/.cxx/keep-me"
rm "${TEST_REPO}/mm/2s2h/TerminaDS/New source.cpp"
removed_output="$(run_suite)"
grep -Fq 'native source list changed -- clearing .cxx to force a CMake re-glob' <<<"${removed_output}"
test ! -e "${TEST_REPO}/Android/app/.cxx/keep-me"

# An existing pre-guard .cxx tree has no trustworthy baseline and must also be
# cleared once before its initial stamp is established.
rm "${TEST_REPO}/Android/app/.cxx/termina-native-source-list.sha256"
touch "${TEST_REPO}/Android/app/.cxx/keep-me"
missing_stamp_output="$(run_suite)"
grep -Fq 'native source list changed -- clearing .cxx to force a CMake re-glob' <<<"${missing_stamp_output}"
test ! -e "${TEST_REPO}/Android/app/.cxx/keep-me"

echo "native source glob guard checks passed"
