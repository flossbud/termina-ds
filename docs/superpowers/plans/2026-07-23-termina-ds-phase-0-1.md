# Termina DS Phase 0 + 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fork the unofficial 2S2H Android port into Termina DS, build it reproducibly in Docker, rebrand it to install alongside the original, and put a Jetpack Compose UI under our control on the AYN Thor's secondary display.

**Architecture:** The fork inherits an SDL2-based native game (`MainActivity extends SDLActivity`) built by CMake/NDK and wrapped by Gradle. Phase 1 adds a parallel Kotlin/Compose layer that renders to the secondary display via Android's `Presentation` API, communicating with the C++ core through a narrow JNI seam. The Compose layer never touches SDL; the game never touches Compose.

**Tech Stack:** C/C++ (NDK 26), CMake 3.30.3, SDL 2.28.5, Gradle 8.11.1, AGP 8.10.1, JDK 17 (Temurin), Kotlin 2.0.21, Jetpack Compose (BOM 2024.10.01), Docker.

## Global Constraints

Every task's requirements implicitly include this section. Values are copied verbatim from the spec.

- **NDK version:** `26.0.10792818` — do not change.
- **CMake version (Android):** `3.30.3` — do not change.
- **SDL version:** `2.28.5`, built from source on the host.
- **tinyxml2 version:** `10.0.0`, built from source, apt package removed first.
- **JDK:** 17, Temurin distribution.
- **`minSdk`:** stays `24`.
- **`targetSdk`:** stays `33` through all of Phase 1. Raising it changes storage-permission behavior and destabilizes the inherited data-root logic. (spec §8.3)
- **`compileSdk`:** `33` in Phase 0, raised to `34` in Task 7 (Compose requires ≥34).
- **ABI:** `arm64-v8a` only.
- **Application ID / namespace:** `com.terminads.mm`
- **App label:** `Termina DS`
- **Data root:** `/storage/emulated/0/TerminaDS`
- **`mm.o2r` must never ship in the APK.** Enforced by the inherited `verifyBundledAssets` Gradle task, which must not be weakened or removed. (spec §8.4)
- **No walkthrough scraper ships in the APK.** Out of scope for these phases entirely. (spec §8.1)
- **Upstream discipline:** prefer new files over edits to inherited ones. Every inherited file modified must be recorded in `docs/UPSTREAM.md`. (spec §11)
- **Native code location:** all Termina DS native code lives in `mm/2s2h/TerminaDS/`. The root `mm/CMakeLists.txt:98` does `file(GLOB_RECURSE ship__ ... "2s2h/*.cpp" ...)`, so new files there are compiled with **no CMake edit** and no merge conflict.

---

## File Structure

**Created by this plan:**

| Path | Responsibility |
|---|---|
| `docker/Dockerfile.android` | Reproducible Android build toolchain |
| `tools/build-apk.sh` | Two-stage build: host `2ship.o2r` → Gradle APK |
| `tools/deploy-apk.sh` | Install built APK to a connected device |
| `tools/make-keystore.sh` | One-time release keystore generation |
| `docs/UPSTREAM.md` | Ledger of inherited files we have modified |
| `mm/2s2h/TerminaDS/NativeBridge.cpp` | JNI seam, native side |
| `Android/app/src/main/java/com/terminads/mm/NativeBridge.kt` | JNI seam, Kotlin side |
| `Android/app/src/main/java/com/terminads/mm/secondscreen/DisplayInfo.kt` | Display data model (Android-free, testable) |
| `Android/app/src/main/java/com/terminads/mm/secondscreen/DisplaySelectionPolicy.kt` | Pure selection logic |
| `Android/app/src/main/java/com/terminads/mm/secondscreen/PresentationLifecycleOwner.kt` | Lifecycle/ViewModel/SavedState shim for `ComposeView` |
| `Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenManager.kt` | Display discovery + `Presentation` lifecycle |
| `Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenPresentation.kt` | `Presentation` subclass hosting `ComposeView` |
| `Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenHost.kt` | Root composable (placeholder UI) |
| `Android/app/src/test/java/com/terminads/mm/secondscreen/DisplaySelectionPolicyTest.kt` | JVM unit tests |
| `Android/app/src/test/java/com/terminads/mm/secondscreen/PresentationLifecycleOwnerTest.kt` | JVM unit tests |

**Inherited files modified (must be logged in `docs/UPSTREAM.md`):**

| Path | Change | Task |
|---|---|---|
| `Android/build.gradle` | Kotlin + Compose compiler classpath | 7 |
| `Android/app/build.gradle` | id/namespace, compileSdk, Kotlin/Compose, test opts | 4, 7 |
| `Android/gradle.properties` | JVM heap bump | 7 |
| `Android/app/src/main/res/values/strings.xml` | `app_name` | 4 |
| `Android/app/src/main/java/com/{twoshipfork→terminads}/mm/*.java` | package rename (3 files), data root | 4 |
| `mm/src/code/main.c` | `FindClass` path ×2 | 4 |
| `mm/2s2h/Extractor/Extract.cpp` | JNI symbol name | 4 |

`MainActivity.java` gains one field and three call sites in Task 12 — deliberately the only game-side integration point.

---

# Phase 0 — Fork, build, baseline

## Task 1: Repository foundation

**Files:**
- Modify: `/srv/projects/2ship2hark/.git/config` (via `git remote`)
- Create: nothing directly — imports the upstream tree

**Interfaces:**
- Consumes: nothing (first task)
- Produces: a working tree containing the full linkzenic `android` branch at repo root, with `docs/` preserved; remotes `upstream-android` and `upstream-core`

- [ ] **Step 1: Verify starting state**

```bash
cd /srv/projects/2ship2hark
git log --oneline
git branch --show-current
```

Expected: one commit (`Add Termina DS dual-screen fork design`), branch `main`.

- [ ] **Step 2: Add fetch-only upstream remotes**

```bash
cd /srv/projects/2ship2hark
git remote add upstream-android https://github.com/linkzenic/2ship2harkinian-Android.git
git remote add upstream-core https://github.com/HarbourMasters/2ship2harkinian.git
git remote -v
```

Expected: four lines, two remotes each with fetch and push URLs.

- [ ] **Step 3: Fetch the upstream Android branch**

```bash
cd /srv/projects/2ship2hark
git fetch upstream-android android
```

Expected: fetch completes, `FETCH_HEAD` written. This downloads full history (~3,371 commits); allow several minutes.

- [ ] **Step 4: Join histories**

```bash
cd /srv/projects/2ship2hark
git merge --allow-unrelated-histories --no-edit -m "Import linkzenic/2ship2harkinian-Android@android as Termina DS base" upstream-android/android
```

Expected: merge commit created, no conflicts (our only file is `docs/superpowers/specs/...`, which upstream does not have).

- [ ] **Step 5: Verify the tree imported correctly**

```bash
cd /srv/projects/2ship2hark
ls Android/app/build.gradle mm/CMakeLists.txt CMakeLists.txt
ls docs/superpowers/specs/2026-07-23-termina-ds-dual-screen-design.md
grep -c "twoshipfork" Android/app/build.gradle
```

Expected: all paths listed without error; grep prints `2`.

- [ ] **Step 6: Initialize submodules**

```bash
cd /srv/projects/2ship2hark
git submodule update --init --recursive
git submodule status | head
```

Expected: `libultraship`, `OTRExporter`, `ZAPDTR` populated, each line prefixed with a space (clean, checked out).

- [ ] **Step 7: Commit**

The merge in Step 4 already created a commit. Verify and stop.

```bash
cd /srv/projects/2ship2hark
git log --oneline -3
git status --short
```

Expected: merge commit at HEAD, working tree clean.

---

## Task 2: Android build container

**Files:**
- Create: `docker/Dockerfile.android`

**Interfaces:**
- Consumes: the repo tree from Task 1 (specifically `.github/workflows/apt-deps.txt`, mirrored inline)
- Produces: Docker image `termina-ds-build:latest` with `ANDROID_HOME=/opt/android-sdk`, `ANDROID_NDK_HOME=/opt/android-sdk/ndk/26.0.10792818`, `VCPKG_ROOT=/opt/vcpkg` (vcpkg deps pre-installed for `arm64-android`), host SDL 2.28.5 and tinyxml2 10.0.0 installed

- [ ] **Step 1: Write the Dockerfile**

Create `docker/Dockerfile.android`:

```dockerfile
# Termina DS Android build environment.
# Mirrors .github/workflows/android-release.yml so local builds match CI.
FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_NDK_HOME=/opt/android-sdk/ndk/26.0.10792818 \
    ANDROID_NDK_ROOT=/opt/android-sdk/ndk/26.0.10792818 \
    VCPKG_ROOT=/opt/vcpkg \
    JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64

ENV PATH=${JAVA_HOME}/bin:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}

# Base tooling. Package list after `cmake ninja-build` is .github/workflows/apt-deps.txt verbatim.
RUN apt-get update && apt-get install -y --no-install-recommends \
        wget curl git unzip zip ca-certificates gnupg file pkg-config \
        build-essential cmake ninja-build python3 \
        libzip-dev zipcmp zipmerge ziptool \
        libusb-dev libusb-1.0-0-dev libsdl2-dev libsdl2-net-dev libpng-dev \
        libglew-dev libogg-dev libvorbis-dev libopus-dev libopusfile-dev \
        nlohmann-json3-dev libtinyxml2-dev libspdlog-dev \
    && rm -rf /var/lib/apt/lists/*

# JDK 17 (Temurin), per spec Global Constraints.
RUN mkdir -p /etc/apt/keyrings \
    && wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public \
        | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb noble main" \
        > /etc/apt/sources.list.d/adoptium.list \
    && apt-get update && apt-get install -y --no-install-recommends temurin-17-jdk \
    && rm -rf /var/lib/apt/lists/*

# SDL 2.28.5 from source (host tools only; the APK uses its own SDL via CMake).
RUN cd /tmp \
    && wget -q https://github.com/libsdl-org/SDL/releases/download/release-2.28.5/SDL2-2.28.5.tar.gz \
    && tar -xzf SDL2-2.28.5.tar.gz \
    && cd SDL2-2.28.5 \
    && ./configure --enable-hidapi-libusb \
    && make -j"$(nproc)" \
    && make install \
    && cp -av /usr/local/lib/libSDL* /lib/x86_64-linux-gnu/ \
    && ldconfig \
    && rm -rf /tmp/SDL2-2.28.5 /tmp/SDL2-2.28.5.tar.gz

# tinyxml2 10.0.0 from source. The apt package must go first — CI does the same.
RUN apt-get update && apt-get remove -y libtinyxml2-dev && rm -rf /var/lib/apt/lists/* \
    && cd /tmp \
    && wget -q https://github.com/leethomason/tinyxml2/archive/refs/tags/10.0.0.tar.gz \
    && tar -xzf 10.0.0.tar.gz \
    && cd tinyxml2-10.0.0 && mkdir -p build && cd build \
    && cmake .. && make -j"$(nproc)" && make install \
    && ldconfig \
    && rm -rf /tmp/tinyxml2-10.0.0 /tmp/10.0.0.tar.gz

# Android SDK command-line tools.
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && cd /tmp \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip \
    && unzip -q cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm cmdline-tools.zip

# SDK packages. compileSdk moves 33 -> 34 in Task 7, so both platforms are installed.
RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install \
        "platform-tools" \
        "platforms;android-33" \
        "platforms;android-34" \
        "build-tools;34.0.0" \
        "ndk;26.0.10792818" \
        "cmake;3.30.3"

# vcpkg with Android deps pre-built, so app/build.gradle finds them via VCPKG_ROOT.
RUN git clone --depth 1 https://github.com/microsoft/vcpkg.git ${VCPKG_ROOT} \
    && ${VCPKG_ROOT}/bootstrap-vcpkg.sh -disableMetrics \
    && ${VCPKG_ROOT}/vcpkg install --triplet arm64-android \
        zlib libpng libogg libvorbis opus opusfile

WORKDIR /workspace
```

- [ ] **Step 2: Build the image**

```bash
cd /srv/projects/2ship2hark
docker build -f docker/Dockerfile.android -t termina-ds-build:latest .
```

Expected: `Successfully tagged termina-ds-build:latest`. First build takes 20–40 minutes (SDL and vcpkg dominate). Subsequent builds hit layer cache.

- [ ] **Step 3: Verify every pinned toolchain version inside the image**

```bash
docker run --rm termina-ds-build:latest bash -lc '
  java -version 2>&1 | head -1
  ls -d $ANDROID_NDK_HOME
  $ANDROID_HOME/cmake/3.30.3/bin/cmake --version | head -1
  cmake --version | head -1
  pkg-config --modversion sdl2
  ls $VCPKG_ROOT/installed/arm64-android/lib | head
'
```

Expected: Temurin 17 line; NDK path exists; SDK cmake reports `3.30.3`; host cmake reports ≥ `3.26.0`; sdl2 reports `2.28.5`; vcpkg lib listing includes `libz.a`, `libpng16.a`, `libogg.a`, `libvorbis.a`, `libopus.a`.

If any check fails, fix the Dockerfile and rebuild before continuing. A wrong version here produces confusing failures three tasks later.

- [ ] **Step 4: Commit**

```bash
cd /srv/projects/2ship2hark
git add docker/Dockerfile.android
git commit -m "build: add reproducible Android build container"
```

---

## Task 3: Build script and first APK

**Files:**
- Create: `tools/build-apk.sh`

**Interfaces:**
- Consumes: image `termina-ds-build:latest` from Task 2
- Produces: `Android/app/build/outputs/apk/release/app-release.apk`; script accepts no arguments and is idempotent

- [ ] **Step 1: Write the build script**

Create `tools/build-apk.sh`:

```bash
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
```

- [ ] **Step 2: Make it executable and run it**

```bash
cd /srv/projects/2ship2hark
chmod +x tools/build-apk.sh
./tools/build-apk.sh
```

Expected: both stages complete; final line prints the APK path. First run takes 30–60 minutes.

- [ ] **Step 3: Verify the APK exists and excludes the ROM archive**

```bash
cd /srv/projects/2ship2hark
ls -lh Android/app/build/outputs/apk/release/app-release.apk
unzip -l Android/app/build/outputs/apk/release/app-release.apk | grep -c "assets/2ship.o2r"
unzip -l Android/app/build/outputs/apk/release/app-release.apk | grep -c "mm.o2r" || true
```

Expected: APK exists; `2ship.o2r` count is `1`; `mm.o2r` count is `0`. This is the spec §8.4 invariant.

- [ ] **Step 4: Prove the `verifyBundledAssets` guard actually fires**

The guard is our mechanical defence against shipping copyrighted data. Verify it is not decorative.

```bash
cd /srv/projects/2ship2hark
echo "not a real archive" > Android/app/src/main/assets/mm.o2r
./tools/build-apk.sh 2>&1 | tail -20; echo "exit=${PIPESTATUS[0]}"
rm -f Android/app/src/main/assets/mm.o2r
```

Expected: build FAILS with `src/main/assets/mm.o2r must not be bundled.` Then the file is removed. If the build succeeds, stop and repair the guard before going further.

- [ ] **Step 5: Ignore build outputs**

Append to `.gitignore` at repo root:

```gitignore

# Termina DS build outputs
/build-cmake/
/Android/app/src/main/assets/2ship.o2r
```

- [ ] **Step 6: Commit**

```bash
cd /srv/projects/2ship2hark
git add tools/build-apk.sh .gitignore
git commit -m "build: add two-stage Docker APK build script"
```

---

## Task 4: Rebrand to Termina DS

Renames the Java package and application ID, changes the app label, and moves the data root. The JNI symbol and `FindClass` strings must change in lockstep with the Java package — miss one and the app links successfully, then crashes on first native call.

**Files:**
- Modify: `Android/app/build.gradle:23-24`
- Modify: `Android/app/src/main/res/values/strings.xml`
- Move: `Android/app/src/main/java/com/twoshipfork/mm/` → `Android/app/src/main/java/com/terminads/mm/`
- Modify: `Android/app/src/main/java/com/terminads/mm/{MainActivity,AssetCopyUtil,ControllerButtons}.java`
- Modify: `mm/src/code/main.c:56,95`
- Modify: `mm/2s2h/Extractor/Extract.cpp:95`
- Create: `docs/UPSTREAM.md`

**Interfaces:**
- Consumes: working build from Task 3
- Produces: APK with `applicationId` `com.terminads.mm`, label `Termina DS`, data root `/storage/emulated/0/TerminaDS`; Java package `com.terminads.mm` (all later Kotlin lives under this package)

- [ ] **Step 1: Move the Java package directory**

```bash
cd /srv/projects/2ship2hark/Android/app/src/main/java/com
git mv twoshipfork terminads
ls terminads/mm/
```

Expected: `AssetCopyUtil.java`, `ControllerButtons.java`, `MainActivity.java`.

- [ ] **Step 2: Rewrite every `twoshipfork` reference**

```bash
cd /srv/projects/2ship2hark
grep -rl "twoshipfork" --exclude-dir=.git . | tee /tmp/rebrand-files.txt
xargs sed -i 's/com\.twoshipfork\.mm/com.terminads.mm/g; s|com/twoshipfork/mm|com/terminads/mm|g; s/com_twoshipfork_mm/com_terminads_mm/g' < /tmp/rebrand-files.txt
```

The three substitution forms are all required: dotted (Java/Gradle), slashed (`FindClass`), and underscored (JNI symbol).

- [ ] **Step 3: Verify no reference survives, and the native side changed**

```bash
cd /srv/projects/2ship2hark
grep -rn "twoshipfork" --exclude-dir=.git . | wc -l
grep -n "Java_com_terminads_mm_MainActivity_nativeHandleSelectedFile" mm/2s2h/Extractor/Extract.cpp
grep -n "com/terminads/mm/MainActivity" mm/src/code/main.c
grep -n "com.terminads.mm" Android/app/build.gradle
```

Expected: first command prints `0`; the JNI symbol is found in `Extract.cpp`; **two** `FindClass` hits in `main.c` (lines ~56 and ~95); two hits in `build.gradle`.

- [ ] **Step 4: Change the app label**

Replace `Android/app/src/main/res/values/strings.xml` with:

```xml
<resources>
    <string name="app_name">Termina DS</string>
</resources>
```

- [ ] **Step 5: Move the data root**

Three string literals in `MainActivity.java` (originally lines 55, 252, 297) reference the old root.

```bash
cd /srv/projects/2ship2hark
sed -i 's|"/storage/emulated/0/2S2H"|"/storage/emulated/0/TerminaDS"|g; s|Environment.getExternalStorageDirectory(), "2S2H"|Environment.getExternalStorageDirectory(), "TerminaDS"|g; s|new File(volumeRoot, "2S2H")|new File(volumeRoot, "TerminaDS")|g' \
    Android/app/src/main/java/com/terminads/mm/MainActivity.java
grep -n "TerminaDS\|2S2H" Android/app/src/main/java/com/terminads/mm/MainActivity.java
```

Expected: three `TerminaDS` hits, zero remaining `"2S2H"` string literals. (Comments or unrelated identifiers mentioning 2S2H are fine.)

- [ ] **Step 6: Rebuild**

```bash
cd /srv/projects/2ship2hark
./tools/build-apk.sh
```

Expected: build succeeds.

- [ ] **Step 7: Verify the APK identity changed**

```bash
cd /srv/projects/2ship2hark
docker run --rm -v "$PWD:/workspace" -w /workspace termina-ds-build:latest \
  bash -lc '$ANDROID_HOME/build-tools/34.0.0/aapt2 dump packagename Android/app/build/outputs/apk/release/app-release.apk'
```

Expected: `com.terminads.mm`.

- [ ] **Step 8: Create the upstream ledger**

Create `docs/UPSTREAM.md`:

```markdown
# Upstream Tracking Ledger

Termina DS tracks `linkzenic/2ship2harkinian-Android` (`android` branch), which
tracks `HarbourMasters/2ship2harkinian`. Rebase debt is the principal long-term
cost of this fork (spec §11).

## Remotes

| Remote | URL | Use |
|---|---|---|
| `upstream-android` | https://github.com/linkzenic/2ship2harkinian-Android.git | primary upstream |
| `upstream-core` | https://github.com/HarbourMasters/2ship2harkinian.git | reference only |

## Merging upstream

```bash
git fetch upstream-android android
git merge upstream-android/android
```

Conflicts are expected only in the files listed below. Anything else conflicting
means our change leaked outside its intended boundary — investigate rather than
resolving mechanically.

## Inherited files we have modified

| File | Change | Why |
|---|---|---|
| `Android/app/build.gradle` | `applicationId`/`namespace` → `com.terminads.mm` | rebrand (Task 4) |
| `Android/app/src/main/res/values/strings.xml` | `app_name` → `Termina DS` | rebrand (Task 4) |
| `Android/app/src/main/java/com/terminads/mm/*.java` | package renamed from `com.twoshipfork.mm`; data root → `TerminaDS` | rebrand (Task 4) |
| `mm/src/code/main.c` | `FindClass` path ×2 | rebrand (Task 4) |
| `mm/2s2h/Extractor/Extract.cpp` | JNI symbol renamed | rebrand (Task 4) |

## Files we have added (never conflict)

- `docker/`, `tools/`, `docs/`
- `mm/2s2h/TerminaDS/` — all Termina DS native code
- `Android/app/src/main/java/com/terminads/mm/secondscreen/` — all second-screen code

## Rule

Prefer new files over edits to inherited ones. When an inherited file must
change, keep the change to a single obvious call site and record it here.
```

- [ ] **Step 9: Commit**

```bash
cd /srv/projects/2ship2hark
git add -A
git commit -m "feat: rebrand to Termina DS (com.terminads.mm)

Renames the Java package, application ID, app label, and data root.
JNI symbol in Extract.cpp and both FindClass paths in main.c are renamed
in lockstep — a mismatch links cleanly and crashes at first native call.

Adds docs/UPSTREAM.md to track inherited files we have modified."
```

---

## Task 5: Deploy tooling and Phase 0 hardware verification

This is the Phase 0 gate. Termina DS is only a valid baseline if it behaves identically to the stock port.

**Files:**
- Create: `tools/make-keystore.sh`
- Create: `tools/deploy-apk.sh`

**Interfaces:**
- Consumes: APK from Task 4
- Produces: `tools/deploy-apk.sh` installing to a connected device; a release keystore at `~/.termina-ds/release-keystore.jks` (outside the repo)

- [ ] **Step 1: Install adb on the build host**

```bash
sudo apt-get update && sudo apt-get install -y android-tools-adb
adb version
```

Expected: version string printed.

- [ ] **Step 2: Write the keystore generator**

Create `tools/make-keystore.sh`:

```bash
#!/usr/bin/env bash
# Generate the Termina DS release keystore. Run once.
# The keystore is stored OUTSIDE the repository and must never be committed.
set -euo pipefail

KEYSTORE_DIR="${HOME}/.termina-ds"
KEYSTORE_PATH="${KEYSTORE_DIR}/release-keystore.jks"

if [ -f "${KEYSTORE_PATH}" ]; then
    echo "Keystore already exists at ${KEYSTORE_PATH} — refusing to overwrite."
    echo "Losing this file means no future build can update an installed APK."
    exit 1
fi

mkdir -p "${KEYSTORE_DIR}"
chmod 700 "${KEYSTORE_DIR}"

docker run --rm -it -v "${KEYSTORE_DIR}:/ks" termina-ds-build:latest \
    keytool -genkeypair -v \
        -keystore /ks/release-keystore.jks \
        -alias termina-ds \
        -keyalg RSA -keysize 4096 -validity 10000

chmod 600 "${KEYSTORE_PATH}"
cat <<EOF

Keystore written to ${KEYSTORE_PATH}

Export these before running tools/build-apk.sh for a release-signed build:

  export ANDROID_KEYSTORE_PATH=${KEYSTORE_PATH}
  export ANDROID_KEYSTORE_PASSWORD=<store password>
  export ANDROID_KEY_ALIAS=termina-ds
  export ANDROID_KEY_PASSWORD=<key password>

Back this file up. It cannot be regenerated.
EOF
```

Note: `app/build.gradle` resolves `ANDROID_KEYSTORE_PATH` with `file(...)`, which is relative to `Android/app/`. Always pass an absolute path.

- [ ] **Step 3: Write the deploy script**

Create `tools/deploy-apk.sh`:

```bash
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
```

- [ ] **Step 4: Make both executable, generate the keystore, rebuild signed**

```bash
cd /srv/projects/2ship2hark
chmod +x tools/make-keystore.sh tools/deploy-apk.sh
./tools/make-keystore.sh
```

Then export the four variables it prints and rebuild:

```bash
cd /srv/projects/2ship2hark
./tools/build-apk.sh
```

Expected: build succeeds with release signing.

- [ ] **Step 5: Connect the Thor and deploy**

Enable Wireless debugging on the Thor (Developer options), then:

```bash
adb pair <thor-ip>:<pair-port>
adb connect <thor-ip>:<port>
adb devices
cd /srv/projects/2ship2hark && ./tools/deploy-apk.sh
```

Expected: `adb devices` lists the Thor; install reports `Success`.

- [ ] **Step 6: Run the Phase 0 verification checklist on hardware (spec §9.7)**

Perform each on the Thor and record the result:

1. Termina DS installs alongside the stock 2S2H build; both appear in the launcher with distinct names and neither replaced the other.
2. First launch creates `/storage/emulated/0/TerminaDS` and prompts for a ROM.
3. ROM selection generates `mm.o2r` inside the new data root.
4. Game boots to title screen and into gameplay.
5. Touch controls respond; `Settings > General > Disable Touch Controls` toggles them.
6. A connected gamepad is detected under `Settings > Controls`.
7. `Settings > General > Menu Scale` changes menu size.
8. Data-root relocation to SD card works and survives a restart.
9. The stock 2S2H install still has its own separate save data, untouched.

Verify item 3 from the host:

```bash
adb shell ls -l /storage/emulated/0/TerminaDS/
```

Expected: `mm.o2r` present with non-zero size.

If any item fails, stop. A broken baseline invalidates every later phase.

- [ ] **Step 7: Commit**

```bash
cd /srv/projects/2ship2hark
git add tools/make-keystore.sh tools/deploy-apk.sh
git commit -m "build: add keystore generation and device deploy scripts"
```

---

## Task 6: Import from an existing 2S2H data root

Saves re-extracting the ROM. Offered once, only when the new root is empty and an old one exists.

**Files:**
- Modify: `Android/app/src/main/java/com/terminads/mm/MainActivity.java`

**Interfaces:**
- Consumes: rebranded app from Task 4
- Produces: `MainActivity.offerLegacy2S2HImport(File targetRoot)` — returns `true` if an import dialog was shown (caller should defer normal setup), `false` otherwise

- [ ] **Step 1: Read the surrounding code**

```bash
cd /srv/projects/2ship2hark
grep -n "beginSetupOrChooseDataRoot\|migrateExistingRootIfNeeded\|isDirectoryEmpty\|shouldMigrateExistingRoot" \
    Android/app/src/main/java/com/terminads/mm/MainActivity.java
```

The class already has `migrateExistingRootIfNeeded(File,File)`, `shouldMigrateExistingRoot(File,File)`, and `isDirectoryEmpty(File)`. Reuse them — do not write new copy logic.

- [ ] **Step 2: Add the import offer method**

Add to `MainActivity.java`, immediately after `migrateExistingRootIfNeeded`:

```java
    /**
     * Termina DS: offer a one-time import of an existing 2 Ship 2 Harkinian data
     * folder, so users need not re-extract their ROM. Shown only when our root is
     * empty and a legacy root exists.
     *
     * @return true if a dialog was shown and normal setup should be deferred.
     */
    private boolean offerLegacy2S2HImport(File targetRoot) {
        File legacyRoot = new File(Environment.getExternalStorageDirectory(), "2S2H");
        if (!legacyRoot.isDirectory() || isDirectoryEmpty(legacyRoot)) {
            return false;
        }
        if (targetRoot.isDirectory() && !isDirectoryEmpty(targetRoot)) {
            return false;
        }
        new AlertDialog.Builder(this)
                .setTitle("Import existing 2S2H data?")
                .setMessage("An existing 2 Ship 2 Harkinian data folder was found. "
                        + "Copy it into Termina DS so you do not have to extract your ROM again? "
                        + "The original folder is left unchanged.")
                .setPositiveButton("Import", (dialog, which) -> {
                    migrateExistingRootIfNeeded(legacyRoot, targetRoot);
                    checkAndSetupFiles();
                })
                .setNegativeButton("Skip", (dialog, which) -> checkAndSetupFiles())
                .setCancelable(false)
                .show();
        return true;
    }
```

- [ ] **Step 3: Call it from the setup path**

In `beginSetupIfStorageReady()`, immediately before its existing call to `checkAndSetupFiles()`, insert:

```java
        // Termina DS: one-time import offer from a legacy 2S2H data root.
        if (offerLegacy2S2HImport(getTargetRootFolder())) {
            return;
        }
```

- [ ] **Step 4: Rebuild and deploy**

```bash
cd /srv/projects/2ship2hark
./tools/build-apk.sh && ./tools/deploy-apk.sh
```

Expected: both succeed.

- [ ] **Step 5: Verify on hardware**

```bash
adb shell pm clear com.terminads.mm
adb shell rm -rf /storage/emulated/0/TerminaDS
adb shell monkey -p com.terminads.mm -c android.intent.category.LAUNCHER 1
```

Expected: with a stock 2S2H folder present, the import dialog appears on launch. Choosing **Import** copies data and the game boots without asking for a ROM. Then verify the source is intact:

```bash
adb shell ls /storage/emulated/0/2S2H/ | head
```

Expected: original folder still populated.

- [ ] **Step 6: Update the ledger and commit**

Add to the modified-files table in `docs/UPSTREAM.md`:

```markdown
| `Android/app/src/main/java/com/terminads/mm/MainActivity.java` | `offerLegacy2S2HImport()` + one call site in `beginSetupIfStorageReady()` | data import (Task 6) |
```

```bash
cd /srv/projects/2ship2hark
git add Android/app/src/main/java/com/terminads/mm/MainActivity.java docs/UPSTREAM.md
git commit -m "feat: offer one-time import from an existing 2S2H data root"
```

**Phase 0 is complete.** An APK built from this repo, branded Termina DS, runs Majora's Mask on the Thor alongside the stock port.

---

# Phase 1 — Second-screen shell

## Task 7: Kotlin and Compose toolchain

No behaviour change. The deliverable is that the project still builds with Kotlin compiled and unit tests running.

**Files:**
- Modify: `Android/build.gradle`
- Modify: `Android/app/build.gradle`
- Modify: `Android/gradle.properties`
- Create: `Android/app/src/test/java/com/terminads/mm/ToolchainSmokeTest.kt`

**Interfaces:**
- Consumes: working build from Task 6
- Produces: Kotlin 2.0.21 + Compose BOM 2024.10.01 available; `compileSdk 34`; `./gradlew :app:testReleaseUnitTest` runs JVM unit tests

- [ ] **Step 1: Add Kotlin and Compose compiler to the buildscript classpath**

In `Android/build.gradle`, inside `buildscript { dependencies { ... } }`, after the existing AGP line:

```groovy
        classpath 'com.android.tools.build:gradle:8.10.1'
        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21'
        classpath 'org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.0.21'
```

Kotlin 2.0+ requires the separate Compose compiler plugin; the old `composeOptions.kotlinCompilerExtensionVersion` mechanism no longer applies.

- [ ] **Step 2: Apply the plugins and raise compileSdk**

In `Android/app/build.gradle`, after the existing `apply plugin` block:

```groovy
apply plugin: 'org.jetbrains.kotlin.android'
apply plugin: 'org.jetbrains.kotlin.plugin.compose'
```

Change `compileSdkVersion 33` to:

```groovy
    compileSdkVersion 34
```

Leave `minSdkVersion 24` and `targetSdkVersion 33` untouched — see Global Constraints.

Inside the `android { }` block, add:

```groovy
    buildFeatures {
        buildConfig true
        compose true
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = '17'
    }

    testOptions {
        unitTests.returnDefaultValues = true
    }
```

Note the existing `buildFeatures { buildConfig true }` block must be replaced by the one above, not duplicated.

- [ ] **Step 3: Add dependencies**

Replace the `dependencies { }` block in `Android/app/build.gradle` with:

```groovy
dependencies {
    implementation fileTree(include: ['*.jar'], dir: 'libs')
    implementation 'androidx.core:core:1.13.1'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'

    implementation platform('androidx.compose:compose-bom:2024.10.01')
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    debugImplementation 'androidx.compose.ui:ui-tooling'

    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.8.7'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7'
    implementation 'androidx.savedstate:savedstate-ktx:1.2.1'

    testImplementation 'junit:junit:4.13.2'
}
```

- [ ] **Step 4: Raise the Gradle JVM heap**

In `Android/gradle.properties`, change:

```properties
org.gradle.jvmargs=-Xmx4096m
```

1536 MB is not enough for the Kotlin and Compose compilers together.

- [ ] **Step 5: Write the failing test**

Create `Android/app/src/test/java/com/terminads/mm/ToolchainSmokeTest.kt`:

```kotlin
package com.terminads.mm

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolchainSmokeTest {
    @Test
    fun kotlinSourcesCompileAndTestsRun() {
        val doubled = listOf(1, 2, 3).map { it * 2 }
        assertEquals(listOf(2, 4, 6), doubled)
    }
}
```

- [ ] **Step 6: Run the unit tests**

```bash
cd /srv/projects/2ship2hark
docker run --rm -v "$PWD:/workspace" -v termina-ds-gradle:/root/.gradle -w /workspace/Android \
    termina-ds-build:latest ./gradlew --no-daemon :app:testReleaseUnitTest
```

Expected: `BUILD SUCCESSFUL`, one test executed. If Kotlin is not wired up the task will not exist and Gradle fails with "Task not found" — that is the failing state this step proves we have left.

- [ ] **Step 7: Rebuild the APK**

```bash
cd /srv/projects/2ship2hark
./tools/build-apk.sh
```

Expected: build succeeds. Adding Kotlin must not break the native build.

- [ ] **Step 8: Update the ledger and commit**

Add to `docs/UPSTREAM.md`:

```markdown
| `Android/build.gradle` | Kotlin + Compose compiler classpath | Compose toolchain (Task 7) |
| `Android/app/build.gradle` | compileSdk 34, Kotlin/Compose plugins, deps, test options | Compose toolchain (Task 7) |
| `Android/gradle.properties` | `org.gradle.jvmargs` 1536m → 4096m | Compose toolchain (Task 7) |
```

```bash
cd /srv/projects/2ship2hark
git add Android/build.gradle Android/app/build.gradle Android/gradle.properties \
        Android/app/src/test docs/UPSTREAM.md
git commit -m "build: add Kotlin 2.0.21 and Jetpack Compose toolchain

compileSdk 33 -> 34 (Compose requirement). targetSdk stays 33 per spec 8.3."
```

---

## Task 8: PresentationLifecycleOwner

A `Presentation` is a `Dialog`. Its decor view has no `LifecycleOwner`, `ViewModelStoreOwner`, or `SavedStateRegistryOwner`, and `ComposeView` requires all three — without them it throws at attach time. This shim supplies them. (spec §10.4)

**Files:**
- Create: `Android/app/src/main/java/com/terminads/mm/secondscreen/PresentationLifecycleOwner.kt`
- Test: `Android/app/src/test/java/com/terminads/mm/secondscreen/PresentationLifecycleOwnerTest.kt`

**Interfaces:**
- Consumes: Kotlin toolchain from Task 7
- Produces: `class PresentationLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner` with `fun onCreate()`, `fun onStart()`, `fun onResume()`, `fun onPause()`, `fun onStop()`, `fun onDestroy()`, and properties `lifecycle`, `viewModelStore`, `savedStateRegistry`

- [ ] **Step 1: Write the failing test**

Create `Android/app/src/test/java/com/terminads/mm/secondscreen/PresentationLifecycleOwnerTest.kt`:

```kotlin
package com.terminads.mm.secondscreen

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

class PresentationLifecycleOwnerTest {

    @Test
    fun startsInInitializedState() {
        val owner = PresentationLifecycleOwner()
        assertEquals(Lifecycle.State.INITIALIZED, owner.lifecycle.currentState)
    }

    @Test
    fun advancesThroughCreatedStartedResumed() {
        val owner = PresentationLifecycleOwner()

        owner.onCreate()
        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)

        owner.onStart()
        assertEquals(Lifecycle.State.STARTED, owner.lifecycle.currentState)

        owner.onResume()
        assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)
    }

    @Test
    fun windsBackDownThroughPauseAndStop() {
        val owner = PresentationLifecycleOwner()
        owner.onCreate()
        owner.onStart()
        owner.onResume()

        owner.onPause()
        assertEquals(Lifecycle.State.STARTED, owner.lifecycle.currentState)

        owner.onStop()
        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)
    }

    @Test
    fun destroyEndsLifecycleAndClearsViewModelStore() {
        val owner = PresentationLifecycleOwner()
        owner.onCreate()
        owner.onStart()
        owner.onResume()

        val store = owner.viewModelStore
        owner.onDestroy()

        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        assertEquals(0, store.keys().size)
    }

    @Test
    fun savedStateRegistryIsRestoredAfterCreate() {
        val owner = PresentationLifecycleOwner()
        owner.onCreate()
        assertEquals(true, owner.savedStateRegistry.isRestored)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /srv/projects/2ship2hark
docker run --rm -v "$PWD:/workspace" -v termina-ds-gradle:/root/.gradle -w /workspace/Android \
    termina-ds-build:latest ./gradlew --no-daemon :app:testReleaseUnitTest
```

Expected: FAIL — `Unresolved reference: PresentationLifecycleOwner`.

- [ ] **Step 3: Write the implementation**

Create `Android/app/src/main/java/com/terminads/mm/secondscreen/PresentationLifecycleOwner.kt`:

```kotlin
package com.terminads.mm.secondscreen

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Supplies the three owners a [android.app.Presentation]'s decor view lacks.
 *
 * A Presentation is a Dialog, so its window has no LifecycleOwner,
 * ViewModelStoreOwner, or SavedStateRegistryOwner. ComposeView requires all
 * three and throws at attach time without them.
 *
 * Driven by the Presentation's own show/dismiss, NOT the host Activity's
 * lifecycle — the second screen can be torn down while the game keeps running.
 */
class PresentationLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onStart() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun onResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onPause() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun onStop() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd /srv/projects/2ship2hark
docker run --rm -v "$PWD:/workspace" -v termina-ds-gradle:/root/.gradle -w /workspace/Android \
    termina-ds-build:latest ./gradlew --no-daemon :app:testReleaseUnitTest
```

Expected: `BUILD SUCCESSFUL`, 5 tests passed.

If `savedStateRegistryIsRestoredAfterCreate` fails on `Bundle` being unmocked, confirm `testOptions.unitTests.returnDefaultValues = true` from Task 7 Step 2 is present.

- [ ] **Step 5: Commit**

```bash
cd /srv/projects/2ship2hark
git add Android/app/src/main/java/com/terminads/mm/secondscreen/PresentationLifecycleOwner.kt \
        Android/app/src/test/java/com/terminads/mm/secondscreen/PresentationLifecycleOwnerTest.kt
git commit -m "feat(secondscreen): add PresentationLifecycleOwner shim for ComposeView"
```

---

## Task 9: Display selection policy

The Thor makes the **top** screen primary; other dual-screen handhelds invert this. The policy must not hardcode either. Kept as a pure function over a data class so it is testable without an Android device. (spec §10.2)

**Files:**
- Create: `Android/app/src/main/java/com/terminads/mm/secondscreen/DisplayInfo.kt`
- Create: `Android/app/src/main/java/com/terminads/mm/secondscreen/DisplaySelectionPolicy.kt`
- Test: `Android/app/src/test/java/com/terminads/mm/secondscreen/DisplaySelectionPolicyTest.kt`

**Interfaces:**
- Consumes: Kotlin toolchain from Task 7
- Produces:
  - `data class DisplayInfo(val displayId: Int, val name: String, val widthPx: Int, val heightPx: Int, val refreshRate: Float, val isDefault: Boolean)`
  - `object DisplaySelectionPolicy { fun select(displays: List<DisplayInfo>, overrideDisplayId: Int?): DisplayInfo? }`

- [ ] **Step 1: Write the failing test**

Create `Android/app/src/test/java/com/terminads/mm/secondscreen/DisplaySelectionPolicyTest.kt`:

```kotlin
package com.terminads.mm.secondscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplaySelectionPolicyTest {

    private fun display(id: Int, isDefault: Boolean, name: String = "display-$id") =
        DisplayInfo(
            displayId = id,
            name = name,
            widthPx = 1080,
            heightPx = if (isDefault) 1920 else 1240,
            refreshRate = if (isDefault) 120f else 60f,
            isDefault = isDefault,
        )

    @Test
    fun returnsNullWhenOnlyTheDefaultDisplayExists() {
        val result = DisplaySelectionPolicy.select(listOf(display(0, true)), null)
        assertNull(result)
    }

    @Test
    fun returnsNullWhenThereAreNoDisplays() {
        assertNull(DisplaySelectionPolicy.select(emptyList(), null))
    }

    @Test
    fun picksTheFirstNonDefaultDisplay() {
        val displays = listOf(display(0, true), display(2, false))
        assertEquals(2, DisplaySelectionPolicy.select(displays, null)?.displayId)
    }

    @Test
    fun picksNonDefaultEvenWhenItIsListedFirst() {
        // Some handhelds enumerate the secondary display before the primary.
        val displays = listOf(display(2, false), display(0, true))
        assertEquals(2, DisplaySelectionPolicy.select(displays, null)?.displayId)
    }

    @Test
    fun overrideWinsWhenItMatchesANonDefaultDisplay() {
        val displays = listOf(display(0, true), display(2, false), display(3, false))
        assertEquals(3, DisplaySelectionPolicy.select(displays, 3)?.displayId)
    }

    @Test
    fun overrideIsIgnoredWhenItNamesTheDefaultDisplay() {
        // Never take over the screen the game is running on.
        val displays = listOf(display(0, true), display(2, false))
        assertEquals(2, DisplaySelectionPolicy.select(displays, 0)?.displayId)
    }

    @Test
    fun overrideIsIgnoredWhenItNamesAnAbsentDisplay() {
        val displays = listOf(display(0, true), display(2, false))
        assertEquals(2, DisplaySelectionPolicy.select(displays, 99)?.displayId)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /srv/projects/2ship2hark
docker run --rm -v "$PWD:/workspace" -v termina-ds-gradle:/root/.gradle -w /workspace/Android \
    termina-ds-build:latest ./gradlew --no-daemon :app:testReleaseUnitTest
```

Expected: FAIL — `Unresolved reference: DisplayInfo`.

- [ ] **Step 3: Write the implementation**

Create `Android/app/src/main/java/com/terminads/mm/secondscreen/DisplayInfo.kt`:

```kotlin
package com.terminads.mm.secondscreen

/**
 * A snapshot of one Android display.
 *
 * Deliberately free of android.view.Display so selection logic is unit-testable
 * on the JVM. SecondScreenManager maps real Displays into this.
 */
data class DisplayInfo(
    val displayId: Int,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val refreshRate: Float,
    val isDefault: Boolean,
)
```

Create `Android/app/src/main/java/com/terminads/mm/secondscreen/DisplaySelectionPolicy.kt`:

```kotlin
package com.terminads.mm.secondscreen

/**
 * Chooses which display hosts the Termina DS second screen.
 *
 * The AYN Thor reports its 6" top panel as the default display and its 3.92"
 * bottom panel as secondary. Other dual-screen handhelds invert this, so the
 * policy keys off the default flag rather than display order, size, or id.
 *
 * The default display is never selectable: it is where the game renders.
 */
object DisplaySelectionPolicy {

    fun select(displays: List<DisplayInfo>, overrideDisplayId: Int?): DisplayInfo? {
        val candidates = displays.filterNot { it.isDefault }

        if (overrideDisplayId != null) {
            val chosen = candidates.firstOrNull { it.displayId == overrideDisplayId }
            if (chosen != null) return chosen
        }

        return candidates.firstOrNull()
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd /srv/projects/2ship2hark
docker run --rm -v "$PWD:/workspace" -v termina-ds-gradle:/root/.gradle -w /workspace/Android \
    termina-ds-build:latest ./gradlew --no-daemon :app:testReleaseUnitTest
```

Expected: `BUILD SUCCESSFUL`, 12 tests passed (5 from Task 8 plus 7 here).

- [ ] **Step 5: Commit**

```bash
cd /srv/projects/2ship2hark
git add Android/app/src/main/java/com/terminads/mm/secondscreen/DisplayInfo.kt \
        Android/app/src/main/java/com/terminads/mm/secondscreen/DisplaySelectionPolicy.kt \
        Android/app/src/test/java/com/terminads/mm/secondscreen/DisplaySelectionPolicyTest.kt
git commit -m "feat(secondscreen): add display selection policy

Keys off the default-display flag, not order or size, because the Thor and
other dual-screen handhelds disagree about which panel is primary."
```

---

## Task 10: NativeBridge

The single seam between Kotlin and C++. Phase 1 exposes only native uptime — enough to prove the seam works end to end. It proves the *seam*, not game-loop liveness; coupling to the game loop is Phase 2's job.

Native code goes in `mm/2s2h/TerminaDS/`, which `mm/CMakeLists.txt:98` globs automatically. No CMake edit, no merge conflict.

**Files:**
- Create: `mm/2s2h/TerminaDS/NativeBridge.cpp`
- Create: `Android/app/src/main/java/com/terminads/mm/NativeBridge.kt`

**Interfaces:**
- Consumes: package `com.terminads.mm` from Task 4
- Produces: `object NativeBridge { fun uptimeMillis(): Long }` — returns native process uptime in ms, or `-1L` if the native library is not yet loaded

- [ ] **Step 1: Write the native side**

Create `mm/2s2h/TerminaDS/NativeBridge.cpp`:

```cpp
/*
 * Termina DS: JNI seam between the Compose second screen and the game core.
 *
 * Phase 1 exposes native uptime only. That proves the bridge is wired end to
 * end; it does not prove the game loop is running. Real game state arrives in
 * Phase 2.
 *
 * This file lives under mm/2s2h/, which mm/CMakeLists.txt globs recursively,
 * so it compiles with no CMake change.
 */
#ifdef __ANDROID__

#include <jni.h>
#include <chrono>
#include <cstdint>

namespace {
std::chrono::steady_clock::time_point NativeStartTime() {
    static const std::chrono::steady_clock::time_point start = std::chrono::steady_clock::now();
    return start;
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_terminads_mm_NativeBridge_nativeGetUptimeMillis(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    const auto elapsed = std::chrono::steady_clock::now() - NativeStartTime();
    const auto millis = std::chrono::duration_cast<std::chrono::milliseconds>(elapsed).count();
    return static_cast<jlong>(millis);
}

#endif // __ANDROID__
```

`NativeStartTime()` uses a function-local static, so the clock starts at first call and initialisation is thread-safe.

- [ ] **Step 2: Write the Kotlin side**

Create `Android/app/src/main/java/com/terminads/mm/NativeBridge.kt`:

```kotlin
package com.terminads.mm

/**
 * The only place in the Kotlin layer permitted to call into native code.
 *
 * Declared as a plain object without @JvmStatic, so the JNI entry point takes
 * (JNIEnv*, jobject) and resolves as
 * Java_com_terminads_mm_NativeBridge_nativeGetUptimeMillis.
 *
 * The native library is loaded by SDLActivity during MainActivity.onCreate.
 * Calls made before that throw UnsatisfiedLinkError, which we translate to a
 * sentinel rather than crashing the second screen.
 */
object NativeBridge {

    private external fun nativeGetUptimeMillis(): Long

    /** Native process uptime in milliseconds, or -1 if native is not loaded yet. */
    fun uptimeMillis(): Long =
        try {
            nativeGetUptimeMillis()
        } catch (e: UnsatisfiedLinkError) {
            -1L
        }
}
```

- [ ] **Step 3: Rebuild**

```bash
cd /srv/projects/2ship2hark
./tools/build-apk.sh
```

Expected: build succeeds. Confirm the symbol was compiled in:

```bash
cd /srv/projects/2ship2hark
find build-cmake Android/app/build -name "lib2ship.so" -o -name "libmain.so" 2>/dev/null | head
```

Then, against whichever `.so` is packaged:

```bash
docker run --rm -v "$PWD:/workspace" -w /workspace termina-ds-build:latest bash -lc '
  APK=Android/app/build/outputs/apk/release/app-release.apk
  mkdir -p /tmp/apkx && cd /tmp/apkx && unzip -oq /workspace/$APK "lib/arm64-v8a/*"
  for so in lib/arm64-v8a/*.so; do
    if $ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm -D "$so" 2>/dev/null \
        | grep -q Java_com_terminads_mm_NativeBridge_nativeGetUptimeMillis; then
      echo "FOUND in $so"
    fi
  done
'
```

Expected: `FOUND in lib/arm64-v8a/<something>.so`. If nothing is found, the file was not picked up by the CMake glob — verify it is under `mm/2s2h/` and has a `.cpp` extension.

- [ ] **Step 4: Commit**

```bash
cd /srv/projects/2ship2hark
git add mm/2s2h/TerminaDS/NativeBridge.cpp \
        Android/app/src/main/java/com/terminads/mm/NativeBridge.kt
git commit -m "feat: add NativeBridge JNI seam

Native code lives in mm/2s2h/TerminaDS/, picked up by the existing
GLOB_RECURSE in mm/CMakeLists.txt, so no inherited file changes."
```

---

## Task 11: SecondScreenHost placeholder UI

Deliberately not a feature. Its job is to prove the foundation, so a foundation failure is never misdiagnosed as a feature bug. (spec §10.5)

**Files:**
- Create: `Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenHost.kt`

**Interfaces:**
- Consumes: `DisplayInfo` (Task 9), `NativeBridge.uptimeMillis()` (Task 10)
- Produces: `@Composable fun SecondScreenHost(displayInfo: DisplayInfo, uptimeMillisProvider: () -> Long)`

- [ ] **Step 1: Write the composable**

Create `Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenHost.kt`:

```kotlin
package com.terminads.mm.secondscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Phase 1 placeholder for the Termina DS second screen.
 *
 * Shows the display Termina DS selected, a native uptime heartbeat proving the
 * JNI seam is live, and a tap counter proving touch input arrives here without
 * disturbing the game on the primary display.
 *
 * Content is intentionally throwaway. Real features begin in Phase 3.
 */
@Composable
fun SecondScreenHost(
    displayInfo: DisplayInfo,
    uptimeMillisProvider: () -> Long,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var uptime by remember { mutableLongStateOf(-1L) }
            var taps by remember { mutableIntStateOf(0) }

            LaunchedEffect(Unit) {
                while (true) {
                    uptime = uptimeMillisProvider()
                    delay(500L)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Termina DS", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Phase 1 second-screen shell",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Text("Display", style = MaterialTheme.typography.titleMedium)
                Text("id: ${displayInfo.displayId}")
                Text("name: ${displayInfo.name}")
                Text("size: ${displayInfo.widthPx} x ${displayInfo.heightPx}")
                Text("refresh: ${displayInfo.refreshRate} Hz")

                Text("Native bridge", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (uptime < 0) "not loaded" else "uptime: ${uptime} ms",
                    modifier = Modifier.semantics {
                        contentDescription =
                            if (uptime < 0) {
                                "Native bridge not loaded"
                            } else {
                                "Native uptime $uptime milliseconds"
                            }
                    },
                )

                Text("Touch", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { taps++ }) {
                    Text("Tap me")
                }
                Text("taps: $taps")
            }
        }
    }
}
```

The heartbeat text carries an explicit `contentDescription` so Task 13's TalkBack check has something meaningful to read — the accessibility premise of spec §7 is verified, not assumed.

- [ ] **Step 2: Verify it compiles**

```bash
cd /srv/projects/2ship2hark
docker run --rm -v "$PWD:/workspace" -v termina-ds-gradle:/root/.gradle -w /workspace/Android \
    termina-ds-build:latest ./gradlew --no-daemon :app:compileReleaseKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
cd /srv/projects/2ship2hark
git add Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenHost.kt
git commit -m "feat(secondscreen): add Phase 1 placeholder Compose UI"
```

---

## Task 12: SecondScreenManager and MainActivity wiring

Brings the pieces together and adds the only integration point in inherited game code.

The `Presentation` window is created `FLAG_NOT_FOCUSABLE` so touching the second screen cannot steal input focus from SDL's surface, pause the game, or break the immersive fullscreen that `MainActivity.applyImmersiveFullscreen()` maintains. (spec §10.4)

**Files:**
- Create: `Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenPresentation.kt`
- Create: `Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenManager.kt`
- Modify: `Android/app/src/main/java/com/terminads/mm/MainActivity.java`

**Interfaces:**
- Consumes: `PresentationLifecycleOwner` (8), `DisplayInfo` + `DisplaySelectionPolicy` (9), `NativeBridge` (10), `SecondScreenHost` (11)
- Produces: `class SecondScreenManager(activity: Activity)` with `fun start()`, `fun stop()`, `fun onActivityResume()`, `fun onActivityPause()`

- [ ] **Step 1: Write the Presentation subclass**

Create `Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenPresentation.kt`:

```kotlin
package com.terminads.mm.secondscreen

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.terminads.mm.NativeBridge

/**
 * Hosts the Termina DS Compose UI on a secondary display.
 *
 * The window is FLAG_NOT_FOCUSABLE: the game's SDL surface on the primary
 * display must keep input focus at all times. Touch still reaches Compose;
 * only focus is withheld.
 */
class SecondScreenPresentation(
    outerContext: Context,
    display: Display,
    private val displayInfo: DisplayInfo,
) : Presentation(outerContext, display) {

    private val lifecycleOwner = PresentationLifecycleOwner()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

        lifecycleOwner.onCreate()

        val composeView = ComposeView(context).apply {
            setContent {
                SecondScreenHost(
                    displayInfo = displayInfo,
                    uptimeMillisProvider = { NativeBridge.uptimeMillis() },
                )
            }
        }

        // ComposeView requires all three owners; a Presentation supplies none.
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeViewModelStoreOwner(lifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        setContentView(composeView)
    }

    override fun onStart() {
        super.onStart()
        lifecycleOwner.onStart()
        lifecycleOwner.onResume()
    }

    override fun onStop() {
        lifecycleOwner.onPause()
        lifecycleOwner.onStop()
        lifecycleOwner.onDestroy()
        super.onStop()
    }
}
```

- [ ] **Step 2: Write the manager**

Create `Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenManager.kt`:

```kotlin
package com.terminads.mm.secondscreen

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display

/**
 * Owns second-screen discovery and Presentation lifecycle.
 *
 * Knows nothing about game state. Its whole contract is start, stop, and which
 * display — everything else lives behind SecondScreenHost and NativeBridge.
 *
 * A missing secondary display is normal, not an error: it is what happens when
 * the device is docked over USB-C or running on single-screen hardware.
 */
class SecondScreenManager(private val activity: Activity) {

    private companion object {
        const val TAG = "TerminaDS/SecondScreen"
        const val PREFS = "termina_ds_second_screen"
        const val KEY_DISPLAY_OVERRIDE = "display_override_id"
    }

    private val displayManager =
        activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val handler = Handler(Looper.getMainLooper())

    private var presentation: SecondScreenPresentation? = null
    private var started = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = refresh()
    }

    fun start() {
        if (started) return
        started = true
        displayManager.registerDisplayListener(displayListener, handler)
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        displayManager.unregisterDisplayListener(displayListener)
        dismiss()
    }

    fun onActivityResume() {
        if (started) refresh()
    }

    fun onActivityPause() {
        // The Presentation is left up: the second screen stays useful while the
        // activity is briefly not resumed. Teardown happens in stop().
    }

    private fun refresh() {
        if (!started) return

        val displays = displayManager.displays
        val infos = displays.map { it.toDisplayInfo() }
        val chosen = DisplaySelectionPolicy.select(infos, readOverride())

        if (chosen == null) {
            if (presentation != null) {
                Log.i(TAG, "No secondary display; dismissing second screen.")
                dismiss()
            }
            return
        }

        val current = presentation
        if (current != null && current.display?.displayId == chosen.displayId && current.isShowing) {
            return
        }

        dismiss()

        val target = displays.firstOrNull { it.displayId == chosen.displayId } ?: return
        Log.i(TAG, "Showing second screen on display ${chosen.displayId} (${chosen.name}).")

        try {
            presentation = SecondScreenPresentation(activity, target, chosen).also { it.show() }
        } catch (e: Exception) {
            // A display can disappear between enumeration and show().
            Log.w(TAG, "Failed to show second screen on display ${chosen.displayId}", e)
            presentation = null
        }
    }

    private fun dismiss() {
        presentation?.let {
            if (it.isShowing) it.dismiss()
        }
        presentation = null
    }

    private fun readOverride(): Int? {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = prefs.getInt(KEY_DISPLAY_OVERRIDE, -1)
        return if (value >= 0) value else null
    }

    private fun Display.toDisplayInfo(): DisplayInfo {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        getMetrics(metrics)
        return DisplayInfo(
            displayId = displayId,
            name = name ?: "display-$displayId",
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            refreshRate = refreshRate,
            isDefault = displayId == Display.DEFAULT_DISPLAY,
        )
    }
}
```

- [ ] **Step 3: Wire into MainActivity**

Add the import near the other imports in `Android/app/src/main/java/com/terminads/mm/MainActivity.java`:

```java
import com.terminads.mm.secondscreen.SecondScreenManager;
```

Add the field alongside the other private fields (near `dataRootMigrationDialog`):

```java
    private SecondScreenManager secondScreenManager;
```

At the end of `onCreate(Bundle)`, after the existing body:

```java
        // Termina DS: bring up the second screen. Safe when no secondary display exists.
        secondScreenManager = new SecondScreenManager(this);
        secondScreenManager.start();
```

At the end of `onResume()`:

```java
        if (secondScreenManager != null) {
            secondScreenManager.onActivityResume();
        }
```

Add an `onDestroy` override immediately after `onResume()`:

```java
    @Override
    protected void onDestroy() {
        if (secondScreenManager != null) {
            secondScreenManager.stop();
            secondScreenManager = null;
        }
        super.onDestroy();
    }
```

- [ ] **Step 4: Build and deploy**

```bash
cd /srv/projects/2ship2hark
./tools/build-apk.sh && ./tools/deploy-apk.sh
```

Expected: both succeed.

- [ ] **Step 5: Confirm the second screen came up**

```bash
adb shell monkey -p com.terminads.mm -c android.intent.category.LAUNCHER 1
sleep 15
adb logcat -d -s "TerminaDS/SecondScreen"
```

Expected: a line `Showing second screen on display <id> (<name>).` The Thor's bottom panel shows the Termina DS placeholder.

If the log says "No secondary display", dump what Android reports and adjust:

```bash
adb shell dumpsys display | grep -A2 "mDisplayId"
```

- [ ] **Step 6: Update the ledger and commit**

Add to `docs/UPSTREAM.md`:

```markdown
| `Android/app/src/main/java/com/terminads/mm/MainActivity.java` | `secondScreenManager` field + start in `onCreate`, resume hook, `onDestroy` override | second screen (Task 12) |
```

```bash
cd /srv/projects/2ship2hark
git add Android/app/src/main/java/com/terminads/mm/secondscreen/ \
        Android/app/src/main/java/com/terminads/mm/MainActivity.java docs/UPSTREAM.md
git commit -m "feat(secondscreen): show Compose UI on the secondary display

Presentation window is FLAG_NOT_FOCUSABLE so the SDL surface keeps input
focus. Absent secondary display is handled as normal, not an error."
```

---

## Task 13: Phase 1 hardware verification

The gate on the entire architecture. If these fail, spec §7 must be revisited before any feature is built on this foundation.

**Files:**
- Create: `docs/verification/2026-07-23-phase-1-thor.md`

**Interfaces:**
- Consumes: everything from Tasks 1–12
- Produces: a recorded verification result

- [ ] **Step 1: Measure the baseline framerate without the second screen**

Check out the Task 6 commit (last Phase 0 state), build, deploy, and record in-game framerate using 2S2H's own frame counter in `Settings`. Play the same short sequence (Clock Town, South entrance, 60 seconds) and note the range.

```bash
cd /srv/projects/2ship2hark
git log --oneline | grep "offer one-time import"   # note the hash
git stash list                                     # ensure nothing uncommitted
```

Record: baseline FPS range.

- [ ] **Step 2: Measure with the second screen active**

Return to `main`, build, deploy, repeat the identical sequence with the second screen showing.

```bash
cd /srv/projects/2ship2hark
git checkout main
./tools/build-apk.sh && ./tools/deploy-apk.sh
```

Record: Phase 1 FPS range. **Expected: unchanged within measurement noise.** A consistent drop means the Presentation is coupled to the game's frame timing and must be investigated before proceeding.

- [ ] **Step 3: Run the full spec §10.6 checklist**

On the Thor, record pass/fail for each:

1. Both displays enumerate; the bottom panel is selected (confirm via the display id in the placeholder UI and the logcat line).
2. Compose UI renders at 1080×1240 with no clipping or letterboxing.
3. Top-screen framerate unchanged versus Step 1 — measured, not eyeballed.
4. Tapping "Tap me" increments the counter **and** the game does not pause, lose input, or exit immersive fullscreen.
5. Native uptime advances every 500 ms (proves the JNI seam is live).
6. Sleep the device, wake it: the second screen returns without a restart.
7. Switch to another app and back: the second screen returns.
8. Attempt rotation: the game stays landscape and the second screen does not crash.
9. Connect USB-C video out (secondary display taken over): the app continues running, logcat reports dismissal, and no crash occurs.
10. TalkBack reads the placeholder — enable with `adb shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService`, then confirm the heartbeat line is announced as "Native uptime N milliseconds".

Item 10 is the load-bearing one for spec §7: it is the empirical check that choosing Compose actually bought the accessibility the architecture was chosen for.

- [ ] **Step 4: Record the results**

Create `docs/verification/2026-07-23-phase-1-thor.md` with a row per check: item, pass/fail, notes, plus the two framerate ranges from Steps 1–2 and the device's Android version and SoC.

Any failure gets an explicit note on whether it blocks Phase 2 or is deferred.

- [ ] **Step 5: Commit**

```bash
cd /srv/projects/2ship2hark
git add docs/verification/2026-07-23-phase-1-thor.md
git commit -m "docs: record Phase 1 hardware verification on AYN Thor"
```

**Phase 1 is complete.** A Compose UI under our control runs on the Thor's bottom display, the JNI seam is proven live, and the game is measurably unaffected. Phase 2 (read-only game state bridge) can begin.

---

## Plan Self-Review

**Spec coverage.** §3 base selection → Task 1. §4 codebase facts → Tasks 2–4. §7 architecture → Tasks 8–12. §8.3 targetSdk 33 → Global Constraints + Task 7. §8.4 no `mm.o2r` → Task 3 Steps 3–4. §9.1 repo → Task 1. §9.2 container → Task 2. §9.3 build script → Task 3. §9.4 rebrand → Task 4. §9.5 signing → Task 5. §9.6 deploy → Task 5. §9.7 verification → Task 5 Step 6. §10.1 components → Tasks 9–12. §10.2 display policy → Task 9. §10.3 gradle → Task 7. §10.4 both failure modes → Tasks 8 and 12. §10.5 placeholder → Task 11. §10.6 verification → Task 13. §11 upstream discipline → `docs/UPSTREAM.md`, created Task 4 and updated in 6, 7, 12.

**Deliberately deferred:** §8.1 walkthrough (Phase 7), §8.2 maps (Phase 6), §12 open questions (repo hosting, icon, CI) — none block these phases.

**Type consistency.** `DisplayInfo(displayId, name, widthPx, heightPx, refreshRate, isDefault)` is used identically in Tasks 9, 11, 12. `DisplaySelectionPolicy.select(List<DisplayInfo>, Int?)` matches its call site in `SecondScreenManager.refresh()`. `NativeBridge.uptimeMillis()` in Task 10 matches the `uptimeMillisProvider` lambda in Task 11 and the call in Task 12. `PresentationLifecycleOwner` exposes exactly the six methods Task 12 calls. The JNI symbol `Java_com_terminads_mm_NativeBridge_nativeGetUptimeMillis` matches the package set in Task 4 and the non-`@JvmStatic` object declaration in Task 10.
