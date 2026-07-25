# Termina DS Phase 4: Pause Bridge and Settings

**Date:** 2026-07-25
**Status:** Approved (2026-07-25)
**Depends on:** Phase 3 gameplay HUD (`docs/superpowers/specs/2026-07-24-termina-ds-phase-3-gameplay-hud-design.md`), hardware-verified 2026-07-25.
**Design source:** `docs/design/second-screen-handoff/README.md` §5 (pause root menu), §6 (subscreen chrome), §10 (Options).

## 1. What Phase 4 is

The first write path into the game — deliberately the narrowest one: a
command mailbox whose Phase 4 vocabulary is pause, CVar set, and CVar save.
On top of it: a pause button on the gameplay screen, the handoff's pause
root menu (§5), and the Options subscreen (§10) with both Graphics
categories re-skinning CVars that 2S2H's own SELECT-key BenMenu already
exposes. Settings are reachable **only while paused** (user decision); the
game's native START pause remains untouched and coexists until later phases
fully replace it.

| Area | Phase 4? | Notes |
|---|---|---|
| Command mailbox (pause + CVar ops) | **Yes** | The §3 architecture |
| Pause button + §5 root menu | **Yes** | INVENTORY / MAP / SONG OF TIME rows present but inert |
| §10 Options — both Graphics categories | **Yes** | Real CVars per §5 below; other categories show the designed empty states |
| Schema v2 (`saveLoaded`, `pauseState`, `menuOpen`) | **Yes** | Retires the scene-8 hotfix |
| Top-screen PAUSED veil (handoff §2) | No | Engine-side rendering; the top screen shows the frozen game frame |
| Item assignment, warps, kaleido replacement | No | Phase 5+ |
| Compose UI test infrastructure | **Yes** | Debt called in by Phase 3's nav bug; scope in §9 |
| Release keystore | **Yes** | Orchestrator task via `tools/make-keystore.sh`; ends debug-keystore fragility |

## 2. Ground truth (verified in-tree, 2026-07-25)

| Fact | Evidence |
|---|---|
| The whole Play update body is gated by frame advance; rendering continues when frozen | `mm/src/code/z_play.c:988` — `if (FrameAdvance_Update(&this->frameAdvCtx, &input[1])) { …update… }` |
| Frame advance is engine-native, initialized per PlayState | `z_play.c:2414` (`FrameAdvance_Init`), `z_play.c:2096` (`FrameAdvance_IsEnabled`), `z_pause.c:36` (update) |
| `OnGameStateUpdate` fires in the outer game loop every frame, regardless of the Play-update gate | `mm/src/code/game.c:168` — this is why the snapshot keeps publishing and the mailbox keeps draining while paused, making RESUME possible |
| BenMenu's graphics rows name the real CVars, including a disable-when pattern matching the design's FPS lock | `mm/2s2h/BenGui/BenMenu.cpp:635` (`gInterpolationFPS`), `:654` (`gMatchRefreshRate`), `:2201` (`disabledInfo` keyed on `gMatchRefreshRate`) |
| BenMenu sets CVars from the game/render thread — matching it is the provably safe threading model | BenMenu is ImGui, drawn on the game side; all our CVar writes route through the mailbox onto the same thread |
| `RegisterShipInitFunc` + re-registration guard idiom | `mm/2s2h/TerminaDS/SnapshotPublisher.cpp:172-189` (Phase 2, hardware-proven) |

**Open research pinned to the plan (not guessed here):**
- The exact `saveLoaded` source field. Candidates: `gSaveContext.fileNum`
  sentinel vs. an entrance/gameMode validity check. The plan must cite the
  decomp lines and the behavior across title demo → file select → load.
- The full 10-row CVar table (names, ranges, defaults, live-vs-restart) read
  from `BenMenu.cpp`'s Settings→Graphics and Enhancements section. Rows
  whose real CVar needs a restart get the design's needs-reload chip
  semantics only if BenMenu itself models that; otherwise rows apply live
  and the reload bar is omitted.

## 3. Architecture: the command mailbox

```
Compose (main thread)                     game thread (outer loop hook)
CommandBridge.submit(cmd) → JNI →  SPSC ring  → drain ≤N/frame → apply
                                                      ↓
                    snapshot (schema v2: pauseState, saveLoaded, menuOpen)
                                                      ↓
                              route()/UI observes the effect — never assumes
```

- **`mm/2s2h/TerminaDS/CommandMailbox.{h,cpp}`** — fixed-capacity (16) SPSC
  ring of fixed-layout commands `{ int32 op; int32 a; int32 b; char name[64]; }`.
  Single producer (JNI, Android main thread), single consumer (game thread),
  atomic head/tail with acquire/release ordering — the same discipline the
  seqlock proved. Ring-full drops the command and returns a status to
  Kotlin (surfaced, never silent). **This file and `SnapshotPublisher.cpp`
  are the only two files that touch game state.**
- **Opcodes (all absolute, never read-modify-write):**
  `TDS_CMD_PAUSE_SET(a=0|1)` — sets `gPlayState->frameAdvCtx.enabled`
  (guarded: no-op without a valid PlayState);
  `TDS_CMD_CVAR_SET_INT(name, a)`; `TDS_CMD_CVAR_SAVE` (debounced by the UI,
  persists via the LUS CVar save path).
- **Drain point:** the head of the existing `OnGameStateUpdate` registration,
  before `Publish()` — commands apply, then the same frame's snapshot
  reports the new state.
- **Schema v2:** `TDS_SNAP_IDX_PAUSE_STATE` (from `FrameAdvance_IsEnabled`),
  flags `TDS_SNAP_FLAG_SAVE_LOADED` and `TDS_SNAP_FLAG_MENU_OPEN`
  (kaleido `pauseCtx.state != PAUSE_STATE_OFF` or BenMenu visible).
  `TDS_SNAP_SCHEMA_VERSION` → 2 on both sides; the Kotlin decoder, layout
  mirror, and tests update together (the documented cost).
- **JNI:** one new entry, `nativeSubmitCommand(op, a, b, name) → status`,
  mirrored in `NativeBridge.kt`; **`CommandBridge.kt`** is the only Kotlin
  writer, consumed by the pause button and settings rows.

## 4. Pause semantics

- Tap PAUSE → `PAUSE_SET 1` → within ~1-2 frames the snapshot's
  `pauseState` flips → `route()` shows the pause root menu. The button
  renders a pending state until the ack; if no ack within 1 s it reverts
  and shows a brief mono `PAUSE FAILED` hint (a lost command is visible,
  never silent).
- RESUME (row or footer `RESUME PLAY`) → `PAUSE_SET 0`; same ack loop back
  to the gameplay HUD.
- The pause button is **disabled while `menuOpen`** (kaleido or BenMenu owns
  the game) and while `!saveLoaded`.
- Scene transitions destroy the PlayState and with it `frameAdvCtx` — pause
  cannot be entered without a valid PlayState, and a transition-under-pause
  self-clears to unpaused; the UI follows `pauseState` wherever it goes.
  (In practice the frozen game cannot start a transition; this covers
  external forces.)
- While frozen, the game thread still runs the outer loop: snapshot and
  mailbox stay live (ground truth §2). Input to the game is frozen with the
  update body, so START cannot open kaleido under our pause.
- Backgrounding while paused: the Presentation dismisses (Phase 3
  behavior); the game stays frozen; on return `pauseState` still reads
  paused and the menu is restored.

## 5. Screens

- **Pause button (design delta):** the handoff has no bottom-screen pause
  affordance (its model was a physical key). A `PAUSE` control sits at the
  right end of the gameplay nav bar (user placement: bottom right):
  Cinzel 19/700, tracking 5, gold `#f0d488` with a 2px `#e0bd66` underline —
  the handoff's action vocabulary (its `RESUME PLAY` footer control,
  mirrored). Disabled state: `#544d69`, transparent underline. Tap target
  ≥69px tall (legibility-scaled like the nav tabs).
- **Pause root menu (§5, faithful):** clock line up top, the five rows with
  flanking breathing diamonds, `SONG OF TIME` in the warm gold treatment,
  sub-lines under active rows only. RESUME and OPTIONS are live;
  INVENTORY / MAP / SONG OF TIME render in the unselected ink `#6b6380`,
  diamonds transparent, semantics-disabled ("Inventory, available in a
  future update"). Sub-lines for the two live rows only; the footer hint
  becomes `TAP A ROW · RESUME RETURNS TO THE GAME` (mono 13) — the
  handoff's Ⓐ/Ⓑ glyphs are controller vocabulary this screen doesn't have.
- **Options subscreen (§6 chrome + §10 content):** header (back chevron →
  root menu, `OPTIONS` title, `PAUSED` chip, clock, hours chip), footer
  (contextual hint left, `RESUME PLAY` right). SETTINGS ◆ ENHANCEMENTS
  tabs; category chips per tab; both Graphics categories carry the ten rows
  bound to their real CVars (§2 research → plan table); every other
  category shows its designed empty state (`SETTINGS FOR AUDIO NOT DESIGNED
  YET`). Controls per the handoff: 300px hairline sliders with diamond
  knobs and chevron steppers, segmented underlined options, 46px checkbox
  squares; row anatomy with breathing diamond + selection wash on the
  row being touched. The FPS row greys out entirely while
  `gMatchRefreshRate` is on, mirroring `BenMenu.cpp:2201`.
- All geometry in design px through `du`/`dus` with the established
  `LEGIBILITY` factor on type and reading-critical glyphs.
- **Value flow:** rows read current values from CVars via a small read-only
  JNI getter at screen-open (CVar reads are cheap and race-free enough for
  display), write through the mailbox on change, and `CVAR_SAVE` debounces
  2 s after the last change. The row UI is optimistic (slider follows the
  finger) but re-reads on ack cadence so a dropped command shows the truth.

## 6. Routing (v2)

| Condition (snapshot) | Screen |
|---|---|
| fault states | DiagnosticPlate (unchanged) |
| `NoFramesYet` | IdlePlate (waiting) |
| `!saveLoaded` (incl. title, file select, pre-save cutscene — retires the scene-8 special case) | IdlePlate |
| `saveLoaded && pauseState` | Pause root menu / Options (per local nav state) |
| `saveLoaded && !pauseState` | GameplayScreen (+ stall chip as today) |
| `menuOpen` | GameplayScreen with the pause button disabled |

Local menu navigation (root ↔ options, tab/category selection) is Kotlin
state in the host; only pause itself is game truth.

## 7. Accessibility

Phase 3 rules carry: no per-poll values in any `contentDescription`, no
live regions, disabled rows read as unavailable. New: every settings row is
one semantic node ("Internal resolution, 100 percent, slider"); the pause
button announces state ("Pause the game" / "Pause unavailable while the
game's own menu is open"). **TalkBack leads the hardware checklist this
phase — three phases deferred is enough.**

## 8. Invariants

- `SnapshotPublisher.cpp` and `CommandMailbox.cpp` are the only files that
  dereference game state; the mailbox never blocks the game thread and
  drains a bounded count per frame.
- Commands are absolute; the UI observes effects through the snapshot, never
  assumes them.
- Schema bump is atomic across native and Kotlin in one commit; the
  diagnostic plates already handle mismatched halves loudly.
- The native START pause, BenMenu, and all engine behavior remain untouched
  except the two registered hooks.
- No `contentDescription` regressions (structural guard test stays).

## 9. Testing

- **JVM:** mailbox status decoding, schema v2 decoder fields, routing table
  (§6 — every condition row), pause ack timeout state machine, settings
  row view-models (value formatting, FPS-lock rule), debounce logic.
- **Native-side sanity:** the publisher's existing pattern extended — a
  static_assert-guarded layout, plus a drain-bound unit check if the build
  hosts one cheaply.
- **Compose UI tests (new infra, the Phase 3 debt):** Robolectric-backed
  `createComposeRule` smoke tests — gameplay nav renders three tabs plus
  PAUSE (the exact class of bug the photo caught), pause menu renders five
  rows with correct enabled/disabled semantics, options rows render and
  emit commands on interaction. Kept to high-value smoke coverage, not
  pixel tests.
- **Hardware (TalkBack first):** TalkBack across gameplay + pause + options;
  pause/resume round-trip latency and the frozen top screen; settings
  changes visibly applying (resolution/FPS on the top screen); match-refresh
  locking the FPS row; persistence across restart (CVAR_SAVE); backgrounding
  while paused; native START pause coexistence; the >10-hearts visual and
  framerate spot-check carried from Phase 3.

## 10. What later phases inherit

- The mailbox is Phase 5's foundation: item assignment, warps, and kaleido
  replacement are new opcodes on proven machinery, not new machinery.
- The pause root menu is the mount point for INVENTORY / MAP / SONG OF TIME.
- The §6 chrome (header/footer/tabs/chips) and control library (sliders,
  segmented, checkboxes) are the vocabulary for every future subscreen.
- `saveLoaded` finally gives every consumer the honest "is there a game"
  signal the payload lacked since Phase 2.
