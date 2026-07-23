# Termina DS — Phase 2: Read-Only Game-State Bridge

**Date:** 2026-07-23
**Status:** Approved for implementation planning
**Scope:** Phase 2 only — reading game state. Writing is Phase 5.

---

## 1. Overview

Phase 1 proved a Compose UI can live on the Thor's bottom display and call into
native code. It carries a placeholder: display metrics, a tap counter, and a
native uptime heartbeat. The heartbeat proves the JNI seam is wired, but it is
thread-agnostic and touches no game data, so it proves nothing about reading the
game.

Phase 2 replaces it with a real one: a per-frame snapshot of the running game's
C structs, published on the game thread and consumed by Compose on the Android
main thread. Every visible feature on the roadmap — HUD, map, walkthrough sync —
reads from this snapshot. Nothing after this phase talks to the engine directly.

**Phase 2 done when:** the bottom screen displays live values from
`gSaveContext`, `PlayState`, and `Player` that track the game in real time, and
survives scene transitions — where `gPlayState` becomes NULL underneath a live
reader — without crashing.

## 2. Non-goals

- **Any write to game state.** Phase 5. Nothing in this phase mutates anything.
- **A real HUD.** The Phase 2 UI is a deliberately ugly debug readout, deleted
  wholesale by Phase 3.
- **Inventory, quest, dungeon-item, or week-event bitfields.** Added when a
  feature needs them; the payload is versioned to make that cheap.
- **Map rendering or coordinate projection.** Phase 6. Phase 2 supplies the
  position data it will consume.

## 3. Verified ground truth

Confirmed by reading the tree, not assumed. Implementation should re-verify if
these files have moved.

| Fact | Evidence |
|---|---|
| Per-frame hook exists and is upstream-provided | `GameInteractor_ExecuteOnGameStateUpdate()` at `mm/src/code/game.c:168` |
| It fires at the *end* of `GameState_Update`, after `main()` and after the draw | `mm/src/code/game.c:155-168` |
| It fires for **every** gamestate, not only gameplay | it is in `GameState_Update`, not `Play_Update` |
| Hooks can be registered with no edit to any inherited file | `RegisterShipInitFunc` in `mm/2s2h/ShipInit.hpp:30`; ~20 existing uses in `mm/2s2h/BenGui/CosmeticEditor.cpp` |
| Init funcs run at startup, before the loop | `ShipInit::InitAll()` at `mm/2s2h/BenPort.cpp:725` |
| `gPlayState` is NULL between scenes | cleared `mm/src/code/z_play.c:481`, set `:2254` |
| The player actor can be absent while a PlayState exists | `GET_PLAYER(play)` is the head of the `ACTORCAT_PLAYER` list — `mm/include/z64play.h:137` |
| The game runs on a dedicated thread, not the Android main thread | `mSDLThread` created in `Android/app/src/main/java/org/libsdl/app/SDLActivity.java:712` |
| Vitals live in an always-valid static struct | `SavePlayerData` in `mm/include/z64save.h`; `SaveContext gSaveContext` at `mm/src/code/z_common_data.c:10` |
| Button items and ammo have existing accessors | `GET_CUR_FORM_BTN_ITEM(btn)` (`z64save.h:619`), `AMMO(item)` (`z64save.h:573`) |

The last one matters more than it looks: `GET_CUR_FORM_BTN_ITEM` already encodes
MM's quirk that the B button is per-transformation while the C buttons are
shared across forms. Using the engine's macro rather than indexing
`buttonItems[form][n]` by hand avoids reimplementing that rule wrongly.

## 4. The problem this phase actually solves

The JNI call is the easy half. The hard half is that **the game runs on
`SDLThread` and Compose runs on the Android main thread**, and the interesting
state is reached through pointers that the game thread invalidates.

`gSaveContext` is a static struct and is always addressable. `gPlayState` is a
pointer that is set to NULL during every scene transition, and `GET_PLAYER`
walks an actor list that is empty during early scene init. A main-thread read of
either is not a torn-value risk — it is a segfault, on a device where the
failure is a hard crash of the game the user is playing.

No amount of care in Kotlin can make that read safe. The dereference has to
happen on the thread that owns the pointer's lifetime.

## 5. Architecture decision

**Decision: the game thread publishes an integer snapshot under a seqlock; the
UI thread pulls a consistent copy at its own cadence.**

### Alternatives considered

**B — Shared direct `ByteBuffer`, no JNI call while polling.** Native allocates
a direct buffer once, hands it to Kotlin, and writes into it each frame; Kotlin
reads fields by byte offset with zero JNI crossings per poll.

*Rejected.* The layout becomes a second source of truth living in Kotlin, and a
wrong offset does not fail loudly — it renders as garbage on the one screen that
cannot be screenshotted (§10 of the Phase 0/1 spec; both Thor displays are
`FLAG_SECURE`). Each iteration to find such a bug costs a full build. The
seqlock would also have to be implemented twice, once per language, doubling the
surface of the one genuinely subtle piece of code in the phase. The performance
it buys is irrelevant: one JNI call at 10 Hz is not a cost worth optimizing.

**C — Push: native upcalls into Kotlin each frame.** `CallVoidMethod` from the
game thread on every frame.

*Rejected* on two independent grounds. It runs JVM code on the render thread, so
a GC pause or a badly-scheduled recomposition stalls the game directly — the
opposite of the Phase 1 requirement that the top screen be measurably
unaffected. And because the Presentation is main-thread-only (invariant §3.1 of
`docs/HANDOFF.md`), delivery would still need a `Handler` post afterward: an
upcall *and* a thread hop, 60 times a second, to feed a screen that needs about
ten updates a second.

### Rationale for the chosen approach

- **Pointer validity is resolved where it can be.** Every dereference happens on
  the game thread inside the hook, where the engine guarantees the pointers are
  sane. The UI thread only ever touches plain integers that were copied while
  they were valid.
- **The game thread is never blocked.** The writer takes no lock and never
  waits; it bumps a counter, writes, and bumps it again. A slow or descheduled
  reader cannot stall a frame.
- **The decode is testable without a device.** Turning integers into a data
  class is a pure function, covered by the existing JVM unit-test target. Given
  the build costs 8-19 minutes and the screen cannot be captured, moving logic
  out of native and into testable Kotlin has outsized value here.
- **It touches no inherited files**, satisfying the upstream-tracking discipline
  in §11 of the Phase 0/1 spec.

## 6. Components

Eight files: four new, four modified. **No inherited file is touched** — every
modified file is one Termina DS already owns.

### Native — `mm/2s2h/TerminaDS/`

**`GameSnapshot.h`** (new) — defines the payload's index enum and the flag bits.
This enum is the entire layout contract. Header-only.

**`SnapshotPublisher.cpp`** (new) — the game-thread half. A file-scope
`static RegisterShipInitFunc` registers an `OnGameStateUpdate` handler at
startup. The handler samples `gSaveContext`, null-checks `gPlayState` and then
`GET_PLAYER`, fills the staging array, and publishes it under the seqlock. Owns
the seqlock and exposes a read function for the JNI layer.

**This is the only file in the project that dereferences game pointers.** That
containment is deliberate: it is the file to read when reasoning about frame
safety, and the only file Phase 5 has to revisit when writes arrive.

**`NativeBridge.cpp`** (modified) — keeps `nativeGetUptimeMillis`, which remains
the honest "is native loaded at all" signal, independent of whether the game
loop runs. Gains one entry point:

```
nativeReadSnapshot(int[] out) -> boolean
```

It performs no game-state work itself; it asks `SnapshotPublisher` for a
consistent copy. Keeping engine internals out of the JNI file keeps the seam
legible as a seam.

### Kotlin — `Android/app/src/main/java/com/terminads/mm/`

**`GameSnapshot.kt`** (new) — an immutable data class plus
`decode(IntArray): GameSnapshot`. Pure, no Android dependencies, fully unit
tested. Mirrors the index constants from `GameSnapshot.h`, with a comment naming
that header as the source of truth.

**`GameSnapshotPoller.kt`** (new) — drives the main-thread poll, owns the
reusable `IntArray`, tracks staleness, and exposes the result as Compose state.
Separated from the host so that cadence and staleness are testable in isolation,
and so `SecondScreenHost` keeps the property §10.1 of the Phase 0/1 spec gave
it: it knows nothing about SDL, JNI, or displays.

**`NativeBridge.kt`** (modified) — gains `readSnapshot(out: IntArray): Boolean`,
with the same `UnsatisfiedLinkError` → sentinel treatment the heartbeat uses.
Remains the only file in the Kotlin layer permitted to call native.

**`secondscreen/SecondScreenHost.kt`** (modified) — placeholder body replaced by
the debug readout. Signature stays state-in, events-out.

**`secondscreen/SecondScreenPresentation.kt`** (modified) — constructs the
poller and passes it to the host, replacing the `uptimeMillisProvider` lambda it
passes today. This is the single wiring point; it remains the only place that
knows both `NativeBridge` and the Compose host exist.

## 7. Payload

**The array is the struct.** `GameSnapshot.h` defines one enum of indices into
an `int32_t[SNAP_COUNT]`; there is no separate C struct for JNI to translate
field by field. This eliminates drift between a definition and its marshalling
code, and sidesteps padding and endianness entirely. Native publishes into the
array, `SetIntArrayRegion` copies it out wholesale, Kotlin indexes the same
positions.

27 slots, 108 bytes.

| Group | Slots |
|---|---|
| Header | `SCHEMA_VERSION`, `FRAME_COUNTER`, `FLAGS` |
| Vitals | `HEALTH`, `HEALTH_CAPACITY`, `MAGIC`, `MAGIC_CAPACITY`, `MAGIC_LEVEL`, `RUPEES` |
| Form and clock | `PLAYER_FORM`, `EQUIPPED_MASK`, `DAY`, `TIME_OF_DAY` |
| Buttons | `BTN_ITEM_{B,C_LEFT,C_DOWN,C_RIGHT}`, `BTN_AMMO_{B,C_LEFT,C_DOWN,C_RIGHT}` |
| World | `SCENE_ID`, `ROOM_NUM`, `PLAYER_X`, `PLAYER_Y`, `PLAYER_Z`, `PLAYER_YAW` |

`FLAGS` bits: `PLAY_STATE_VALID`, `PLAYER_VALID`, `IS_NIGHT`, `DOUBLE_DEFENSE`.

The first two flags are the honest answer to "was there a world to read this
frame". When they are clear the six World slots are **zeroed, not left stale**,
so the UI cannot present a position from a previous scene as current.

Signed engine values (`s16` health, `s16` rupees) are sign-extended into their
slots. Positions cross as **raw IEEE-754 bits** — `memcpy` into `int32_t`
natively, `Float.fromBits()` in Kotlin — for an exact round trip with no
conversion logic in the JNI layer.

### Field sources

Named explicitly because several are not where they first appear to be —
`magicCapacity` in particular sits on `SaveContext` itself, not alongside the
other vitals in `SavePlayerData`.

| Slot(s) | Source |
|---|---|
| `HEALTH`, `HEALTH_CAPACITY`, `MAGIC`, `MAGIC_LEVEL`, `RUPEES` | `gSaveContext.save.saveInfo.playerData` |
| `MAGIC_CAPACITY` | `gSaveContext.magicCapacity` |
| `PLAYER_FORM`, `EQUIPPED_MASK`, `DAY`, `TIME_OF_DAY` | `gSaveContext.save` (`playerForm`, `equippedMask`, `day`, `time`) |
| `IS_NIGHT` flag | `gSaveContext.save.isNight` |
| `DOUBLE_DEFENSE` flag | `gSaveContext.save.saveInfo.playerData.doubleDefense` |
| `BTN_ITEM_*` | `GET_CUR_FORM_BTN_ITEM(btn)` for `EQUIP_SLOT_B`, `_C_LEFT`, `_C_DOWN`, `_C_RIGHT` |
| `BTN_AMMO_*` | `AMMO(item)` for each resolved button item |
| `SCENE_ID` | `gPlayState->sceneId` |
| `ROOM_NUM` | `gPlayState->roomCtx.curRoom.num` (`s8`; -1 means invalid) |
| `PLAYER_X/Y/Z` | `GET_PLAYER(gPlayState)->actor.world.pos` |
| `PLAYER_YAW` | `GET_PLAYER(gPlayState)->actor.shape.rot.y` — the *visual* facing, which is what a map indicator should show, rather than `world.rot.y` |

### Why the header earns three slots

Two failure modes are specific to this repo and both present identically as a
frozen readout.

`docs/HANDOFF.md` §5 documents that a new `.cpp` can compile green and ship
without its code, because CMake's `GLOB_RECURSE` freezes the source list at
configure time. If `SnapshotPublisher.cpp` does not ship, `nativeReadSnapshot`
fails to link and the existing `UnsatisfiedLinkError` path catches it. The
nastier case is a *partial* rebuild where native and Kotlin disagree about
layout, which renders as plausible-looking wrong numbers. `SCHEMA_VERSION` turns
that into an explicit mismatch message.

`FRAME_COUNTER` separates "the bridge is broken" from "the game loop stopped",
which are otherwise indistinguishable.

### Accepted duplication

Kotlin mirrors the 27 index constants by hand. Codegen is not justified at this
size. The duplication is guarded at runtime by `SCHEMA_VERSION` and at build
time by a unit test asserting `SNAP_COUNT` and the boundary indices.

## 8. Concurrency protocol

Single writer (game thread), single reader (main thread), no locks.

**Writer**, once per frame in the `OnGameStateUpdate` handler:

1. bump `seq` to odd, release ordering — signals "write in progress"
2. fill the value slots
3. bump `seq` to even, release ordering — signals "stable"

**Reader**, on the Android main thread:

1. load `seq`, acquire ordering; retry if odd
2. copy the values
3. load `seq` again; accept only if unchanged
4. up to **4 attempts**, then return `false`

Four attempts is generous: the writer holds the array for well under a
microsecond, once every 16.6 ms.

**The value slots are declared `std::atomic<int32_t>` and accessed with
`memory_order_relaxed`**, not as plain `int32_t`. A seqlock over non-atomic data
is formally a data race under the C++ memory model even though it works in
practice. On arm64 relaxed atomics compile to ordinary loads and stores, so this
is free at runtime and makes the code defined rather than incidentally correct.
Under a 19-minute build cycle on a device that cannot be debugged interactively,
that trade is obvious.

Publishing is **unconditional** — it does not check whether a second screen is
attached. Roughly 30 field reads and a 108-byte store against a 16.6 ms budget.
Gating would add a second cross-thread flag to keep coherent and would leave the
first poll after the bottom screen appears with nothing to show.

## 9. Lifecycle and cadence

The hook is registered once at startup and never unregistered.

**Poll cadence: 10 Hz**, from a `LaunchedEffect` on the main thread — the shape
the existing heartbeat already uses, faster. Hearts, magic, and rupees change on
human timescales; 100 ms of latency is imperceptible. Locking to the panel's
60 Hz refresh via `withFrameNanos` was considered and rejected: 6× the JNI
crossings and recompositions to update a number sooner than anyone can perceive.
Because cadence lives only in `GameSnapshotPoller`, raising it for Phase 6's map
dot is a one-line change.

**Scope:** the poller starts when the Compose UI enters composition and stops
when it leaves; the composition is driven by the Presentation's show/dismiss. No
threads, no executors, no handlers — this stays on the main thread, honoring
invariant §3.1 of `docs/HANDOFF.md`. With no bottom screen attached, nothing
polls and the publisher writes into a buffer nobody reads.

## 10. Validity states and error handling

Null pointers are handled where they are read, on the game thread. A NULL
`gPlayState` clears `PLAY_STATE_VALID` and zeroes the world slots. A NULL
`GET_PLAYER` clears `PLAYER_VALID` independently, because a PlayState can exist
before the player actor spawns.

`nativeReadSnapshot` never throws across JNI. It returns `false` for a
too-small array or an exhausted retry budget, and `false` is not a panic — the
poller keeps the previous snapshot.

Six states the debug readout distinguishes. On a screen that cannot be captured,
frozen numbers must be diagnosable from the numbers themselves.

| State | Detection | Meaning |
|---|---|---|
| Native not loaded | `UnsatisfiedLinkError` | `.so` absent or symbol missing |
| Schema mismatch | `SCHEMA_VERSION` ≠ expected | stale native from a bad re-glob |
| No frames yet | `FRAME_COUNTER == 0` | publisher never ran; hook not registered |
| Game loop stalled | counter unchanged ~1 s | native alive, game not stepping |
| No world | `PLAY_STATE_VALID` clear | title or file select; save fields valid, world zeroed |
| Live | all flags set | everything readable |

Read-failure-after-retries is deliberately not among these. It is transient by
nature; the frame-counter staleness check catches it if it ever persists.

**No allocation per poll.** The `IntArray` is reused and JNI uses
`SetIntArrayRegion` with no object creation, so there are no local references to
leak at 10 Hz. The decoded `GameSnapshot` is a data class, so structural
equality is free: when nothing changed, Compose sees an equal `State` value and
skips recomposition. The bottom screen redraws only when the game changed.

## 11. Deliverable: the debug readout

A monospace dump of every slot, raw, plus the frame counter and the current
validity state. **Deliberately not designed.** Phase 3 deletes it.

The reasoning follows §10.5 of the Phase 0/1 spec. Building the real HUD here
would conflate "is the bridge correct" with "is the HUD right", and a
mis-decoded value would hide behind a plausible-looking heart row. Raw output
makes a wrong decode obvious: a mis-signed `s16` reads as garbage, not as a
believable heart count.

It carries a `contentDescription` for the TalkBack check, consistent with the
Phase 1 placeholder.

## 12. Testing and verification

### JVM unit tests

No device needed; joins the existing 13 in `./gradlew :app:testReleaseUnitTest`.

- `decode()` over hand-built arrays: known values produce the expected data class
- sign extension of negative `s16` values
- float-bit round trip for positions
- flag unpacking, including the world-zeroed case
- schema-mismatch detection
- index constants: `SNAP_COUNT` and boundary indices
- poller staleness logic, driven by a fake reader and a fake clock

### Native-side checks

There is no unit-test harness for game code in this tree, so the native half
gets two compensating checks.

- `llvm-nm -D` on the packaged `arm64-v8a` `.so`, grepping for
  `Java_com_terminads_mm_NativeBridge_nativeReadSnapshot`. Per `docs/HANDOFF.md`
  §5, a green build is not proof a symbol shipped.
- One `__android_log_print` on the publisher's **first** publish, so logcat
  proves the static registration ran without needing the user's eyes. With both
  displays `FLAG_SECURE`, every diagnostic routable through logcat is worth
  having.

### On-device verification (requires the user)

1. Title screen — "no world", frame counter advancing
2. Load a save — world flags set, scene and room populated
3. Take damage — health drops within ~100 ms
4. Collect rupees — rupee count tracks
5. Use magic — magic drains
6. Walk — X/Z move smoothly, yaw changes when turning
7. Transform with a mask — `playerForm` and the B/C items change together
8. **Scene transition — world flags blink clear, then repopulate, no crash**
9. Top-screen framerate unchanged versus the Phase 1 build; measured, per §10.6.3
   of the Phase 0/1 spec
10. Background and return — polling stops and resumes cleanly
11. TalkBack reads the readout

Step 8 is the decisive one. It is the only step that exercises `gPlayState`
going NULL while a reader is live — the single failure this architecture exists
to prevent. If the design is wrong, that is where it crashes.

## 13. Invariants preserved

| Invariant | How this phase honors it |
|---|---|
| Main-thread only for the Presentation and its lifecycle owner (`HANDOFF.md` §3.1) | The poller is a main-thread `LaunchedEffect`. No thread, executor, or handler is introduced. |
| `mm.o2r` never ships (§3.2) | Untouched; `verifyBundledAssets` unmodified. |
| `compileSdk 34` / `targetSdk 33` / `minSdk 24` / `arm64-v8a` (§3.3) | No Gradle change of any kind. |
| New native code lives in `mm/2s2h/TerminaDS/` (§3.4) | Both new native files land there; the recursive glob picks them up. |
| `NativeBridge.kt` is the only Kotlin caller of native (Phase 0/1 spec §10.1) | `GameSnapshotPoller` calls `NativeBridge`, never native directly. |
| Prefer new files over edits to inherited ones (§11) | Zero inherited files touched. `RegisterShipInitFunc` removes the need for a call site. |

## 14. What Phase 3 inherits

A `GameSnapshot` data class updating at 10 Hz as Compose state, carrying live
vitals, form, clock, C-button assignments with ammo, and world position — with
explicit validity flags so the UI knows when a field is meaningful. Phase 3
deletes the debug readout and renders hearts, rupees, magic, and C-items from
the same object, adding no new native code.

Extending the payload later costs: one enum entry, one field in the publisher,
one field in the decoder, one test, and a `SCHEMA_VERSION` bump.
