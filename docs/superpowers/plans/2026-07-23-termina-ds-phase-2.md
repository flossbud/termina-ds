# Termina DS Phase 2 — Read-Only Game-State Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish a per-frame snapshot of `gSaveContext`/`PlayState`/`Player` from the game thread and render it live on the Thor's bottom screen.

**Architecture:** A `GameInteractor::OnGameStateUpdate` hook runs on `SDLThread` once per frame, dereferences the game's pointers, and publishes 27 `int32_t` slots under a seqlock. Compose polls at 10 Hz on the Android main thread, copies the slots through one JNI call, and decodes them into an immutable Kotlin data class. Dereferences happen only on the game thread; the UI thread sees plain integers.

**Tech Stack:** C++17 (`std::atomic`, no locks), JNI, Kotlin 2.0.21, Jetpack Compose, JUnit 4, Docker build (`termina-ds-build:latest`), Gradle 8.3.2, NDK 26.0.10792818, arm64-v8a.

**Spec:** `docs/superpowers/specs/2026-07-23-termina-ds-phase-2-state-bridge-design.md`

## Global Constraints

- **Do not modify any inherited file.** Only the eight files named in this plan change. If a task appears to require editing an inherited file, stop and report — `RegisterShipInitFunc` exists to make that unnecessary.
- **Main thread only** for anything touching the Presentation or its lifecycle owner. Never introduce a thread, executor, or `Handler`. `PresentationLifecycleOwner` uses `LifecycleRegistry.createUnsafe`, which drops the main-thread assertion — violations corrupt state silently instead of crashing.
- **`compileSdk 34`, `targetSdk 33`, `minSdk 24`, `arm64-v8a` only.** No Gradle change in this phase.
- **New native code lives in `mm/2s2h/TerminaDS/`.** A recursive CMake glob picks it up; no `CMakeLists.txt` edit.
- **`mm.o2r` must never ship in the APK.** Do not weaken `verifyBundledAssets`.
- **Nothing in this phase writes game state.** Reads only. No non-`const` access to any engine struct.
- **`TDS_SNAP_SCHEMA_VERSION` is 1** for this phase and must match `GameSnapshotLayout.SCHEMA_VERSION` in Kotlin.
- **Commits** are authored as `jaret <jaretmsanchez@gmail.com>`; never involve the `WheelHouse-Software` account.
- **Builds take 8–19 minutes.** Run them backgrounded and poll. A slow build is not a hung build.

## File Structure

| File | Status | Responsibility |
|---|---|---|
| `mm/2s2h/TerminaDS/GameSnapshot.h` | create | Layout contract: slot indices, flag bits, schema version, reader declaration |
| `mm/2s2h/TerminaDS/SnapshotPublisher.cpp` | create | Game-thread sampling + seqlock. **The only file that dereferences game pointers.** |
| `mm/2s2h/TerminaDS/NativeBridge.cpp` | modify | JNI seam only. No engine internals. |
| `Android/app/src/main/java/com/terminads/mm/GameSnapshot.kt` | create | Kotlin mirror of the layout + pure decoder |
| `Android/app/src/main/java/com/terminads/mm/GameSnapshotPoller.kt` | create | Cadence, staleness, bridge-state classification |
| `Android/app/src/main/java/com/terminads/mm/NativeBridge.kt` | modify | Sole Kotlin caller of native |
| `Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenHost.kt` | modify | Throwaway debug readout |
| `Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenPresentation.kt` | modify | Wiring: constructs the poller, passes it to the host |
| `Android/app/src/test/java/com/terminads/mm/GameSnapshotTest.kt` | create | Decoder tests |
| `Android/app/src/test/java/com/terminads/mm/GameSnapshotPollerTest.kt` | create | Poller/staleness tests |
| `tools/run-unit-tests.sh` | create | One-liner for the JVM test target |

---

### Task 1: Kotlin layout contract and decoder

Pure Kotlin, no device, no native. Runs in ~1 minute instead of ~19, so it goes first and locks the contract every later task consumes.

**Files:**
- Create: `Android/app/src/main/java/com/terminads/mm/GameSnapshot.kt`
- Create: `Android/app/src/test/java/com/terminads/mm/GameSnapshotTest.kt`
- Create: `tools/run-unit-tests.sh`

**Interfaces:**
- Consumes: nothing.
- Produces: `GameSnapshotLayout` (all `IDX_*`, `FLAG_*`, `SLOT_COUNT`, `SCHEMA_VERSION`), `GameSnapshot` data class, `SnapshotDecode` sealed interface (`Ok`, `SchemaMismatch`), `decodeSnapshot(IntArray): SnapshotDecode`.

- [ ] **Step 1: Create the unit-test runner script**

`tools/run-unit-tests.sh`:

```bash
#!/usr/bin/env bash
# Run the Termina DS JVM unit tests inside the Docker toolchain image.
#
# These are plain JUnit tests -- no NDK, no device, no APK assembly -- so this
# takes about a minute rather than the 8-19 minutes a full ./tools/build-apk.sh
# costs. Use it for every Kotlin change; save the full build for native changes.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${TERMINA_BUILD_IMAGE:-termina-ds-build:latest}"

docker run --rm \
    -v "${REPO_ROOT}:/workspace" \
    -v "termina-ds-gradle:/root/.gradle" \
    -v "termina-ds-android-home:/root/.android" \
    -w /workspace/Android \
    "${IMAGE}" ./gradlew --no-daemon :app:testReleaseUnitTest "$@"
```

Then: `chmod +x tools/run-unit-tests.sh`

- [ ] **Step 2: Verify the runner works against the existing 13 tests**

Run: `./tools/run-unit-tests.sh`
Expected: `BUILD SUCCESSFUL`. This confirms the script before any new test depends on it.

- [ ] **Step 3: Write the failing tests**

`Android/app/src/test/java/com/terminads/mm/GameSnapshotTest.kt`:

```kotlin
package com.terminads.mm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSnapshotTest {

    /** A well-formed payload with every slot zeroed except the schema version. */
    private fun emptyPayload() = IntArray(GameSnapshotLayout.SLOT_COUNT).also {
        it[GameSnapshotLayout.IDX_SCHEMA_VERSION] = GameSnapshotLayout.SCHEMA_VERSION
    }

    private fun decodeOk(values: IntArray): GameSnapshot {
        val result = decodeSnapshot(values)
        assertTrue("expected Ok but was $result", result is SnapshotDecode.Ok)
        return (result as SnapshotDecode.Ok).snapshot
    }

    @Test
    fun slotCountMatchesTheDocumentedLayout() {
        // Guards the hand-written mirror of mm/2s2h/TerminaDS/GameSnapshot.h.
        assertEquals(27, GameSnapshotLayout.SLOT_COUNT)
        assertEquals(0, GameSnapshotLayout.IDX_SCHEMA_VERSION)
        assertEquals(26, GameSnapshotLayout.IDX_PLAYER_YAW)
    }

    @Test
    fun reportsSchemaMismatchInsteadOfDecodingGarbage() {
        val values = emptyPayload()
        values[GameSnapshotLayout.IDX_SCHEMA_VERSION] = 99
        val result = decodeSnapshot(values)
        assertTrue(result is SnapshotDecode.SchemaMismatch)
        assertEquals(99, (result as SnapshotDecode.SchemaMismatch).nativeVersion)
        assertEquals(GameSnapshotLayout.SCHEMA_VERSION, result.expected)
    }

    @Test
    fun decodesVitals() {
        val values = emptyPayload()
        values[GameSnapshotLayout.IDX_HEALTH] = 48
        values[GameSnapshotLayout.IDX_HEALTH_CAPACITY] = 80
        values[GameSnapshotLayout.IDX_MAGIC] = 24
        values[GameSnapshotLayout.IDX_MAGIC_CAPACITY] = 48
        values[GameSnapshotLayout.IDX_MAGIC_LEVEL] = 1
        values[GameSnapshotLayout.IDX_RUPEES] = 137

        val snapshot = decodeOk(values)

        assertEquals(48, snapshot.health)
        assertEquals(80, snapshot.healthCapacity)
        assertEquals(24, snapshot.magic)
        assertEquals(48, snapshot.magicCapacity)
        assertEquals(1, snapshot.magicLevel)
        assertEquals(137, snapshot.rupees)
    }

    @Test
    fun preservesNegativeValues() {
        // roomNum is s8 and is -1 when invalid; native sign-extends it. If the
        // decoder ever masks instead of sign-extending, -1 becomes 255 and the
        // readout shows a plausible-looking room that does not exist.
        val values = emptyPayload()
        values[GameSnapshotLayout.IDX_ROOM_NUM] = -1
        values[GameSnapshotLayout.IDX_HEALTH] = -16

        val snapshot = decodeOk(values)

        assertEquals(-1, snapshot.roomNum)
        assertEquals(-16, snapshot.health)
    }

    @Test
    fun decodesPositionsFromRawFloatBits() {
        val values = emptyPayload()
        values[GameSnapshotLayout.IDX_PLAYER_X] = (-1234.5f).toRawBits()
        values[GameSnapshotLayout.IDX_PLAYER_Y] = 0.0f.toRawBits()
        values[GameSnapshotLayout.IDX_PLAYER_Z] = 987.25f.toRawBits()

        val snapshot = decodeOk(values)

        assertEquals(-1234.5f, snapshot.playerX, 0.0f)
        assertEquals(0.0f, snapshot.playerY, 0.0f)
        assertEquals(987.25f, snapshot.playerZ, 0.0f)
    }

    @Test
    fun decodesFlags() {
        val values = emptyPayload()
        values[GameSnapshotLayout.IDX_FLAGS] =
            GameSnapshotLayout.FLAG_PLAY_STATE_VALID or GameSnapshotLayout.FLAG_IS_NIGHT

        val snapshot = decodeOk(values)

        assertTrue(snapshot.hasPlayState)
        assertFalse(snapshot.hasPlayer)
        assertTrue(snapshot.isNight)
        assertFalse(snapshot.doubleDefense)
    }

    @Test
    fun decodesButtonItemsAndAmmoInOrder() {
        val values = emptyPayload()
        values[GameSnapshotLayout.IDX_BTN_ITEM_B] = 10
        values[GameSnapshotLayout.IDX_BTN_ITEM_C_LEFT] = 11
        values[GameSnapshotLayout.IDX_BTN_ITEM_C_DOWN] = 12
        values[GameSnapshotLayout.IDX_BTN_ITEM_C_RIGHT] = 13
        values[GameSnapshotLayout.IDX_BTN_AMMO_B] = 20
        values[GameSnapshotLayout.IDX_BTN_AMMO_C_LEFT] = 21
        values[GameSnapshotLayout.IDX_BTN_AMMO_C_DOWN] = 22
        values[GameSnapshotLayout.IDX_BTN_AMMO_C_RIGHT] = 23

        val snapshot = decodeOk(values)

        assertEquals(listOf(10, 11, 12, 13), snapshot.buttonItems)
        assertEquals(listOf(20, 21, 22, 23), snapshot.buttonAmmo)
    }

    @Test
    fun equalSnapshotsCompareEqualSoComposeCanSkipRecomposition() {
        // The whole no-redraw-when-nothing-changed story depends on this. It
        // would silently break if any field were ever changed to an IntArray,
        // which compares by reference.
        val a = decodeOk(emptyPayload())
        val b = decodeOk(emptyPayload())
        assertEquals(a, b)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnUndersizedArray() {
        decodeSnapshot(IntArray(GameSnapshotLayout.SLOT_COUNT - 1))
    }
}
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `./tools/run-unit-tests.sh --tests 'com.terminads.mm.GameSnapshotTest'`
Expected: FAIL — compilation errors, `Unresolved reference: GameSnapshotLayout`.

- [ ] **Step 5: Write the implementation**

`Android/app/src/main/java/com/terminads/mm/GameSnapshot.kt`:

```kotlin
package com.terminads.mm

/**
 * Layout of the Phase 2 game-state payload.
 *
 * SOURCE OF TRUTH: mm/2s2h/TerminaDS/GameSnapshot.h. These constants are a hand
 * maintained mirror of the enum there. Any change to that header must be
 * reflected here AND must bump SCHEMA_VERSION on both sides -- decodeSnapshot
 * then reports the mismatch instead of decoding garbage.
 *
 * Codegen is not worth it for 27 integers; the runtime guard plus
 * GameSnapshotTest.slotCountMatchesTheDocumentedLayout is the safety net.
 */
object GameSnapshotLayout {
    const val SCHEMA_VERSION = 1

    const val IDX_SCHEMA_VERSION = 0
    const val IDX_FRAME_COUNTER = 1
    const val IDX_FLAGS = 2

    const val IDX_HEALTH = 3
    const val IDX_HEALTH_CAPACITY = 4
    const val IDX_MAGIC = 5
    const val IDX_MAGIC_CAPACITY = 6
    const val IDX_MAGIC_LEVEL = 7
    const val IDX_RUPEES = 8

    const val IDX_PLAYER_FORM = 9
    const val IDX_EQUIPPED_MASK = 10
    const val IDX_DAY = 11
    const val IDX_TIME_OF_DAY = 12

    const val IDX_BTN_ITEM_B = 13
    const val IDX_BTN_ITEM_C_LEFT = 14
    const val IDX_BTN_ITEM_C_DOWN = 15
    const val IDX_BTN_ITEM_C_RIGHT = 16
    const val IDX_BTN_AMMO_B = 17
    const val IDX_BTN_AMMO_C_LEFT = 18
    const val IDX_BTN_AMMO_C_DOWN = 19
    const val IDX_BTN_AMMO_C_RIGHT = 20

    const val IDX_SCENE_ID = 21
    const val IDX_ROOM_NUM = 22
    const val IDX_PLAYER_X = 23
    const val IDX_PLAYER_Y = 24
    const val IDX_PLAYER_Z = 25
    const val IDX_PLAYER_YAW = 26

    const val SLOT_COUNT = 27

    const val FLAG_PLAY_STATE_VALID = 1 shl 0
    const val FLAG_PLAYER_VALID = 1 shl 1
    const val FLAG_IS_NIGHT = 1 shl 2
    const val FLAG_DOUBLE_DEFENSE = 1 shl 3
}

/**
 * One frame of read-only game state.
 *
 * Every field is a plain value copied while the game thread held valid
 * pointers. Nothing here refers to engine memory.
 *
 * buttonItems/buttonAmmo are List, not IntArray, deliberately: this is a data
 * class, and IntArray would make equals() reference-based, defeating Compose's
 * skip-recomposition-when-equal optimisation.
 *
 * When hasPlayState is false the world fields (sceneId, roomNum, position, yaw)
 * are zero rather than stale -- the game thread had no world to read.
 */
data class GameSnapshot(
    val frameCounter: Int,
    val health: Int,
    val healthCapacity: Int,
    val magic: Int,
    val magicCapacity: Int,
    val magicLevel: Int,
    val rupees: Int,
    val playerForm: Int,
    val equippedMask: Int,
    val day: Int,
    val timeOfDay: Int,
    val isNight: Boolean,
    val doubleDefense: Boolean,
    /** B, C-left, C-down, C-right. */
    val buttonItems: List<Int>,
    /** Ammo for the corresponding entry in [buttonItems]; 0 where not applicable. */
    val buttonAmmo: List<Int>,
    val hasPlayState: Boolean,
    val hasPlayer: Boolean,
    val sceneId: Int,
    val roomNum: Int,
    val playerX: Float,
    val playerY: Float,
    val playerZ: Float,
    val playerYaw: Int,
)

/** Outcome of decoding a raw payload. */
sealed interface SnapshotDecode {
    data class Ok(val snapshot: GameSnapshot) : SnapshotDecode

    /** Native was built from a different layout than this Kotlin mirror. */
    data class SchemaMismatch(val nativeVersion: Int, val expected: Int) : SnapshotDecode
}

/**
 * Decode a raw payload. Pure: no Android dependencies, no native calls.
 *
 * @throws IllegalArgumentException if [values] is smaller than SLOT_COUNT. That
 *   is a programming error -- the poller always allocates exactly SLOT_COUNT --
 *   not a runtime condition to render.
 */
fun decodeSnapshot(values: IntArray): SnapshotDecode {
    require(values.size >= GameSnapshotLayout.SLOT_COUNT) {
        "snapshot payload has ${values.size} slots, need ${GameSnapshotLayout.SLOT_COUNT}"
    }

    val version = values[GameSnapshotLayout.IDX_SCHEMA_VERSION]
    if (version != GameSnapshotLayout.SCHEMA_VERSION) {
        return SnapshotDecode.SchemaMismatch(version, GameSnapshotLayout.SCHEMA_VERSION)
    }

    val flags = values[GameSnapshotLayout.IDX_FLAGS]
    fun flag(bit: Int) = (flags and bit) != 0

    return SnapshotDecode.Ok(
        GameSnapshot(
            frameCounter = values[GameSnapshotLayout.IDX_FRAME_COUNTER],
            health = values[GameSnapshotLayout.IDX_HEALTH],
            healthCapacity = values[GameSnapshotLayout.IDX_HEALTH_CAPACITY],
            magic = values[GameSnapshotLayout.IDX_MAGIC],
            magicCapacity = values[GameSnapshotLayout.IDX_MAGIC_CAPACITY],
            magicLevel = values[GameSnapshotLayout.IDX_MAGIC_LEVEL],
            rupees = values[GameSnapshotLayout.IDX_RUPEES],
            playerForm = values[GameSnapshotLayout.IDX_PLAYER_FORM],
            equippedMask = values[GameSnapshotLayout.IDX_EQUIPPED_MASK],
            day = values[GameSnapshotLayout.IDX_DAY],
            timeOfDay = values[GameSnapshotLayout.IDX_TIME_OF_DAY],
            isNight = flag(GameSnapshotLayout.FLAG_IS_NIGHT),
            doubleDefense = flag(GameSnapshotLayout.FLAG_DOUBLE_DEFENSE),
            buttonItems = listOf(
                values[GameSnapshotLayout.IDX_BTN_ITEM_B],
                values[GameSnapshotLayout.IDX_BTN_ITEM_C_LEFT],
                values[GameSnapshotLayout.IDX_BTN_ITEM_C_DOWN],
                values[GameSnapshotLayout.IDX_BTN_ITEM_C_RIGHT],
            ),
            buttonAmmo = listOf(
                values[GameSnapshotLayout.IDX_BTN_AMMO_B],
                values[GameSnapshotLayout.IDX_BTN_AMMO_C_LEFT],
                values[GameSnapshotLayout.IDX_BTN_AMMO_C_DOWN],
                values[GameSnapshotLayout.IDX_BTN_AMMO_C_RIGHT],
            ),
            hasPlayState = flag(GameSnapshotLayout.FLAG_PLAY_STATE_VALID),
            hasPlayer = flag(GameSnapshotLayout.FLAG_PLAYER_VALID),
            sceneId = values[GameSnapshotLayout.IDX_SCENE_ID],
            roomNum = values[GameSnapshotLayout.IDX_ROOM_NUM],
            playerX = Float.fromBits(values[GameSnapshotLayout.IDX_PLAYER_X]),
            playerY = Float.fromBits(values[GameSnapshotLayout.IDX_PLAYER_Y]),
            playerZ = Float.fromBits(values[GameSnapshotLayout.IDX_PLAYER_Z]),
            playerYaw = values[GameSnapshotLayout.IDX_PLAYER_YAW],
        )
    )
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./tools/run-unit-tests.sh --tests 'com.terminads.mm.GameSnapshotTest'`
Expected: `BUILD SUCCESSFUL`, 9 tests passing.

- [ ] **Step 7: Run the full suite to confirm nothing regressed**

Run: `./tools/run-unit-tests.sh`
Expected: `BUILD SUCCESSFUL`, 22 tests total (13 existing + 9 new).

- [ ] **Step 8: Commit**

```bash
git add Android/app/src/main/java/com/terminads/mm/GameSnapshot.kt \
        Android/app/src/test/java/com/terminads/mm/GameSnapshotTest.kt \
        tools/run-unit-tests.sh
git -c user.name=jaret -c user.email=jaretmsanchez@gmail.com commit -m "feat(bridge): add the Phase 2 snapshot layout and decoder

The int array IS the struct: one index table is the whole layout
contract, so there is no separate definition for marshalling code to
drift away from. SCHEMA_VERSION makes a native/Kotlin layout mismatch
report itself instead of rendering plausible-looking wrong numbers.

Also adds tools/run-unit-tests.sh -- the JVM tests need no NDK and run
in about a minute, versus 8-19 for a full APK build.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Native layout header and snapshot publisher

The subtle task. It owns the seqlock and every pointer dereference in the phase.

**Files:**
- Create: `mm/2s2h/TerminaDS/GameSnapshot.h`
- Create: `mm/2s2h/TerminaDS/SnapshotPublisher.cpp`

**Interfaces:**
- Consumes: `GameInteractor::OnGameStateUpdate`, `RegisterShipInitFunc` (`mm/2s2h/ShipInit.hpp`), engine globals `gSaveContext` and `gPlayState`.
- Produces: `bool TerminaDS_ReadSnapshot(int32_t* out, int count)` with C linkage, declared in `GameSnapshot.h`. Task 3 calls exactly this.

There is no JVM test harness for native code in this tree. Verification is the build in Task 3, `llvm-nm` for the symbol, and a first-publish log line for the registration. Do not claim this task works before Task 3's build passes.

- [ ] **Step 1: Write the layout header**

`mm/2s2h/TerminaDS/GameSnapshot.h`:

```c
/*
 * Termina DS: layout contract for the Phase 2 read-only state bridge.
 *
 * The payload is an int32_t[TDS_SNAP_COUNT], not a struct. The enum below IS
 * the contract, which is why there is no separate struct for the marshalling
 * code to drift away from, and why padding and endianness never enter into it.
 *
 * Android/app/src/main/java/com/terminads/mm/GameSnapshot.kt mirrors these
 * indices by hand. BUMP TDS_SNAP_SCHEMA_VERSION ON ANY CHANGE BELOW and bump
 * GameSnapshotLayout.SCHEMA_VERSION to match -- the Kotlin decoder then reports
 * the mismatch rather than decoding garbage.
 */
#ifndef TERMINADS_GAME_SNAPSHOT_H
#define TERMINADS_GAME_SNAPSHOT_H

#include <stdbool.h>
#include <stdint.h>

#define TDS_SNAP_SCHEMA_VERSION 1

enum TdsSnapshotIndex {
    TDS_SNAP_IDX_SCHEMA_VERSION = 0,
    TDS_SNAP_IDX_FRAME_COUNTER,
    TDS_SNAP_IDX_FLAGS,

    TDS_SNAP_IDX_HEALTH,
    TDS_SNAP_IDX_HEALTH_CAPACITY,
    TDS_SNAP_IDX_MAGIC,
    TDS_SNAP_IDX_MAGIC_CAPACITY,
    TDS_SNAP_IDX_MAGIC_LEVEL,
    TDS_SNAP_IDX_RUPEES,

    TDS_SNAP_IDX_PLAYER_FORM,
    TDS_SNAP_IDX_EQUIPPED_MASK,
    TDS_SNAP_IDX_DAY,
    TDS_SNAP_IDX_TIME_OF_DAY,

    TDS_SNAP_IDX_BTN_ITEM_B,
    TDS_SNAP_IDX_BTN_ITEM_C_LEFT,
    TDS_SNAP_IDX_BTN_ITEM_C_DOWN,
    TDS_SNAP_IDX_BTN_ITEM_C_RIGHT,
    TDS_SNAP_IDX_BTN_AMMO_B,
    TDS_SNAP_IDX_BTN_AMMO_C_LEFT,
    TDS_SNAP_IDX_BTN_AMMO_C_DOWN,
    TDS_SNAP_IDX_BTN_AMMO_C_RIGHT,

    TDS_SNAP_IDX_SCENE_ID,
    TDS_SNAP_IDX_ROOM_NUM,
    TDS_SNAP_IDX_PLAYER_X,
    TDS_SNAP_IDX_PLAYER_Y,
    TDS_SNAP_IDX_PLAYER_Z,
    TDS_SNAP_IDX_PLAYER_YAW,

    TDS_SNAP_COUNT
};

enum TdsSnapshotFlag {
    TDS_SNAP_FLAG_PLAY_STATE_VALID = 1 << 0,
    TDS_SNAP_FLAG_PLAYER_VALID = 1 << 1,
    TDS_SNAP_FLAG_IS_NIGHT = 1 << 2,
    TDS_SNAP_FLAG_DOUBLE_DEFENSE = 1 << 3
};

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Copy the most recently published snapshot into `out`. Safe to call from any
 * thread; intended for the Android main thread while the game thread publishes.
 *
 * Returns false if `out` is null, if `count` < TDS_SNAP_COUNT, or if the
 * seqlock retry budget was exhausted (a transient collision -- the caller
 * should keep whatever it had). Never blocks the publishing thread.
 *
 * Before the first publish this succeeds and returns an all-zero payload, so
 * TDS_SNAP_IDX_FRAME_COUNTER == 0 means "the publisher has not run yet".
 */
bool TerminaDS_ReadSnapshot(int32_t* out, int count);

#ifdef __cplusplus
}
#endif

#endif /* TERMINADS_GAME_SNAPSHOT_H */
```

- [ ] **Step 2: Write the publisher**

`mm/2s2h/TerminaDS/SnapshotPublisher.cpp`:

```cpp
/*
 * Termina DS: game-thread half of the Phase 2 read-only state bridge.
 *
 * THIS IS THE ONLY FILE IN THE PROJECT THAT DEREFERENCES GAME POINTERS.
 * That containment is deliberate -- gPlayState is NULL across every scene
 * transition (z_play.c:481) and the player actor can be absent while a
 * PlayState exists, so these reads are only safe on the thread that owns those
 * lifetimes. Everything downstream sees plain integers.
 *
 * Registration uses RegisterShipInitFunc, so no inherited file is edited.
 */
#include "GameSnapshot.h"

#include <atomic>
#include <cstring>

#include "2s2h/GameInteractor/GameInteractor.h"
#include "2s2h/ShipInit.hpp"

extern "C" {
#include "z64save.h"
#include "z64play.h"
#include "z64interface.h"
#include "macros.h"
#include "variables.h"

extern SaveContext gSaveContext;
extern PlayState* gPlayState;
}

#ifdef __ANDROID__
#include <android/log.h>
#endif

namespace {

// Seqlock: odd while a write is in progress, even when the values are stable.
std::atomic<uint32_t> sSeq{ 0 };

// Relaxed atomics rather than plain int32_t. A seqlock over non-atomic data is
// formally a data race under the C++ memory model even though it works in
// practice; on arm64 relaxed atomics compile to ordinary loads and stores, so
// this is free at runtime and makes the code defined rather than lucky.
std::atomic<int32_t> sValues[TDS_SNAP_COUNT];

// Touched only by the game thread inside Publish().
uint32_t sFrameCounter = 0;
bool sLoggedFirstPublish = false;

int32_t FloatBits(float value) {
    int32_t bits;
    std::memcpy(&bits, &value, sizeof(bits));
    return bits;
}

// Resolve a button item's ammo without trusting the item id.
//
// AMMO(item) expands to inventory.ammo[gItemSlots[item]], indexing two arrays
// in a row with no bounds check. An empty button holds ITEM_NONE (0xFF), which
// is past the end of gItemSlots, and plenty of valid items map to a slot that
// is past the end of ammo[]. Both are out-of-bounds reads on the game thread.
int32_t ResolveAmmo(uint8_t item) {
    if (item >= ARRAY_COUNT(gItemSlots)) {
        return 0;
    }
    uint8_t slot = gItemSlots[item];
    if (slot >= ARRAY_COUNT(gSaveContext.save.saveInfo.inventory.ammo)) {
        return 0;
    }
    return gSaveContext.save.saveInfo.inventory.ammo[slot];
}

void Publish() {
    int32_t v[TDS_SNAP_COUNT] = { 0 };
    int32_t flags = 0;

    v[TDS_SNAP_IDX_SCHEMA_VERSION] = TDS_SNAP_SCHEMA_VERSION;
    v[TDS_SNAP_IDX_FRAME_COUNTER] = static_cast<int32_t>(++sFrameCounter);

    // gSaveContext is a static struct and is always addressable, including on
    // the title screen and file select.
    const SavePlayerData& playerData = gSaveContext.save.saveInfo.playerData;
    v[TDS_SNAP_IDX_HEALTH] = playerData.health;
    v[TDS_SNAP_IDX_HEALTH_CAPACITY] = playerData.healthCapacity;
    v[TDS_SNAP_IDX_MAGIC] = playerData.magic;
    v[TDS_SNAP_IDX_MAGIC_CAPACITY] = gSaveContext.magicCapacity;
    v[TDS_SNAP_IDX_MAGIC_LEVEL] = playerData.magicLevel;
    v[TDS_SNAP_IDX_RUPEES] = playerData.rupees;
    if (playerData.doubleDefense) {
        flags |= TDS_SNAP_FLAG_DOUBLE_DEFENSE;
    }

    v[TDS_SNAP_IDX_PLAYER_FORM] = gSaveContext.save.playerForm;
    v[TDS_SNAP_IDX_EQUIPPED_MASK] = gSaveContext.save.equippedMask;
    v[TDS_SNAP_IDX_DAY] = gSaveContext.save.day;
    v[TDS_SNAP_IDX_TIME_OF_DAY] = gSaveContext.save.time;
    if (gSaveContext.save.isNight) {
        flags |= TDS_SNAP_FLAG_IS_NIGHT;
    }

    // GET_CUR_FORM_BTN_ITEM already encodes MM's rule that B is per-form while
    // the C buttons are shared across forms. Use it rather than indexing
    // buttonItems[form][n] by hand and reimplementing that rule wrongly.
    static const int32_t kSlots[4] = { EQUIP_SLOT_B, EQUIP_SLOT_C_LEFT, EQUIP_SLOT_C_DOWN,
                                       EQUIP_SLOT_C_RIGHT };
    for (int32_t i = 0; i < 4; i++) {
        uint8_t item = GET_CUR_FORM_BTN_ITEM(kSlots[i]);
        v[TDS_SNAP_IDX_BTN_ITEM_B + i] = item;
        v[TDS_SNAP_IDX_BTN_AMMO_B + i] = ResolveAmmo(item);
    }

    // Everything below here is pointer-guarded. When a guard fails the
    // corresponding slots stay zero -- never stale -- so the UI cannot present
    // a previous scene's position as current.
    PlayState* play = gPlayState;
    if (play != NULL) {
        flags |= TDS_SNAP_FLAG_PLAY_STATE_VALID;
        v[TDS_SNAP_IDX_SCENE_ID] = play->sceneId;
        v[TDS_SNAP_IDX_ROOM_NUM] = play->roomCtx.curRoom.num;

        Player* player = GET_PLAYER(play);
        if (player != NULL) {
            flags |= TDS_SNAP_FLAG_PLAYER_VALID;
            v[TDS_SNAP_IDX_PLAYER_X] = FloatBits(player->actor.world.pos.x);
            v[TDS_SNAP_IDX_PLAYER_Y] = FloatBits(player->actor.world.pos.y);
            v[TDS_SNAP_IDX_PLAYER_Z] = FloatBits(player->actor.world.pos.z);
            v[TDS_SNAP_IDX_PLAYER_YAW] = player->actor.shape.rot.y;
        }
    }

    v[TDS_SNAP_IDX_FLAGS] = flags;

    // Seqlock write. The release fence keeps the value stores from being
    // hoisted above the odd sequence number; the release store publishes them.
    uint32_t seq = sSeq.load(std::memory_order_relaxed);
    sSeq.store(seq + 1, std::memory_order_relaxed);
    std::atomic_thread_fence(std::memory_order_release);

    for (int32_t i = 0; i < TDS_SNAP_COUNT; i++) {
        sValues[i].store(v[i], std::memory_order_relaxed);
    }

    sSeq.store(seq + 2, std::memory_order_release);

#ifdef __ANDROID__
    if (!sLoggedFirstPublish) {
        sLoggedFirstPublish = true;
        // Both Thor displays are FLAG_SECURE, so the bottom screen cannot be
        // screenshotted. This line is how logcat proves the static
        // registration ran without needing the user to look at the device.
        __android_log_print(ANDROID_LOG_INFO, "TerminaDS",
                            "Snapshot: publisher registered, first publish (schema %d, %d slots)",
                            TDS_SNAP_SCHEMA_VERSION, TDS_SNAP_COUNT);
    }
#endif
}

static RegisterShipInitFunc sRegisterSnapshotPublisher([]() {
    GameInteractor::Instance->RegisterGameHook<GameInteractor::OnGameStateUpdate>(Publish);
});

} // namespace

extern "C" bool TerminaDS_ReadSnapshot(int32_t* out, int count) {
    if (out == NULL || count < TDS_SNAP_COUNT) {
        return false;
    }

    // Four attempts is generous: the writer holds the array for well under a
    // microsecond, once every 16.6 ms.
    for (int attempt = 0; attempt < 4; attempt++) {
        uint32_t before = sSeq.load(std::memory_order_acquire);
        if ((before & 1u) != 0u) {
            continue; // a write is in progress
        }

        for (int32_t i = 0; i < TDS_SNAP_COUNT; i++) {
            out[i] = sValues[i].load(std::memory_order_relaxed);
        }

        std::atomic_thread_fence(std::memory_order_acquire);
        if (sSeq.load(std::memory_order_relaxed) == before) {
            return true;
        }
    }

    return false;
}
```

- [ ] **Step 3: Commit**

The build that exercises this happens in Task 3, which adds the only caller. Commit now so the publisher is reviewable on its own.

```bash
git add mm/2s2h/TerminaDS/GameSnapshot.h mm/2s2h/TerminaDS/SnapshotPublisher.cpp
git -c user.name=jaret -c user.email=jaretmsanchez@gmail.com commit -m "feat(bridge): publish a per-frame game-state snapshot

Samples gSaveContext, gPlayState and Player once per frame from
GameInteractor's OnGameStateUpdate hook and publishes 27 int32 slots
under a seqlock. Registered via RegisterShipInitFunc, so no inherited
file is touched.

Every dereference happens here, on the game thread, where the engine
guarantees the pointers are sane. gPlayState is NULL across every scene
transition, so a main-thread read would be a segfault rather than a torn
value -- no amount of care on the Kotlin side could make it safe.

Ammo resolution bounds-checks both indirections: AMMO(item) expands to
inventory.ammo[gItemSlots[item]] with no checks, and an empty button
holds ITEM_NONE (0xFF), which is past the end of gItemSlots.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: JNI entry point

Mechanical, but it is the first task that can actually be verified, so it carries the build.

**Files:**
- Modify: `mm/2s2h/TerminaDS/NativeBridge.cpp`
- Modify: `Android/app/src/main/java/com/terminads/mm/NativeBridge.kt`

**Interfaces:**
- Consumes: `TerminaDS_ReadSnapshot(int32_t*, int)` from Task 2; `GameSnapshotLayout.SLOT_COUNT` from Task 1.
- Produces: `NativeBridge.readSnapshot(out: IntArray): SnapshotReadResult` and `enum class SnapshotReadResult { OK, UNAVAILABLE, RETRY_EXHAUSTED }`. Task 4 consumes exactly these.

- [ ] **Step 1: Add the native entry point**

Replace the whole of `mm/2s2h/TerminaDS/NativeBridge.cpp` with:

```cpp
/*
 * Termina DS: JNI seam between the Compose second screen and the game core.
 *
 * This file is the seam and nothing more -- it holds no engine includes and
 * dereferences no game pointers. Snapshot sampling lives in
 * SnapshotPublisher.cpp; this only marshals an already-consistent copy.
 *
 * This file lives under mm/2s2h/, which mm/CMakeLists.txt globs recursively,
 * so it compiles with no CMake change.
 */
#ifdef __ANDROID__

#include <jni.h>
#include <chrono>
#include <cstdint>

#include "GameSnapshot.h"

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

/*
 * Copy the latest published snapshot into `out`, which must have at least
 * TDS_SNAP_COUNT elements. Returns JNI_FALSE if it is too short or if the
 * seqlock retry budget was exhausted; the caller keeps its previous snapshot.
 *
 * Allocates nothing: SetIntArrayRegion writes into the caller's reusable array,
 * so there are no local references to leak at 10 Hz.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_terminads_mm_NativeBridge_nativeReadSnapshot(JNIEnv* env, jobject thiz, jintArray out) {
    (void)thiz;

    if (out == nullptr) {
        return JNI_FALSE;
    }
    if (env->GetArrayLength(out) < TDS_SNAP_COUNT) {
        return JNI_FALSE;
    }

    int32_t values[TDS_SNAP_COUNT];
    if (!TerminaDS_ReadSnapshot(values, TDS_SNAP_COUNT)) {
        return JNI_FALSE;
    }

    env->SetIntArrayRegion(out, 0, TDS_SNAP_COUNT, reinterpret_cast<const jint*>(values));
    return JNI_TRUE;
}

#endif // __ANDROID__
```

- [ ] **Step 2: Add the Kotlin side**

Replace the whole of `Android/app/src/main/java/com/terminads/mm/NativeBridge.kt` with:

```kotlin
package com.terminads.mm

/** Outcome of one attempt to read the game-state snapshot. */
enum class SnapshotReadResult {
    /** The array now holds a consistent snapshot. */
    OK,

    /** The native library is not loaded, or the symbol is missing from it. */
    UNAVAILABLE,

    /** A transient seqlock collision, or the array was too short. Keep the previous value. */
    RETRY_EXHAUSTED,
}

/**
 * The only place in the Kotlin layer permitted to call into native code.
 *
 * Declared as a plain object without @JvmStatic, so JNI entry points take
 * (JNIEnv*, jobject) and resolve as Java_com_terminads_mm_NativeBridge_*.
 *
 * The native library is loaded by SDLActivity during MainActivity.onCreate.
 * Calls made before that throw UnsatisfiedLinkError, which we translate to a
 * sentinel rather than crashing the second screen.
 */
object NativeBridge {

    private external fun nativeGetUptimeMillis(): Long

    private external fun nativeReadSnapshot(out: IntArray): Boolean

    /** Native process uptime in milliseconds, or -1 if native is not loaded yet. */
    fun uptimeMillis(): Long =
        try {
            nativeGetUptimeMillis()
        } catch (e: UnsatisfiedLinkError) {
            -1L
        }

    /**
     * Fill [out] with the latest published snapshot.
     *
     * [out] must have at least [GameSnapshotLayout.SLOT_COUNT] elements and is
     * expected to be reused across calls -- nothing here allocates.
     */
    fun readSnapshot(out: IntArray): SnapshotReadResult =
        try {
            if (nativeReadSnapshot(out)) {
                SnapshotReadResult.OK
            } else {
                SnapshotReadResult.RETRY_EXHAUSTED
            }
        } catch (e: UnsatisfiedLinkError) {
            SnapshotReadResult.UNAVAILABLE
        }
}
```

- [ ] **Step 3: Build the APK**

This is the first build with native changes. It takes 8–19 minutes and clears `.cxx` on purpose so the new `.cpp` is picked up by the glob. Run it backgrounded and poll the log; do not treat it as hung.

Run: `./tools/build-apk.sh 2>&1 | tee /tmp/phase2-build.log`
Expected: ends with `==> APK: .../app-release.apk`.

If it fails to compile, the likely causes in order: a missing engine include in `SnapshotPublisher.cpp`; `ARRAY_COUNT` not visible (it comes from `macros.h`); or `EQUIP_SLOT_*` not visible (it comes from `z64interface.h`, not `z64save.h`).

- [ ] **Step 4: Verify the symbol actually shipped**

A green build is not proof. `GLOB_RECURSE` freezes the source list at configure time, so a new `.cpp` can compile green and ship without its code.

```bash
cd /tmp && rm -rf apkcheck && mkdir apkcheck && cd apkcheck
unzip -o -q /srv/projects/2ship2hark/Android/app/build/outputs/apk/release/app-release.apk 'lib/arm64-v8a/*'
llvm-nm -D lib/arm64-v8a/*.so 2>/dev/null | grep -c 'Java_com_terminads_mm_NativeBridge_nativeReadSnapshot'
```

Expected: `1`.

If it prints `0`, the publisher or the JNI entry point did not ship. Do not proceed — re-run the build and confirm `rm -rf Android/app/.cxx` ran.

- [ ] **Step 5: Confirm mm.o2r is still absent**

```bash
unzip -l /srv/projects/2ship2hark/Android/app/build/outputs/apk/release/app-release.apk | grep -c 'mm\.o2r'
```

Expected: `0`.

- [ ] **Step 6: Commit**

```bash
git add mm/2s2h/TerminaDS/NativeBridge.cpp \
        Android/app/src/main/java/com/terminads/mm/NativeBridge.kt
git -c user.name=jaret -c user.email=jaretmsanchez@gmail.com commit -m "feat(bridge): expose the snapshot over JNI

One entry point, one array copy, no allocation: SetIntArrayRegion writes
into a caller-owned reusable array, so nothing leaks local refs at 10 Hz.

readSnapshot returns a three-state result rather than a boolean, because
'native is not loaded' and 'transient seqlock collision' need different
handling -- the first is a permanent condition worth displaying, the
second means keep what you had.

Verified with llvm-nm on the packaged arm64-v8a .so; a green build does
not prove a native symbol shipped.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: The poller

**Files:**
- Create: `Android/app/src/main/java/com/terminads/mm/GameSnapshotPoller.kt`
- Create: `Android/app/src/test/java/com/terminads/mm/GameSnapshotPollerTest.kt`

**Interfaces:**
- Consumes: `decodeSnapshot`, `SnapshotDecode`, `GameSnapshotLayout` (Task 1); `SnapshotReadResult` (Task 3).
- Produces: `BridgeState` sealed interface (`NativeUnavailable`, `SchemaMismatch`, `NoFramesYet`, `Live`, `Stalled`) and `class GameSnapshotPoller(read, nowMillis, stalenessThresholdMillis)` with `fun poll(): BridgeState`. Task 5 renders exactly these.

- [ ] **Step 1: Write the failing tests**

`Android/app/src/test/java/com/terminads/mm/GameSnapshotPollerTest.kt`:

```kotlin
package com.terminads.mm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSnapshotPollerTest {

    /** A fake native reader driven entirely from the test. */
    private class FakeReader {
        var result = SnapshotReadResult.OK
        var schemaVersion = GameSnapshotLayout.SCHEMA_VERSION
        var frameCounter = 1
        var health = 48

        fun read(out: IntArray): SnapshotReadResult {
            if (result == SnapshotReadResult.OK) {
                out.fill(0)
                out[GameSnapshotLayout.IDX_SCHEMA_VERSION] = schemaVersion
                out[GameSnapshotLayout.IDX_FRAME_COUNTER] = frameCounter
                out[GameSnapshotLayout.IDX_HEALTH] = health
            }
            return result
        }
    }

    private class FakeClock {
        var now = 1_000L
        fun advance(millis: Long) { now += millis }
    }

    private fun poller(reader: FakeReader, clock: FakeClock) =
        GameSnapshotPoller(
            read = reader::read,
            nowMillis = { clock.now },
            stalenessThresholdMillis = 1_000L,
        )

    @Test
    fun reportsNativeUnavailableWhenTheLibraryIsNotLoaded() {
        val reader = FakeReader().apply { result = SnapshotReadResult.UNAVAILABLE }
        val state = poller(reader, FakeClock()).poll()
        assertTrue("was $state", state is BridgeState.NativeUnavailable)
    }

    @Test
    fun reportsSchemaMismatchWithBothVersions() {
        val reader = FakeReader().apply { schemaVersion = 7 }
        val state = poller(reader, FakeClock()).poll()
        assertTrue("was $state", state is BridgeState.SchemaMismatch)
        assertEquals(7, (state as BridgeState.SchemaMismatch).nativeVersion)
        assertEquals(GameSnapshotLayout.SCHEMA_VERSION, state.expected)
    }

    @Test
    fun reportsNoFramesYetWhenThePublisherHasNeverRun() {
        val reader = FakeReader().apply { frameCounter = 0 }
        val state = poller(reader, FakeClock()).poll()
        assertTrue("was $state", state is BridgeState.NoFramesYet)
    }

    @Test
    fun reportsLiveWhenTheFrameCounterAdvances() {
        val reader = FakeReader()
        val clock = FakeClock()
        val subject = poller(reader, clock)

        assertTrue(subject.poll() is BridgeState.Live)

        reader.frameCounter = 7
        reader.health = 32
        clock.advance(100)
        val state = subject.poll()

        assertTrue("was $state", state is BridgeState.Live)
        assertEquals(32, (state as BridgeState.Live).snapshot.health)
    }

    @Test
    fun staysLiveWhileTheCounterFreezeIsShorterThanTheThreshold() {
        val reader = FakeReader()
        val clock = FakeClock()
        val subject = poller(reader, clock)

        subject.poll()
        clock.advance(500) // under the 1000 ms threshold
        val state = subject.poll()

        assertTrue("was $state", state is BridgeState.Live)
    }

    @Test
    fun reportsStalledWhenTheFrameCounterStopsAdvancing() {
        val reader = FakeReader()
        val clock = FakeClock()
        val subject = poller(reader, clock)

        subject.poll()
        clock.advance(1_500)
        val state = subject.poll()

        assertTrue("was $state", state is BridgeState.Stalled)
        assertEquals(1_500L, (state as BridgeState.Stalled).millisSinceChange)
        // The last good snapshot is retained so the readout still shows values.
        assertEquals(48, state.snapshot.health)
    }

    @Test
    fun recoversFromStalledWhenTheGameResumes() {
        val reader = FakeReader()
        val clock = FakeClock()
        val subject = poller(reader, clock)

        subject.poll()
        clock.advance(1_500)
        assertTrue(subject.poll() is BridgeState.Stalled)

        reader.frameCounter = 2
        clock.advance(100)
        assertTrue(subject.poll() is BridgeState.Live)
    }

    @Test
    fun keepsThePreviousSnapshotWhenAReadCollides() {
        val reader = FakeReader()
        val clock = FakeClock()
        val subject = poller(reader, clock)

        subject.poll()
        reader.result = SnapshotReadResult.RETRY_EXHAUSTED
        clock.advance(100)
        val state = subject.poll()

        assertTrue("was $state", state is BridgeState.Live)
        assertEquals(48, (state as BridgeState.Live).snapshot.health)
    }

    @Test
    fun reportsNoFramesYetWhenTheFirstReadEverCollides() {
        val reader = FakeReader().apply { result = SnapshotReadResult.RETRY_EXHAUSTED }
        val state = poller(reader, FakeClock()).poll()
        assertTrue("was $state", state is BridgeState.NoFramesYet)
    }

    @Test
    fun reusesOneArrayAcrossPollsSoNothingAllocatesAtTenHertz() {
        val seen = mutableSetOf<IntArray>()
        val subject = GameSnapshotPoller(
            read = { out ->
                seen.add(out)
                out[GameSnapshotLayout.IDX_SCHEMA_VERSION] = GameSnapshotLayout.SCHEMA_VERSION
                out[GameSnapshotLayout.IDX_FRAME_COUNTER] = 1
                SnapshotReadResult.OK
            },
            nowMillis = { 0L },
        )

        repeat(5) { subject.poll() }

        assertEquals(1, seen.size)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./tools/run-unit-tests.sh --tests 'com.terminads.mm.GameSnapshotPollerTest'`
Expected: FAIL — `Unresolved reference: GameSnapshotPoller`.

- [ ] **Step 3: Write the implementation**

`Android/app/src/main/java/com/terminads/mm/GameSnapshotPoller.kt`:

```kotlin
package com.terminads.mm

/**
 * What the bridge can tell us right now.
 *
 * These states exist because the bottom screen cannot be screenshotted -- both
 * Thor displays are FLAG_SECURE -- so "the numbers are frozen" has to be
 * diagnosable from the numbers themselves.
 */
sealed interface BridgeState {
    /** The native library is not loaded, or the symbol is missing. */
    data object NativeUnavailable : BridgeState

    /** Native was built from a different payload layout than this build's Kotlin. */
    data class SchemaMismatch(val nativeVersion: Int, val expected: Int) : BridgeState

    /** Native is answering, but the publisher has never run. */
    data object NoFramesYet : BridgeState

    /** The game loop is stepping and the snapshot is current. */
    data class Live(val snapshot: GameSnapshot) : BridgeState

    /** Native is answering but the frame counter has stopped advancing. */
    data class Stalled(val snapshot: GameSnapshot, val millisSinceChange: Long) : BridgeState
}

/**
 * Polls the native snapshot and classifies the result.
 *
 * Call [poll] from the Android main thread only. It never blocks: the native
 * read is a bounded-retry seqlock copy, and a failed read simply keeps the
 * previous snapshot.
 *
 * @param read the native reader, normally `NativeBridge::readSnapshot`
 * @param nowMillis a monotonic clock, normally `SystemClock::uptimeMillis`
 * @param stalenessThresholdMillis how long the frame counter may sit unchanged
 *   before the game loop is considered stopped. At a 10 Hz poll against a 60 Hz
 *   publisher the counter advances every poll, so a full second of no movement
 *   is unambiguous.
 */
class GameSnapshotPoller(
    private val read: (IntArray) -> SnapshotReadResult,
    private val nowMillis: () -> Long,
    private val stalenessThresholdMillis: Long = 1_000L,
) {
    // Reused across polls: nothing allocates on the main thread at 10 Hz.
    private val buffer = IntArray(GameSnapshotLayout.SLOT_COUNT)

    private var lastSnapshot: GameSnapshot? = null
    private var lastFrameCounter = Int.MIN_VALUE
    private var lastChangeMillis = 0L

    fun poll(): BridgeState {
        when (read(buffer)) {
            SnapshotReadResult.UNAVAILABLE -> return BridgeState.NativeUnavailable
            SnapshotReadResult.RETRY_EXHAUSTED -> return carryForward()
            SnapshotReadResult.OK -> Unit
        }

        val snapshot = when (val decoded = decodeSnapshot(buffer)) {
            is SnapshotDecode.SchemaMismatch ->
                return BridgeState.SchemaMismatch(decoded.nativeVersion, decoded.expected)
            is SnapshotDecode.Ok -> decoded.snapshot
        }

        if (snapshot.frameCounter == 0) {
            return BridgeState.NoFramesYet
        }

        val now = nowMillis()
        if (snapshot.frameCounter != lastFrameCounter) {
            lastFrameCounter = snapshot.frameCounter
            lastChangeMillis = now
        }
        lastSnapshot = snapshot

        val sinceChange = now - lastChangeMillis
        return if (sinceChange >= stalenessThresholdMillis) {
            BridgeState.Stalled(snapshot, sinceChange)
        } else {
            BridgeState.Live(snapshot)
        }
    }

    /**
     * A read collided with the publisher. That is transient by nature, so keep
     * the previous snapshot and let the staleness check catch it if it somehow
     * persists.
     */
    private fun carryForward(): BridgeState {
        val previous = lastSnapshot ?: return BridgeState.NoFramesYet
        val sinceChange = nowMillis() - lastChangeMillis
        return if (sinceChange >= stalenessThresholdMillis) {
            BridgeState.Stalled(previous, sinceChange)
        } else {
            BridgeState.Live(previous)
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./tools/run-unit-tests.sh --tests 'com.terminads.mm.GameSnapshotPollerTest'`
Expected: `BUILD SUCCESSFUL`, 10 tests passing.

- [ ] **Step 5: Run the full suite**

Run: `./tools/run-unit-tests.sh`
Expected: `BUILD SUCCESSFUL`, 32 tests (13 existing + 9 + 10).

- [ ] **Step 6: Commit**

```bash
git add Android/app/src/main/java/com/terminads/mm/GameSnapshotPoller.kt \
        Android/app/src/test/java/com/terminads/mm/GameSnapshotPollerTest.kt
git -c user.name=jaret -c user.email=jaretmsanchez@gmail.com commit -m "feat(bridge): add the snapshot poller and bridge-state classification

Turns raw reads into the six states the debug readout distinguishes.
The frame counter is what separates 'the bridge is broken' from 'the
game loop stopped' -- otherwise identical symptoms, since both present
as frozen numbers on a screen that cannot be screenshotted.

Clock and reader are injected so staleness is testable without a device
or a real 60 Hz publisher.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Debug readout and wiring

Deliberately ugly. Phase 3 deletes it. Do not invest in visual design.

**Files:**
- Modify: `Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenHost.kt`
- Modify: `Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenPresentation.kt`

**Interfaces:**
- Consumes: `BridgeState`, `GameSnapshotPoller` (Task 4); `NativeBridge.readSnapshot` (Task 3); existing `DisplayInfo`.
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Rewrite the host as a debug readout**

Replace the whole of `Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenHost.kt` with:

```kotlin
package com.terminads.mm.secondscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.terminads.mm.BridgeState
import com.terminads.mm.GameSnapshot
import kotlinx.coroutines.delay

/**
 * Phase 2 debug readout for the Termina DS second screen.
 *
 * Deliberately not designed. It exists to prove the state bridge carries real
 * values, and it is deleted wholesale by Phase 3's HUD.
 *
 * Raw output is the point: a mis-decoded s16 reads as obvious garbage here,
 * whereas a styled heart row would render it as a believable wrong number. Both
 * Thor displays are FLAG_SECURE, so this text is the only way anyone sees what
 * the bridge produced.
 */
@Composable
fun SecondScreenHost(
    displayInfo: DisplayInfo,
    pollBridge: () -> BridgeState,
    pollIntervalMillis: Long = 100L,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var state by remember { mutableStateOf<BridgeState>(BridgeState.NoFramesYet) }

            // Main-thread coroutine scoped to this composition: it starts when
            // the Presentation shows and stops when it dismisses. No thread, no
            // executor, no Handler -- the Presentation lifecycle owner drops the
            // main-thread assertion, so nothing here may leave the main thread.
            LaunchedEffect(pollBridge, pollIntervalMillis) {
                while (true) {
                    state = pollBridge()
                    delay(pollIntervalMillis)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Termina DS", style = MaterialTheme.typography.titleMedium)
                DebugRow("display", "${displayInfo.displayId} ${displayInfo.name}")
                DebugRow("size", "${displayInfo.widthPx}x${displayInfo.heightPx} @ ${displayInfo.refreshRate}Hz")

                Text(
                    text = statusLine(state),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { contentDescription = "Bridge status: ${statusLine(state)}" },
                )

                when (state) {
                    is BridgeState.Live -> SnapshotRows((state as BridgeState.Live).snapshot)
                    is BridgeState.Stalled -> SnapshotRows((state as BridgeState.Stalled).snapshot)
                    else -> Unit
                }
            }
        }
    }
}

private fun statusLine(state: BridgeState): String = when (state) {
    is BridgeState.NativeUnavailable -> "NATIVE NOT LOADED"
    is BridgeState.SchemaMismatch ->
        "SCHEMA MISMATCH native=${state.nativeVersion} expected=${state.expected}"
    is BridgeState.NoFramesYet -> "NO FRAMES YET (publisher has not run)"
    is BridgeState.Stalled -> "STALLED ${state.millisSinceChange}ms  frame=${state.snapshot.frameCounter}"
    is BridgeState.Live -> "LIVE  frame=${state.snapshot.frameCounter}"
}

@Composable
private fun SnapshotRows(snapshot: GameSnapshot) {
    DebugRow("health", "${snapshot.health}/${snapshot.healthCapacity}")
    DebugRow("magic", "${snapshot.magic}/${snapshot.magicCapacity} lvl=${snapshot.magicLevel}")
    DebugRow("rupees", "${snapshot.rupees}")
    DebugRow("form", "${snapshot.playerForm}  mask=${snapshot.equippedMask}")
    DebugRow("clock", "day=${snapshot.day} time=${snapshot.timeOfDay} night=${snapshot.isNight}")
    DebugRow("dbl-def", "${snapshot.doubleDefense}")
    DebugRow("btn items", snapshot.buttonItems.joinToString(" "))
    DebugRow("btn ammo", snapshot.buttonAmmo.joinToString(" "))

    if (snapshot.hasPlayState) {
        DebugRow("scene", "${snapshot.sceneId}  room=${snapshot.roomNum}")
    } else {
        DebugRow("scene", "NO WORLD")
    }

    if (snapshot.hasPlayer) {
        DebugRow("pos", "%.1f %.1f %.1f".format(snapshot.playerX, snapshot.playerY, snapshot.playerZ))
        DebugRow("yaw", "${snapshot.playerYaw}")
    } else {
        DebugRow("pos", "NO PLAYER")
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Text(
        text = label.padEnd(10) + value,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.semantics { contentDescription = "$label: $value" },
    )
}
```

- [ ] **Step 2: Wire the poller in the Presentation**

In `Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenPresentation.kt`, replace the import line:

```kotlin
import com.terminads.mm.NativeBridge
```

with:

```kotlin
import com.terminads.mm.GameSnapshotPoller
import com.terminads.mm.NativeBridge
```

Then replace the `ComposeView` block:

```kotlin
        val composeView = ComposeView(context).apply {
            setContent {
                SecondScreenHost(
                    displayInfo = displayInfo,
                    uptimeMillisProvider = { NativeBridge.uptimeMillis() },
                )
            }
        }
```

with:

```kotlin
        // One poller per Presentation: it owns the reusable payload array and
        // the staleness bookkeeping, both of which must not be shared.
        val poller = GameSnapshotPoller(
            read = NativeBridge::readSnapshot,
            nowMillis = SystemClock::uptimeMillis,
        )

        val composeView = ComposeView(context).apply {
            setContent {
                SecondScreenHost(
                    displayInfo = displayInfo,
                    pollBridge = poller::poll,
                )
            }
        }
```

And add to the imports at the top of the file:

```kotlin
import android.os.SystemClock
```

- [ ] **Step 3: Run the full unit-test suite**

Run: `./tools/run-unit-tests.sh`
Expected: `BUILD SUCCESSFUL`, 32 tests. This catches compile errors in the Compose changes without a full APK build.

- [ ] **Step 4: Commit**

```bash
git add Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenHost.kt \
        Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenPresentation.kt
git -c user.name=jaret -c user.email=jaretmsanchez@gmail.com commit -m "feat(secondscreen): replace the placeholder with a state debug readout

Raw monospace dump of every snapshot slot plus the bridge status line.
Deliberately undesigned -- Phase 3 deletes it. Building the real HUD here
would conflate 'is the bridge correct' with 'is the HUD right', and a
mis-decoded value would hide behind a plausible-looking heart row.

Polling is a LaunchedEffect scoped to the composition, so it starts and
stops with the Presentation and never leaves the main thread.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: Build, deploy, and hardware verification

The build and symbol checks are yours. **Every on-device step needs the user** — both Thor displays are `FLAG_SECURE`, so `screencap` returns black and only they can see the bottom panel. Ask; do not guess, and do not report the phase complete on the strength of a green build.

**Files:** none changed unless verification finds a defect.

- [ ] **Step 1: Build the release APK**

Run: `./tools/build-apk.sh 2>&1 | tee /tmp/phase2-final-build.log`
Expected: ends with `==> APK: .../app-release.apk`. Takes 8–19 minutes; background it.

- [ ] **Step 2: Re-verify the native symbol**

```bash
cd /tmp && rm -rf apkcheck2 && mkdir apkcheck2 && cd apkcheck2
unzip -o -q /srv/projects/2ship2hark/Android/app/build/outputs/apk/release/app-release.apk 'lib/arm64-v8a/*'
llvm-nm -D lib/arm64-v8a/*.so 2>/dev/null | grep 'Java_com_terminads_mm_NativeBridge_native'
```

Expected: two lines — `nativeGetUptimeMillis` and `nativeReadSnapshot`.

- [ ] **Step 3: Confirm the device is connected**

Run: `adb devices`
Expected: `10.0.0.30:41277	device`. If it is offline, ask the user to re-pair — the Thor's Wi-Fi debugging pairing often needs re-establishing per session and only they can read the code off the device.

- [ ] **Step 4: Deploy**

Run: `./tools/deploy-apk.sh`
Expected: `Success`.

- [ ] **Step 5: Prove the publisher registered, via logcat**

```bash
adb logcat -c
# ask the user to launch Termina DS and load a save, then:
adb logcat -d | grep -E 'TerminaDS'
```

Expected: `TerminaDS: Snapshot: publisher registered, first publish (schema 1, 27 slots)`, alongside the existing `TerminaDS/SecondScreen: Showing second screen on display ...`.

If the SecondScreen line appears but the Snapshot line never does, the publisher's translation unit was dropped by the linker despite the build being green. That is the `GLOB_RECURSE`/`.cxx` failure mode; confirm the `llvm-nm` check from Step 2 and re-run a clean build.

- [ ] **Step 6: Walk the on-device checklist with the user**

Ask the user to read the bottom screen and report. Do not proceed past a failure.

1. **Title screen** — status reads `LIVE`, frame counter advancing, `scene` reads `NO WORLD`
2. **Load a save** — `scene` and `room` populate
3. **Take damage** — `health` drops within about 100 ms
4. **Collect rupees** — `rupees` tracks
5. **Use magic** — `magic` drains
6. **Walk around** — `pos` moves smoothly; `yaw` changes when turning
7. **Transform with a mask** — `form` changes and `btn items` change with it
8. **Scene transition** — `scene` briefly shows `NO WORLD`, then repopulates, **no crash**
9. **Framerate** — top-screen framerate unchanged versus the Phase 1 build, measured
10. **Background and return** — the readout resumes, no crash
11. **TalkBack** — reads the status line and the rows

Step 8 is decisive. It is the only step that exercises `gPlayState` going NULL while a reader is live — the single failure this architecture exists to prevent.

- [ ] **Step 7: Record the verification results**

Write `docs/verification/2026-07-23-phase-2-thor.md` following the format of the existing `docs/verification/2026-07-23-phase-1-thor.md`: each checklist item, pass or fail, with the user's observed values quoted. Record failures as failures.

- [ ] **Step 8: Update the handoff doc**

In `docs/HANDOFF.md`: mark Phase 2 done in §7, update the `NativeBridge.cpp` row in §4 (it is no longer "uptime heartbeat only"), and add the new files to that table.

- [ ] **Step 9: Commit**

```bash
git add docs/verification/2026-07-23-phase-2-thor.md docs/HANDOFF.md
git -c user.name=jaret -c user.email=jaretmsanchez@gmail.com commit -m "docs: record Phase 2 hardware verification on AYN Thor

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Notes for the implementer

**`docs/UPSTREAM.md` needs no entry.** This phase modifies no inherited file. If you find yourself editing one, stop — that is a design deviation, not an implementation detail.

**Do not raise `targetSdk`, add a Gradle dependency, or touch `CMakeLists.txt`.** None of this phase requires it. The recursive glob picks up both new native files; clearing `.cxx` (which `build-apk.sh` already does every build) is what makes that reliable.

**If the game crashes on a scene transition,** the fault is in `SnapshotPublisher.cpp`'s pointer guards, not in Kotlin. `Publish()` is the only code that dereferences anything. Check that `GET_PLAYER` is null-checked separately from `gPlayState` — a PlayState can exist before the player actor spawns, and conflating the two guards is the likely mistake.
