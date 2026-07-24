# Termina DS Phase 3: Bottom-Screen Gameplay HUD

**Date:** 2026-07-24
**Status:** Approved (design sections approved in session; spec pending user review)
**Depends on:** Phase 2 state bridge (`docs/superpowers/specs/2026-07-23-termina-ds-phase-2-state-bridge-design.md`), verified on hardware 2026-07-23.
**Design source:** `docs/design/second-screen-handoff/README.md` — the "Termina DS — dual-screen gameplay HUD, pause menu & graphics options" handoff package. Section numbers below (§4, §6, …) refer to that document.

## 1. What Phase 3 is, against the full handoff

The design handoff specifies the complete second-screen system: gameplay HUD,
pause veil, root menu, inventory with assignment, map/song/options subscreens,
and three top-screen states. Most of that **writes** to the game (pausing,
assigning items, setting CVars) or draws on the top screen (the SDL render
surface, not Compose). The bridge is read-only by design until Phase 5, and the
top screen belongs to the engine.

Phase 3 implements the one slice the Phase 2 bridge can feed today: **the
bottom-screen gameplay view (handoff §4)** — vitals bar, map region, area
label, and nav — built precisely to the handoff's design tokens so the pause
screens later slot into the same vocabulary without rework.

| Handoff area | Phase 3? | Why not / when |
|---|---|---|
| §4 Bottom screen — gameplay (vitals, map, area label, nav) | **Yes** | This spec |
| §4 nav actions (ITEMS/MASKS open inventory) | No — rendered inactive | Needs pause control: Phase 5 |
| §5–§10 Pause root menu + all subscreens | No | All write paths: Phase 5+ |
| §1–§3 Top screen (HUD ring, veil, assign overview) | No | Engine render surface, not Compose |
| Live player marker on the map | No | Needs per-scene world→map calibration: own phase |

User-approved scope decisions (2026-07-24 session):
- Map region shows the **static Termina overworld art** from the bundle, full
  bleed per §4, no player marker yet.
- Nav renders **all three tabs to spec**; ITEMS and MASKS use the inactive ink
  and are non-interactive. Phase 5 switches them on in place.
- Implementation is **pure Compose against a design-token module** (approach A;
  WebView and lone-Canvas approaches rejected — the handoff HTML is reference,
  not production code, and Canvas-only would blind TalkBack).

## 2. Constraints inherited from Phase 2 (unchanged)

- **No native changes.** No schema bump, `lib2ship.so` untouched. Phase 3 is
  Android/Kotlin only.
- The poller (`GameSnapshotPoller`), decoder (`GameSnapshot.kt`), and JNI seam
  (`NativeBridge.kt`) are not modified.
- The save-derived slots publish garbage-adjacent values on the title screen —
  **no vitals render unless `hasPlayState`** (`GameSnapshot.h` warns exactly
  this).
- Snapshot data is up to ~100 ms stale by construction; nothing in this phase
  may treat it as exact-frame truth.
- Both Thor displays are FLAG_SECURE: visual verification is the user's eyes
  plus logcat, never screenshots.

## 3. Architecture

Compose UI, one-way data flow, unchanged from the verified Phase 2 shape:

```
10 Hz poll (main thread) → BridgeState → route(BridgeState) ─┬→ GameplayScreen(HudModel)
                                                             ├→ IdlePlate
                                                             └→ DiagnosticPlate
```

New files, all under the Android app:

| File | Role |
|---|---|
| `secondscreen/TerminaDesign.kt` | Design tokens: every color from the handoff table as a named `Color`, bundled font families, text styles with exact size/weight/tracking, `DesignFrame` scaling |
| `secondscreen/HudModel.kt` | Pure snapshot→display mapping (no Compose imports). All formulas in §5 live here. JVM-tested |
| `secondscreen/SceneNames.kt` | Generated `sceneId → humanName` table from `mm/include/tables/scene_table.h` (115 entries) |
| `secondscreen/GameplayScreen.kt` | The §4 layout: vitals bar, map region, area label, nav |
| `secondscreen/SecondScreenHost.kt` | Rewritten: keeps the poll loop, adds `route()`, deletes the Phase 2 debug readout wholesale (its own charter) |

Modified: `SecondScreenHost.kt` only. Deleted: the debug readout composables
inside it (git history preserves them; the diagnostic plate keeps their exact
status strings).

**Scaling.** All geometry is written in design units against the handoff's
1240×1080 reference frame. A `BoxWithConstraints` at the root computes one
scale factor `min(widthPx/1240, heightPx/1080)` and a `Dp`-converting helper;
every dimension below flows through it. The Thor's bottom panel is natively
1240×1080 so the factor is ~1.0 there, but nothing assumes it.

**Recomposition.** `HudModel` is a data class of primitives and `String`s —
stable, structurally comparable. The poll site derives it once per tick;
`frameCounter` deliberately never enters `HudModel`, so the HUD subtree
recomposes only when a displayed value actually changed. This retires the
Phase 2 caveat that the whole readout recomposed at 10 Hz.

## 4. State routing

`route()` is a pure function, JVM-tested:

| BridgeState | Screen |
|---|---|
| `Live` / `Stalled`, snapshot has `hasPlayState` | **GameplayScreen** (Stalled additionally shows the stall chip, §6) |
| `Live` / `Stalled`, no `hasPlayState` (title, file select) | **IdlePlate** |
| `NoFramesYet` | **IdlePlate** with caption `WAITING FOR THE GAME` |
| `NativeUnavailable`, `SchemaMismatch`, `BufferTooSmall`, `UnknownReadStatus` | **DiagnosticPlate** — build-skew faults stay loud |

`hasPlayer` is not consulted: no Phase 3 element depends on the player actor
(the marker would; it is out of scope).

## 5. Display formulas (all in `HudModel`, all JVM-tested)

Engine semantics per `SnapshotPublisher.cpp`:

- **Hearts.** `health`/`healthCapacity` are in 1/16-heart units.
  `totalHearts = healthCapacity / 16`, `fullHearts = health / 16`,
  `partialFraction = (health % 16) / 16f` (one partial heart drawn as a
  horizontal clip fill — invisible granularity at 18 design-px, but honest).
  Rows wrap at **10 hearts per row** like the original game (MM max is 20;
  two rows of 18px hearts + 3px gap fit the 64px bar). `doubleDefense` draws a
  1.4px gold (`#e0bd66`) stroke on filled hearts — a token-vocabulary extension,
  the handoff doesn't cover double defense.
- **Magic.** `magicPct = magic * 100 / magicCapacity`, clamped 0–100. If
  `magicCapacity == 0` (magic not yet acquired) the rail is **hidden entirely**,
  not shown empty.
- **Rupees.** Rendered as-is.
- **Clock.** `time` is the engine u16 day fraction, `0x0000` = midnight:
  `minutesOfDay = time * 1440 / 0x10000`. Displayed 12-hour `h:mm` with the
  `AM`/`PM` suffix per §4 (`12:00 AM` at midnight, `12:00 PM` at noon).
- **Day + countdown chip.** `DAY n` from the `day` slot. Hours remaining until
  the cycle ends (Day 4, 6:00 AM):
  `remainingMinutes = (4 - day) * 1440 + 360 - minutesOfDay`, clamped ≥ 0;
  chip shows `floor(remainingMinutes / 60) H` (floor matches the in-game
  "hours remain" convention at day boundaries). If `day < 1` (pre-cycle intro
  state) the day label and chip are hidden.
- **Area label.** `SceneNames[sceneId]`, uppercased at render. Unknown or
  unset scene id → `SCENE <id>` (never blank, never a crash — the table has
  gaps by construction, e.g. ids 1–6).
- **`isNight`** is not separately displayed; AM/PM already carries it.

`SceneNames.kt` is generated once, by hand or script, from the `humanName`
column of `mm/include/tables/scene_table.h` (2S2H's own curated names, already
shipping in Better Map Select). A comment in the file records the source and
regeneration instruction. The table is not expected to change; if it does, the
fallback renders `SCENE <id>` rather than lying.

## 6. Visual spec

Authoritative geometry, color, and type come from the handoff README ("Design
tokens" + §4); this section binds them and records the deltas. Fidelity target
is the handoff's own: **hifi — reproduce precisely.**

- **Vitals bar** (§4): 64 design-px tall, 30px side padding, one flex row —
  hearts (18px, 3px gap), magic rail (130×5px, `#4ade80` on `#1e1c24`,
  12px left margin), rupee diamond (11px `#5ec46f`) + count (Chivo Mono 17/700
  `#eaeaea`), then right-aligned: `DAY n` (Chivo Mono 13/700, tracking 4,
  `#a58ed0`), clock (Chivo Mono 18/700 `#f0eef5`, suffix 11px `#6f6288`),
  countdown chip (1px `rgba(203,176,242,.45)` border, radius 6, Chivo Mono
  13/700 `#cbb0f2`).
- **Map region** (§4): absolute `top:64 bottom:104`, full bleed,
  `ContentScale.Crop`, no vignette. Art: the bundle's Termina overworld PNG,
  committed to `res/drawable-nodpi/`.
- **Area label** (§4): 30px from left, 14px below the vitals bar, Cinzel
  20/700, tracking 3, `#f0eef5`, shadow `0 2px 10px rgba(0,0,0,.9)`.
- **Nav** (§4): 104px bottom bar, centered, 56px gap, Cinzel 23/700,
  tracking 7, 46px tall, 2px bottom border. `MAP` active (`#efe6ff` +
  `#c9a2ff` underline); `ITEMS` and `MASKS` inactive (`#544d69`, transparent
  underline), **no click handling**, semantics-disabled.
- **Stall chip** (delta — the handoff has no stalled concept): when
  `BridgeState.Stalled`, a chip in the map region's top-right corner (mirroring
  the area label's insets): `STALLED <n>s`, Chivo Mono 13, tracking 3, on the
  warning-amber border token `rgba(224,189,102,.34)`, radius 6. The HUD behind
  it keeps rendering the last snapshot — frozen data plus an honest label, not
  a blank screen.
- **IdlePlate** (delta — the handoff doesn't cover the no-save state): black
  screen, centered column: `TERMINA DS` (Cinzel 700, 48px, tracking .18em,
  `#d7c6f4`) over a breathing 9px `#b48ce8` diamond (the handoff's `pzBreathe`,
  2.1s). `NoFramesYet` adds `WAITING FOR THE GAME` (Chivo Mono 13, tracking 3,
  `#4f4763`) beneath.
- **DiagnosticPlate** (delta): black, centered, Chivo Mono, the exact Phase 2
  status strings (`SCHEMA MISMATCH native=… expected=…`, etc.) so
  `docs/HANDOFF.md`'s diagnostic vocabulary still matches the screen.
- **Motion:** only `pzBreathe` (idle diamond) in this phase. Nothing else in
  §4 animates. Value changes snap; no tweening of hearts/magic (the design
  does not specify any, and 10 Hz data would fight it).

**Fonts.** Cinzel (700, 800) and Chivo Mono (500, 700) as static-weight TTFs in
`res/font/`, committed with their OFL license files. Barlow is used only by
pause-phase screens and is deferred until one exists. No runtime font fetching
— the Thor build must work offline.

## 7. Accessibility

TalkBack is the reason this project chose Compose; Phase 2 left it unverified
and flagged one predicted defect. Phase 3 makes it verifiable:

- The vitals bar is **one semantic node** with a composed description
  ("8 of 10 hearts. Magic 62 percent. 218 rupees. Day 1, 7:40 AM, 70 hours
  left."), not per-glyph noise.
- **No `contentDescription` may embed `frameCounter` or any value that changes
  every poll** — this was the predicted continuous-re-announcement defect. The
  clock string changes roughly once a real-world second at normal time speed;
  that is the fastest-changing text on screen.
- No `liveRegion` semantics anywhere: TalkBack announces on focus, never
  spontaneously.
- Area label, nav tabs, stall chip, and both plates each carry a plain
  description; inactive tabs read as disabled ("Items, unavailable").

## 8. Assets and licensing

- The full design bundle is committed at `docs/design/second-screen-handoff/`
  (README, prototype HTML + runtime, icons, d-pad, both stand-in renders) as
  the durable design source of truth — the wheelhouse upload location is
  ephemeral.
- The map art is the handoff's stand-in ("temporary reference art" per its IP
  note). Acceptable for this personal-device build; a from-game map is the
  eventual replacement and arrives with the player-marker phase.
- Cinzel and Chivo Mono are SIL OFL 1.1; license files ship beside the TTFs.

## 9. Build and iteration

Phase 3 never touches native code, so the 8–19 minute Docker pipeline is only
for install candidates. UI iteration uses the Gradle Android tasks directly
(inside the build container, without `build-apk.sh`'s deliberate `.cxx` clear —
native is unchanged, the frozen source list is exactly what we want). JVM tests
run via the existing `tools/run-unit-tests.sh`, which already self-verifies
against the UP-TO-DATE zero-test trap.

## 10. Testing

**JVM (all pure logic, no Compose testing framework this phase):**
- Clock: `0x0000`→`12:00 AM`, `0x4000`→`6:00 AM`, `0x8000`→`12:00 PM`,
  7:40 AM's exact u16, `0xFFFF`→`11:59 PM`.
- Countdown: Day 1 6:00 AM → 72; Day 3 5:59 AM → 24 (floor at the boundary);
  clamp to 0 past the deadline; hidden when `day < 1`.
- Hearts: 0 health; partial (41/48 → 2 full + 9/16 partial of 3); exactly 10
  (one row); 11 and 20 (two rows); capacity 0.
- Magic: pct math, clamping, capacity-0 hides the rail.
- Scene names: known id, unset gap (ids 1–6), out-of-range id → `SCENE <id>`.
- `route()`: every `BridgeState` × `hasPlayState` combination → expected screen.
- A guard test asserting `HudModel` contains no `frameCounter` field (the
  accessibility invariant, enforced structurally).

**Hardware (user-verified on the Thor, recorded in `docs/verification/`):**
1. Boot → IdlePlate, **not** a plausible-garbage vitals bar (the §2 gating).
2. File select → still IdlePlate.
3. Load save → HUD; hearts/rupees/magic match the top screen's own HUD.
4. Damage / rupee pickup / magic use reflect within ~200 ms.
5. Area label tracks several scene transitions; no crash (re-validates the
   Phase 2 NULL-window under the real consumer).
6. Clock vs. the in-game clock: time, AM/PM, day, hours-remaining plausibility.
7. Heart wrap >10 and double defense (2S2H's save editor can set both).
8. Background and return → stall chip within ~1 s, LIVE on return — closes
   Phase 2 deferred item 10.
9. TalkBack pass over every element; confirm no continuous re-announcement —
   closes Phase 2 deferred item 11.
10. Top-screen framerate spot-check with 2S2H's FPS display enabled — finally
    measures the twice-deferred Phase 2 item 9.
11. Visual fidelity check against the handoff render, user's eyes.

## 11. Invariants preserved

- The bridge stays read-only; schema stays at version 1; no native diff.
- The only file that dereferences game pointers is still `SnapshotPublisher.cpp`.
- The poll cadence, staleness threshold, and `BridgeState` machine are untouched.
- Every Phase 2 diagnostic remains distinguishable on screen (DiagnosticPlate
  keeps the exact strings).

## 12. What later phases inherit

- `TerminaDesign.kt` is the shared vocabulary for every pause-phase screen:
  colors, type styles, the diamond/chip/underline-tab primitives, `DesignFrame`.
- The nav's ITEMS/MASKS activation point is a one-line change once Phase 5's
  write bridge can pause the game.
- The map region is the mount point for the player marker (needs per-scene
  calibration + from-game map art).
- `HudModel` is the pattern for pause-screen models: pure, primitive-typed,
  JVM-tested, `frameCounter`-free.
