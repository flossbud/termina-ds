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
| `mm/src/code/main.c` | `FindClass` path ×2 renamed to `com/terminads/mm/MainActivity` | rebrand (Task 4) |
| `mm/2s2h/Extractor/Extract.cpp` | JNI symbol renamed to `Java_com_terminads_mm_MainActivity_nativeHandleSelectedFile` | rebrand (Task 4) |
| `Android/app/src/main/java/com/terminads/mm/MainActivity.java` | `offerLegacy2S2HImport()` + one call site in `beginSetupIfStorageReady()` | data import (Task 6) |
| `Android/app/src/main/java/com/terminads/mm/MainActivity.java` | `secondScreenManager` field + start in `onCreate`, resume hook, `onDestroy` override | second screen (Task 12) |
| `Android/build.gradle` | Kotlin + Compose compiler classpath | Compose toolchain (Task 7) |
| `Android/app/build.gradle` | compileSdk 34, Kotlin/Compose plugins, deps, test options | Compose toolchain (Task 7) |
| `Android/gradle.properties` | `org.gradle.jvmargs` 1536m → 4096m | Compose toolchain (Task 7) |

## Files we have added (never conflict)

- `docker/`, `tools/`, `docs/`
- `mm/2s2h/TerminaDS/` — all Termina DS native code
- `Android/app/src/main/java/com/terminads/mm/secondscreen/` — all second-screen code

## Rule

Prefer new files over edits to inherited ones. When an inherited file must
change, keep the change to a single obvious call site and record it here.

## libultraship fork (Task 4)

The `libultraship` submodule is itself an unofficial fork
(`Jameriquiah/libultraship`), pinned in `.gitmodules` in detached-HEAD form.
It contains five JNI entry points in
`src/ship/port/mobile/MobileImpl.cpp` — `attachController`, `setCameraState`,
`setButton`, `setAxis`, `detachController` — named after the Java package
that calls them (`Java_com_twoshipfork_mm_MainActivity_*`). These are declared
`native` in `MainActivity.java` and bound to the shared library by symbol name
at runtime, not at link time, so a mismatch between the Java package and the
`.so` symbol names compiles and links cleanly and only fails with
`UnsatisfiedLinkError` the first time a controller is touched or the camera
moves.

The main-repo rebrand (this commit) moved the Java package to
`com.terminads.mm`, so the submodule's five symbols had to move in lockstep.
Because the submodule was in detached HEAD at `6c5a562`, the rename was
committed inside the submodule on a new local branch, `termina-ds`:

- Submodule commit: `62945178154e9c0dde77d120e3b0fd1b7a652e73`
  ("Rename Android JNI symbols com.twoshipfork.mm -> com.terminads.mm for
  Termina DS"), on branch `termina-ds`, one commit ahead of `6c5a562`.
- The superproject's submodule pointer (`libultraship` gitlink) was bumped to
  that commit in the same commit as the main-repo rebrand.

**This fork commit exists only in this local clone.** `.gitmodules` still
points `libultraship` at `Jameriquiah/libultraship`, and we have no push
access to create a fork there. Consequently:

- A fresh `git clone --recursive` (or `git submodule update --init`) of this
  repo will **not** resolve the new submodule SHA — it will fail to find
  `62945178...` on the configured remote.
- **Hosting a `libultraship` fork containing this commit (or cherry-picking
  it onto a hosted fork) is a prerequisite for anyone but this workspace to
  build Termina DS from a clean checkout.** Until then, treat this submodule
  state as workspace-local, and do not attempt to push it — there is nowhere
  to push it to yet.
- End-to-end verification that the rename propagated through compilation,
  linking, and APK packaging was done via `llvm-nm -D` on the packaged
  `arm64-v8a` `.so` files: all 5 new `Java_com_terminads_mm_MainActivity_*`
  symbols are present and 0 old `Java_com_twoshipfork_mm_*` symbols remain.
