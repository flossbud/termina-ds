# Termina DS Phase 4a: Command Mailbox and Pause Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The first write path into the game — a fixed-layout SPSC command mailbox drained on the game thread — carrying pause/resume from a new bottom-screen PAUSE control, with schema v2 (`saveLoaded`, `pauseState`, `menuOpen`) closing the observation loop.

**Architecture:** Kotlin submits fixed-layout commands over one new JNI entry into a 16-slot SPSC ring; the game thread drains it at the head of the existing `OnGameStateUpdate` registration, before the snapshot publishes — so the same frame's snapshot reports the effect. Pause is the engine's own frame-advance gate (`z_play.c:988`). The UI routes on observed `pauseState`, never on assumption; a pending request that never acks becomes a visible failure.

**Tech Stack:** C++17 (relaxed/acquire-release atomics), JNI, Kotlin, Compose, JUnit 4, Docker toolchain.

**Spec:** `docs/superpowers/specs/2026-07-25-termina-ds-phase-4-pause-settings-design.md` (§1–§4, §6 routing, pause parts of §5; Plan B covers the full root menu styling, Options, Compose UI test infra, keystore)

## Global Constraints

- Only `SnapshotPublisher.cpp` and the new `CommandMailbox.cpp` may touch game state. The mailbox never blocks the game thread and drains a bounded count per frame.
- Commands are absolute (`PAUSE_SET 1`), never read-modify-write. The UI observes effects through the snapshot.
- Schema bump is atomic across native and Kotlin in one task's commits: `TDS_SNAP_SCHEMA_VERSION` 1→2 and `GameSnapshotLayout.SCHEMA_VERSION` 1→2 land together with their tests.
- Do not modify `GameSnapshotPoller.kt` or `SecondScreenPresentation.kt`. `GameSnapshot.kt`, `NativeBridge.kt`, `HudModel.kt`, `GameplayScreen.kt`, `SecondScreenHost.kt` change only as specified.
- **New native FILES appear only in Task 3** — after that task's full `./tools/build-apk.sh` re-glob, later tasks touch existing files only, so `./tools/assemble-apk.sh` stays safe for iteration (the GLOB_RECURSE trap, HANDOFF §5).
- JVM tests: `./tools/run-unit-tests.sh` (Docker; trust its XML counts). Current suite: 82 tests.
- Inherited-file edits (`BenGui.hpp/.cpp`, `MainActivity.java` if touched) get a `docs/UPSTREAM.md` ledger row.
- Design geometry through `du`/`dus` with `LEGIBILITY`; no `contentDescription` may carry per-poll values.
- Commits authored as `jaret <jaretmsanchez@gmail.com>`; never push; never involve WheelHouse-Software.

---

### Task 1: Schema v2, native side

**Files:**
- Modify: `mm/2s2h/TerminaDS/GameSnapshot.h`
- Modify: `mm/2s2h/TerminaDS/SnapshotPublisher.cpp`
- Modify: `mm/2s2h/BenGui/BenGui.hpp`, `mm/2s2h/BenGui/BenGui.cpp` (3-line accessor)
- Modify: `docs/UPSTREAM.md` (ledger row for the BenGui edit)

**Interfaces:**
- Consumes: existing publisher structure (Phase 2).
- Produces: `TDS_SNAP_SCHEMA_VERSION 2`, `TDS_SNAP_IDX_PAUSE_STATE` (= 27), `TDS_SNAP_COUNT` (= 28), `TDS_SNAP_FLAG_SAVE_LOADED (1<<4)`, `TDS_SNAP_FLAG_MENU_OPEN (1<<5)`, `bool BenGui::IsBenMenuVisible()`. Task 2 mirrors the layout in Kotlin; Task 3's drain hooks into this publisher.

- [ ] **Step 1: Bump the schema and extend the layout in `GameSnapshot.h`**

Change the version line:

```c
#define TDS_SNAP_SCHEMA_VERSION 2
```

Append to `enum TdsSnapshotIndex`, after `TDS_SNAP_IDX_PLAYER_YAW` and before `TDS_SNAP_COUNT`:

```c
    /*
     * v2: 1 while the engine's frame-advance gate holds the Play update
     * frozen (our pause; z_play.c:988). 0 when unpaused or no PlayState.
     */
    TDS_SNAP_IDX_PAUSE_STATE,
```

Append to `enum TdsSnapshotFlag`:

```c
    /*
     * v2: a save file is active. gSaveContext.fileNum is the slot index
     * after file select commits (z_file_choose_NES.c:2200) and 0xFF on the
     * title screen (z_title.c:283); the pre-save intro cutscene inherits
     * the title's sentinel. This is the honest "is there a game" signal
     * the payload lacked since Phase 2.
     */
    TDS_SNAP_FLAG_SAVE_LOADED = 1 << 4,
    /*
     * v2: the engine owns the screen -- kaleido pause is up
     * (pauseCtx.state != PAUSE_STATE_OFF) or the BenMenu ImGui menu is
     * visible. The UI disables its pause control while set.
     */
    TDS_SNAP_FLAG_MENU_OPEN = 1 << 5
```

- [ ] **Step 2: Add the BenMenu visibility accessor**

`BenGui.hpp`, inside `namespace BenGui`, after `GetMenuThemeColor()`:

```cpp
bool IsBenMenuVisible();
```

`BenGui.cpp`, next to `GetMenuThemeColor()` (which shows the `mBenMenu` access pattern):

```cpp
bool IsBenMenuVisible() {
    return mBenMenu != nullptr && mBenMenu->IsVisible();
}
```

(`IsVisible()` is the LUS `GuiWindow` base method — the same class `mBenMenu`
already is. If the compiler disagrees on the method name, check
`engine/include/libultraship/window/gui/GuiWindow.h` and use its visibility
getter; do not invent state.)

Add to `docs/UPSTREAM.md`'s inherited-edit ledger, matching its row format:
`BenGui.hpp/.cpp — added BenGui::IsBenMenuVisible() accessor for the TerminaDS snapshot's MENU_OPEN flag (Phase 4a).`

- [ ] **Step 3: Publish the three new signals in `SnapshotPublisher.cpp`**

Add the include (with the existing 2s2h includes, outside the `extern "C"` block):

```cpp
#include "2s2h/BenGui/BenGui.hpp"
```

In `Publish()`, after the `doubleDefense` flag block (still in the
unconditional gSaveContext section):

```cpp
    // v2: fileNum is the committed save slot; the title screen parks it at
    // 0xFF (z_title.c:283). Unsigned compare rejects the sentinel and any
    // debug negative in one test.
    if ((uint32_t)gSaveContext.fileNum <= 2u) {
        flags |= TDS_SNAP_FLAG_SAVE_LOADED;
    }
    if (BenGui::IsBenMenuVisible()) {
        flags |= TDS_SNAP_FLAG_MENU_OPEN;
    }
```

Inside the existing `if (play != NULL)` block, after the sceneId/roomNum
lines:

```cpp
        v[TDS_SNAP_IDX_PAUSE_STATE] = FrameAdvance_IsEnabled((PlayState*)play);
        if (play->pauseCtx.state != PAUSE_STATE_OFF) {
            flags |= TDS_SNAP_FLAG_MENU_OPEN;
        }
```

`FrameAdvance_IsEnabled` is declared via the engine headers already in the
`extern "C"` block (`z64play.h` / `functions.h` — it is defined at
`z_play.c:2096`; if the build cannot see the declaration, add
`#include "functions.h"` inside the extern block rather than forward-declaring
by hand). `PAUSE_STATE_OFF` comes from `z64pause_menu.h`, included via
`z64play.h`. The cast drops the pointer's constness for the engine's
non-const-audited helper — it reads one field; keep the cast local, do not
de-const the pointer declaration.

- [ ] **Step 4: Compile gate**

Run: `./tools/assemble-apk.sh` (no new files yet — safe; compiles the edited
native sources). Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add mm/2s2h/TerminaDS/GameSnapshot.h mm/2s2h/TerminaDS/SnapshotPublisher.cpp \
    mm/2s2h/BenGui/BenGui.hpp mm/2s2h/BenGui/BenGui.cpp docs/UPSTREAM.md
git commit -m "feat(bridge): publish saveLoaded, pauseState, menuOpen (schema v2)"
```

(The Kotlin half is Task 2; between these commits the device build would show
`SCHEMA MISMATCH native=2 expected=1` by design — do not install between them.)

---

### Task 2: Schema v2, Kotlin mirror

**Files:**
- Modify: `Android/app/src/main/java/com/terminads/mm/GameSnapshot.kt`
- Test: `Android/app/src/test/java/com/terminads/mm/GameSnapshotTest.kt`
- Test (edit): `Android/app/src/test/java/com/terminads/mm/secondscreen/HudModelTest.kt`, `RouteTest.kt` (constructor call sites)

**Interfaces:**
- Consumes: Task 1's layout (verbatim values above).
- Produces: `GameSnapshotLayout.SCHEMA_VERSION = 2`, `IDX_PAUSE_STATE = 27`, `SLOT_COUNT = 28`, `FLAG_SAVE_LOADED = 1 shl 4`, `FLAG_MENU_OPEN = 1 shl 5`; `GameSnapshot` gains `val isPaused: Boolean`, `val saveLoaded: Boolean`, `val menuOpen: Boolean` (appended after `playerYaw`). Tasks 5–6 route on these.

- [ ] **Step 1: Write the failing tests**

In `GameSnapshotTest.kt`, update the layout-pinning test's expectations
(`SLOT_COUNT` 27→28, `SCHEMA_VERSION` 1→2 — find the existing
`slotCountMatchesTheDocumentedLayout`-style assertions and adjust), and add:

```kotlin
    @Test
    fun v2FieldsDecode() {
        val values = IntArray(GameSnapshotLayout.SLOT_COUNT)
        values[GameSnapshotLayout.IDX_SCHEMA_VERSION] = GameSnapshotLayout.SCHEMA_VERSION
        values[GameSnapshotLayout.IDX_FRAME_COUNTER] = 5
        values[GameSnapshotLayout.IDX_FLAGS] =
            GameSnapshotLayout.FLAG_SAVE_LOADED or GameSnapshotLayout.FLAG_MENU_OPEN
        values[GameSnapshotLayout.IDX_PAUSE_STATE] = 1

        val snapshot = (decodeSnapshot(values) as SnapshotDecode.Ok).snapshot
        assertTrue(snapshot.saveLoaded)
        assertTrue(snapshot.menuOpen)
        assertTrue(snapshot.isPaused)
    }

    @Test
    fun v2FieldsAbsentDecodeFalse() {
        val values = IntArray(GameSnapshotLayout.SLOT_COUNT)
        values[GameSnapshotLayout.IDX_SCHEMA_VERSION] = GameSnapshotLayout.SCHEMA_VERSION
        values[GameSnapshotLayout.IDX_FRAME_COUNTER] = 5

        val snapshot = (decodeSnapshot(values) as SnapshotDecode.Ok).snapshot
        assertFalse(snapshot.saveLoaded)
        assertFalse(snapshot.menuOpen)
        assertFalse(snapshot.isPaused)
    }
```

- [ ] **Step 2: Run to verify RED**

Run: `./tools/run-unit-tests.sh --tests '*GameSnapshotTest*'`
Expected: FAIL — `Unresolved reference` for the new layout members.

- [ ] **Step 3: Implement the mirror**

In `GameSnapshotLayout`: `SCHEMA_VERSION = 2`; after `IDX_PLAYER_YAW = 26`
add `const val IDX_PAUSE_STATE = 27`; `SLOT_COUNT = 28`; after
`FLAG_DOUBLE_DEFENSE` add:

```kotlin
    const val FLAG_SAVE_LOADED = 1 shl 4
    const val FLAG_MENU_OPEN = 1 shl 5
```

In the `GameSnapshot` data class, append after `playerYaw`:

```kotlin
    /** v2: the frame-advance gate holds the Play update frozen (our pause). */
    val isPaused: Boolean,
    /** v2: a save file is active — the honest "is there a game" signal. */
    val saveLoaded: Boolean,
    /** v2: kaleido or the BenMenu owns the game's screen. */
    val menuOpen: Boolean,
```

In `decodeSnapshot`, append to the constructor call:

```kotlin
            isPaused = values[GameSnapshotLayout.IDX_PAUSE_STATE] != 0,
            saveLoaded = flag(GameSnapshotLayout.FLAG_SAVE_LOADED),
            menuOpen = flag(GameSnapshotLayout.FLAG_MENU_OPEN),
```

Fix the `GameSnapshot(...)` constructor call sites in `HudModelTest.kt` and
`RouteTest.kt` test builders: add parameters
`isPaused: Boolean = false, saveLoaded: Boolean = true, menuOpen: Boolean = false`
to each `snapshot(...)` helper and pass them through. (Default
`saveLoaded = true` keeps every existing routing/model expectation valid
until Task 5 rewrites the gates.)

- [ ] **Step 4: Run the full suite**

Run: `./tools/run-unit-tests.sh`
Expected: PASS — 84 tests (82 + 2), 0 failures.

- [ ] **Step 5: Commit**

```bash
git add Android/app/src/main/java/com/terminads/mm/GameSnapshot.kt \
    Android/app/src/test/java/com/terminads/mm/GameSnapshotTest.kt \
    Android/app/src/test/java/com/terminads/mm/secondscreen/HudModelTest.kt \
    Android/app/src/test/java/com/terminads/mm/secondscreen/RouteTest.kt
git commit -m "feat(bridge): mirror schema v2 in the Kotlin decoder"
```

---

### Task 3: The command mailbox (native) and its JNI entry

**Files:**
- Create: `mm/2s2h/TerminaDS/CommandMailbox.h`
- Create: `mm/2s2h/TerminaDS/CommandMailbox.cpp`
- Modify: `mm/2s2h/TerminaDS/SnapshotPublisher.cpp` (drain call in the hook)
- Modify: `mm/2s2h/TerminaDS/NativeBridge.cpp` (submit JNI entry)

**Interfaces:**
- Consumes: Task 1's publisher/hook structure.
- Produces: `TerminaDS_SubmitCommand(int32_t op, int32_t a, int32_t b, const char* name) -> int32_t` (a `TdsSubmitStatus`), `TerminaDS_DrainCommands(void)`, opcodes `TDS_CMD_PAUSE_SET=1 / TDS_CMD_CVAR_SET_INT=2 / TDS_CMD_CVAR_SAVE=3`, statuses `TDS_SUBMIT_OK=0 / TDS_SUBMIT_FULL=1 / TDS_SUBMIT_INVALID=2`, and JNI `Java_com_terminads_mm_NativeBridge_nativeSubmitCommand`. Task 4 mirrors the enums in Kotlin.

- [ ] **Step 1: Write `CommandMailbox.h`**

```c
/*
 * Termina DS: Phase 4 write path. A fixed-capacity SPSC ring: the Android
 * main thread is the single producer (via JNI), the game thread the single
 * consumer (drained at the head of the OnGameStateUpdate registration,
 * before the snapshot publishes -- the same frame's snapshot reports the
 * effect).
 *
 * Commands are ABSOLUTE ("set paused true"), never read-modify-write: the
 * UI's view of state is up to ~100 ms stale by construction, so a command
 * must mean the same thing regardless of when it lands.
 *
 * Alongside SnapshotPublisher.cpp, CommandMailbox.cpp is the ONLY other
 * file allowed to touch game state.
 */
#ifndef TERMINADS_COMMAND_MAILBOX_H
#define TERMINADS_COMMAND_MAILBOX_H

#include <stdint.h>

#define TDS_CMD_NAME_CAPACITY 64
#define TDS_CMD_QUEUE_CAPACITY 16

enum TdsCommandOp {
    /* a: 0 = resume, nonzero = freeze. Requires a live PlayState. */
    TDS_CMD_PAUSE_SET = 1,
    /* name: CVar key; a: value. */
    TDS_CMD_CVAR_SET_INT = 2,
    /* Persist CVars via the LUS save path. Debounced by the caller. */
    TDS_CMD_CVAR_SAVE = 3
};

enum TdsSubmitStatus {
    TDS_SUBMIT_OK = 0,
    /* Ring full: the caller surfaces this; it never silently drops. */
    TDS_SUBMIT_FULL = 1,
    /* Unknown op, or a name-carrying op with a null/oversized name. */
    TDS_SUBMIT_INVALID = 2
};

#ifdef __cplusplus
extern "C" {
#endif

/* Producer side. Safe from exactly one non-game thread. */
int32_t TerminaDS_SubmitCommand(int32_t op, int32_t a, int32_t b, const char* name);

/* Consumer side. Game thread only; bounded (drains at most the ring). */
void TerminaDS_DrainCommands(void);

#ifdef __cplusplus
}
#endif

#endif /* TERMINADS_COMMAND_MAILBOX_H */
```

- [ ] **Step 2: Write `CommandMailbox.cpp`**

```cpp
#include "CommandMailbox.h"

#include <atomic>
#include <cstring>

#include <libultraship/bridge/consolevariablebridge.h>

extern "C" {
#include "z64play.h"
#include "functions.h"

extern PlayState* gPlayState;
}

namespace {

struct TdsCommand {
    int32_t op;
    int32_t a;
    int32_t b;
    char name[TDS_CMD_NAME_CAPACITY];
};

TdsCommand sSlots[TDS_CMD_QUEUE_CAPACITY];
// head: consumer's next read index. tail: producer's next write index.
// Equal means empty; tail one lap ahead of head means full.
std::atomic<uint32_t> sHead{ 0 };
std::atomic<uint32_t> sTail{ 0 };

void Apply(const TdsCommand& cmd) {
    switch (cmd.op) {
        case TDS_CMD_PAUSE_SET: {
            // gPlayState is only ever mutated on this thread, so the guard
            // and the write cannot race (same argument as the publisher).
            PlayState* play = gPlayState;
            if (play != NULL) {
                play->frameAdvCtx.enabled = (cmd.a != 0);
            }
            break;
        }
        case TDS_CMD_CVAR_SET_INT:
            CVarSetInteger(cmd.name, cmd.a);
            break;
        case TDS_CMD_CVAR_SAVE:
            CVarSave();
            break;
        default:
            // Validated at submit; an unknown op here is a torn build --
            // drop it rather than guess.
            break;
    }
}

} // namespace

extern "C" int32_t TerminaDS_SubmitCommand(int32_t op, int32_t a, int32_t b, const char* name) {
    if (op < TDS_CMD_PAUSE_SET || op > TDS_CMD_CVAR_SAVE) {
        return TDS_SUBMIT_INVALID;
    }
    const bool needsName = (op == TDS_CMD_CVAR_SET_INT);
    if (needsName && (name == NULL || std::strlen(name) >= TDS_CMD_NAME_CAPACITY)) {
        return TDS_SUBMIT_INVALID;
    }

    const uint32_t tail = sTail.load(std::memory_order_relaxed);
    const uint32_t head = sHead.load(std::memory_order_acquire);
    if (tail - head >= TDS_CMD_QUEUE_CAPACITY) {
        return TDS_SUBMIT_FULL;
    }

    TdsCommand& slot = sSlots[tail % TDS_CMD_QUEUE_CAPACITY];
    slot.op = op;
    slot.a = a;
    slot.b = b;
    if (needsName) {
        std::strncpy(slot.name, name, TDS_CMD_NAME_CAPACITY - 1);
        slot.name[TDS_CMD_NAME_CAPACITY - 1] = '\0';
    } else {
        slot.name[0] = '\0';
    }

    // Release publishes the slot contents before the new tail is visible.
    sTail.store(tail + 1, std::memory_order_release);
    return TDS_SUBMIT_OK;
}

extern "C" void TerminaDS_DrainCommands(void) {
    uint32_t head = sHead.load(std::memory_order_relaxed);
    const uint32_t tail = sTail.load(std::memory_order_acquire);

    while (head != tail) {
        Apply(sSlots[head % TDS_CMD_QUEUE_CAPACITY]);
        head++;
    }
    // Release lets the producer reuse the consumed slots.
    sHead.store(head, std::memory_order_release);
}
```

- [ ] **Step 3: Drain at the head of the hook in `SnapshotPublisher.cpp`**

Add near the top with the other TerminaDS includes:

```cpp
#include "CommandMailbox.h"
```

Change the registration body (inside the existing guarded
`RegisterShipInitFunc` lambda) from:

```cpp
    GameInteractor::Instance->RegisterGameHook<GameInteractor::OnGameStateUpdate>(Publish);
```

to:

```cpp
    GameInteractor::Instance->RegisterGameHook<GameInteractor::OnGameStateUpdate>([]() {
        // Drain before publishing: the same frame's snapshot reports the
        // commands' effects, closing the observe-don't-assume loop.
        TerminaDS_DrainCommands();
        Publish();
    });
```

- [ ] **Step 4: Add the JNI entry in `NativeBridge.cpp`**

Add `#include "CommandMailbox.h"` next to the GameSnapshot include, and
append before the closing `#endif`:

```cpp
/*
 * Submit one absolute command to the game thread. Returns a TdsSubmitStatus.
 * The name string is copied into the ring before this returns; no reference
 * is retained.
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_terminads_mm_NativeBridge_nativeSubmitCommand(JNIEnv* env, jobject thiz, jint op, jint a,
                                                       jint b, jstring name) {
    (void)thiz;

    const char* nameChars = nullptr;
    if (name != nullptr) {
        nameChars = env->GetStringUTFChars(name, nullptr);
        if (nameChars == nullptr) {
            return static_cast<jint>(TDS_SUBMIT_INVALID);
        }
    }

    const int32_t status = TerminaDS_SubmitCommand(op, a, b, nameChars);

    if (nameChars != nullptr) {
        env->ReleaseStringUTFChars(name, nameChars);
    }
    return static_cast<jint>(status);
}
```

- [ ] **Step 5: Full pipeline build (MANDATORY — new native files)**

Run in the background: `./tools/build-apk.sh`
Expected: BUILD SUCCESSFUL (~15 min on this host). `assemble-apk.sh` would
silently ship a lib without `CommandMailbox.cpp` — the GLOB_RECURSE trap.

- [ ] **Step 6: Verify the new symbol in the packaged lib**

Run the documented Docker llvm-nm check
(`.superpowers/codex-sol/verify-apk.sh` pattern) and additionally grep for
`Java_com_terminads_mm_NativeBridge_nativeSubmitCommand`.
Expected: three `T` symbols (uptime, readSnapshot, submitCommand), mm.o2r
absent.

- [ ] **Step 7: Commit**

```bash
git add mm/2s2h/TerminaDS/CommandMailbox.h mm/2s2h/TerminaDS/CommandMailbox.cpp \
    mm/2s2h/TerminaDS/SnapshotPublisher.cpp mm/2s2h/TerminaDS/NativeBridge.cpp
git commit -m "feat(bridge): add the SPSC command mailbox with pause and CVar ops"
```

---

### Task 4: Kotlin command seam

**Files:**
- Modify: `Android/app/src/main/java/com/terminads/mm/NativeBridge.kt`
- Create: `Android/app/src/main/java/com/terminads/mm/CommandBridge.kt`
- Test: `Android/app/src/test/java/com/terminads/mm/CommandBridgeTest.kt`

**Interfaces:**
- Consumes: Task 3's JNI signature and enum values (verbatim).
- Produces: `enum class SubmitStatus { OK, FULL, INVALID, UNKNOWN }`;
  `class CommandBridge(private val submit: (Int, Int, Int, String?) -> Int)` with
  `fun setPaused(paused: Boolean): SubmitStatus`,
  `fun setCVarInt(name: String, value: Int): SubmitStatus`,
  `fun saveCVars(): SubmitStatus`. Task 6 constructs it with the real native
  lambda; tests construct it with fakes.

- [ ] **Step 1: Write the failing test**

`CommandBridgeTest.kt`:

```kotlin
package com.terminads.mm

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandBridgeTest {

    private class Recorder(var result: Int = 0) {
        var lastOp = -1; var lastA = -1; var lastB = -1; var lastName: String? = "unset"
        fun submit(op: Int, a: Int, b: Int, name: String?): Int {
            lastOp = op; lastA = a; lastB = b; lastName = name
            return result
        }
    }

    @Test
    fun pauseSetSubmitsAbsoluteValues() {
        val rec = Recorder()
        val bridge = CommandBridge(rec::submit)
        assertEquals(SubmitStatus.OK, bridge.setPaused(true))
        assertEquals(CommandBridge.OP_PAUSE_SET, rec.lastOp)
        assertEquals(1, rec.lastA)
        assertEquals(null, rec.lastName)
        bridge.setPaused(false)
        assertEquals(0, rec.lastA)
    }

    @Test
    fun cvarSetCarriesNameAndValue() {
        val rec = Recorder()
        CommandBridge(rec::submit).setCVarInt("gInterpolationFPS", 60)
        assertEquals(CommandBridge.OP_CVAR_SET_INT, rec.lastOp)
        assertEquals(60, rec.lastA)
        assertEquals("gInterpolationFPS", rec.lastName)
    }

    @Test
    fun statusesDecodeAndUnknownIsPermanent() {
        val rec = Recorder(result = 1)
        assertEquals(SubmitStatus.FULL, CommandBridge(rec::submit).saveCVars())
        rec.result = 2
        assertEquals(SubmitStatus.INVALID, CommandBridge(rec::submit).saveCVars())
        rec.result = 99
        assertEquals(SubmitStatus.UNKNOWN, CommandBridge(rec::submit).saveCVars())
    }
}
```

- [ ] **Step 2: Run to verify RED**

Run: `./tools/run-unit-tests.sh --tests '*CommandBridgeTest*'`
Expected: FAIL — `Unresolved reference: CommandBridge`.

- [ ] **Step 3: Implement**

In `NativeBridge.kt`, alongside the existing externals (matching its
established wrapper style — read the file first):

```kotlin
    private external fun nativeSubmitCommand(op: Int, a: Int, b: Int, name: String?): Int
```

plus a public `fun submitCommand(op: Int, a: Int, b: Int, name: String?): Int`
that guards `isNativeAvailable` the same way the snapshot path does
(returning `-1` when native is unavailable — decoded as `UNKNOWN`).

`CommandBridge.kt`:

```kotlin
package com.terminads.mm

/** Outcome of submitting one command; mirrors TdsSubmitStatus + UNKNOWN. */
enum class SubmitStatus { OK, FULL, INVALID, UNKNOWN }

/**
 * The only Kotlin writer into the game. Commands are ABSOLUTE -- callers
 * state the target value, never a delta -- because the UI's view of game
 * state is up to ~100 ms stale by construction (spec §3).
 *
 * @param submit the native entry, normally NativeBridge::submitCommand;
 *   injected so JVM tests need no native library.
 */
class CommandBridge(private val submit: (Int, Int, Int, String?) -> Int) {

    fun setPaused(paused: Boolean): SubmitStatus =
        decode(submit(OP_PAUSE_SET, if (paused) 1 else 0, 0, null))

    fun setCVarInt(name: String, value: Int): SubmitStatus =
        decode(submit(OP_CVAR_SET_INT, value, 0, name))

    fun saveCVars(): SubmitStatus = decode(submit(OP_CVAR_SAVE, 0, 0, null))

    private fun decode(status: Int): SubmitStatus = when (status) {
        0 -> SubmitStatus.OK
        1 -> SubmitStatus.FULL
        2 -> SubmitStatus.INVALID
        // Anything else means the halves disagree -- permanent, surfaced.
        else -> SubmitStatus.UNKNOWN
    }

    companion object {
        // Mirrors TdsCommandOp in mm/2s2h/TerminaDS/CommandMailbox.h.
        const val OP_PAUSE_SET = 1
        const val OP_CVAR_SET_INT = 2
        const val OP_CVAR_SAVE = 3
    }
}
```

- [ ] **Step 4: Run the full suite**

Run: `./tools/run-unit-tests.sh`
Expected: PASS — 87 tests (84 + 3), 0 failures.

- [ ] **Step 5: Commit**

```bash
git add Android/app/src/main/java/com/terminads/mm/NativeBridge.kt \
    Android/app/src/main/java/com/terminads/mm/CommandBridge.kt \
    Android/app/src/test/java/com/terminads/mm/CommandBridgeTest.kt
git commit -m "feat(bridge): Kotlin command seam with absolute pause and CVar ops"
```

---

### Task 5: Pause tracking and routing v2

**Files:**
- Create: `Android/app/src/main/java/com/terminads/mm/secondscreen/PauseRequestTracker.kt`
- Modify: `Android/app/src/main/java/com/terminads/mm/secondscreen/HudModel.kt` (routing + delete the scene-8 special case)
- Test: `Android/app/src/test/java/com/terminads/mm/secondscreen/PauseRequestTrackerTest.kt`
- Test (edit): `Android/app/src/test/java/com/terminads/mm/secondscreen/RouteTest.kt`

**Interfaces:**
- Consumes: schema v2 fields (Task 2).
- Produces:
  - `ScreenKind` gains `data class PauseMenu(val model: HudModel) : ScreenKind`.
  - `route(state)` v2 per the spec §6 table; `ScreenKind.Gameplay` gains
    `val pauseAvailable: Boolean`.
  - `class PauseRequestTracker(private val nowMillis: () -> Long, private val timeoutMillis: Long = 1_000L)`
    with `fun request(target: Boolean)`,
    `fun observe(isPaused: Boolean): PauseRequestState`, and
    `enum class PauseRequestState { IDLE, PENDING, TIMED_OUT }`.
    Task 6 wires it between the button and `CommandBridge`.

- [ ] **Step 1: Write the failing tests**

Append to `RouteTest.kt` (its `snapshot(...)` helper gained
`isPaused`/`saveLoaded`/`menuOpen` defaults in Task 2):

```kotlin
    @Test
    fun noSaveIdlesEvenWithLiveWorld() {
        // Replaces the scene-8 special case: title demo, intro cutscene,
        // file select all publish saveLoaded=false.
        assertEquals(
            ScreenKind.Idle(waitingForGame = false),
            route(BridgeState.Live(snapshot(hasPlayState = true, saveLoaded = false))),
        )
    }

    @Test
    fun pausedRoutesToThePauseMenu() {
        val screen = route(BridgeState.Live(snapshot(hasPlayState = true, isPaused = true)))
        assertTrue(screen is ScreenKind.PauseMenu)
    }

    @Test
    fun stalledWhilePausedStaysOnThePauseMenu() {
        assertTrue(
            route(BridgeState.Stalled(snapshot(hasPlayState = true, isPaused = true), 2400))
                is ScreenKind.PauseMenu,
        )
    }

    @Test
    fun engineMenuDisablesThePauseControl() {
        val screen = route(BridgeState.Live(snapshot(hasPlayState = true, menuOpen = true)))
        assertEquals(false, (screen as ScreenKind.Gameplay).pauseAvailable)
    }

    @Test
    fun normalPlayOffersThePauseControl() {
        val screen = route(BridgeState.Live(snapshot(hasPlayState = true)))
        assertEquals(true, (screen as ScreenKind.Gameplay).pauseAvailable)
    }
```

Delete `preSaveCutsceneSceneIdlesDespiteLiveWorld` (the scene-8 case it
pinned is being removed) and update the remaining expectations if any
referenced it.

`PauseRequestTrackerTest.kt`:

```kotlin
package com.terminads.mm.secondscreen

import org.junit.Assert.assertEquals
import org.junit.Test

class PauseRequestTrackerTest {

    private class Clock(var now: Long = 1_000L)

    @Test
    fun idleUntilRequested() {
        val clock = Clock()
        val tracker = PauseRequestTracker({ clock.now })
        assertEquals(PauseRequestState.IDLE, tracker.observe(isPaused = false))
    }

    @Test
    fun pendingUntilTheSnapshotAcks() {
        val clock = Clock()
        val tracker = PauseRequestTracker({ clock.now })
        tracker.request(target = true)
        assertEquals(PauseRequestState.PENDING, tracker.observe(isPaused = false))
        clock.now += 200
        assertEquals(PauseRequestState.IDLE, tracker.observe(isPaused = true))
    }

    @Test
    fun timesOutVisiblyAndOnce() {
        val clock = Clock()
        val tracker = PauseRequestTracker({ clock.now })
        tracker.request(target = true)
        clock.now += 1_001
        assertEquals(PauseRequestState.TIMED_OUT, tracker.observe(isPaused = false))
        // A timeout is reported once, then the tracker returns to idle.
        assertEquals(PauseRequestState.IDLE, tracker.observe(isPaused = false))
    }

    @Test
    fun ackAtTheBoundaryBeatsTheTimeout() {
        val clock = Clock()
        val tracker = PauseRequestTracker({ clock.now })
        tracker.request(target = true)
        clock.now += 1_000
        assertEquals(PauseRequestState.IDLE, tracker.observe(isPaused = true))
    }
}
```

- [ ] **Step 2: Run to verify RED**

Run: `./tools/run-unit-tests.sh --tests '*RouteTest*' --tests '*PauseRequestTrackerTest*'`
Expected: FAIL — unresolved `PauseMenu`, `pauseAvailable`, `PauseRequestTracker`.

- [ ] **Step 3: Implement**

`PauseRequestTracker.kt`:

```kotlin
package com.terminads.mm.secondscreen

enum class PauseRequestState { IDLE, PENDING, TIMED_OUT }

/**
 * Tracks one in-flight pause/resume request against the observed snapshot.
 * The UI never assumes a command took effect: it stays PENDING until the
 * snapshot's isPaused matches the requested target, and a request that
 * never acks becomes one visible TIMED_OUT observation (spec §4) before
 * returning to idle.
 */
class PauseRequestTracker(
    private val nowMillis: () -> Long,
    private val timeoutMillis: Long = 1_000L,
) {
    private var target: Boolean? = null
    private var requestedAt = 0L

    fun request(target: Boolean) {
        this.target = target
        requestedAt = nowMillis()
    }

    fun observe(isPaused: Boolean): PauseRequestState {
        val wanted = target ?: return PauseRequestState.IDLE
        if (isPaused == wanted) {
            target = null
            return PauseRequestState.IDLE
        }
        if (nowMillis() - requestedAt > timeoutMillis) {
            target = null
            return PauseRequestState.TIMED_OUT
        }
        return PauseRequestState.PENDING
    }
}
```

In `HudModel.kt`: delete `CUTSCENE_SCENE_ID` and `inPlayableWorld` (their
comment promised exactly this replacement); add to `ScreenKind`:

```kotlin
    data class PauseMenu(val model: HudModel) : ScreenKind
```

change `Gameplay` to
`data class Gameplay(val model: HudModel, val stalledSeconds: Long?, val pauseAvailable: Boolean) : ScreenKind`,
and rewrite the two live/stalled branches:

```kotlin
    is BridgeState.Live -> routeSnapshot(state.snapshot, stalledSeconds = null)
    is BridgeState.Stalled -> routeSnapshot(state.snapshot, state.millisSinceChange / 1000)
```

with:

```kotlin
/** Spec §6: the routing table for a decoded snapshot. */
private fun routeSnapshot(s: GameSnapshot, stalledSeconds: Long?): ScreenKind = when {
    !s.hasPlayState || !s.saveLoaded -> ScreenKind.Idle(waitingForGame = false)
    s.isPaused -> ScreenKind.PauseMenu(deriveHudModel(s))
    else -> ScreenKind.Gameplay(
        deriveHudModel(s),
        stalledSeconds,
        pauseAvailable = !s.menuOpen,
    )
}
```

- [ ] **Step 4: Run the full suite**

Run: `./tools/run-unit-tests.sh`
Expected: PASS — 95 tests (87 − 1 deleted + 5 route + 4 tracker), 0 failures.
(Task 6 updates `SecondScreenHost`'s `when` for the new `PauseMenu` variant —
until then the host does not compile against `route()`? It does: the `when`
on `ScreenKind` in `SecondScreenHost.kt` must gain the new branch NOW for the
suite to compile. Add a temporary branch rendering `IdlePlate(false)` with a
`// Task 6 replaces this with the pause menu` comment, and update
`GameplayScreen`'s call site signature only in Task 6 — pass
`screen.pauseAvailable` through when you get there; for this task change the
host's `Gameplay` destructuring minimally so it compiles.)

- [ ] **Step 5: Commit**

```bash
git add Android/app/src/main/java/com/terminads/mm/secondscreen/PauseRequestTracker.kt \
    Android/app/src/main/java/com/terminads/mm/secondscreen/HudModel.kt \
    Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenHost.kt \
    Android/app/src/test/java/com/terminads/mm/secondscreen/PauseRequestTrackerTest.kt \
    Android/app/src/test/java/com/terminads/mm/secondscreen/RouteTest.kt
git commit -m "feat(secondscreen): route on saveLoaded and pauseState, track pause acks"
```

---

### Task 6: The PAUSE control and the root-menu skeleton

**Files:**
- Modify: `Android/app/src/main/java/com/terminads/mm/secondscreen/GameplayScreen.kt` (PAUSE control in the nav bar)
- Create: `Android/app/src/main/java/com/terminads/mm/secondscreen/PauseMenuScreen.kt`
- Modify: `Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenHost.kt` (wiring)
- Modify: `Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenPresentation.kt` — **constraint release:** construct `CommandBridge` + `PauseRequestTracker` beside the existing poller (same pattern, outside recomposition) and pass them into `SecondScreenHost`

**Interfaces:**
- Consumes: `CommandBridge` (Task 4), `PauseRequestTracker`, `ScreenKind.PauseMenu`, `Gameplay.pauseAvailable` (Task 5), design tokens (Phase 3).
- Produces: `SecondScreenHost(displayInfo, pollBridge, commandBridge, pauseTracker, pollIntervalMillis)` — the presentation's construction site changes accordingly.

This task is layout plus wiring; the tracker and routing logic it renders
were tested in Task 5. The full-suite run is the compile gate.

- [ ] **Step 1: Add the PAUSE control to the nav bar in `GameplayScreen.kt`**

`GameplayScreen` signature becomes:

```kotlin
@Composable
fun GameplayScreen(
    model: HudModel,
    stalledSeconds: Long?,
    pauseAvailable: Boolean,
    pausePending: Boolean,
    pauseFailed: Boolean,
    onPauseTap: () -> Unit,
)
```

`NavBar` becomes a `Box` so the tab row stays centered while PAUSE anchors
right (user placement):

```kotlin
@Composable
private fun NavBar(
    pauseAvailable: Boolean,
    pausePending: Boolean,
    pauseFailed: Boolean,
    onPauseTap: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier) {
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(du(56f), Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab("MAP", active = true)
            NavTab("ITEMS", active = false)
            NavTab("MASKS", active = false)
        }
        PauseControl(
            available = pauseAvailable,
            pending = pausePending,
            failed = pauseFailed,
            onTap = onPauseTap,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = du(30f)),
        )
    }
}

/**
 * Design delta (spec §5): the handoff assumed a physical pause key; this
 * control borrows its RESUME PLAY action vocabulary — gold underlined
 * Cinzel. Disabled while the engine's own menu is up or no save is loaded.
 */
@Composable
private fun PauseControl(
    available: Boolean,
    pending: Boolean,
    failed: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = when {
        !available -> TerminaColors.TextDimmer
        pending -> TerminaColors.GoldDim
        else -> TerminaColors.GoldLight
    }
    val underline = if (available) TerminaColors.Gold else Color.Transparent
    Column(
        modifier
            .width(IntrinsicSize.Min)
            .height(du(46f * LEGIBILITY))
            .clickable(enabled = available && !pending, onClick = onTap)
            .semantics {
                contentDescription = when {
                    !available -> "Pause unavailable while the game's own menu is open"
                    pending -> "Pausing"
                    else -> "Pause the game"
                }
                if (!available) disabled()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("PAUSE", style = TerminaType.PauseAction.toStyle(ink))
        Box(Modifier.fillMaxWidth().height(du(2f * LEGIBILITY)).background(underline))
    }
    if (failed) {
        Text(
            "PAUSE FAILED",
            style = TerminaType.StallChip.toStyle(TerminaColors.GoldDim),
            modifier = Modifier.padding(top = du(4f)),
        )
    }
}
```

Add to `TerminaType` in `TerminaDesign.kt`:

```kotlin
    val PauseAction = DesignTextSpec(TerminaFonts.Cinzel, FontWeight.Bold, 19f, 5f)
```

Imports needed in `GameplayScreen.kt`: `androidx.compose.foundation.clickable`,
`androidx.compose.foundation.layout.IntrinsicSize`,
`androidx.compose.foundation.layout.width`. `PAUSE FAILED` placement: wrap
`PauseControl` + the failure text in a `Column` if the inline layout fights —
keep the hint out of `contentDescription` churn (it is visible text only;
its semantics node carries the static description "Pause failed").

- [ ] **Step 2: Write `PauseMenuScreen.kt` (skeleton — Plan B restyles to full §5)**

```kotlin
package com.terminads.mm.secondscreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics

/**
 * Pause root menu skeleton (handoff §5). Plan A ships the five rows with
 * RESUME live and the rest inert; Plan B brings the full §5 styling
 * (diamonds, sub-lines, warm SONG OF TIME) and lights up OPTIONS.
 */
@Composable
fun PauseMenuScreen(model: HudModel, resumePending: Boolean, onResumeTap: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row {
            Text(
                "${model.dayLabel ?: ""} · ${model.clockTime} ${model.clockSuffix}",
                style = TerminaType.IdleCaption.toStyle(TerminaColors.TextHint),
            )
        }
        MenuRow("RESUME", enabled = true, pending = resumePending, onTap = onResumeTap)
        MenuRow("INVENTORY", enabled = false)
        MenuRow("MAP", enabled = false)
        MenuRow("SONG OF TIME", enabled = false)
        MenuRow("OPTIONS", enabled = false)
    }
}

@Composable
private fun MenuRow(
    label: String,
    enabled: Boolean,
    pending: Boolean = false,
    onTap: () -> Unit = {},
) {
    val ink = when {
        !enabled -> TerminaColors.TextDimmer
        pending -> TerminaColors.GoldDim
        else -> TerminaColors.Ink2
    }
    Text(
        label,
        style = TerminaType.NavTab.toStyle(ink),
        modifier = Modifier
            .height(du(106f))
            .padding(du(16f))
            .clickable(enabled = enabled && !pending, onClick = onTap)
            .semantics {
                contentDescription =
                    if (enabled) label else "$label, available in a future update"
                if (!enabled) disabled()
            },
    )
}
```

- [ ] **Step 3: Wire the host**

`SecondScreenHost.kt` — new signature and pause plumbing:

```kotlin
@Composable
fun SecondScreenHost(
    displayInfo: DisplayInfo,
    pollBridge: () -> BridgeState,
    commandBridge: CommandBridge,
    pauseTracker: PauseRequestTracker,
    pollIntervalMillis: Long = 100L,
) {
    var state by remember { mutableStateOf<BridgeState>(BridgeState.NoFramesYet) }
    var pauseRequestState by remember { mutableStateOf(PauseRequestState.IDLE) }

    LaunchedEffect(pollBridge, pollIntervalMillis) {
        while (true) {
            val polled = pollBridge()
            state = polled
            val paused = when (polled) {
                is BridgeState.Live -> polled.snapshot.isPaused
                is BridgeState.Stalled -> polled.snapshot.isPaused
                else -> false
            }
            pauseRequestState = pauseTracker.observe(paused)
            delay(pollIntervalMillis)
        }
    }

    DesignRoot {
        when (val screen = route(state)) {
            is ScreenKind.Gameplay -> GameplayScreen(
                model = screen.model,
                stalledSeconds = screen.stalledSeconds,
                pauseAvailable = screen.pauseAvailable,
                pausePending = pauseRequestState == PauseRequestState.PENDING,
                pauseFailed = pauseRequestState == PauseRequestState.TIMED_OUT,
                onPauseTap = {
                    if (commandBridge.setPaused(true) == SubmitStatus.OK) {
                        pauseTracker.request(target = true)
                    }
                },
            )
            is ScreenKind.PauseMenu -> PauseMenuScreen(
                model = screen.model,
                resumePending = pauseRequestState == PauseRequestState.PENDING,
                onResumeTap = {
                    if (commandBridge.setPaused(false) == SubmitStatus.OK) {
                        pauseTracker.request(target = false)
                    }
                },
            )
            is ScreenKind.Idle -> IdlePlate(screen.waitingForGame)
            is ScreenKind.Diagnostic -> DiagnosticPlate(screen.message, displayInfo)
        }
    }
}
```

(Remove the Task 5 temporary `PauseMenu -> IdlePlate` branch. Imports:
`com.terminads.mm.CommandBridge`, `com.terminads.mm.SubmitStatus`.)

`SecondScreenPresentation.kt` — beside the existing poller construction (same
main-thread, outside-recomposition pattern; this is the sanctioned edit):

```kotlin
        val commandBridge = CommandBridge(NativeBridge::submitCommand)
        val pauseTracker = PauseRequestTracker(SystemClock::uptimeMillis)
```

and pass both into the `SecondScreenHost(...)` call. Match the file's
existing import and reference style exactly (read it first — the poller
construction at ~line 40 is the template).

- [ ] **Step 4: Compile gate + full suite**

Run: `./tools/run-unit-tests.sh`
Expected: PASS — 95 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add Android/app/src/main/java/com/terminads/mm/secondscreen/GameplayScreen.kt \
    Android/app/src/main/java/com/terminads/mm/secondscreen/PauseMenuScreen.kt \
    Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenHost.kt \
    Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenPresentation.kt \
    Android/app/src/main/java/com/terminads/mm/secondscreen/TerminaDesign.kt
git commit -m "feat(secondscreen): pause control, ack tracking, root-menu skeleton"
```

---

### Task 7: Install candidate and docs

**Files:**
- Modify: `docs/HANDOFF.md`

- [ ] **Step 1:** Full pipeline build (background): `./tools/build-apk.sh` — expected BUILD SUCCESSFUL.
- [ ] **Step 2:** Symbol + o2r gate (Docker llvm-nm): all three JNI symbols `T`, `mm.o2r` absent.
- [ ] **Step 3:** `adb install -r` on the Thor (orchestrator/user; record honestly if unreachable).
- [ ] **Step 4:** Update `docs/HANDOFF.md`: Phase 4a status line ("command mailbox + pause implemented, pending hardware verification; Plan B — full pause menu styling + Options — next"); file-table rows for `CommandMailbox.{h,cpp}`, `CommandBridge.kt`, `PauseRequestTracker.kt`, `PauseMenuScreen.kt`; §2's "read-only" claim amended to name the mailbox as the sanctioned write path; the Z+R frame-step quirk documented (holding Z+R single-steps the frozen game — engine dev affordance, z_pause.c:44, accepted).
- [ ] **Step 5:** Commit: `git commit -m "docs: record the Phase 4a pause bridge build"`.
- [ ] **Step 6:** Hand the hardware checklist to the user: pause round-trip latency and the frozen top screen; PAUSE disabled during kaleido/BenMenu; resume; backgrounding while paused (screen releases, game stays frozen, menu restored on return); the idle plate on title/intro (saveLoaded now gating it); Z+R quirk sanity check; TalkBack over the new control and menu rows.

---

## Self-Review Notes

- **Spec coverage (Plan A scope):** §3 mailbox/schema/JNI → Tasks 1–4; §4
  pause semantics → Tasks 5–6 (ack tracker, disabled states, timeout); §6
  routing table → Task 5; §2 ground truth embedded in code comments with the
  same file:line citations; §5 pause button + skeleton menu → Task 6; §8
  invariants → Global Constraints. Deferred to Plan B (stated in the goal):
  full §5 styling, §5/§10 Options, §7 row semantics beyond the pause control,
  Compose UI test infra, keystore, TalkBack-led full checklist.
- **Type consistency:** `TerminaDS_SubmitCommand(op,a,b,name)` matches the
  JNI call and `CommandBridge`'s lambda `(Int,Int,Int,String?)->Int`; opcode
  values 1/2/3 match across `CommandMailbox.h`, `CommandBridge.companion`;
  `ScreenKind.Gameplay(model, stalledSeconds, pauseAvailable)` matches Task
  5's definition and Task 6's host; tracker API matches its test.
- **Final test count:** 104 JVM tests. Trust failure/error counts over totals
  if the baseline drifts.
- **Submit-failure UX note:** Task 6 surfaces non-OK pause/resume submissions
  as target-specific visible failures per spec §4; the tracker never goes
  pending for rejected commands.
