# Upstream Tracking Ledger

The game/Android layer (`mm/`, `Android/`) derives from
`linkzenic/2ship2harkinian-Android` (`android` branch), which tracks
`HarbourMasters/2ship2harkinian`. Those remotes remain available for reference
and cherry-picking, but Termina DS is diverging deliberately and does not aim to
stay mergeable long-term (spec §11).

The engine and asset tooling are **no longer submodules** — they have been
vendored into this repo as first-class source (see "Vendored engine & tooling"
below), so the project is self-contained and clones + builds with no
`.gitmodules` and no external submodule hosting.

## Remotes

| Remote | URL | Use |
|---|---|---|
| `upstream-android` | https://github.com/linkzenic/2ship2harkinian-Android.git | reference / cherry-pick |
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
| `mm/src/code/main.c` | `FindClass` path ×2 renamed to `com/terminads/mm/MainActivity` | rebrand (Task 4) |
| `mm/2s2h/Extractor/Extract.cpp` | JNI symbol renamed to `Java_com_terminads_mm_MainActivity_nativeHandleSelectedFile` | rebrand (Task 4) |
| `Android/app/src/main/java/com/terminads/mm/MainActivity.java` | `offerLegacy2S2HImport()` + one call site in `beginSetupIfStorageReady()` | data import (Task 6) |
| `Android/app/src/main/java/com/terminads/mm/MainActivity.java` | `secondScreenManager` field + start in `onCreate`, resume hook, `onDestroy` override | second screen (Task 12) |
| `Android/app/src/main/java/com/terminads/mm/MainActivity.java` | `onStart`/`onStop` hooks call `SecondScreenManager.start()`/`stop()` | dismiss second screen while backgrounded and restore it on return |
| `Android/build.gradle` | Kotlin + Compose compiler classpath | Compose toolchain (Task 7) |
| `Android/app/build.gradle` | compileSdk 34, Kotlin/Compose plugins, deps, test options | Compose toolchain (Task 7) |
| `Android/gradle.properties` | `org.gradle.jvmargs` 1536m → 4096m | Compose toolchain (Task 7) |
| `mm/2s2h/BenGui/BenGui.hpp`, `mm/2s2h/BenGui/BenGui.cpp` | added `BenGui::IsBenMenuVisible()` accessor | TerminaDS snapshot's `MENU_OPEN` flag (Phase 4a) |
| `engine/src/ship/window/gui/Gui.cpp` | Phase 4 Plan B | One call to `TerminaDS_LoadVeilFont()` after the FontAwesome registration, so the pause veil's Cinzel is in the font atlas. The atlas is built once during `Gui::Init`, so a later `AddFont` would not be rasterised. |

## Files we have added (never conflict)

- `docker/`, `tools/`, `docs/`
- `mm/2s2h/TerminaDS/` — all Termina DS native code
- `Android/app/src/main/java/com/terminads/mm/secondscreen/` — all second-screen code

## Rule

Prefer new files over edits to inherited ones. When an inherited file must
change, keep the change to a single obvious call site and record it here.

## Vendored engine & tooling

The three former submodules were vendored into the repo as regular tracked
source and `.gitmodules` was removed, making the project self-contained (no
external submodule hosting, `git clone` alone is buildable):

| Was (submodule) | Now (vendored path) | Notes |
|---|---|---|
| `libultraship` (`Jameriquiah/libultraship`) | **`engine/`** | renamed folder |
| `OTRExporter` (`Jameriquiah/OTRExporter`) | `OTRExporter/` | asset-build tool |
| `ZAPDTR` (`Jameriquiah/ZAPDTR`) | `ZAPDTR/` | asset-build tool |

The engine folder was renamed `libultraship/` → `engine/`. All **directory-path**
references were updated: `CMakeLists.txt`, `mm/CMakeLists.txt`,
`CMake/lus-cvars.cmake`, `.github/workflows/android-release.yml`,
`OTRExporter/CMakeLists.txt`, `OTRExporter/OTRExporter/CMakeLists.txt`,
`ZAPDTR/ZAPD/CMakeLists.txt`.

**Intentionally left as `libultraship` (internal, invisible):** the CMake target
name (`libultraship`), the C++ namespace (`Ship`/`LUS`, 173 files), and the
`#include <libultraship/…>` header prefix (390 files) and the
`engine/src/libultraship/` internal source dir. Renaming these is a separate
500+ file operation, to be done deliberately and incrementally as the engine
diverges — not bundled with vendoring.

`.gitattributes` marks `engine/`, `OTRExporter/`, `ZAPDTR/` as `-text`
(byte-preserved, no line-ending normalization) so vendored patches and build
scripts stay identical to what upstream built (notably
`engine/cmake/dependencies/patches/stormlib-optimizations.patch`).

The five Android JNI entry points in
`engine/src/ship/port/mobile/MobileImpl.cpp` (`attachController`,
`setCameraState`, `setButton`, `setAxis`, `detachController`) carry the
`Java_com_terminads_mm_MainActivity_*` names (renamed from `…twoshipfork…` when
the app package moved). These bind by symbol name at runtime, so a mismatch
would only fail with `UnsatisfiedLinkError` when a control is used — verified on
the packaged `.so` via `llvm-nm -D`: all renamed symbols present, 0 old symbols,
confirmed on hardware.
