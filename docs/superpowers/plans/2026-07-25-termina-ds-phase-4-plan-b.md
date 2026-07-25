# Termina DS Phase 4 Plan B Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the pause experience — full §5 pause-root styling, the §10 Options subscreen bound to real BenMenu CVars, an engine-side ImGui PAUSED veil, Compose UI test infrastructure, and the release keystore.

**Architecture:** Ten graphics settings are read through the existing seqlock snapshot (schema v2 → v3) and written through the existing SPSC command mailbox. Three of them need an engine apply call that a bare CVar write does not perform, so they get semantic opcodes that do the write and the apply inside one drained command. The veil is a `Ship::GuiWindow` overlay drawn while the frame-advance gate holds. All Compose work sits on a pure data-model backbone with a thin Robolectric smoke layer over it.

**Tech Stack:** Kotlin / Jetpack Compose (Android), C++17 (2S2H + libultraship), JUnit 4, Robolectric, Docker build toolchain.

**Spec:** `docs/superpowers/specs/2026-07-25-termina-ds-phase-4-plan-b-design.md`

## Global Constraints

- **Commits authored as `jaret <jaretmsanchez@gmail.com>`.** Never involve the WheelHouse-Software GitHub account. Push only when the user asks.
- **`SnapshotPublisher.cpp` and `CommandMailbox.cpp` are the only files permitted to dereference game state.** No new file may join them.
- **Commands are absolute.** Every command carries its target value, never a delta. The UI observes effects through the snapshot and never assumes them.
- **The schema bump is atomic: native header, Kotlin mirror, and tests change in ONE commit.** Plan A's final review classified split schema history as a defect (ledger line 43).
- **Main-thread only for the Presentation and its lifecycle owner.** `PresentationLifecycleOwner` uses `LifecycleRegistry.createUnsafe`, which drops the main-thread assertion — a background thread touching it corrupts state silently instead of crashing.
- **New native code goes in `mm/2s2h/TerminaDS/`.** The veil's font registration in `engine/src/ship/window/gui/Gui.cpp` is the single sanctioned inherited edit and must be recorded in `docs/UPSTREAM.md`.
- **`compileSdk 34`, `targetSdk 33` (do NOT raise), `minSdk 24`, `arm64-v8a` only.**
- **`mm.o2r` must never ship in the APK.** The inherited `verifyBundledAssets` Gradle task enforces this; do not weaken it.
- **Never trust Gradle console text for test results.** Only `./tools/run-unit-tests.sh`'s XML-derived counts are evidence. It prints `BUILD SUCCESSFUL` with zero tests run when tasks are UP-TO-DATE.
- **A green build does not prove a native symbol shipped.** Verify with `llvm-nm` inside the Docker image (`.superpowers/codex-sol/verify-apk.sh`). Never suppress stderr on that check — a silent `llvm-nm` is byte-identical to the real failure it exists to catch.
- **Never use `tools/assemble-apk.sh` after adding a native file.** CMake's `GLOB_RECURSE` freezes the source list at configure time; the fast path skips the `.cxx` clear and the new file compiles green but ships without its code.
- **Prefix docker-touching commands with `sg docker -c '...'`** if bare `docker` is denied in the shell.
- **Design geometry is expressed in design px** through `du()` / `dus()` / `dupx()` with the established `LEGIBILITY` factor on type and reading-critical glyphs. Never hardcode Dp.
- **Accessibility:** no per-poll values in any `contentDescription`, no live regions, disabled controls read as unavailable. The structural guard test stays green.

---

## File Structure

**Native — created:**
- `mm/2s2h/TerminaDS/PauseVeil.cpp` — the ImGui overlay window (Task 10)
- `mm/2s2h/TerminaDS/CinzelFontData.cpp` — generated compressed-base85 font bytes (Task 10)
- `mm/2s2h/TerminaDS/CinzelFontData.h` — its one extern declaration (Task 10)

**Native — modified:**
- `mm/2s2h/TerminaDS/GameSnapshot.h` — schema v3, eleven new indices (Task 2)
- `mm/2s2h/TerminaDS/SnapshotPublisher.cpp` — sample the eleven slots (Task 2)
- `mm/2s2h/TerminaDS/CommandMailbox.h` — three opcodes (Task 3)
- `mm/2s2h/TerminaDS/CommandMailbox.cpp` — their apply + range validation (Task 3)
- `engine/src/ship/window/gui/Gui.cpp` — one font registration line (Task 10)

**Kotlin — created:**
- `.../secondscreen/OptionsModel.kt` — row view-models, grey-out rules, formatting (Task 4)
- `.../secondscreen/OptionsCommands.kt` — interaction → command mapping + save debounce (Task 5)
- `.../secondscreen/OptionsScreen.kt` — §6 chrome, tabs, category chips, empty state (Task 7)
- `.../secondscreen/OptionControls.kt` — slider, segmented, checkbox, row anatomy (Task 8)
- `.../secondscreen/PauseNavState.kt` — local menu navigation state (Task 9)

**Kotlin — modified:**
- `.../GameSnapshot.kt` — v3 mirror + `GameSettings` (Task 2)
- `.../CommandBridge.kt` — three new writers (Task 3)
- `.../secondscreen/PauseMenuScreen.kt` — full §5 styling (Task 6)
- `.../secondscreen/TerminaDesign.kt` — new type roles and colors (Task 6)
- `.../secondscreen/SecondScreenHost.kt` — Options routing and wiring (Task 9)

**Build / tooling — modified:**
- `Android/app/build.gradle` — test dependencies (Task 1), signing guard (Task 11)
- `tools/build-apk.sh`, `tools/assemble-apk.sh` — keystore mount (Task 11)

---

### Task 1: Compose test infrastructure spike

This task exists to fail fast. Robolectric fetches `android-all-instrumented` jars at **test-run** time, not build time, so the Docker image may need a network round trip on first run. Prove one trivial assertion green before any real test depends on this.

**Files:**
- Modify: `Android/app/build.gradle:117-133`
- Test: `Android/app/src/test/java/com/terminads/mm/secondscreen/ComposeSmokeTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: a working `createComposeRule()` under `./tools/run-unit-tests.sh`. Tasks 6, 7, 8 depend on it. If this task fails, they drop their Robolectric layer and keep their pure-model tests.

- [ ] **Step 1: Add the test dependencies and resource flag**

In `Android/app/build.gradle`, inside the existing `android { }` block, add:

```groovy
    testOptions {
        unitTests {
            includeAndroidResources = true
        }
    }
```

In the `dependencies { }` block, replace the lone `testImplementation 'junit:junit:4.13.2'` line with:

```groovy
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.robolectric:robolectric:4.13'
    testImplementation 'androidx.compose.ui:ui-test-junit4:1.7.5'
    debugImplementation 'androidx.compose.ui:ui-test-manifest:1.7.5'
```

- [ ] **Step 2: Write the smoke test**

Create `Android/app/src/test/java/com/terminads/mm/secondscreen/ComposeSmokeTest.kt`:

```kotlin
package com.terminads.mm.secondscreen

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Proves the Compose UI test toolchain runs inside the Docker image. If this
 * test cannot be made green, Tasks 6-8 drop their Robolectric layer and keep
 * their pure-model coverage -- see the plan's Task 1 note.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class ComposeSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun composeRuleRendersAndFindsANode() {
        composeTestRule.setContent { Text("termina-ds-compose-smoke") }
        composeTestRule.onNodeWithText("termina-ds-compose-smoke").assertIsDisplayed()
    }
}
```

- [ ] **Step 3: Run the suite and confirm the new test is counted**

Run: `sg docker -c './tools/run-unit-tests.sh'`

Expected: `PASS` with **105** tests (104 + 1). If the run hangs on a Robolectric download, that is the failure mode this task exists to surface — report it to the orchestrator rather than retrying blindly.

- [ ] **Step 4: Commit**

```bash
git add Android/app/build.gradle Android/app/src/test/java/com/terminads/mm/secondscreen/ComposeSmokeTest.kt
git commit -m "test: add Compose UI test infrastructure (Robolectric spike)"
```

---

### Task 2: Schema v3 — eleven settings slots

**Files:**
- Modify: `mm/2s2h/TerminaDS/GameSnapshot.h:19`, `:68-71`
- Modify: `mm/2s2h/TerminaDS/SnapshotPublisher.cpp:14-22` (includes), `:160` (sampling)
- Modify: `Android/app/src/main/java/com/terminads/mm/GameSnapshot.kt`
- Test: `Android/app/src/test/java/com/terminads/mm/GameSnapshotTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `GameSettings` data class with fields `internalResPercent: Int`, `msaa: Int`, `fps: Int`, `matchRefreshRate: Boolean`, `textureFilter: Int`, `clockType: Int`, `motionBlurMode: Int`, `motionBlurStrength: Int`, `actorDrawDistance: Int`, `threeDItemDrops: Boolean`, `displayRefreshHz: Int`; reachable as `GameSnapshot.settings`. Tasks 4, 5, 7, 8, 9 consume it.

**This task's native and Kotlin halves MUST land in one commit.** Splitting them is the defect Plan A's final review caught.

- [ ] **Step 1: Add the eleven indices to the native header**

In `mm/2s2h/TerminaDS/GameSnapshot.h`, change line 19:

```c
#define TDS_SNAP_SCHEMA_VERSION 3
```

Then replace the `TDS_SNAP_IDX_PAUSE_STATE` block (currently lines 64-70) with:

```c
    /*
     * v2: 1 while the engine's frame-advance gate holds the Play update
     * frozen (our pause; z_play.c:988). 0 when unpaused or no PlayState.
     */
    TDS_SNAP_IDX_PAUSE_STATE,

    /*
     * v3: the ten graphics settings the Options subscreen renders, sampled
     * from the CVars BenMenu itself uses. They ride the snapshot rather than
     * a JNI getter because CVars live in an unmutexed std::unordered_map
     * (engine/include/ship/config/ConsoleVariable.h:67) that this thread
     * writes whenever a command drains -- reading it from the Android main
     * thread would be a data race, not merely a stale read.
     *
     * Internal resolution is a float CVar (0.5-2.0); it is published as a
     * percent so the whole payload stays int32.
     */
    TDS_SNAP_IDX_CVAR_INTERNAL_RES,
    TDS_SNAP_IDX_CVAR_MSAA,
    TDS_SNAP_IDX_CVAR_FPS,
    TDS_SNAP_IDX_CVAR_MATCH_HZ,
    TDS_SNAP_IDX_CVAR_TEXTURE_FILTER,
    TDS_SNAP_IDX_CVAR_CLOCK_TYPE,
    TDS_SNAP_IDX_CVAR_BLUR_MODE,
    TDS_SNAP_IDX_CVAR_BLUR_STRENGTH,
    TDS_SNAP_IDX_CVAR_DRAW_DISTANCE,
    TDS_SNAP_IDX_CVAR_3D_ITEM_DROPS,

    /*
     * v3: not a CVar. The FPS row's maximum and its "MAX n HZ" chip come from
     * the live display, and GetCurrentRefreshRate()
     * (engine/include/ship/window/Window.h:56) is only safe on this thread.
     */
    TDS_SNAP_IDX_DISPLAY_REFRESH_HZ,

    TDS_SNAP_COUNT
};
```

- [ ] **Step 2: Sample the eleven slots in the publisher**

In `mm/2s2h/TerminaDS/SnapshotPublisher.cpp`, add to the include block (after the existing `#include "2s2h/ShipInit.hpp"` on line 22):

```cpp
#include <libultraship/libultraship.h>
#include <libultraship/bridge/consolevariablebridge.h>
```

Then, in `Publish()`, immediately before the line `v[TDS_SNAP_IDX_FLAGS] = flags;`, insert:

```cpp
    // v3 settings block. These are engine configuration, not game state, so
    // they sit outside the gPlayState guard above -- they are valid on the
    // title screen and across scene transitions alike. CVar names and their
    // defaults are copied from mm/2s2h/BenGui/BenMenu.cpp so the two menus
    // cannot disagree about what a row means.
    v[TDS_SNAP_IDX_CVAR_INTERNAL_RES] =
        (int32_t)(CVarGetFloat(CVAR_INTERNAL_RESOLUTION, 1.0f) * 100.0f + 0.5f);
    v[TDS_SNAP_IDX_CVAR_MSAA] = CVarGetInteger(CVAR_MSAA_VALUE, 1);
    v[TDS_SNAP_IDX_CVAR_FPS] = CVarGetInteger("gInterpolationFPS", 20);
    v[TDS_SNAP_IDX_CVAR_MATCH_HZ] = CVarGetInteger("gMatchRefreshRate", 0);
    v[TDS_SNAP_IDX_CVAR_TEXTURE_FILTER] = CVarGetInteger(CVAR_TEXTURE_FILTER, 0);
    v[TDS_SNAP_IDX_CVAR_CLOCK_TYPE] = CVarGetInteger("gEnhancements.Graphics.ClockType", 0);
    v[TDS_SNAP_IDX_CVAR_BLUR_MODE] = CVarGetInteger("gEnhancements.Graphics.MotionBlur.Mode", 0);
    v[TDS_SNAP_IDX_CVAR_BLUR_STRENGTH] =
        CVarGetInteger("gEnhancements.Graphics.MotionBlur.Strength", 180);
    v[TDS_SNAP_IDX_CVAR_DRAW_DISTANCE] =
        CVarGetInteger("gEnhancements.Graphics.IncreaseActorDrawDistance", 1);
    v[TDS_SNAP_IDX_CVAR_3D_ITEM_DROPS] = CVarGetInteger("gEnhancements.Graphics.3DItemDrops", 0);
    v[TDS_SNAP_IDX_DISPLAY_REFRESH_HZ] =
        (int32_t)Ship::Context::GetInstance()->GetWindow()->GetCurrentRefreshRate();
```

- [ ] **Step 3: Write the failing Kotlin tests**

Add to `Android/app/src/test/java/com/terminads/mm/GameSnapshotTest.kt`:

```kotlin
    @Test
    fun slotCountMatchesSchemaV3() {
        assertEquals(3, GameSnapshotLayout.SCHEMA_VERSION)
        assertEquals(39, GameSnapshotLayout.SLOT_COUNT)
        assertEquals(38, GameSnapshotLayout.IDX_DISPLAY_REFRESH_HZ)
    }

    @Test
    fun decodesTheSettingsBlock() {
        val raw = IntArray(GameSnapshotLayout.SLOT_COUNT)
        raw[GameSnapshotLayout.IDX_SCHEMA_VERSION] = 3
        raw[GameSnapshotLayout.IDX_CVAR_INTERNAL_RES] = 150
        raw[GameSnapshotLayout.IDX_CVAR_MSAA] = 4
        raw[GameSnapshotLayout.IDX_CVAR_FPS] = 60
        raw[GameSnapshotLayout.IDX_CVAR_MATCH_HZ] = 1
        raw[GameSnapshotLayout.IDX_CVAR_TEXTURE_FILTER] = 2
        raw[GameSnapshotLayout.IDX_CVAR_CLOCK_TYPE] = 1
        raw[GameSnapshotLayout.IDX_CVAR_BLUR_MODE] = 2
        raw[GameSnapshotLayout.IDX_CVAR_BLUR_STRENGTH] = 180
        raw[GameSnapshotLayout.IDX_CVAR_DRAW_DISTANCE] = 3
        raw[GameSnapshotLayout.IDX_CVAR_3D_ITEM_DROPS] = 1
        raw[GameSnapshotLayout.IDX_DISPLAY_REFRESH_HZ] = 120

        val decoded = decodeSnapshot(raw) as SnapshotDecode.Ok
        val s = decoded.snapshot.settings
        assertEquals(150, s.internalResPercent)
        assertEquals(4, s.msaa)
        assertEquals(60, s.fps)
        assertEquals(true, s.matchRefreshRate)
        assertEquals(2, s.textureFilter)
        assertEquals(1, s.clockType)
        assertEquals(2, s.motionBlurMode)
        assertEquals(180, s.motionBlurStrength)
        assertEquals(3, s.actorDrawDistance)
        assertEquals(true, s.threeDItemDrops)
        assertEquals(120, s.displayRefreshHz)
    }

    @Test
    fun aV2PayloadIsReportedAsAMismatchNotDecoded() {
        val raw = IntArray(GameSnapshotLayout.SLOT_COUNT)
        raw[GameSnapshotLayout.IDX_SCHEMA_VERSION] = 2
        val decoded = decodeSnapshot(raw)
        assertEquals(SnapshotDecode.SchemaMismatch(2, 3), decoded)
    }
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `sg docker -c "./tools/run-unit-tests.sh --tests '*GameSnapshotTest*'"`

Expected: FAIL — `Unresolved reference: IDX_CVAR_INTERNAL_RES` (compilation error).

- [ ] **Step 5: Update the Kotlin mirror**

In `Android/app/src/main/java/com/terminads/mm/GameSnapshot.kt`, change `SCHEMA_VERSION` to `3`, then replace the `IDX_PAUSE_STATE` / `SLOT_COUNT` lines (currently 48-50) with:

```kotlin
    const val IDX_PAUSE_STATE = 27

    // v3: the ten graphics settings the Options subscreen renders, plus the
    // live display refresh rate. They ride the snapshot rather than a JNI
    // getter because CVars are an unmutexed map the game thread writes.
    const val IDX_CVAR_INTERNAL_RES = 28
    const val IDX_CVAR_MSAA = 29
    const val IDX_CVAR_FPS = 30
    const val IDX_CVAR_MATCH_HZ = 31
    const val IDX_CVAR_TEXTURE_FILTER = 32
    const val IDX_CVAR_CLOCK_TYPE = 33
    const val IDX_CVAR_BLUR_MODE = 34
    const val IDX_CVAR_BLUR_STRENGTH = 35
    const val IDX_CVAR_DRAW_DISTANCE = 36
    const val IDX_CVAR_3D_ITEM_DROPS = 37
    const val IDX_DISPLAY_REFRESH_HZ = 38

    const val SLOT_COUNT = 39
```

Add the `GameSettings` class above `GameSnapshot`:

```kotlin
/**
 * Engine graphics configuration as of this frame. Mirrors the CVars
 * mm/2s2h/BenGui/BenMenu.cpp binds its own Settings/Enhancements Graphics rows
 * to, so the two menus cannot disagree about what a row means.
 *
 * [internalResPercent] is the float CVar gSettings.InternalResolution scaled by
 * 100, so the payload stays int32 end to end.
 * [displayRefreshHz] is not a CVar -- it is the live display rate, which the
 * FPS row needs for its maximum and its chip.
 */
data class GameSettings(
    val internalResPercent: Int,
    val msaa: Int,
    val fps: Int,
    val matchRefreshRate: Boolean,
    val textureFilter: Int,
    val clockType: Int,
    val motionBlurMode: Int,
    val motionBlurStrength: Int,
    val actorDrawDistance: Int,
    val threeDItemDrops: Boolean,
    val displayRefreshHz: Int,
)
```

Add the field to `GameSnapshot`, after `menuOpen`:

```kotlin
    /** v3: engine graphics configuration, for the Options subscreen. */
    val settings: GameSettings,
```

And populate it in `decodeSnapshot`, after the `menuOpen = ...` line:

```kotlin
            settings = GameSettings(
                internalResPercent = values[GameSnapshotLayout.IDX_CVAR_INTERNAL_RES],
                msaa = values[GameSnapshotLayout.IDX_CVAR_MSAA],
                fps = values[GameSnapshotLayout.IDX_CVAR_FPS],
                matchRefreshRate = values[GameSnapshotLayout.IDX_CVAR_MATCH_HZ] != 0,
                textureFilter = values[GameSnapshotLayout.IDX_CVAR_TEXTURE_FILTER],
                clockType = values[GameSnapshotLayout.IDX_CVAR_CLOCK_TYPE],
                motionBlurMode = values[GameSnapshotLayout.IDX_CVAR_BLUR_MODE],
                motionBlurStrength = values[GameSnapshotLayout.IDX_CVAR_BLUR_STRENGTH],
                actorDrawDistance = values[GameSnapshotLayout.IDX_CVAR_DRAW_DISTANCE],
                threeDItemDrops = values[GameSnapshotLayout.IDX_CVAR_3D_ITEM_DROPS] != 0,
                displayRefreshHz = values[GameSnapshotLayout.IDX_DISPLAY_REFRESH_HZ],
            ),
```

- [ ] **Step 6: Fix every other construction site**

`GameSnapshot` gained a required field, so every test helper that builds one fails to compile. Run the full suite to find them:

Run: `sg docker -c './tools/run-unit-tests.sh'`

Expected: compilation errors listing each construction site. Add a shared default to the test helpers rather than repeating the literal — if `GameSnapshotTest.kt` or `RouteTest.kt` has a `fun snapshot(...)` factory, give it:

```kotlin
    settings: GameSettings = GameSettings(
        internalResPercent = 100, msaa = 1, fps = 20, matchRefreshRate = false,
        textureFilter = 0, clockType = 0, motionBlurMode = 0, motionBlurStrength = 180,
        actorDrawDistance = 1, threeDItemDrops = false, displayRefreshHz = 60,
    ),
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `sg docker -c './tools/run-unit-tests.sh'`

Expected: `PASS`, 108 tests (105 + 3 new).

- [ ] **Step 8: Commit — both halves together**

```bash
git add mm/2s2h/TerminaDS/GameSnapshot.h mm/2s2h/TerminaDS/SnapshotPublisher.cpp \
        Android/app/src/main/java/com/terminads/mm/GameSnapshot.kt \
        Android/app/src/test/java/com/terminads/mm/
git commit -m "feat(bridge): publish the ten graphics settings (schema v3)"
```

---

### Task 3: Three semantic mailbox opcodes

**Files:**
- Modify: `mm/2s2h/TerminaDS/CommandMailbox.h:23-38`
- Modify: `mm/2s2h/TerminaDS/CommandMailbox.cpp:1-13`, `:30-52`, `:56-63`
- Modify: `Android/app/src/main/java/com/terminads/mm/CommandBridge.kt`
- Test: `Android/app/src/test/java/com/terminads/mm/CommandBridgeTest.kt`

**Interfaces:**
- Consumes: nothing from Task 2.
- Produces: `CommandBridge.setInternalResPercent(percent: Int): SubmitStatus`, `CommandBridge.setMsaa(level: Int): SubmitStatus`, `CommandBridge.setTextureFilter(mode: Int): SubmitStatus`, and the constants `OP_SET_INTERNAL_RES = 4`, `OP_SET_MSAA = 5`, `OP_SET_TEXTURE_FILTER = 6`. Task 5 consumes all three.

Why semantic opcodes rather than a generic float write plus an apply command: the ring can drop a command, and a dropped *apply* paired with a landed *write* produces a persisted value the screen never shows — the exact silent divergence the mailbox exists to prevent. One command, one complete effect.

- [ ] **Step 1: Add the opcodes to the native header**

In `mm/2s2h/TerminaDS/CommandMailbox.h`, replace the `TdsCommandOp` enum (lines 23-30) with:

```c
enum TdsCommandOp {
    /* a: 0 = resume, nonzero = freeze. Requires a live PlayState. */
    TDS_CMD_PAUSE_SET = 1,
    /* name: CVar key; a: value. For CVars that need no engine apply call. */
    TDS_CMD_CVAR_SET_INT = 2,
    /* Persist CVars via the LUS save path. Debounced by the caller. */
    TDS_CMD_CVAR_SAVE = 3,

    /*
     * The three settings whose CVar write alone changes nothing. BenMenu
     * applies them through .Callback() (BenMenu.cpp:596-599, :621-623) and the
     * interpreter reads the CVars only at Init (interpreter.cpp:4187-4189,
     * Fast3dWindow.cpp:101-102). Each opcode below performs the CVar write AND
     * the engine apply inside one drained command, so a dropped command can
     * never leave a persisted value the screen does not show.
     */
    /* a: 50..200, the internal resolution as a percent. */
    TDS_CMD_SET_INTERNAL_RES = 4,
    /* a: 1..8, the MSAA sample count. */
    TDS_CMD_SET_MSAA = 5,
    /* a: 0..2, FILTER_THREE_POINT / FILTER_LINEAR / FILTER_NONE. */
    TDS_CMD_SET_TEXTURE_FILTER = 6
};
```

- [ ] **Step 2: Implement apply and range validation**

In `mm/2s2h/TerminaDS/CommandMailbox.cpp`, extend the include block (lines 1-13) to:

```cpp
#include "CommandMailbox.h"

#include <atomic>
#include <cstring>

#include <libultraship/libultraship.h>
#include <libultraship/bridge/consolevariablebridge.h>
#include <fast/Fast3dWindow.h>

extern "C" {
#include "z64play.h"
#include "functions.h"

extern PlayState* gPlayState;
}
```

Add these cases to `Apply()`, before the `default:` label:

```cpp
        case TDS_CMD_SET_INTERNAL_RES: {
            const float multiplier = cmd.a / 100.0f;
            CVarSetFloat(CVAR_INTERNAL_RESOLUTION, multiplier);
            Ship::Context::GetInstance()->GetWindow()->SetResolutionMultiplier(multiplier);
            break;
        }
        case TDS_CMD_SET_MSAA:
            CVarSetInteger(CVAR_MSAA_VALUE, cmd.a);
            Ship::Context::GetInstance()->GetWindow()->SetMsaaLevel(cmd.a);
            break;
        case TDS_CMD_SET_TEXTURE_FILTER: {
            CVarSetInteger(CVAR_TEXTURE_FILTER, cmd.a);
            // SetTextureFilter is the one apply call not on the abstract
            // Ship::Window (engine/include/fast/Fast3dWindow.h:54), so it needs
            // the concrete backend. This is the only place in TerminaDS that
            // knows which renderer is in use; keep it here.
            auto window = std::dynamic_pointer_cast<Fast::Fast3dWindow>(
                Ship::Context::GetInstance()->GetWindow());
            if (window != nullptr) {
                window->SetTextureFilter((Fast::FilteringMode)cmd.a);
            }
            break;
        }
```

Then replace the validation block in `TerminaDS_SubmitCommand` (lines 57-63) with:

```cpp
    if (op < TDS_CMD_PAUSE_SET || op > TDS_CMD_SET_TEXTURE_FILTER) {
        return TDS_SUBMIT_INVALID;
    }
    const bool needsName = (op == TDS_CMD_CVAR_SET_INT);
    if (needsName && (name == NULL || std::strlen(name) >= TDS_CMD_NAME_CAPACITY)) {
        return TDS_SUBMIT_INVALID;
    }
    // Range-check the semantic opcodes at submit, not at apply. An
    // out-of-range value reaching the engine would be a real fault; rejecting
    // it here means the caller sees it as a status rather than the game
    // silently taking a nonsense multiplier.
    if ((op == TDS_CMD_SET_INTERNAL_RES && (a < 50 || a > 200)) ||
        (op == TDS_CMD_SET_MSAA && (a < 1 || a > 8)) ||
        (op == TDS_CMD_SET_TEXTURE_FILTER && (a < 0 || a > 2))) {
        return TDS_SUBMIT_INVALID;
    }
```

- [ ] **Step 3: Write the failing Kotlin tests**

Add to `Android/app/src/test/java/com/terminads/mm/CommandBridgeTest.kt`:

```kotlin
    @Test
    fun opcodeValuesArePinnedToTheNativeHeader() {
        // Mirrors enum TdsCommandOp in mm/2s2h/TerminaDS/CommandMailbox.h.
        // Pinned to literals, not to each other: asserting OP_SET_MSAA ==
        // OP_SET_MSAA proves nothing, which was a Plan A review finding.
        assertEquals(1, CommandBridge.OP_PAUSE_SET)
        assertEquals(2, CommandBridge.OP_CVAR_SET_INT)
        assertEquals(3, CommandBridge.OP_CVAR_SAVE)
        assertEquals(4, CommandBridge.OP_SET_INTERNAL_RES)
        assertEquals(5, CommandBridge.OP_SET_MSAA)
        assertEquals(6, CommandBridge.OP_SET_TEXTURE_FILTER)
    }

    @Test
    fun semanticSettersSubmitTheirOpcodeAndValue() {
        val calls = mutableListOf<List<Any?>>()
        val bridge = CommandBridge { op, a, b, name ->
            calls += listOf(op, a, b, name)
            0
        }

        assertEquals(SubmitStatus.OK, bridge.setInternalResPercent(150))
        assertEquals(SubmitStatus.OK, bridge.setMsaa(4))
        assertEquals(SubmitStatus.OK, bridge.setTextureFilter(2))

        assertEquals(listOf(4, 150, 0, null), calls[0])
        assertEquals(listOf(5, 4, 0, null), calls[1])
        assertEquals(listOf(6, 2, 0, null), calls[2])
    }

    @Test
    fun semanticSettersSurfaceANonOkStatus() {
        val bridge = CommandBridge { _, _, _, _ -> 1 }
        assertEquals(SubmitStatus.FULL, bridge.setMsaa(4))
    }
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `sg docker -c "./tools/run-unit-tests.sh --tests '*CommandBridgeTest*'"`

Expected: FAIL — `Unresolved reference: OP_SET_INTERNAL_RES`.

- [ ] **Step 5: Add the Kotlin writers**

In `Android/app/src/main/java/com/terminads/mm/CommandBridge.kt`, add these methods after `saveCVars()`:

```kotlin
    /**
     * The three settings whose CVar write alone changes nothing: BenMenu
     * applies them through a Callback and the interpreter reads the CVars only
     * at Init. Native performs the write and the apply in one drained command.
     *
     * Values are range-checked natively; an out-of-range value returns INVALID
     * rather than reaching the engine.
     */
    fun setInternalResPercent(percent: Int): SubmitStatus =
        decode(submit(OP_SET_INTERNAL_RES, percent, 0, null))

    fun setMsaa(level: Int): SubmitStatus = decode(submit(OP_SET_MSAA, level, 0, null))

    fun setTextureFilter(mode: Int): SubmitStatus =
        decode(submit(OP_SET_TEXTURE_FILTER, mode, 0, null))
```

And extend the companion object:

```kotlin
        const val OP_SET_INTERNAL_RES = 4
        const val OP_SET_MSAA = 5
        const val OP_SET_TEXTURE_FILTER = 6
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `sg docker -c './tools/run-unit-tests.sh'`

Expected: `PASS`, 111 tests.

- [ ] **Step 7: Commit**

```bash
git add mm/2s2h/TerminaDS/CommandMailbox.h mm/2s2h/TerminaDS/CommandMailbox.cpp \
        Android/app/src/main/java/com/terminads/mm/CommandBridge.kt \
        Android/app/src/test/java/com/terminads/mm/CommandBridgeTest.kt
git commit -m "feat(bridge): semantic opcodes for resolution, MSAA, and texture filter"
```

---

### Task 4: Options row model

Pure Kotlin. This is the backbone the Compose tasks render and the layer that survives if Task 1's Robolectric spike failed.

**Files:**
- Create: `Android/app/src/main/java/com/terminads/mm/secondscreen/OptionsModel.kt`
- Test: `Android/app/src/test/java/com/terminads/mm/secondscreen/OptionsModelTest.kt`

**Interfaces:**
- Consumes: `GameSettings` from Task 2.
- Produces: `OptionsTab`, `OptionsCategory`, `OptionKey`, `OptionControl`, `OptionRow`, `fun optionRows(tab: OptionsTab, category: OptionsCategory, settings: GameSettings): List<OptionRow>`, `fun categoriesFor(tab: OptionsTab): List<OptionsCategory>`, `fun emptyStateFor(category: OptionsCategory): String`. Tasks 5, 7, 8, 9 consume these.

- [ ] **Step 1: Write the failing tests**

Create `Android/app/src/test/java/com/terminads/mm/secondscreen/OptionsModelTest.kt`:

```kotlin
package com.terminads.mm.secondscreen

import com.terminads.mm.GameSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionsModelTest {

    private fun settings(
        internalResPercent: Int = 100,
        msaa: Int = 1,
        fps: Int = 20,
        matchRefreshRate: Boolean = false,
        textureFilter: Int = 0,
        clockType: Int = 0,
        motionBlurMode: Int = 0,
        motionBlurStrength: Int = 180,
        actorDrawDistance: Int = 1,
        threeDItemDrops: Boolean = false,
        displayRefreshHz: Int = 60,
    ) = GameSettings(
        internalResPercent, msaa, fps, matchRefreshRate, textureFilter, clockType,
        motionBlurMode, motionBlurStrength, actorDrawDistance, threeDItemDrops,
        displayRefreshHz,
    )

    private fun settingsGraphics(s: GameSettings) =
        optionRows(OptionsTab.SETTINGS, OptionsCategory.GRAPHICS, s)

    private fun enhancementsGraphics(s: GameSettings) =
        optionRows(OptionsTab.ENHANCEMENTS, OptionsCategory.GRAPHICS, s)

    @Test
    fun bothGraphicsCategoriesCarryFiveRowsInDesignOrder() {
        assertEquals(
            listOf(
                OptionKey.INTERNAL_RES, OptionKey.MSAA, OptionKey.FPS,
                OptionKey.MATCH_HZ, OptionKey.TEXTURE_FILTER,
            ),
            settingsGraphics(settings()).map { it.key },
        )
        assertEquals(
            listOf(
                OptionKey.CLOCK_TYPE, OptionKey.BLUR_MODE, OptionKey.DRAW_DISTANCE,
                OptionKey.BLUR_STRENGTH, OptionKey.ITEM_DROPS_3D,
            ),
            enhancementsGraphics(settings()).map { it.key },
        )
    }

    @Test
    fun everyOtherCategoryHasNoRowsAndAnEmptyState() {
        for (tab in OptionsTab.entries) {
            for (category in categoriesFor(tab)) {
                if (category == OptionsCategory.GRAPHICS) continue
                assertTrue(optionRows(tab, category, settings()).isEmpty())
                assertEquals(
                    "SETTINGS FOR ${category.label} NOT DESIGNED YET",
                    emptyStateFor(category),
                )
            }
        }
    }

    @Test
    fun resolutionSliderCarriesRangeStepAndGoldReadout() {
        val row = settingsGraphics(settings(internalResPercent = 150))[0]
        val control = row.control as OptionControl.Slider
        assertEquals(150, control.value)
        assertEquals(50, control.min)
        assertEquals(200, control.max)
        assertEquals(5, control.step)
        assertEquals(100, control.defaultValue)
        assertEquals("150%", control.readout)
    }

    @Test
    fun msaaMapsFourSegmentsOntoTheEngineSampleCounts() {
        // The engine CVar is 1..8 (BenMenu.cpp:619-632); odd sample counts are
        // not universally supported, so the screen offers OFF/2x/4x/8x.
        val control = settingsGraphics(settings(msaa = 4))[1].control as OptionControl.Segmented
        assertEquals(listOf("OFF", "2×", "4×", "8×"), control.options)
        assertEquals(2, control.selectedIndex)
        assertEquals(listOf(1, 2, 4, 8), control.values)

        assertEquals(0, (settingsGraphics(settings(msaa = 1))[1].control as OptionControl.Segmented).selectedIndex)
        assertEquals(3, (settingsGraphics(settings(msaa = 8))[1].control as OptionControl.Segmented).selectedIndex)
    }

    @Test
    fun anUnrepresentableMsaaValueSelectsNothingRatherThanLying() {
        // BenMenu can set 3, 5, 6, 7. The screen must not claim one of its own
        // four segments is active when none is.
        val control = settingsGraphics(settings(msaa = 5))[1].control as OptionControl.Segmented
        assertEquals(-1, control.selectedIndex)
    }

    @Test
    fun fpsSliderMaximumAndChipComeFromTheLiveRefreshRate() {
        val row = settingsGraphics(settings(fps = 60, displayRefreshHz = 120))[2]
        val control = row.control as OptionControl.Slider
        assertEquals(20, control.min)
        assertEquals(120, control.max)
        assertEquals("MAX 120 HZ", row.chip)
    }

    @Test
    fun fpsRowGreysOutAndRewordsWhileMatchRefreshRateIsOn() {
        val on = settingsGraphics(settings(matchRefreshRate = true))[2]
        assertFalse(on.enabled)
        assertEquals("LOCKED BY MATCH REFRESH RATE", on.description)

        val off = settingsGraphics(settings(matchRefreshRate = false))[2]
        assertTrue(off.enabled)
        assertEquals(
            "CAPS THE FRAME RATE BETWEEN 20 AND THE DISPLAY MAXIMUM",
            off.description,
        )
    }

    @Test
    fun fpsShowsTheRefreshRateWhileLocked() {
        val row = settingsGraphics(settings(fps = 30, matchRefreshRate = true, displayRefreshHz = 90))[2]
        assertEquals("90", (row.control as OptionControl.Slider).readout)
    }

    @Test
    fun blurStrengthGreysOutUnlessMotionBlurIsAlwaysOn() {
        // Mirrors BenMenu.cpp:1336-1341: the CVar Strength row is meaningful
        // only in MOTION_BLUR_ALWAYS_ON (mode index 2).
        assertFalse(enhancementsGraphics(settings(motionBlurMode = 0))[3].enabled)
        assertFalse(enhancementsGraphics(settings(motionBlurMode = 1))[3].enabled)
        assertTrue(enhancementsGraphics(settings(motionBlurMode = 2))[3].enabled)
    }

    @Test
    fun blurStrengthReadsItsRealRange() {
        val control =
            enhancementsGraphics(settings(motionBlurMode = 2, motionBlurStrength = 180))[3].control
                as OptionControl.Slider
        assertEquals(0, control.min)
        assertEquals(255, control.max)
        assertEquals(5, control.step)
        assertEquals("180", control.readout)
    }

    @Test
    fun checkboxRowsReportTheirState() {
        assertTrue((settingsGraphics(settings(matchRefreshRate = true))[3].control as OptionControl.Checkbox).checked)
        assertFalse((enhancementsGraphics(settings(threeDItemDrops = false))[4].control as OptionControl.Checkbox).checked)
    }

    @Test
    fun textureFilterOffersTheEnginesThreeModesInOrder() {
        // gfx_rendering_api.h:17 -- FILTER_THREE_POINT, FILTER_LINEAR, FILTER_NONE.
        val control = settingsGraphics(settings(textureFilter = 1))[4].control as OptionControl.Segmented
        assertEquals(listOf("THREE-POINT", "LINEAR", "NONE"), control.options)
        assertEquals(1, control.selectedIndex)
    }

    @Test
    fun everyRowHasNonEmptySemanticsNamingItsStateAndControl() {
        val rows = settingsGraphics(settings()) + enhancementsGraphics(settings())
        for (row in rows) {
            assertTrue("empty semantics for ${row.key}", row.semantics.isNotBlank())
        }
        assertEquals(
            "Internal resolution, 100 percent, slider",
            settingsGraphics(settings())[0].semantics,
        )
        assertEquals(
            "Current FPS, unavailable, locked by match refresh rate",
            settingsGraphics(settings(matchRefreshRate = true))[2].semantics,
        )
    }

    @Test
    fun semanticsCarryNoPerPollValues() {
        // Accessibility rule from spec section 7: nothing that changes at the
        // poll rate may appear in a contentDescription. Settings change only on
        // interaction, so the guard is that no row mentions the clock or frame
        // counter vocabulary.
        val rows = settingsGraphics(settings()) + enhancementsGraphics(settings())
        for (row in rows) {
            assertFalse(row.semantics.contains("frame", ignoreCase = true))
            assertFalse(row.semantics.contains(":"))
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `sg docker -c "./tools/run-unit-tests.sh --tests '*OptionsModelTest*'"`

Expected: FAIL — `Unresolved reference: OptionsTab`.

- [ ] **Step 3: Write the model**

Create `Android/app/src/main/java/com/terminads/mm/secondscreen/OptionsModel.kt`:

```kotlin
package com.terminads.mm.secondscreen

import com.terminads.mm.GameSettings

/**
 * The Options subscreen's content model (handoff section 10), sorted on two
 * axes: kind (tab) and category (chip).
 *
 * Pure -- no Compose, no Android. The screen renders this; the tests assert on
 * it. Rows are derived from the schema-v3 settings block, so a dropped command
 * corrects itself on the next poll without any reconciliation logic here.
 *
 * The handoff's Enhancements/Graphics rows (Widescreen, High-Res Texture Pack,
 * Anisotropic Filtering, Post Sharpening, Draw Distance Fog) have no backing
 * CVars in 2S2H. That tab is re-sourced from the real ones in
 * mm/2s2h/BenGui/BenMenu.cpp:1299-1392, keeping the design's row anatomy and
 * control mix. See the spec's section 10 for the full deviation list.
 */
enum class OptionsTab(val label: String) {
    SETTINGS("SETTINGS"),
    ENHANCEMENTS("ENHANCEMENTS"),
}

enum class OptionsCategory(val label: String) {
    GRAPHICS("GRAPHICS"),
    AUDIO("AUDIO"),
    CONTROLS("CONTROLS"),
    SYSTEM("SYSTEM"),
    GAMEPLAY("GAMEPLAY"),
    CAMERA("CAMERA"),
    QUALITY_OF_LIFE("QUALITY OF LIFE"),
}

/** Stable identity for a row, independent of its position or label. */
enum class OptionKey {
    INTERNAL_RES, MSAA, FPS, MATCH_HZ, TEXTURE_FILTER,
    CLOCK_TYPE, BLUR_MODE, DRAW_DISTANCE, BLUR_STRENGTH, ITEM_DROPS_3D,
}

sealed interface OptionControl {
    /**
     * A 300px hairline rail with a diamond knob. [defaultValue] draws the 1px
     * tick; [readout] is the gold Cinzel numeral beside it.
     */
    data class Slider(
        val value: Int,
        val min: Int,
        val max: Int,
        val step: Int,
        val defaultValue: Int,
        val readout: String,
    ) : OptionControl

    /**
     * Underlined segmented options. [values] holds the engine value each
     * segment writes, so the screen never has to know the mapping.
     * [selectedIndex] is -1 when the live engine value is not representable by
     * any segment -- the screen shows no active underline rather than claiming
     * a wrong one.
     */
    data class Segmented(
        val options: List<String>,
        val values: List<Int>,
        val selectedIndex: Int,
    ) : OptionControl

    /** A 46px hairline square holding a 15px gold diamond when checked. */
    data class Checkbox(val checked: Boolean) : OptionControl
}

/**
 * One row of the Options list. [qualifier] is the small mono word beside the
 * label ("MSAA"); [chip] is the bordered chip ("MAX 60 HZ").
 */
data class OptionRow(
    val key: OptionKey,
    val label: String,
    val description: String,
    val control: OptionControl,
    val enabled: Boolean,
    val qualifier: String? = null,
    val chip: String? = null,
    val semantics: String,
)

fun categoriesFor(tab: OptionsTab): List<OptionsCategory> = when (tab) {
    OptionsTab.SETTINGS -> listOf(
        OptionsCategory.GRAPHICS, OptionsCategory.AUDIO,
        OptionsCategory.CONTROLS, OptionsCategory.SYSTEM,
    )
    OptionsTab.ENHANCEMENTS -> listOf(
        OptionsCategory.GRAPHICS, OptionsCategory.GAMEPLAY,
        OptionsCategory.CAMERA, OptionsCategory.QUALITY_OF_LIFE,
    )
}

fun emptyStateFor(category: OptionsCategory): String =
    "SETTINGS FOR ${category.label} NOT DESIGNED YET"

private val MSAA_VALUES = listOf(1, 2, 4, 8)
private val MSAA_LABELS = listOf("OFF", "2×", "4×", "8×")
private val TEXTURE_FILTER_LABELS = listOf("THREE-POINT", "LINEAR", "NONE")
private val CLOCK_TYPE_LABELS = listOf("ORIGINAL", "MM3D", "TEXT ONLY")
private val BLUR_MODE_LABELS = listOf("DYNAMIC", "OFF", "ALWAYS ON")
private val DRAW_DISTANCE_LABELS = listOf("1×", "2×", "3×", "4×", "5×")

/** MOTION_BLUR_ALWAYS_ON, per BenMenu.cpp:1341. */
private const val BLUR_MODE_ALWAYS_ON = 2

fun optionRows(
    tab: OptionsTab,
    category: OptionsCategory,
    settings: GameSettings,
): List<OptionRow> = when {
    category != OptionsCategory.GRAPHICS -> emptyList()
    tab == OptionsTab.SETTINGS -> settingsGraphicsRows(settings)
    else -> enhancementsGraphicsRows(settings)
}

private fun settingsGraphicsRows(s: GameSettings): List<OptionRow> {
    val fpsLocked = s.matchRefreshRate
    val fpsShown = if (fpsLocked) s.displayRefreshHz else s.fps
    return listOf(
        OptionRow(
            key = OptionKey.INTERNAL_RES,
            label = "INTERNAL RESOLUTION",
            description = "RENDERS ABOVE NATIVE, THEN DOWNSAMPLES · DEFAULT 100%",
            control = OptionControl.Slider(
                value = s.internalResPercent, min = 50, max = 200, step = 5,
                defaultValue = 100, readout = "${s.internalResPercent}%",
            ),
            enabled = true,
            semantics = "Internal resolution, ${s.internalResPercent} percent, slider",
        ),
        OptionRow(
            key = OptionKey.MSAA,
            label = "ANTI-ALIASING",
            qualifier = "MSAA",
            description = "SMOOTHS POLYGON EDGES · HIGHER LEVELS COST FILL RATE",
            control = OptionControl.Segmented(
                options = MSAA_LABELS,
                values = MSAA_VALUES,
                selectedIndex = MSAA_VALUES.indexOf(s.msaa),
            ),
            enabled = true,
            semantics = "Anti-aliasing, ${msaaSpoken(s.msaa)}, segmented control",
        ),
        OptionRow(
            key = OptionKey.FPS,
            label = "CURRENT FPS",
            chip = "MAX ${s.displayRefreshHz} HZ",
            description = if (fpsLocked) {
                "LOCKED BY MATCH REFRESH RATE"
            } else {
                "CAPS THE FRAME RATE BETWEEN 20 AND THE DISPLAY MAXIMUM"
            },
            control = OptionControl.Slider(
                value = fpsShown, min = 20, max = s.displayRefreshHz, step = 5,
                defaultValue = 20, readout = "$fpsShown",
            ),
            enabled = !fpsLocked,
            semantics = if (fpsLocked) {
                "Current FPS, unavailable, locked by match refresh rate"
            } else {
                "Current FPS, $fpsShown frames per second, slider"
            },
        ),
        OptionRow(
            key = OptionKey.MATCH_HZ,
            label = "MATCH REFRESH RATE",
            description = "FOLLOWS THE DISPLAY AND LOCKS THE FPS CAP",
            control = OptionControl.Checkbox(s.matchRefreshRate),
            enabled = true,
            semantics = "Match refresh rate, ${onOff(s.matchRefreshRate)}, checkbox",
        ),
        OptionRow(
            key = OptionKey.TEXTURE_FILTER,
            label = "TEXTURE FILTER",
            description = "THREE-POINT MATCHES THE ORIGINAL HARDWARE BLUR",
            control = OptionControl.Segmented(
                options = TEXTURE_FILTER_LABELS,
                values = listOf(0, 1, 2),
                selectedIndex = s.textureFilter.takeIf { it in 0..2 } ?: -1,
            ),
            enabled = true,
            semantics = "Texture filter, ${spoken(TEXTURE_FILTER_LABELS, s.textureFilter)}, " +
                "segmented control",
        ),
    )
}

private fun enhancementsGraphicsRows(s: GameSettings): List<OptionRow> {
    val strengthLive = s.motionBlurMode == BLUR_MODE_ALWAYS_ON
    return listOf(
        OptionRow(
            key = OptionKey.CLOCK_TYPE,
            label = "CLOCK TYPE",
            description = "SWAPS THE IN-GAME CLOCK BETWEEN ITS THREE TREATMENTS",
            control = OptionControl.Segmented(
                options = CLOCK_TYPE_LABELS,
                values = listOf(0, 1, 2),
                selectedIndex = s.clockType.takeIf { it in 0..2 } ?: -1,
            ),
            enabled = true,
            semantics = "Clock type, ${spoken(CLOCK_TYPE_LABELS, s.clockType)}, segmented control",
        ),
        OptionRow(
            key = OptionKey.BLUR_MODE,
            label = "MOTION BLUR",
            description = "DYNAMIC FOLLOWS THE ORIGINAL GAME'S OWN BLUR TRIGGERS",
            control = OptionControl.Segmented(
                options = BLUR_MODE_LABELS,
                values = listOf(0, 1, 2),
                selectedIndex = s.motionBlurMode.takeIf { it in 0..2 } ?: -1,
            ),
            enabled = true,
            semantics = "Motion blur, ${spoken(BLUR_MODE_LABELS, s.motionBlurMode)}, " +
                "segmented control",
        ),
        OptionRow(
            key = OptionKey.DRAW_DISTANCE,
            label = "ACTOR DRAW DISTANCE",
            description = "DRAWS ACTORS FARTHER OUT · MAY HAVE SIDE EFFECTS",
            control = OptionControl.Segmented(
                options = DRAW_DISTANCE_LABELS,
                values = listOf(1, 2, 3, 4, 5),
                selectedIndex = (s.actorDrawDistance - 1).takeIf { it in 0..4 } ?: -1,
            ),
            enabled = true,
            semantics = "Actor draw distance, ${s.actorDrawDistance} times, segmented control",
        ),
        OptionRow(
            key = OptionKey.BLUR_STRENGTH,
            label = "MOTION BLUR STRENGTH",
            description = if (strengthLive) {
                "HOW MUCH OF THE PREVIOUS FRAME PERSISTS · 0 TO 255"
            } else {
                "LOCKED UNLESS MOTION BLUR IS ALWAYS ON"
            },
            control = OptionControl.Slider(
                value = s.motionBlurStrength, min = 0, max = 255, step = 5,
                defaultValue = 180, readout = "${s.motionBlurStrength}",
            ),
            enabled = strengthLive,
            semantics = if (strengthLive) {
                "Motion blur strength, ${s.motionBlurStrength} of 255, slider"
            } else {
                "Motion blur strength, unavailable, locked unless motion blur is always on"
            },
        ),
        OptionRow(
            key = OptionKey.ITEM_DROPS_3D,
            label = "3D ITEM DROPS",
            description = "DRAWS DROPPED ITEMS AS MODELS INSTEAD OF FLAT SPRITES",
            control = OptionControl.Checkbox(s.threeDItemDrops),
            enabled = true,
            semantics = "3D item drops, ${onOff(s.threeDItemDrops)}, checkbox",
        ),
    )
}

private fun onOff(value: Boolean) = if (value) "on" else "off"

private fun spoken(labels: List<String>, index: Int): String =
    labels.getOrNull(index)?.lowercase() ?: "unknown"

private fun msaaSpoken(value: Int): String =
    if (value == 1) "off" else "$value times"
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `sg docker -c './tools/run-unit-tests.sh'`

Expected: `PASS`, 125 tests (111 + 14).

- [ ] **Step 5: Commit**

```bash
git add Android/app/src/main/java/com/terminads/mm/secondscreen/OptionsModel.kt \
        Android/app/src/test/java/com/terminads/mm/secondscreen/OptionsModelTest.kt
git commit -m "feat(secondscreen): Options row model on real BenMenu CVars"
```

---

### Task 5: Options command mapping and save debounce

**Files:**
- Create: `Android/app/src/main/java/com/terminads/mm/secondscreen/OptionsCommands.kt`
- Test: `Android/app/src/test/java/com/terminads/mm/secondscreen/OptionsCommandsTest.kt`

**Interfaces:**
- Consumes: `OptionKey`, `OptionControl` (Task 4); `CommandBridge` and its three semantic setters (Task 3).
- Produces: `fun submitOptionChange(bridge: CommandBridge, key: OptionKey, value: Int): SubmitStatus`, `fun quantize(value: Int, min: Int, max: Int, step: Int): Int`, `fun nextSegmentValue(control: OptionControl.Segmented, delta: Int): Int`, `class CVarSaveDebouncer(windowMillis: Long = 2_000L)` with `fun noteChange(nowMillis: Long)`, `fun dueAt(): Long?`, `fun fire(nowMillis: Long): Boolean`. Task 9 consumes all of these.

- [ ] **Step 1: Write the failing tests**

Create `Android/app/src/test/java/com/terminads/mm/secondscreen/OptionsCommandsTest.kt`:

```kotlin
package com.terminads.mm.secondscreen

import com.terminads.mm.CommandBridge
import com.terminads.mm.SubmitStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionsCommandsTest {

    private val calls = mutableListOf<List<Any?>>()
    private val bridge = CommandBridge { op, a, b, name ->
        calls += listOf(op, a, b, name)
        0
    }

    @Test
    fun theThreeApplyRowsUseTheirSemanticOpcodes() {
        submitOptionChange(bridge, OptionKey.INTERNAL_RES, 150)
        submitOptionChange(bridge, OptionKey.MSAA, 4)
        submitOptionChange(bridge, OptionKey.TEXTURE_FILTER, 1)

        assertEquals(listOf(CommandBridge.OP_SET_INTERNAL_RES, 150, 0, null), calls[0])
        assertEquals(listOf(CommandBridge.OP_SET_MSAA, 4, 0, null), calls[1])
        assertEquals(listOf(CommandBridge.OP_SET_TEXTURE_FILTER, 1, 0, null), calls[2])
    }

    @Test
    fun everyOtherRowWritesItsCVarByName() {
        submitOptionChange(bridge, OptionKey.MATCH_HZ, 1)
        submitOptionChange(bridge, OptionKey.CLOCK_TYPE, 2)
        submitOptionChange(bridge, OptionKey.BLUR_MODE, 2)
        submitOptionChange(bridge, OptionKey.BLUR_STRENGTH, 200)
        submitOptionChange(bridge, OptionKey.DRAW_DISTANCE, 3)
        submitOptionChange(bridge, OptionKey.ITEM_DROPS_3D, 1)

        assertEquals(listOf(CommandBridge.OP_CVAR_SET_INT, 1, 0, "gMatchRefreshRate"), calls[0])
        assertEquals(
            listOf(CommandBridge.OP_CVAR_SET_INT, 2, 0, "gEnhancements.Graphics.ClockType"),
            calls[1],
        )
        assertEquals(
            listOf(CommandBridge.OP_CVAR_SET_INT, 2, 0, "gEnhancements.Graphics.MotionBlur.Mode"),
            calls[2],
        )
        assertEquals(
            listOf(
                CommandBridge.OP_CVAR_SET_INT, 200, 0,
                "gEnhancements.Graphics.MotionBlur.Strength",
            ),
            calls[3],
        )
        assertEquals(
            listOf(
                CommandBridge.OP_CVAR_SET_INT, 3, 0,
                "gEnhancements.Graphics.IncreaseActorDrawDistance",
            ),
            calls[4],
        )
        assertEquals(
            listOf(CommandBridge.OP_CVAR_SET_INT, 1, 0, "gEnhancements.Graphics.3DItemDrops"),
            calls[5],
        )
    }

    @Test
    fun aNonOkStatusIsReturnedNotSwallowed() {
        val full = CommandBridge { _, _, _, _ -> 1 }
        assertEquals(SubmitStatus.FULL, submitOptionChange(full, OptionKey.MSAA, 4))
    }

    @Test
    fun quantizeSnapsToTheStepAndClampsToTheRange() {
        assertEquals(100, quantize(102, min = 50, max = 200, step = 5))
        assertEquals(105, quantize(103, min = 50, max = 200, step = 5))
        assertEquals(50, quantize(10, min = 50, max = 200, step = 5))
        assertEquals(200, quantize(999, min = 50, max = 200, step = 5))
    }

    @Test
    fun quantizeKeepsTheMaximumReachableWhenItIsOffStep() {
        // A 90 Hz display gives max=90 with step 5; 90 is on-step, but a 144 Hz
        // one gives max=144, which is not. The maximum must stay selectable.
        assertEquals(144, quantize(144, min = 20, max = 144, step = 5))
        assertEquals(144, quantize(143, min = 20, max = 144, step = 5))
        assertEquals(140, quantize(141, min = 20, max = 144, step = 5))
    }

    @Test
    fun segmentSteppingWrapsInBothDirections() {
        val control = OptionControl.Segmented(
            options = listOf("OFF", "2×", "4×", "8×"),
            values = listOf(1, 2, 4, 8),
            selectedIndex = 3,
        )
        assertEquals(1, nextSegmentValue(control, delta = 1))
        assertEquals(4, nextSegmentValue(control, delta = -1))
    }

    @Test
    fun segmentSteppingFromAnUnrepresentableValueLandsOnTheFirstSegment() {
        val control = OptionControl.Segmented(
            options = listOf("OFF", "2×"), values = listOf(1, 2), selectedIndex = -1,
        )
        assertEquals(1, nextSegmentValue(control, delta = 1))
        assertEquals(1, nextSegmentValue(control, delta = -1))
    }

    @Test
    fun debouncerFiresOnlyAfterTheQuietWindow() {
        val d = CVarSaveDebouncer(windowMillis = 2_000L)
        assertNull(d.dueAt())

        d.noteChange(nowMillis = 1_000L)
        assertEquals(3_000L, d.dueAt())
        assertFalse(d.fire(nowMillis = 2_999L))
        assertTrue(d.fire(nowMillis = 3_000L))
    }

    @Test
    fun eachChangeRestartsTheWindow() {
        val d = CVarSaveDebouncer(windowMillis = 2_000L)
        d.noteChange(nowMillis = 1_000L)
        d.noteChange(nowMillis = 2_500L)
        assertEquals(4_500L, d.dueAt())
        assertFalse(d.fire(nowMillis = 3_000L))
        assertTrue(d.fire(nowMillis = 4_500L))
    }

    @Test
    fun firingClearsThePendingSaveSoItDoesNotRepeat() {
        val d = CVarSaveDebouncer(windowMillis = 2_000L)
        d.noteChange(nowMillis = 0L)
        assertTrue(d.fire(nowMillis = 2_000L))
        assertNull(d.dueAt())
        assertFalse(d.fire(nowMillis = 9_000L))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `sg docker -c "./tools/run-unit-tests.sh --tests '*OptionsCommandsTest*'"`

Expected: FAIL — `Unresolved reference: submitOptionChange`.

- [ ] **Step 3: Write the implementation**

Create `Android/app/src/main/java/com/terminads/mm/secondscreen/OptionsCommands.kt`:

```kotlin
package com.terminads.mm.secondscreen

import com.terminads.mm.CommandBridge
import com.terminads.mm.SubmitStatus

/**
 * Turns an Options interaction into exactly one absolute command.
 *
 * Three rows use semantic opcodes because their CVar write alone applies
 * nothing (see CommandMailbox.h). The rest write their CVar by name. Nothing
 * here reads state back: the next snapshot is the acknowledgement, exactly as
 * pause works.
 */

/** CVar names, copied from mm/2s2h/BenGui/BenMenu.cpp so the menus agree. */
private val CVAR_NAMES = mapOf(
    OptionKey.MATCH_HZ to "gMatchRefreshRate",
    OptionKey.CLOCK_TYPE to "gEnhancements.Graphics.ClockType",
    OptionKey.BLUR_MODE to "gEnhancements.Graphics.MotionBlur.Mode",
    OptionKey.BLUR_STRENGTH to "gEnhancements.Graphics.MotionBlur.Strength",
    OptionKey.DRAW_DISTANCE to "gEnhancements.Graphics.IncreaseActorDrawDistance",
    OptionKey.ITEM_DROPS_3D to "gEnhancements.Graphics.3DItemDrops",
)

fun submitOptionChange(
    bridge: CommandBridge,
    key: OptionKey,
    value: Int,
): SubmitStatus = when (key) {
    OptionKey.INTERNAL_RES -> bridge.setInternalResPercent(value)
    OptionKey.MSAA -> bridge.setMsaa(value)
    OptionKey.TEXTURE_FILTER -> bridge.setTextureFilter(value)
    else -> bridge.setCVarInt(
        // Every non-semantic key is in the map; a miss is a programming error,
        // not a runtime condition, so fail loudly rather than writing "null".
        requireNotNull(CVAR_NAMES[key]) { "no CVar name for $key" },
        value,
    )
}

/**
 * Snap a slider value to its step and clamp it to the range.
 *
 * [max] stays reachable even when it is not on-step: a 144 Hz display gives the
 * FPS row max=144 with step 5, and the player must be able to select the
 * display's actual maximum.
 */
fun quantize(value: Int, min: Int, max: Int, step: Int): Int {
    if (value >= max) return max
    if (value <= min) return min
    val snapped = min + ((value - min + step / 2) / step) * step
    return snapped.coerceIn(min, max)
}

/**
 * The engine value one segment away, wrapping. A control whose live value is
 * not representable (selectedIndex -1) steps to the first segment in either
 * direction rather than guessing where the player meant to be.
 */
fun nextSegmentValue(control: OptionControl.Segmented, delta: Int): Int {
    if (control.selectedIndex !in control.values.indices) return control.values.first()
    val n = control.values.size
    return control.values[((control.selectedIndex + delta) % n + n) % n]
}

/**
 * Persists CVars 2 s after the last change, so dragging a slider does not write
 * the config file on every frame.
 *
 * Time is passed in rather than read, so the tests are deterministic and the
 * caller keeps its single source of "now".
 */
class CVarSaveDebouncer(private val windowMillis: Long = 2_000L) {
    private var dueAtMillis: Long? = null

    fun noteChange(nowMillis: Long) {
        dueAtMillis = nowMillis + windowMillis
    }

    fun dueAt(): Long? = dueAtMillis

    /** True exactly once per quiet window, when it has elapsed. */
    fun fire(nowMillis: Long): Boolean {
        val due = dueAtMillis ?: return false
        if (nowMillis < due) return false
        dueAtMillis = null
        return true
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `sg docker -c './tools/run-unit-tests.sh'`

Expected: `PASS`, 136 tests (125 + 11).

- [ ] **Step 5: Commit**

```bash
git add Android/app/src/main/java/com/terminads/mm/secondscreen/OptionsCommands.kt \
        Android/app/src/test/java/com/terminads/mm/secondscreen/OptionsCommandsTest.kt
git commit -m "feat(secondscreen): Options command mapping and CVar save debounce"
```

---

### Task 6: Pause root menu — full §5 styling

**Files:**
- Modify: `Android/app/src/main/java/com/terminads/mm/secondscreen/TerminaDesign.kt:107-120` (type roles), `:38-65` (colors)
- Modify: `Android/app/src/main/java/com/terminads/mm/secondscreen/PauseMenuScreen.kt` (full rewrite)
- Test: `Android/app/src/test/java/com/terminads/mm/secondscreen/PauseMenuScreenTest.kt`

**Interfaces:**
- Consumes: `HudModel` (existing), `SceneNames` (existing).
- Produces: `PauseMenuScreen(model, resumePending, resumeFailed, onResumeTap, onOptionsTap)` — Task 9 calls it with the new `onOptionsTap` parameter. Also `pauseMenuRows(model, resumePending): List<PauseMenuRow>` as the pure structure Task 9 and the tests read.

- [ ] **Step 1: Write the failing tests**

Create `Android/app/src/test/java/com/terminads/mm/secondscreen/PauseMenuScreenTest.kt`:

```kotlin
package com.terminads.mm.secondscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseMenuScreenTest {

    private fun model(areaName: String = "TERMINA FIELD") = HudModel(
        fullHearts = 8, partialSixteenths = 0, totalHearts = 10, doubleDefense = false,
        magicPct = 62, rupees = 218, dayLabel = "DAY 1", clockTime = "7:40",
        clockSuffix = "AM", hoursChip = "60 H", areaName = areaName,
    )

    @Test
    fun theFiveDesignRowsAppearInOrder() {
        assertEquals(
            listOf("RESUME", "INVENTORY", "MAP", "SONG OF TIME", "OPTIONS"),
            pauseMenuRows(model(), resumePending = false).map { it.label },
        )
    }

    @Test
    fun onlyResumeAndOptionsAreLive() {
        val rows = pauseMenuRows(model(), resumePending = false).associateBy { it.label }
        assertTrue(rows.getValue("RESUME").enabled)
        assertTrue(rows.getValue("OPTIONS").enabled)
        assertFalse(rows.getValue("INVENTORY").enabled)
        assertFalse(rows.getValue("MAP").enabled)
        assertFalse(rows.getValue("SONG OF TIME").enabled)
    }

    @Test
    fun subLinesAppearOnLiveRowsOnly() {
        val rows = pauseMenuRows(model(), resumePending = false).associateBy { it.label }
        // The handoff's "AUTOSAVED 4 MIN AGO" clause is dropped: no autosave
        // data exists (spec section 10, deviation 7).
        assertEquals("TERMINA FIELD", rows.getValue("RESUME").subLine)
        assertEquals("RESOLUTION · MSAA · FRAME RATE", rows.getValue("OPTIONS").subLine)
        assertNull(rows.getValue("INVENTORY").subLine)
        assertNull(rows.getValue("MAP").subLine)
        assertNull(rows.getValue("SONG OF TIME").subLine)
    }

    @Test
    fun songOfTimeKeepsItsWarmTreatmentEvenWhileInert() {
        val row = pauseMenuRows(model(), resumePending = false).single { it.label == "SONG OF TIME" }
        assertTrue(row.warm)
        assertFalse(row.enabled)
    }

    @Test
    fun inertRowsAnnounceThemselvesAsFutureWork() {
        val rows = pauseMenuRows(model(), resumePending = false).associateBy { it.label }
        assertEquals(
            "Inventory, available in a future update",
            rows.getValue("INVENTORY").semantics,
        )
        assertEquals("Resume the game", rows.getValue("RESUME").semantics)
        assertEquals("Options", rows.getValue("OPTIONS").semantics)
    }

    @Test
    fun resumeReportsPendingWithoutBecomingUnavailable() {
        val row = pauseMenuRows(model(), resumePending = true).first()
        assertTrue(row.pending)
        assertFalse(row.enabled)
    }

    @Test
    fun theSubLineFollowsTheSceneName() {
        val rows = pauseMenuRows(model(areaName = "CLOCK TOWN"), resumePending = false)
        assertEquals("CLOCK TOWN", rows.first().subLine)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `sg docker -c "./tools/run-unit-tests.sh --tests '*PauseMenuScreenTest*'"`

Expected: FAIL — `Unresolved reference: pauseMenuRows`.

- [ ] **Step 3: Add the new design tokens**

In `TerminaDesign.kt`, add to `TerminaColors`:

```kotlin
    val MenuRowInk = Color(0xFFE7DCFA)      // selected pause-menu row
    val MenuRowInert = Color(0xFF6B6380)    // unselected pause-menu row
    val SongOfTimeInert = Color(0xFF8A7647) // the one warm row, unselected
    val HairlineFaint = Color(0x1FB48CE8)   // rgba(180,140,232,.12) row rules
    val HairlineStrong = Color(0x75B48CE8)  // rgba(180,140,232,.46) selected rule
```

And to `TerminaType`:

```kotlin
    val MenuRow = DesignTextSpec(TerminaFonts.Cinzel, FontWeight.Bold, 42f, 7f)
    val MenuSubLine = DesignTextSpec(TerminaFonts.ChivoMono, FontWeight.Medium, 14f, 4f)
    val SubscreenTitle = DesignTextSpec(TerminaFonts.Cinzel, FontWeight.Bold, 30f, 5f)
    val SubscreenTab = DesignTextSpec(TerminaFonts.Cinzel, FontWeight.Bold, 27f, 6f)
    val CategoryChip = DesignTextSpec(TerminaFonts.ChivoMono, FontWeight.Bold, 14f, 4f)
    val OptionLabel = DesignTextSpec(TerminaFonts.Cinzel, FontWeight.Bold, 30f, 4f)
    val OptionDescription = DesignTextSpec(TerminaFonts.ChivoMono, FontWeight.Medium, 13f, 2.5f)
    val OptionReadout = DesignTextSpec(TerminaFonts.Cinzel, FontWeight.Bold, 34f, 1f)
    val OptionSegment = DesignTextSpec(TerminaFonts.Cinzel, FontWeight.Bold, 23f, 3f)
    val FooterHint = DesignTextSpec(TerminaFonts.ChivoMono, FontWeight.Medium, 13f, 3f)
```

- [ ] **Step 4: Rewrite the pause menu screen**

Replace the whole of `Android/app/src/main/java/com/terminads/mm/secondscreen/PauseMenuScreen.kt`:

```kotlin
package com.terminads.mm.secondscreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics

/**
 * The pause root menu (handoff section 5).
 *
 * RESUME and OPTIONS are live; INVENTORY, MAP and SONG OF TIME render in the
 * unselected ink with transparent diamonds and disabled semantics until later
 * phases mount them. SONG OF TIME keeps its warm gold treatment throughout --
 * it is the one row the design colours differently in both states.
 */
data class PauseMenuRow(
    val label: String,
    val subLine: String?,
    val enabled: Boolean,
    val pending: Boolean,
    val warm: Boolean,
    val semantics: String,
)

/**
 * The menu's structure, separated from its rendering so the row set, the
 * enabled/disabled split and the semantics are testable without Compose. This
 * is the layer that would have caught the Phase 3 nav bug at build time.
 */
fun pauseMenuRows(model: HudModel, resumePending: Boolean): List<PauseMenuRow> = listOf(
    PauseMenuRow(
        label = "RESUME",
        subLine = model.areaName,
        enabled = !resumePending,
        pending = resumePending,
        warm = false,
        semantics = "Resume the game",
    ),
    PauseMenuRow(
        label = "INVENTORY", subLine = null, enabled = false, pending = false, warm = false,
        semantics = "Inventory, available in a future update",
    ),
    PauseMenuRow(
        label = "MAP", subLine = null, enabled = false, pending = false, warm = false,
        semantics = "Map, available in a future update",
    ),
    PauseMenuRow(
        label = "SONG OF TIME", subLine = null, enabled = false, pending = false, warm = true,
        semantics = "Song of Time, available in a future update",
    ),
    PauseMenuRow(
        label = "OPTIONS",
        subLine = "RESOLUTION · MSAA · FRAME RATE",
        enabled = true, pending = false, warm = false,
        semantics = "Options",
    ),
)

@Composable
fun PauseMenuScreen(
    model: HudModel,
    resumePending: Boolean,
    resumeFailed: Boolean,
    onResumeTap: () -> Unit,
    onOptionsTap: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Clock line, top:34px.
        Row(
            Modifier.padding(top = du(34f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${model.dayLabel ?: ""} · ${model.clockTime} ${model.clockSuffix}",
                style = TerminaType.IdleCaption.toStyle(TerminaColors.TextHint),
            )
            model.hoursChip?.let {
                Text(
                    "  |  $it LEFT",
                    style = TerminaType.IdleCaption.toStyle(TerminaColors.TextHint),
                )
            }
        }

        Column(
            Modifier
                .padding(top = du(40f))
                .width(du(760f)),
            verticalArrangement = Arrangement.spacedBy(du(4f)),
        ) {
            for (row in pauseMenuRows(model, resumePending)) {
                MenuRow(
                    row = row,
                    onTap = when (row.label) {
                        "RESUME" -> onResumeTap
                        "OPTIONS" -> onOptionsTap
                        else -> ({})
                    },
                )
            }
        }

        if (resumeFailed) {
            Text(
                "RESUME FAILED",
                style = TerminaType.StallChip.toStyle(TerminaColors.GoldDim),
                modifier = Modifier
                    .padding(top = du(12f))
                    .semantics { contentDescription = "Resume failed" },
            )
        }

        Box(Modifier.weight(1f))

        Text(
            "TAP A ROW · RESUME RETURNS TO THE GAME",
            style = TerminaType.FooterHint.toStyle(TerminaColors.TextDimmest),
            modifier = Modifier.padding(bottom = du(38f)),
        )
    }
}

@Composable
private fun MenuRow(row: PauseMenuRow, onTap: () -> Unit) {
    val ink = when {
        row.pending -> TerminaColors.GoldDim
        row.warm && row.enabled -> TerminaColors.GoldLight
        row.warm -> TerminaColors.SongOfTimeInert
        row.enabled -> TerminaColors.MenuRowInk
        else -> TerminaColors.MenuRowInert
    }
    val glow = if (row.enabled && !row.pending) {
        Shadow(
            color = if (row.warm) TerminaColors.Gold else TerminaColors.Accent,
            offset = Offset.Zero,
            blurRadius = dupx(26f),
        )
    } else {
        null
    }
    val diamondColor = when {
        !row.enabled -> Color.Transparent
        row.warm -> TerminaColors.Gold
        else -> TerminaColors.Accent
    }

    Column(
        Modifier
            .defaultMinSize(minHeight = du(106f))
            .clickable(enabled = row.enabled, onClick = onTap)
            .semantics {
                contentDescription = row.semantics
                if (!row.enabled) disabled()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(du(24f)),
        ) {
            BreathingDiamond(color = diamondColor)
            Text(row.label, style = TerminaType.MenuRow.toStyle(ink, glow))
            BreathingDiamond(color = diamondColor)
        }
        row.subLine?.let {
            Text(
                it,
                style = TerminaType.MenuSubLine.toStyle(TerminaColors.ClockDim),
                modifier = Modifier.padding(top = du(6f)),
            )
        }
    }
}
```

- [ ] **Step 5: Fix the existing call site so the project compiles**

`SecondScreenHost.kt` now misses `onOptionsTap`. Add a temporary no-op — Task 9 replaces it:

```kotlin
                onOptionsTap = {},
```

- [ ] **Step 6: Add the Robolectric smoke test**

Append to `PauseMenuScreenTest.kt` — **skip this step if Task 1's spike failed**:

```kotlin
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class PauseMenuScreenRenderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val model = HudModel(
        fullHearts = 8, partialSixteenths = 0, totalHearts = 10, doubleDefense = false,
        magicPct = 62, rupees = 218, dayLabel = "DAY 1", clockTime = "7:40",
        clockSuffix = "AM", hoursChip = "60 H", areaName = "TERMINA FIELD",
    )

    @Test
    fun allFiveRowsRenderAndOnlyTheLiveOnesRespondToTaps() {
        var resumed = false
        var options = false
        composeTestRule.setContent {
            DesignRoot {
                PauseMenuScreen(
                    model = model, resumePending = false, resumeFailed = false,
                    onResumeTap = { resumed = true }, onOptionsTap = { options = true },
                )
            }
        }

        for (label in listOf("RESUME", "INVENTORY", "MAP", "SONG OF TIME", "OPTIONS")) {
            composeTestRule.onNodeWithText(label).assertExists()
        }

        composeTestRule.onNodeWithContentDescription("Options").performClick()
        assertTrue(options)

        composeTestRule.onNodeWithContentDescription("Resume the game").performClick()
        assertTrue(resumed)
    }

    @Test
    fun inertRowsAreMarkedUnavailableToAccessibility() {
        composeTestRule.setContent {
            DesignRoot {
                PauseMenuScreen(
                    model = model, resumePending = false, resumeFailed = false,
                    onResumeTap = {}, onOptionsTap = {},
                )
            }
        }
        composeTestRule
            .onNodeWithContentDescription("Inventory, available in a future update")
            .assertIsNotEnabled()
    }
}
```

Add these imports at the top of the file:

```kotlin
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `sg docker -c './tools/run-unit-tests.sh'`

Expected: `PASS`, 145 tests (136 + 7 + 2).

- [ ] **Step 8: Commit**

```bash
git add Android/app/src/main/java/com/terminads/mm/secondscreen/PauseMenuScreen.kt \
        Android/app/src/main/java/com/terminads/mm/secondscreen/TerminaDesign.kt \
        Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenHost.kt \
        Android/app/src/test/java/com/terminads/mm/secondscreen/PauseMenuScreenTest.kt
git commit -m "feat(secondscreen): full pause root-menu styling"
```

---

### Task 7: Options subscreen chrome

Header, footer, tabs, category chips, empty state. No rows yet — Task 8 fills the body.

**Files:**
- Create: `Android/app/src/main/java/com/terminads/mm/secondscreen/OptionsScreen.kt`
- Test: `Android/app/src/test/java/com/terminads/mm/secondscreen/OptionsScreenTest.kt`

**Interfaces:**
- Consumes: `OptionsTab`, `OptionsCategory`, `categoriesFor`, `emptyStateFor`, `optionRows` (Task 4); `HudModel` (existing).
- Produces: `OptionsScreen(model, settings, tab, category, selectedKey, onTabSelect, onCategorySelect, onRowSelect, onRowChange, onBack, onResume)`. Task 9 calls it.

- [ ] **Step 1: Write the failing tests**

Create `Android/app/src/test/java/com/terminads/mm/secondscreen/OptionsScreenTest.kt`:

```kotlin
package com.terminads.mm.secondscreen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.terminads.mm.GameSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class OptionsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val model = HudModel(
        fullHearts = 8, partialSixteenths = 0, totalHearts = 10, doubleDefense = false,
        magicPct = 62, rupees = 218, dayLabel = "DAY 1", clockTime = "7:40",
        clockSuffix = "AM", hoursChip = "60 H", areaName = "TERMINA FIELD",
    )

    private val settings = GameSettings(
        internalResPercent = 100, msaa = 1, fps = 20, matchRefreshRate = false,
        textureFilter = 0, clockType = 0, motionBlurMode = 0, motionBlurStrength = 180,
        actorDrawDistance = 1, threeDItemDrops = false, displayRefreshHz = 60,
    )

    private fun show(
        tab: OptionsTab = OptionsTab.SETTINGS,
        category: OptionsCategory = OptionsCategory.GRAPHICS,
        onTabSelect: (OptionsTab) -> Unit = {},
        onCategorySelect: (OptionsCategory) -> Unit = {},
        onBack: () -> Unit = {},
        onResume: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            DesignRoot {
                OptionsScreen(
                    model = model, settings = settings, tab = tab, category = category,
                    selectedKey = null, onTabSelect = onTabSelect,
                    onCategorySelect = onCategorySelect, onRowSelect = {},
                    onRowChange = { _, _ -> }, onBack = onBack, onResume = onResume,
                )
            }
        }
    }

    @Test
    fun headerCarriesTitlePausedChipAndClock() {
        show()
        composeTestRule.onNodeWithText("OPTIONS").assertIsDisplayed()
        composeTestRule.onNodeWithText("PAUSED").assertIsDisplayed()
        composeTestRule.onNodeWithText("DAY 1 · 7:40 AM").assertIsDisplayed()
        composeTestRule.onNodeWithText("60 H").assertIsDisplayed()
    }

    @Test
    fun bothTabsRenderAndEmitOnTap() {
        var picked: OptionsTab? = null
        show(onTabSelect = { picked = it })
        composeTestRule.onNodeWithText("SETTINGS").assertIsDisplayed()
        composeTestRule.onNodeWithText("ENHANCEMENTS").performClick()
        assertEquals(OptionsTab.ENHANCEMENTS, picked)
    }

    @Test
    fun settingsTabShowsItsFourCategoryChips() {
        show(tab = OptionsTab.SETTINGS)
        for (label in listOf("GRAPHICS", "AUDIO", "CONTROLS", "SYSTEM")) {
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun enhancementsTabShowsItsOwnFourCategoryChips() {
        show(tab = OptionsTab.ENHANCEMENTS, category = OptionsCategory.GAMEPLAY)
        for (label in listOf("GRAPHICS", "GAMEPLAY", "CAMERA", "QUALITY OF LIFE")) {
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun aNonGraphicsCategoryShowsItsDesignedEmptyState() {
        show(category = OptionsCategory.AUDIO)
        composeTestRule.onNodeWithText("SETTINGS FOR AUDIO NOT DESIGNED YET").assertIsDisplayed()
    }

    @Test
    fun categoryChipsEmitOnTap() {
        var picked: OptionsCategory? = null
        show(onCategorySelect = { picked = it })
        composeTestRule.onNodeWithText("CONTROLS").performClick()
        assertEquals(OptionsCategory.CONTROLS, picked)
    }

    @Test
    fun backChevronAndResumePlayEmit() {
        var backed = false
        var resumed = false
        show(onBack = { backed = true }, onResume = { resumed = true })

        composeTestRule.onNodeWithContentDescription("Back to the pause menu").performClick()
        assert(backed)

        composeTestRule.onNodeWithText("RESUME PLAY").performClick()
        assert(resumed)
    }

    @Test
    fun footerHintUsesTouchVocabularyNotControllerGlyphs() {
        show()
        composeTestRule.onNodeWithText("TAP A ROW TO ADJUST IT").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `sg docker -c "./tools/run-unit-tests.sh --tests '*OptionsScreenTest*'"`

Expected: FAIL — `Unresolved reference: OptionsScreen`.

- [ ] **Step 3: Write the chrome**

Create `Android/app/src/main/java/com/terminads/mm/secondscreen/OptionsScreen.kt`:

```kotlin
package com.terminads.mm.secondscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.terminads.mm.GameSettings

/**
 * The Options subscreen: handoff section 6 chrome around section 10 content.
 *
 * Both Graphics categories carry real rows; the other six show the designed
 * empty state rather than filler. Local navigation (tab, category, selected
 * row) is host state -- only pause itself is game truth.
 */
@Composable
fun OptionsScreen(
    model: HudModel,
    settings: GameSettings,
    tab: OptionsTab,
    category: OptionsCategory,
    selectedKey: OptionKey?,
    onTabSelect: (OptionsTab) -> Unit,
    onCategorySelect: (OptionsCategory) -> Unit,
    onRowSelect: (OptionKey) -> Unit,
    onRowChange: (OptionKey, Int) -> Unit,
    onBack: () -> Unit,
    onResume: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OptionsHeader(model, onBack)
        OptionsTabs(tab, onTabSelect)
        CategoryChips(categoriesFor(tab), category, onCategorySelect)

        val rows = optionRows(tab, category, settings)
        if (rows.isEmpty()) {
            EmptyState(category)
        } else {
            OptionRowList(
                rows = rows,
                selectedKey = selectedKey,
                onRowSelect = onRowSelect,
                onRowChange = onRowChange,
                modifier = Modifier.weight(1f),
            )
        }

        OptionsFooter(onResume)
    }
}

@Composable
private fun OptionsHeader(model: HudModel, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(du(96f))
            .padding(horizontal = du(40f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(du(20f)),
    ) {
        Text(
            "‹",
            style = TerminaType.SubscreenTitle.toStyle(TerminaColors.InkMuted),
            modifier = Modifier
                .clickable(onClick = onBack)
                .semantics { contentDescription = "Back to the pause menu" },
        )
        Text("OPTIONS", style = TerminaType.SubscreenTitle.toStyle(TerminaColors.Ink3))
        Box(Modifier.weight(1f))
        PausedChip()
        Text(
            "${model.dayLabel ?: ""} · ${model.clockTime} ${model.clockSuffix}",
            style = TerminaType.IdleCaption.toStyle(TerminaColors.ClockDim),
        )
        model.hoursChip?.let { HoursChip(it) }
    }
    Hairline()
}

@Composable
private fun PausedChip() {
    Row(
        Modifier
            .border(du(1f), TerminaColors.HairlineStrong, RoundedCornerShape(du(7f)))
            .padding(horizontal = du(11f), vertical = du(4f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(du(6f)),
    ) {
        BreathingDiamond(sizePx = 7f, color = TerminaColors.AccentBright)
        Text("PAUSED", style = TerminaType.CountdownChip.toStyle(TerminaColors.AccentBright))
    }
}

@Composable
private fun HoursChip(text: String) {
    Text(
        text,
        style = TerminaType.CountdownChip.toStyle(TerminaColors.AccentLight),
        modifier = Modifier
            .border(du(1f), TerminaColors.ChipBorder, RoundedCornerShape(du(7f)))
            .padding(horizontal = du(10f), vertical = du(3f)),
    )
}

@Composable
private fun OptionsTabs(tab: OptionsTab, onSelect: (OptionsTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = du(22f), start = du(44f), end = du(44f)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UnderlinedLabel(
            text = OptionsTab.SETTINGS.label,
            active = tab == OptionsTab.SETTINGS,
            spec = TerminaType.SubscreenTab,
            onTap = { onSelect(OptionsTab.SETTINGS) },
        )
        Box(Modifier.padding(horizontal = du(24f))) {
            Box(
                Modifier
                    .size(du(8f))
                    .background(TerminaColors.HairlineStrong, RoundedCornerShape(du(2f))),
            )
        }
        UnderlinedLabel(
            text = OptionsTab.ENHANCEMENTS.label,
            active = tab == OptionsTab.ENHANCEMENTS,
            spec = TerminaType.SubscreenTab,
            onTap = { onSelect(OptionsTab.ENHANCEMENTS) },
        )
    }
}

@Composable
private fun CategoryChips(
    categories: List<OptionsCategory>,
    active: OptionsCategory,
    onSelect: (OptionsCategory) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = du(14f), start = du(44f), end = du(44f), bottom = du(6f)),
        horizontalArrangement = Arrangement.spacedBy(du(28f), Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (category in categories) {
            UnderlinedLabel(
                text = category.label,
                active = category == active,
                spec = TerminaType.CategoryChip,
                activeInk = TerminaColors.GoldLight,
                activeUnderline = TerminaColors.Gold,
                inactiveInk = TerminaColors.TextDim,
                onTap = { onSelect(category) },
            )
        }
    }
}

/**
 * The design's one control shape for tabs and chips alike: a label with a 2px
 * underline, gold or lavender depending on which axis it belongs to. Never a
 * box -- the handoff's geometry rules forbid rounded cards.
 *
 * The underline is sized by IntrinsicSize.Max against the measured text, NOT by
 * estimating from character count. Phase 3's nav underline shipped an estimated
 * width, two code reviews missed it, and a photo of the device caught it in
 * seconds -- on a FLAG_SECURE screen that is the only way it surfaces.
 */
@Composable
private fun UnderlinedLabel(
    text: String,
    active: Boolean,
    spec: DesignTextSpec,
    onTap: () -> Unit,
    activeInk: Color = TerminaColors.Ink,
    activeUnderline: Color = TerminaColors.AccentBright,
    inactiveInk: Color = TerminaColors.TextDimmer,
) {
    Column(
        Modifier
            .clickable(onClick = onTap)
            .width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = spec.toStyle(if (active) activeInk else inactiveInk))
        Box(
            Modifier
                .padding(top = du(6f))
                .height(du(2f))
                .fillMaxWidth()
                .background(if (active) activeUnderline else Color.Transparent),
        )
    }
}

@Composable
private fun EmptyState(category: OptionsCategory) {
    Column(
        Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(horizontal = du(40f), vertical = du(14f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(category.label, style = TerminaType.OptionLabel.toStyle(TerminaColors.TextHint))
        Text(
            emptyStateFor(category),
            style = TerminaType.OptionDescription.toStyle(TerminaColors.TextDimmest),
            modifier = Modifier.padding(top = du(10f)),
        )
    }
}

@Composable
private fun OptionsFooter(onResume: () -> Unit) {
    Hairline()
    Row(
        Modifier
            .fillMaxWidth()
            .height(du(80f))
            .padding(horizontal = du(40f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "TAP A ROW TO ADJUST IT",
            style = TerminaType.FooterHint.toStyle(TerminaColors.TextHint),
        )
        Box(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "RESUME PLAY",
                style = TerminaType.PauseAction.toStyle(TerminaColors.InkMuted),
                modifier = Modifier.clickable(onClick = onResume),
            )
            Box(
                Modifier
                    .padding(top = du(4f))
                    .height(du(2f))
                    .width(du(150f))
                    .background(TerminaColors.HairlineStrong),
            )
        }
    }
}

@Composable
private fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(du(1f))
            .background(TerminaColors.HairlineFaint),
    )
}
```

- [ ] **Step 4: Add a temporary row-list stub so this task compiles**

Task 8 replaces it. Add to the bottom of `OptionsScreen.kt`:

```kotlin
/** Replaced by the real control library in Task 8. */
@Composable
private fun OptionRowList(
    rows: List<OptionRow>,
    selectedKey: OptionKey?,
    onRowSelect: (OptionKey) -> Unit,
    onRowChange: (OptionKey, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        for (row in rows) {
            Text(row.label, style = TerminaType.OptionLabel.toStyle(TerminaColors.Ink2))
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `sg docker -c './tools/run-unit-tests.sh'`

Expected: `PASS`, 153 tests (145 + 8).

- [ ] **Step 6: Commit**

```bash
git add Android/app/src/main/java/com/terminads/mm/secondscreen/OptionsScreen.kt \
        Android/app/src/test/java/com/terminads/mm/secondscreen/OptionsScreenTest.kt
git commit -m "feat(secondscreen): Options subscreen chrome, tabs, and empty states"
```

---

### Task 8: Option control library

Slider, segmented, checkbox, and the row anatomy with persistent selection.

**Files:**
- Create: `Android/app/src/main/java/com/terminads/mm/secondscreen/OptionControls.kt`
- Modify: `Android/app/src/main/java/com/terminads/mm/secondscreen/OptionsScreen.kt` (delete the Task 7 stub, import the real list)
- Test: `Android/app/src/test/java/com/terminads/mm/secondscreen/OptionControlsTest.kt`

**Interfaces:**
- Consumes: `OptionRow`, `OptionControl`, `OptionKey` (Task 4); `quantize`, `nextSegmentValue` (Task 5).
- Produces: `OptionRowList(rows, selectedKey, onRowSelect, onRowChange, modifier)` as a public composable in `OptionControls.kt`.

- [ ] **Step 1: Write the failing tests**

Create `Android/app/src/test/java/com/terminads/mm/secondscreen/OptionControlsTest.kt`:

```kotlin
package com.terminads.mm.secondscreen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.terminads.mm.GameSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class OptionControlsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun settings(
        msaa: Int = 1,
        matchRefreshRate: Boolean = false,
        motionBlurMode: Int = 0,
    ) = GameSettings(
        internalResPercent = 100, msaa = msaa, fps = 20,
        matchRefreshRate = matchRefreshRate, textureFilter = 0, clockType = 0,
        motionBlurMode = motionBlurMode, motionBlurStrength = 180,
        actorDrawDistance = 1, threeDItemDrops = false, displayRefreshHz = 60,
    )

    private var lastChange: Pair<OptionKey, Int>? = null
    private var lastSelect: OptionKey? = null

    private fun show(
        tab: OptionsTab = OptionsTab.SETTINGS,
        s: GameSettings = settings(),
        selectedKey: OptionKey? = null,
    ) {
        composeTestRule.setContent {
            DesignRoot {
                OptionRowList(
                    rows = optionRows(tab, OptionsCategory.GRAPHICS, s),
                    selectedKey = selectedKey,
                    onRowSelect = { lastSelect = it },
                    onRowChange = { key, value -> lastChange = key to value },
                )
            }
        }
    }

    @Test
    fun everyRowRendersItsLabelAndDescription() {
        show()
        composeTestRule.onNodeWithText("INTERNAL RESOLUTION").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("RENDERS ABOVE NATIVE, THEN DOWNSAMPLES · DEFAULT 100%")
            .assertIsDisplayed()
    }

    @Test
    fun theSliderReadoutRenders() {
        show()
        composeTestRule.onNodeWithText("100%").assertIsDisplayed()
    }

    @Test
    fun tappingASegmentEmitsItsEngineValueNotItsIndex() {
        show()
        // MSAA segments are OFF/2x/4x/8x -> engine values 1/2/4/8.
        composeTestRule.onNodeWithText("4×").performClick()
        assertEquals(OptionKey.MSAA to 4, lastChange)
    }

    @Test
    fun tappingARowSelectsIt() {
        show()
        composeTestRule.onNodeWithText("MATCH REFRESH RATE").performClick()
        assertEquals(OptionKey.MATCH_HZ, lastSelect)
    }

    @Test
    fun tappingACheckboxRowTogglesItAbsolutely() {
        show(s = settings(matchRefreshRate = false))
        composeTestRule.onNodeWithContentDescription(
            "Match refresh rate, off, checkbox",
        ).performClick()
        assertEquals(OptionKey.MATCH_HZ to 1, lastChange)
    }

    @Test
    fun chevronsStepTheSliderByItsStep() {
        show(selectedKey = OptionKey.INTERNAL_RES)
        composeTestRule.onNodeWithContentDescription("Increase internal resolution").performClick()
        assertEquals(OptionKey.INTERNAL_RES to 105, lastChange)

        composeTestRule.onNodeWithContentDescription("Decrease internal resolution").performClick()
        assertEquals(OptionKey.INTERNAL_RES to 95, lastChange)
    }

    @Test
    fun aDisabledRowIsInertAndAnnouncesItself() {
        lastChange = null
        show(s = settings(matchRefreshRate = true))
        composeTestRule
            .onNodeWithContentDescription("Current FPS, unavailable, locked by match refresh rate")
            .assertIsNotEnabled()
        assertNull(lastChange)
    }

    @Test
    fun theLockedFpsRowShowsItsLockedDescription() {
        show(s = settings(matchRefreshRate = true))
        composeTestRule.onNodeWithText("LOCKED BY MATCH REFRESH RATE").assertIsDisplayed()
    }

    @Test
    fun blurStrengthIsInertUnlessMotionBlurIsAlwaysOn() {
        show(tab = OptionsTab.ENHANCEMENTS, s = settings(motionBlurMode = 0))
        composeTestRule
            .onNodeWithContentDescription(
                "Motion blur strength, unavailable, locked unless motion blur is always on",
            )
            .assertIsNotEnabled()
    }

    @Test
    fun draggingTheRailEmitsAQuantizedAbsoluteValue() {
        // The design's slider follows the finger; the spec requires the emitted
        // value be absolute and on-step, never a delta.
        show()
        composeTestRule.onNodeWithText("INTERNAL RESOLUTION").performTouchInput {
            // Drag is captured on the rail, which sits to the right of the
            // label column; swipe across the middle of the row.
            swipeRight()
        }
        val (key, value) = requireNotNull(lastChange)
        assertEquals(OptionKey.INTERNAL_RES, key)
        assertEquals(0, value % 5)
        assertTrue(value in 50..200)
    }

    @Test
    fun aDisabledRowsRailDoesNotRespondToDrag() {
        lastChange = null
        show(s = settings(matchRefreshRate = true))
        composeTestRule.onNodeWithText("LOCKED BY MATCH REFRESH RATE").performTouchInput {
            swipeRight()
        }
        assertNull(lastChange)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `sg docker -c "./tools/run-unit-tests.sh --tests '*OptionControlsTest*'"`

Expected: FAIL — `Unresolved reference: OptionRowList`.

- [ ] **Step 3: Write the control library**

Create `Android/app/src/main/java/com/terminads/mm/secondscreen/OptionControls.kt`:

```kotlin
package com.terminads.mm.secondscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics

/**
 * The Options control library (handoff section 10, "Row anatomy").
 *
 * Selection is marked three ways and never with a box: a breathing gold
 * diamond at the left of the label, the row's top hairline brightening, and a
 * soft wash. Selection is persistent and touch-driven -- this screen has no
 * arrow keys, and a purely transient treatment would leave the row anatomy's
 * centrepiece invisible at rest and give TalkBack no focus anchor.
 *
 * Every control emits the ABSOLUTE engine value, never a delta or an index, so
 * a command means the same thing whenever it lands.
 */
@Composable
fun OptionRowList(
    rows: List<OptionRow>,
    selectedKey: OptionKey?,
    onRowSelect: (OptionKey) -> Unit,
    onRowChange: (OptionKey, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = du(44f), end = du(44f), top = du(4f), bottom = du(18f)),
    ) {
        for (row in rows) {
            OptionRowView(
                row = row,
                selected = row.key == selectedKey,
                onSelect = { onRowSelect(row.key) },
                onChange = { onRowChange(row.key, it) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OptionRowView(
    row: OptionRow,
    selected: Boolean,
    onSelect: () -> Unit,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelInk = if (row.enabled) TerminaColors.Ink2 else TerminaColors.MenuRowInert
    Column(
        modifier
            .fillMaxWidth()
            .background(if (selected) selectionWash() else Brush.linearGradient(
                listOf(Color.Transparent, Color.Transparent),
            ))
            .clickable(enabled = row.enabled) {
                onSelect()
                // A checkbox row's whole area is its hit target (handoff
                // section 10). Absolute: state the target, never "toggle".
                (row.control as? OptionControl.Checkbox)?.let {
                    onChange(if (it.checked) 0 else 1)
                }
            }
            .semantics {
                contentDescription = row.semantics
                if (!row.enabled) disabled()
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(du(1f))
                .background(
                    if (selected) TerminaColors.HairlineStrong else TerminaColors.HairlineFaint,
                ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = du(22f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(du(26f)),
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(du(10f)),
                ) {
                    BreathingDiamond(
                        color = if (selected) TerminaColors.Gold else Color.Transparent,
                    )
                    Text(row.label, style = TerminaType.OptionLabel.toStyle(labelInk))
                    row.qualifier?.let {
                        Text(
                            it,
                            style = TerminaType.OptionDescription.toStyle(TerminaColors.ClockDim),
                        )
                    }
                    row.chip?.let {
                        Text(
                            it,
                            style = TerminaType.CountdownChip.toStyle(TerminaColors.AccentLight),
                            modifier = Modifier
                                .border(
                                    du(1f), TerminaColors.ChipBorder, RoundedCornerShape(du(7f)),
                                )
                                .padding(horizontal = du(8f), vertical = du(2f)),
                        )
                    }
                }
                Text(
                    row.description,
                    style = TerminaType.OptionDescription.toStyle(
                        if (row.enabled) TerminaColors.TextDim else TerminaColors.TextDimmest,
                    ),
                    modifier = Modifier.padding(start = du(22f), top = du(6f)),
                )
            }
            ControlView(row, onChange)
        }
    }
}

/** The handoff's selection wash: a soft radial from the row's left edge. */
@Composable
private fun selectionWash(): Brush = Brush.horizontalGradient(
    0f to TerminaColors.Accent.copy(alpha = 0.14f),
    0.74f to Color.Transparent,
)

@Composable
private fun ControlView(row: OptionRow, onChange: (Int) -> Unit) {
    when (val control = row.control) {
        is OptionControl.Slider -> SliderControl(row, control, onChange)
        is OptionControl.Segmented -> SegmentedControl(row, control, onChange)
        is OptionControl.Checkbox -> CheckboxControl(control, row.enabled)
    }
}

/**
 * A 300x2px hairline rail with a 16px diamond knob and a tick at the default
 * value, flanked by chevron steppers.
 *
 * The rail is draggable and OPTIMISTIC: while a finger is down the knob follows
 * it directly, so it never lags the ~100 ms poll. The override is dropped on
 * release, after which the snapshot is the only source of truth -- so a command
 * the ring dropped makes the knob visibly snap back rather than leaving the
 * screen showing a value the game never took.
 *
 * Every emitted value is absolute and quantized; nothing sends a delta.
 */
@Composable
private fun SliderControl(
    row: OptionRow,
    control: OptionControl.Slider,
    onChange: (Int) -> Unit,
) {
    val spokenLabel = row.label.lowercase()
    val railInk = if (row.enabled) TerminaColors.AccentLight else TerminaColors.TextDimmer
    val knobInk = if (row.enabled) TerminaColors.AccentLight else TerminaColors.TextDimmer
    val readoutInk = if (row.enabled) TerminaColors.Gold else TerminaColors.GoldDim
    val chevronInk = if (row.enabled) TerminaColors.InkMuted else TerminaColors.TextDimmest
    val span = (control.max - control.min).coerceAtLeast(1)

    var dragValue by remember { mutableStateOf<Int?>(null) }
    var railWidthPx by remember { mutableStateOf(0) }
    val shown = dragValue ?: control.value
    val fillFraction = ((shown - control.min).toFloat() / span).coerceIn(0f, 1f)

    fun emitFromOffset(offsetX: Float) {
        if (railWidthPx <= 0) return
        val raw = control.min + (offsetX / railWidthPx) * span
        val quantized = quantize(raw.toInt(), control.min, control.max, control.step)
        dragValue = quantized
        onChange(quantized)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "‹",
            style = TerminaType.SubscreenTitle.toStyle(chevronInk),
            modifier = Modifier
                .size(width = du(50f), height = du(54f))
                .clickable(enabled = row.enabled) {
                    onChange(
                        quantize(shown - control.step, control.min, control.max, control.step),
                    )
                }
                .semantics { contentDescription = "Decrease $spokenLabel" },
        )
        Box(
            Modifier
                .width(du(300f))
                // Taller than the 2px rail so the whole strip is a real touch
                // target; the handoff's own rule is >= 42px on anything
                // interactive.
                .height(du(44f))
                .onSizeChanged { railWidthPx = it.width }
                .then(
                    if (row.enabled) {
                        Modifier.pointerInput(control.min, control.max, control.step) {
                            detectHorizontalDragGestures(
                                onDragEnd = { dragValue = null },
                                onDragCancel = { dragValue = null },
                            ) { change, _ -> emitFromOffset(change.position.x) }
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(du(2f))
                    .background(TerminaColors.MagicTrack),
            )
            Box(
                Modifier
                    .fillMaxWidth(fillFraction)
                    .height(du(2f))
                    .background(railInk),
            )
            // Default-value tick: 1px, 9px proud of the rail either side.
            Box(
                Modifier
                    .padding(
                        start = du(
                            300f * (control.defaultValue - control.min).toFloat() / span,
                        ),
                    )
                    .width(du(1f))
                    .height(du(20f))
                    .background(TerminaColors.HairlineStrong),
            )
            Box(
                Modifier
                    .padding(start = du(300f * fillFraction - 8f))
                    .size(du(16f))
                    .rotate(45f)
                    .background(knobInk, RoundedCornerShape(du(2f))),
            )
        }
        Text(
            "›",
            style = TerminaType.SubscreenTitle.toStyle(chevronInk),
            modifier = Modifier
                .size(width = du(50f), height = du(54f))
                .clickable(enabled = row.enabled) {
                    onChange(
                        quantize(shown + control.step, control.min, control.max, control.step),
                    )
                }
                .semantics { contentDescription = "Increase $spokenLabel" },
        )
        Text(
            control.readout,
            style = TerminaType.OptionReadout.toStyle(readoutInk),
            modifier = Modifier.padding(start = du(14f)),
        )
    }
}

/**
 * Underlined segmented options. Tapping emits the engine value the segment
 * stands for, so no caller has to know the index-to-value mapping.
 */
@Composable
private fun SegmentedControl(
    row: OptionRow,
    control: OptionControl.Segmented,
    onChange: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(du(6f))) {
        control.options.forEachIndexed { index, label ->
            val active = index == control.selectedIndex
            Column(
                Modifier
                    .clickable(enabled = row.enabled) { onChange(control.values[index]) }
                    .padding(horizontal = du(10f)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    style = TerminaType.OptionSegment.toStyle(
                        when {
                            !row.enabled -> TerminaColors.TextDimmest
                            active -> TerminaColors.GoldLight
                            else -> TerminaColors.TextDimmer
                        },
                    ),
                    modifier = Modifier.padding(vertical = du(16f)),
                )
                Box(
                    Modifier
                        .height(du(2f))
                        .width(du(58f))
                        .background(
                            if (active && row.enabled) TerminaColors.Gold else Color.Transparent,
                        ),
                )
            }
        }
    }
}

/**
 * A 46px hairline square holding a 15px gold diamond when checked, beside the
 * state word. The row -- not this square -- is the hit target, so this carries
 * no click handler and no semantics of its own.
 */
@Composable
private fun CheckboxControl(control: OptionControl.Checkbox, enabled: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(du(14f)),
    ) {
        Text(
            if (control.checked) "ON" else "OFF",
            style = TerminaType.CategoryChip.toStyle(
                when {
                    !enabled -> TerminaColors.TextDimmest
                    control.checked -> TerminaColors.GoldLight
                    else -> TerminaColors.TextDimmer
                },
            ),
        )
        Box(
            Modifier
                .size(du(46f))
                .border(
                    du(1f),
                    if (control.checked && enabled) {
                        TerminaColors.Gold
                    } else {
                        TerminaColors.HairlineFaint
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (control.checked) {
                Box(
                    Modifier
                        .size(du(15f))
                        .rotate(45f)
                        .background(
                            if (enabled) TerminaColors.Gold else TerminaColors.TextDimmest,
                            RoundedCornerShape(du(2f)),
                        ),
                )
            }
        }
    }
}
```

- [ ] **Step 4: Delete the Task 7 stub**

Remove the `private fun OptionRowList(...)` block from the bottom of `OptionsScreen.kt`. The real one in `OptionControls.kt` is in the same package, so no import is needed.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `sg docker -c './tools/run-unit-tests.sh'`

Expected: `PASS`, 164 tests (153 + 11).

- [ ] **Step 6: Commit**

```bash
git add Android/app/src/main/java/com/terminads/mm/secondscreen/OptionControls.kt \
        Android/app/src/main/java/com/terminads/mm/secondscreen/OptionsScreen.kt \
        Android/app/src/test/java/com/terminads/mm/secondscreen/OptionControlsTest.kt
git commit -m "feat(secondscreen): Options control library with persistent selection"
```

---

### Task 9: Host integration

Wire the pause menu, Options navigation, command submission, and the save debounce into the host.

**Files:**
- Create: `Android/app/src/main/java/com/terminads/mm/secondscreen/PauseNavState.kt`
- Modify: `Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenHost.kt:60-101`
- Test: `Android/app/src/test/java/com/terminads/mm/secondscreen/PauseNavStateTest.kt`

**Interfaces:**
- Consumes: everything from Tasks 4-8.
- Produces: `PauseNavState` with `view: PauseView`, `tab: OptionsTab`, `category: OptionsCategory`, `selectedKey: OptionKey?` and the transitions `openOptions()`, `back()`, `selectTab(tab)`, `selectCategory(category)`, `selectRow(key)`; plus `enum class PauseView { ROOT, OPTIONS }`.

- [ ] **Step 1: Write the failing tests**

Create `Android/app/src/test/java/com/terminads/mm/secondscreen/PauseNavStateTest.kt`:

```kotlin
package com.terminads.mm.secondscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PauseNavStateTest {

    @Test
    fun startsAtTheRootMenuOnTheSettingsGraphicsSlice() {
        val s = PauseNavState()
        assertEquals(PauseView.ROOT, s.view)
        assertEquals(OptionsTab.SETTINGS, s.tab)
        assertEquals(OptionsCategory.GRAPHICS, s.category)
        assertNull(s.selectedKey)
    }

    @Test
    fun openingOptionsAndComingBack() {
        val opened = PauseNavState().openOptions()
        assertEquals(PauseView.OPTIONS, opened.view)
        assertEquals(PauseView.ROOT, opened.back().view)
    }

    @Test
    fun switchingTabResetsCategoryAndSelection() {
        val s = PauseNavState()
            .openOptions()
            .selectCategory(OptionsCategory.CONTROLS)
            .selectRow(OptionKey.MSAA)
            .selectTab(OptionsTab.ENHANCEMENTS)

        assertEquals(OptionsTab.ENHANCEMENTS, s.tab)
        assertEquals(OptionsCategory.GRAPHICS, s.category)
        assertNull(s.selectedKey)
    }

    @Test
    fun switchingCategoryResetsSelectionButKeepsTheTab() {
        val s = PauseNavState()
            .openOptions()
            .selectRow(OptionKey.MSAA)
            .selectCategory(OptionsCategory.AUDIO)

        assertEquals(OptionsTab.SETTINGS, s.tab)
        assertEquals(OptionsCategory.AUDIO, s.category)
        assertNull(s.selectedKey)
    }

    @Test
    fun selectingARowRecordsIt() {
        assertEquals(OptionKey.FPS, PauseNavState().openOptions().selectRow(OptionKey.FPS).selectedKey)
    }

    @Test
    fun returningToTheRootClearsTheRowSelection() {
        // Reopening Options must not restore a selection the player cannot see
        // the origin of.
        val s = PauseNavState().openOptions().selectRow(OptionKey.MSAA).back()
        assertNull(s.selectedKey)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `sg docker -c "./tools/run-unit-tests.sh --tests '*PauseNavStateTest*'"`

Expected: FAIL — `Unresolved reference: PauseNavState`.

- [ ] **Step 3: Write the nav state**

Create `Android/app/src/main/java/com/terminads/mm/secondscreen/PauseNavState.kt`:

```kotlin
package com.terminads.mm.secondscreen

/** Which pause view the bottom screen shows. Local state, never game truth. */
enum class PauseView { ROOT, OPTIONS }

/**
 * Local navigation within the pause experience.
 *
 * Immutable: every transition returns a new value, so Compose sees a changed
 * object and the tests need no mutation bookkeeping. Only `paused` itself comes
 * from the game; everything here is the host's own state.
 *
 * Switching tab or category resets the row selection, which is the design's own
 * rule (handoff section 10, "Options interaction").
 */
data class PauseNavState(
    val view: PauseView = PauseView.ROOT,
    val tab: OptionsTab = OptionsTab.SETTINGS,
    val category: OptionsCategory = OptionsCategory.GRAPHICS,
    val selectedKey: OptionKey? = null,
) {
    fun openOptions() = copy(view = PauseView.OPTIONS)

    fun back() = copy(view = PauseView.ROOT, selectedKey = null)

    fun selectTab(tab: OptionsTab) =
        copy(tab = tab, category = OptionsCategory.GRAPHICS, selectedKey = null)

    fun selectCategory(category: OptionsCategory) =
        copy(category = category, selectedKey = null)

    fun selectRow(key: OptionKey) = copy(selectedKey = key)
}
```

- [ ] **Step 4: Wire the host**

In `SecondScreenHost.kt`, add to the imports:

```kotlin
import android.os.SystemClock
```

Add state alongside the existing `remember` calls:

```kotlin
    var nav by remember { mutableStateOf(PauseNavState()) }
    val saveDebouncer = remember { CVarSaveDebouncer() }
```

Inside the existing `LaunchedEffect` poll loop, after the `state = pollBridge()` line, add:

```kotlin
            // The poll loop is the single clock for the save debounce, so no
            // second timer touches the main thread.
            if (saveDebouncer.fire(SystemClock.uptimeMillis())) {
                commandBridge.saveCVars()
            }
```

Replace the `is ScreenKind.PauseMenu ->` branch with:

```kotlin
            is ScreenKind.PauseMenu -> when (nav.view) {
                PauseView.ROOT -> PauseMenuScreen(
                    model = screen.model,
                    resumePending = pauseRequestState == PauseRequestState.PENDING,
                    resumeFailed =
                        pauseRequestState == PauseRequestState.TIMED_OUT ||
                            isSubmitFailureVisible(failedTarget, screenTarget = false),
                    onResumeTap = {
                        when (commandBridge.setPaused(false)) {
                            SubmitStatus.OK -> {
                                failedTarget = null
                                pauseTracker.request(target = false)
                                pauseRequestState = PauseRequestState.PENDING
                            }
                            else -> failedTarget = false
                        }
                    },
                    onOptionsTap = { nav = nav.openOptions() },
                )
                PauseView.OPTIONS -> OptionsScreen(
                    model = screen.model,
                    settings = screen.settings,
                    tab = nav.tab,
                    category = nav.category,
                    selectedKey = nav.selectedKey,
                    onTabSelect = { nav = nav.selectTab(it) },
                    onCategorySelect = { nav = nav.selectCategory(it) },
                    onRowSelect = { nav = nav.selectRow(it) },
                    onRowChange = { key, value ->
                        // Fire and observe: the next snapshot is the ack. A
                        // dropped command simply never changes the row.
                        submitOptionChange(commandBridge, key, value)
                        saveDebouncer.noteChange(SystemClock.uptimeMillis())
                    },
                    onBack = { nav = nav.back() },
                    onResume = {
                        when (commandBridge.setPaused(false)) {
                            SubmitStatus.OK -> {
                                failedTarget = null
                                pauseTracker.request(target = false)
                                pauseRequestState = PauseRequestState.PENDING
                            }
                            else -> failedTarget = false
                        }
                    },
                )
            }
```

- [ ] **Step 5: Carry the settings through routing**

`ScreenKind.PauseMenu` needs the settings block. In `HudModel.kt`, change the declaration:

```kotlin
    data class PauseMenu(val model: HudModel, val settings: GameSettings) : ScreenKind
```

Add `import com.terminads.mm.GameSettings` to that file, and update `routeSnapshot`:

```kotlin
    s.isPaused -> ScreenKind.PauseMenu(deriveHudModel(s), s.settings)
```

- [ ] **Step 6: Add a routing test**

Append to `RouteTest.kt`:

```kotlin
    @Test
    fun theSettingsBlockReachesThePauseMenuRoute() {
        val snapshot = snapshot(
            hasPlayState = true, saveLoaded = true, isPaused = true,
            settings = GameSettings(
                internalResPercent = 150, msaa = 4, fps = 60, matchRefreshRate = true,
                textureFilter = 1, clockType = 2, motionBlurMode = 2,
                motionBlurStrength = 200, actorDrawDistance = 3, threeDItemDrops = true,
                displayRefreshHz = 120,
            ),
        )
        val screen = route(BridgeState.Live(snapshot)) as ScreenKind.PauseMenu
        assertEquals(150, screen.settings.internalResPercent)
        assertEquals(120, screen.settings.displayRefreshHz)
    }
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `sg docker -c './tools/run-unit-tests.sh'`

Expected: `PASS`, 171 tests (164 + 6 + 1).

- [ ] **Step 8: Commit**

```bash
git add Android/app/src/main/java/com/terminads/mm/secondscreen/PauseNavState.kt \
        Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenHost.kt \
        Android/app/src/main/java/com/terminads/mm/secondscreen/HudModel.kt \
        Android/app/src/test/java/com/terminads/mm/secondscreen/
git commit -m "feat(secondscreen): wire Options navigation and settings commands"
```

---

### Task 10: Top-screen ImGui PAUSED veil

**Native task — the full `./tools/build-apk.sh` pipeline is mandatory after this. Never use `assemble-apk.sh`.**

**Files:**
- Create: `mm/2s2h/TerminaDS/CinzelFontData.h`, `mm/2s2h/TerminaDS/CinzelFontData.cpp`
- Create: `mm/2s2h/TerminaDS/PauseVeil.cpp`
- Modify: `engine/src/ship/window/gui/Gui.cpp:118-128`
- Modify: `docs/UPSTREAM.md`

**Interfaces:**
- Consumes: nothing from earlier tasks (it reads `gPlayState->frameAdvCtx.enabled` directly).
- Produces: nothing consumed by Kotlin.

- [ ] **Step 1: Generate the compressed font data**

ImGui ships `binary_to_compressed_c` for exactly this. Run inside the build image:

CMake's FetchContent already places it at
`build-cmake/_deps/imgui-src/misc/fonts/binary_to_compressed_c.cpp` (verified
present at plan time). Compile and run it inside the build image:

```bash
sg docker -c 'docker run --rm -v "$PWD:/workspace" -w /workspace termina-ds-build:latest bash -c "
  set -e
  src=build-cmake/_deps/imgui-src/misc/fonts/binary_to_compressed_c.cpp
  test -f \"\$src\" || { echo \"MISSING: \$src -- run a cmake configure first\" >&2; exit 1; }
  g++ -O2 -o /tmp/b2c \"\$src\"
  /tmp/b2c -base85 Android/app/src/main/res/font/cinzel_variable.ttf kCinzelCompressed
"' > /tmp/cinzel_generated.txt
```

If `build-cmake/` has been cleaned the file is absent; re-run
`./tools/build-apk.sh` (or a bare `cmake -H. -Bbuild-cmake -GNinja`) to restore
it. Do not download ImGui separately — the pinned version must match the one
the engine links against.

Check the payload size before pasting it in: `wc -c /tmp/cinzel_generated.txt`.
A variable-weight Cinzel compresses to roughly 100-200 KB of base85, which is a
large but acceptable generated source file. If it is dramatically larger,
escalate rather than committing it.

Create `mm/2s2h/TerminaDS/CinzelFontData.cpp` containing the generated `static const char CinzelFont_compressed_data_base85[]` array, wrapped as:

```cpp
/*
 * Cinzel, embedded as ImGui compressed-base85 so the veil's wordmark renders
 * in the design's typeface.
 *
 * The engine's other font path (GameOverlay::LoadFont) sources fonts from .o2r
 * archives through the ResourceManager, which our Android-resource fonts cannot
 * reach. Embedding matches what Gui.cpp already does for FontAwesome
 * (engine/src/ship/window/gui/Gui.cpp:127-128).
 *
 * GENERATED by imgui's binary_to_compressed_c -base85 from
 * Android/app/src/main/res/font/cinzel_variable.ttf. Do not edit by hand;
 * regenerate if the font file changes.
 */
#include "CinzelFontData.h"

const char kCinzelCompressedBase85[] = "<generated payload>";
```

And `mm/2s2h/TerminaDS/CinzelFontData.h`:

```cpp
#ifndef TERMINADS_CINZEL_FONT_DATA_H
#define TERMINADS_CINZEL_FONT_DATA_H

/* ImGui compressed-base85 payload; see CinzelFontData.cpp. */
extern const char kCinzelCompressedBase85[];

#endif /* TERMINADS_CINZEL_FONT_DATA_H */
```

- [ ] **Step 2: Register the font in the engine's Gui init**

In `engine/src/ship/window/gui/Gui.cpp`, immediately after the FontAwesome `AddFontFromMemoryCompressedBase85TTF` call (line 128), add:

```cpp
    // Termina DS: the pause veil's wordmark font. Registered here because the
    // atlas is built once during Gui::Init; a later AddFont would not be
    // rasterised. See mm/2s2h/TerminaDS/PauseVeil.cpp.
    TerminaDS_LoadVeilFont();
```

And near the top of the file, with the other includes:

```cpp
// Termina DS veil font registration; defined in mm/2s2h/TerminaDS/PauseVeil.cpp.
extern "C" void TerminaDS_LoadVeilFont(void);
```

- [ ] **Step 3: Write the veil**

Create `mm/2s2h/TerminaDS/PauseVeil.cpp`:

```cpp
/*
 * Termina DS: the top-screen PAUSED veil (design handoff section 2).
 *
 * An always-on-top, input-transparent ImGui window drawn while the engine's
 * frame-advance gate holds the Play update frozen. Rendering continues while
 * frozen -- only the update body is gated (z_play.c:988) -- which is exactly
 * why this overlay is possible at all.
 *
 * Registered like Notification::Window (mm/2s2h/BenGui/BenGui.cpp:187-189).
 *
 * KNOWN FIDELITY GAP: the design specifies
 * backdrop-filter: blur(7px) saturate(.42) brightness(.34). An ImGui window has
 * no framebuffer or shader access, so the frozen render is dimmed and tinted by
 * layered draw-list rects rather than blurred. Accepted; see the spec's
 * section 10.
 *
 * This file does NOT touch game state beyond the one frame-advance read it
 * needs to know whether to draw, which is the same value SnapshotPublisher.cpp
 * already publishes.
 */
#include "CinzelFontData.h"

#include <cmath>

#include <imgui.h>
#include <libultraship/libultraship.h>

#include "2s2h/ShipInit.hpp"

extern "C" {
#include "z64play.h"

extern PlayState* gPlayState;
}

namespace {

ImFont* sVeilFont = nullptr;

/* Design px at 1920x1080; the veil scales with the viewport's shorter axis. */
constexpr float kWordmarkPx = 176.0f;

bool VeilShouldDraw() {
    const PlayState* play = gPlayState;
    return play != NULL && play->frameAdvCtx.enabled;
}

class PauseVeilWindow : public Ship::GuiWindow {
  public:
    using GuiWindow::GuiWindow;

    void InitElement() override {
    }

    void UpdateElement() override {
    }

    void DrawElement() override {
        if (!VeilShouldDraw()) {
            return;
        }

        ImGuiViewport* vp = ImGui::GetMainViewport();
        ImGui::SetNextWindowPos(vp->Pos);
        ImGui::SetNextWindowSize(vp->Size);
        ImGui::SetNextWindowViewport(vp->ID);
        ImGui::PushStyleColor(ImGuiCol_WindowBg, ImVec4(0, 0, 0, 0));
        ImGui::PushStyleVar(ImGuiStyleVar_WindowPadding, ImVec2(0, 0));

        ImGui::Begin("TerminaDSPauseVeil", nullptr,
                     ImGuiWindowFlags_NoInputs | ImGuiWindowFlags_NoDecoration |
                         ImGuiWindowFlags_NoNav | ImGuiWindowFlags_NoDocking |
                         ImGuiWindowFlags_NoBringToFrontOnFocus |
                         ImGuiWindowFlags_NoFocusOnAppearing | ImGuiWindowFlags_NoSavedSettings);

        ImDrawList* draw = ImGui::GetWindowDrawList();
        const ImVec2 origin = vp->Pos;
        const ImVec2 size = vp->Size;
        const float scale = size.y / 1080.0f;

        /*
         * The design's veil is a dark radial over the render. Approximated
         * with two stacked rects: a flat dim plus a vertical gradient that is
         * darkest at the edges, which reads as the intended vignette without a
         * shader.
         */
        draw->AddRectFilled(origin, ImVec2(origin.x + size.x, origin.y + size.y),
                            IM_COL32(0, 0, 0, 190));
        draw->AddRectFilledMultiColor(origin, ImVec2(origin.x + size.x, origin.y + size.y),
                                      IM_COL32(26, 14, 44, 90), IM_COL32(26, 14, 44, 90),
                                      IM_COL32(0, 0, 0, 150), IM_COL32(0, 0, 0, 150));

        const float centerX = origin.x + size.x * 0.5f;
        float y = origin.y + size.y * 0.32f;

        /* Wordmark. */
        if (sVeilFont != nullptr) {
            ImGui::PushFont(sVeilFont);
        }
        const float wordmarkSize = kWordmarkPx * scale;
        const char* kWordmark = "PAUSED";
        /*
         * Tracking is .18em in the design; ImGui has no letter-spacing, so the
         * glyphs are drawn one at a time with the gap added manually.
         */
        const float tracking = wordmarkSize * 0.18f;
        float wordWidth = 0.0f;
        for (const char* c = kWordmark; *c != '\0'; ++c) {
            wordWidth += ImGui::GetFont()->CalcTextSizeA(wordmarkSize, FLT_MAX, 0.0f, c, c + 1).x;
            wordWidth += tracking;
        }
        wordWidth -= tracking;

        float penX = centerX - wordWidth * 0.5f;
        for (const char* c = kWordmark; *c != '\0'; ++c) {
            draw->AddText(ImGui::GetFont(), wordmarkSize, ImVec2(penX, y), IM_COL32(246, 236, 255, 255),
                          c, c + 1);
            penX += ImGui::GetFont()->CalcTextSizeA(wordmarkSize, FLT_MAX, 0.0f, c, c + 1).x + tracking;
        }
        if (sVeilFont != nullptr) {
            ImGui::PopFont();
        }
        y += wordmarkSize * 1.05f;

        /* Subtitle. */
        DrawCenteredText(draw, centerX, y, 21.0f * scale, IM_COL32(157, 141, 190, 255),
                         "THE CLOCK HOLDS ITS BREATH", 9.0f * scale);
        y += 40.0f * scale;

        /* Rule. */
        draw->AddLine(ImVec2(centerX - 210.0f * scale, y), ImVec2(centerX + 210.0f * scale, y),
                      IM_COL32(180, 140, 232, 140), 1.0f);
        y += 34.0f * scale;

        /* Hint. */
        DrawCenteredText(draw, centerX, y, 22.0f * scale, IM_COL32(201, 191, 224, 255),
                         "CONTINUE ON THE BOTTOM SCREEN", 2.5f * scale);

        ImGui::End();
        ImGui::PopStyleVar();
        ImGui::PopStyleColor();
    }

  private:
    static void DrawCenteredText(ImDrawList* draw, float centerX, float y, float pixelSize,
                                 ImU32 color, const char* text, float tracking) {
        float width = 0.0f;
        for (const char* c = text; *c != '\0'; ++c) {
            width += ImGui::GetFont()->CalcTextSizeA(pixelSize, FLT_MAX, 0.0f, c, c + 1).x + tracking;
        }
        width -= tracking;

        float penX = centerX - width * 0.5f;
        for (const char* c = text; *c != '\0'; ++c) {
            draw->AddText(ImGui::GetFont(), pixelSize, ImVec2(penX, y), color, c, c + 1);
            penX += ImGui::GetFont()->CalcTextSizeA(pixelSize, FLT_MAX, 0.0f, c, c + 1).x + tracking;
        }
    }
};

std::shared_ptr<PauseVeilWindow> sVeilWindow;

static RegisterShipInitFunc sRegisterPauseVeil([]() {
    // Same re-registration guard as SnapshotPublisher.cpp:189-203:
    // ShipInit::Init("*") runs again on every preset load, and a second
    // registration would draw the veil twice per frame.
    static bool registered = false;
    if (registered) {
        return;
    }
    registered = true;

    auto gui = Ship::Context::GetInstance()->GetWindow()->GetGui();
    sVeilWindow = std::make_shared<PauseVeilWindow>("gWindows.TerminaDSPauseVeil", "Pause Veil");
    gui->AddGuiWindow(sVeilWindow);
    sVeilWindow->Show();
});

} // namespace

extern "C" void TerminaDS_LoadVeilFont(void) {
    ImGuiIO& io = ImGui::GetIO();
    ImFontConfig config;
    config.MergeMode = false;
    sVeilFont = io.Fonts->AddFontFromMemoryCompressedBase85TTF(kCinzelCompressedBase85,
                                                              kWordmarkPx, &config);
}
```

- [ ] **Step 4: Record the inherited edit**

Add a row to the edit ledger in `docs/UPSTREAM.md`:

```markdown
| `engine/src/ship/window/gui/Gui.cpp` | Phase 4 Plan B | One call to `TerminaDS_LoadVeilFont()` after the FontAwesome registration, so the pause veil's Cinzel is in the font atlas. The atlas is built once during `Gui::Init`, so a later `AddFont` would not be rasterised. |
```

- [ ] **Step 5: Build the full pipeline**

Run: `sg docker -c './tools/build-apk.sh' 2>&1 | tail -20`

Expected: `==> APK: .../app-release.apk` after roughly 15-20 minutes. Background it; slow is not hung.

- [ ] **Step 6: Verify the natives actually shipped**

Run: `sg docker -c './.superpowers/codex-sol/verify-apk.sh'`

Expected: all three `Java_com_terminads_mm_*` symbols present as `T`, `mm.o2r` absent, `2ship.o2r` present. **Never suppress stderr on this check** — a silent `llvm-nm` looks identical to the `GLOB_RECURSE` failure it exists to catch.

- [ ] **Step 7: Run the suite**

Run: `sg docker -c './tools/run-unit-tests.sh'`

Expected: `PASS`, 171 tests (unchanged — this task adds no JVM tests).

- [ ] **Step 8: Commit**

```bash
git add mm/2s2h/TerminaDS/PauseVeil.cpp mm/2s2h/TerminaDS/CinzelFontData.cpp \
        mm/2s2h/TerminaDS/CinzelFontData.h engine/src/ship/window/gui/Gui.cpp \
        docs/UPSTREAM.md
git commit -m "feat(veil): engine-side ImGui PAUSED overlay"
```

---

### Task 11: Release keystore and the build-script mount fix

**Files:**
- Modify: `tools/build-apk.sh:14-22`
- Modify: `tools/assemble-apk.sh:17-23`
- Modify: `Android/app/build.gradle:57-72`
- Modify: `docs/HANDOFF.md` §10 (signing note)

**Interfaces:**
- Consumes: nothing.
- Produces: a release-signed APK when the four `ANDROID_KEY*` variables are set.

The signing plumbing looks complete but has never worked: both scripts forward the four environment variables but neither mounts the keystore directory into the container, so `storeFile file(releaseKeystorePath)` resolves a host path that does not exist inside it. `hasReleaseSigning` only checks the variables are non-empty, so the failure lands late and confusingly.

- [ ] **Step 1: Mount the keystore directory in both scripts**

In **both** `tools/build-apk.sh` (after line 17) and `tools/assemble-apk.sh` (after line 19), add:

```bash
    -v "${ANDROID_KEYSTORE_DIR:-${HOME}/.termina-ds}:/keystore:ro" \
```

and change the four `-e ANDROID_KEYSTORE_PATH=...` lines in both files to rewrite the path into the container:

```bash
    -e ANDROID_KEYSTORE_PATH="${ANDROID_KEYSTORE_PATH:+/keystore/$(basename "${ANDROID_KEYSTORE_PATH}")}" \
```

Add a comment above the mount in both files:

```bash
# The keystore lives outside the repo (tools/make-keystore.sh writes it to
# ~/.termina-ds). Gradle runs inside the container, so the directory has to be
# mounted and the path rewritten -- forwarding ANDROID_KEYSTORE_PATH alone
# hands Gradle a host path that does not exist in the container.
```

- [ ] **Step 2: Make the signing check fail loudly**

In `Android/app/build.gradle`, replace line 61 with:

```groovy
    def hasReleaseSigning = releaseKeystorePath && releaseKeystorePassword && releaseKeyAlias && releaseKeyPassword
    if (hasReleaseSigning && !file(releaseKeystorePath).exists()) {
        throw new GradleException(
            "ANDROID_KEYSTORE_PATH is set to '${releaseKeystorePath}' but no file is there. " +
            "Inside the build container the keystore is mounted at /keystore; check that " +
            "tools/build-apk.sh mounted \$ANDROID_KEYSTORE_DIR. Refusing to fall back to " +
            "debug signing silently -- a debug-signed 'release' APK cannot update a " +
            "release-signed install.")
    }
```

- [ ] **Step 3: Verify the guard fires**

Run: `sg docker -c 'ANDROID_KEYSTORE_PATH=/nonexistent/nope.jks ANDROID_KEYSTORE_PASSWORD=x ANDROID_KEY_ALIAS=x ANDROID_KEY_PASSWORD=x ./tools/assemble-apk.sh' 2>&1 | tail -5`

Expected: the build fails with the `no file is there` message, **not** a silent debug-signed APK.

- [ ] **Step 4: Confirm the unsigned path still works**

Run: `sg docker -c './tools/assemble-apk.sh' 2>&1 | tail -3`

Expected: an APK is produced, debug-signed as before.

- [ ] **Step 5: Update the handoff**

In `docs/HANDOFF.md` §10, replace "A real release keystore via `tools/make-keystore.sh` is planned Plan B work." with:

```markdown
Release signing works when all four `ANDROID_KEY*` variables are set; the
keystore directory (`~/.termina-ds` by default, override with
`ANDROID_KEYSTORE_DIR`) is mounted into the build container at `/keystore` and
the path is rewritten automatically. Generate the keystore once with
`tools/make-keystore.sh` — it is interactive and its passwords must never enter
a transcript. Switching from the debug key to the release key forces one
uninstall/reinstall on the device; game data in `/sdcard/TerminaDS` survives.
```

- [ ] **Step 6: Commit**

```bash
git add tools/build-apk.sh tools/assemble-apk.sh Android/app/build.gradle docs/HANDOFF.md
git commit -m "fix(build): mount the release keystore into the build container"
```

- [ ] **Step 7: Hand the interactive part to the user**

The keystore itself must be generated by the user — `keytool` prompts for passwords that must not enter a transcript. Ask them to run:

```
! ./tools/make-keystore.sh
```

then export the four variables in their own shell and confirm a release-signed build. Do not proceed to the device switch without them.

---

### Task 12: Candidate build, gate, and deploy

**Files:** none modified. This is an orchestrator task.

- [ ] **Step 1: Run the full suite**

Run: `sg docker -c './tools/run-unit-tests.sh'`

Expected: `PASS`, 171 tests, 0 failures/errors/skips. Record the exact count.

- [ ] **Step 2: Build the full pipeline**

Run: `sg docker -c './tools/build-apk.sh' 2>&1 | tail -20`

Expected: `==> APK: .../app-release.apk`. Native files changed in Tasks 2, 3, and 10, so `assemble-apk.sh` is forbidden here.

- [ ] **Step 3: Gate the APK**

Run: `sg docker -c './.superpowers/codex-sol/verify-apk.sh'`

Expected: three `Java_com_terminads_mm_*` symbols present as `T`; `mm.o2r` absent; `2ship.o2r` present.

- [ ] **Step 4: Ask the user to re-pair the Thor**

`adb devices` is empty at plan time. The user runs Settings → Developer options → Wireless debugging → "Pair device with pairing code" and supplies the pairing `IP:port` + 6-digit code, then the main-screen port.

```bash
adb pair <pair-ip>:<pair-port>   # user supplies the code
adb connect 10.0.0.30:<port>
adb devices
```

- [ ] **Step 5: Deploy**

Run: `./tools/deploy-apk.sh`

- [ ] **Step 6: Confirm the bridge came up**

Run: `adb logcat -d | grep TerminaDS | tail -20`

Expected: `Snapshot: publisher registered, first publish (schema 3, 39 slots)` — **schema 3, 39 slots**, which is the proof the v3 native half shipped. Also expect the second-screen line: `Showing second screen on display 4 (Screen-2)`.

- [ ] **Step 7: Hardware verification — TalkBack leads**

Both Thor displays are `FLAG_SECURE`; screenshots return black. Only the user can judge rendering. Deferred three phases, TalkBack goes first this time.

| # | Check |
|---|---|
| 1 | **TalkBack across the gameplay HUD** — vitals read as one node, stall chip announces, no per-poll chatter |
| 2 | **TalkBack across the pause root** — five rows, three announce "available in a future update" |
| 3 | **TalkBack across Options** — each row reads "label, value, control"; locked rows read "unavailable" |
| 4 | Pause root renders the full §5 styling: diamonds, sub-lines, warm SONG OF TIME |
| 5 | OPTIONS opens; back chevron and RESUME PLAY both work |
| 6 | Both tabs and all eight category chips render; six show the empty state |
| 7 | Internal Resolution visibly changes the top screen **live** |
| 8 | MSAA visibly changes edge quality **live** |
| 9 | Texture Filter visibly changes filtering **live** (the row BenMenu labels "needs reload") |
| 10 | Match Refresh Rate greys out the FPS row and rewords its description |
| 11 | Motion Blur = Always On un-greys Motion Blur Strength |
| 12 | Settings persist across an app restart (`CVAR_SAVE` debounce fired) |
| 13 | The PAUSED veil appears on the top screen while paused, with the Cinzel wordmark |
| 14 | The veil disappears immediately on resume |
| 15 | Pause/resume round-trip still feels instant |
| 16 | Carried from Phase 3: the >10-hearts visual, and a framerate spot-check with the second screen active |

**When a visual report and the model disagree, ask for a phone photo of the panel first.** A photo found in seconds a layout bug that two code reviews missed in Phase 3.

- [ ] **Step 8: Write the verification document**

Create `docs/verification/2026-07-25-phase-4-plan-b-thor.md` following the shape of `docs/verification/2026-07-25-phase-4a-thor.md`: what Plan B delivers, the pre-device gate table, the hardware checklist with results, observations and dispositions, review-loop notes, and what is carried forward.

- [ ] **Step 9: Update the handoff and ledger**

- `docs/HANDOFF.md`: mark Plan B complete, update the test count, replace §11 with the next phase's scope, and move the resolved items out of §8's "untested verification tail".
- `.superpowers/codex-sol/progress.md`: append the Plan B task-by-task record.

- [ ] **Step 10: Commit**

```bash
git add docs/verification/2026-07-25-phase-4-plan-b-thor.md docs/HANDOFF.md \
        .superpowers/codex-sol/progress.md
git commit -m "docs: record Phase 4 Plan B hardware verification"
```

---

## Notes on sequencing

- **Tasks 2, 3, and 10 change native code.** A full `./tools/build-apk.sh` (15-20 min) is required after any of them; `assemble-apk.sh` would compile green and ship without the new code. Tasks 4-9 are Kotlin-only and can iterate on the fast path.
- **Task 1 is the phase's main risk.** If the Robolectric spike cannot be made green inside the Docker image, Tasks 6, 7, and 8 drop their `@RunWith(RobolectricTestRunner::class)` classes and keep their pure-model tests. Task 4, 5, and 9 are unaffected — they have no Compose dependency at all.
- **Test counts in this plan assume every task lands.** If Task 1 fails, subtract the Robolectric tests: Task 6 loses 2, Task 7 loses 8, Task 8 loses 11, giving 150 rather than 171.
- **Task 11 Step 7 blocks on the user.** The keystore generation is interactive and its secrets must not enter the transcript.
