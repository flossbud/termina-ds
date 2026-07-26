#!/usr/bin/env bash
# Run the Termina DS JVM unit tests inside the Docker toolchain image.
#
# These are plain JUnit tests, but Gradle also invokes the incremental debug
# native build. With an unchanged CMake source list this takes about 40 seconds,
# rather than the 8-19 minutes a full ./tools/build-apk.sh costs. No device or
# APK assembly is involved. Use it for every Kotlin change; save the full release
# build for native release verification.
#
# Gradle prints "BUILD SUCCESSFUL" with "testDebugUnitTest UP-TO-DATE" while
# running zero tests, which has already produced a false pass in this project
# (docs/HANDOFF.md section 5). Console text is therefore not evidence here. This
# script forces --rerun-tasks, deletes the previous results first, and decides
# pass/fail from the JUnit XML alone -- no XML, or zero tests in it, is a
# failure, not a pass.
#
# Extra arguments are passed to Gradle, so `./tools/run-unit-tests.sh
# --tests '*PollerTest*'` still works.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${TERMINA_BUILD_IMAGE:-termina-ds-build:latest}"
RESULTS_REL="app/build/test-results/testDebugUnitTest"
RESULTS_DIR="${REPO_ROOT}/Android/${RESULTS_REL}"

# Stale results are removed inside the container, not on the host: the image
# runs as root, so a previous run's output is not always host-deletable.
gradle_status=0
docker run --rm \
    -v "${REPO_ROOT}:/workspace" \
    -v "termina-ds-gradle:/root/.gradle" \
    -v "termina-ds-android-home:/root/.android" \
    -w /workspace/Android \
    "${IMAGE}" \
    bash -euo pipefail -c '
        native_cxx_dir="app/.cxx"
        native_stamp_file="${native_cxx_dir}/termina-native-source-list.sha256"

        # mm/CMakeLists.txt uses GLOB_RECURSE without CONFIGURE_DEPENDS. Hash
        # only the sorted matching path list: additions/removals require a
        # re-glob, while edits to an existing file are normal Ninja inputs.
        shopt -s globstar nullglob dotglob
        native_sources=(
            ../mm/2s2h/**/*.c
            ../mm/2s2h/**/*.cpp
            ../mm/2s2h/**/*.h
            ../mm/2s2h/**/*.hpp
        )
        native_source_stamp="$(
            {
                if [ "${#native_sources[@]}" -gt 0 ]; then
                    printf "%s\0" "${native_sources[@]}"
                fi
            } \
                | LC_ALL=C sort -z \
                | sha256sum \
                | awk "{ print \$1 }"
        )"
        previous_native_source_stamp=""

        if [ -f "${native_stamp_file}" ]; then
            IFS= read -r previous_native_source_stamp < "${native_stamp_file}" || true
        fi
        if [ -d "${native_cxx_dir}" ] \
            && [ "${previous_native_source_stamp}" != "${native_source_stamp}" ]; then
            echo "native source list changed -- clearing .cxx to force a CMake re-glob"
            rm -rf "${native_cxx_dir}"
        fi

        mkdir -p "${native_cxx_dir}"
        printf "%s\n" "${native_source_stamp}" > "${native_stamp_file}"

        rm -rf "$1"
        shift
        exec ./gradlew --no-daemon --rerun-tasks :app:testDebugUnitTest "$@"
    ' bash "${RESULTS_REL}" "$@" || gradle_status=$?

shopt -s nullglob
xml_files=("${RESULTS_DIR}"/*.xml)
shopt -u nullglob

if [ ${#xml_files[@]} -eq 0 ]; then
    echo "FAIL: no JUnit XML under ${RESULTS_DIR}" >&2
    echo "      Gradle ran zero tests; its exit status was ${gradle_status}." >&2
    exit 1
fi

# Sum the testsuite attributes. Counts come from here and nowhere else.
totals="$(awk '
    function attr(tag, name,   value) {
        if (match(tag, name "=\"[0-9]+\"")) {
            value = substr(tag, RSTART, RLENGTH)
            sub(name "=\"", "", value)
            sub("\"", "", value)
            return value + 0
        }
        return 0
    }
    match($0, /<testsuite [^>]*>/) {
        tag = substr($0, RSTART, RLENGTH)
        tests += attr(tag, "tests")
        failures += attr(tag, "failures")
        errors += attr(tag, "errors")
        skipped += attr(tag, "skipped")
        suites += 1
    }
    END { printf "%d %d %d %d %d\n", tests, failures, errors, skipped, suites }
' "${xml_files[@]}")"

read -r tests failures errors skipped suites <<EOF
${totals}
EOF

printf 'unit tests: %d run in %d suites -- %d failures, %d errors, %d skipped\n' \
    "${tests}" "${suites}" "${failures}" "${errors}" "${skipped}"
printf '  (counted from %s/*.xml)\n' "${RESULTS_DIR}"

if [ "${tests}" -eq 0 ]; then
    echo "FAIL: the XML reports zero tests." >&2
    exit 1
fi
if [ "${failures}" -ne 0 ] || [ "${errors}" -ne 0 ]; then
    echo "FAIL: ${failures} failures, ${errors} errors. See ${RESULTS_DIR}." >&2
    exit 1
fi
if [ "${gradle_status}" -ne 0 ]; then
    echo "FAIL: tests all passed but Gradle exited ${gradle_status}." >&2
    exit "${gradle_status}"
fi

echo "PASS"
