# Termina DS Phase 4 Plan B: Pause Menu Styling, Options, and the Veil

**Date:** 2026-07-25
**Status:** Approved (2026-07-25)
**Amends:** `docs/superpowers/specs/2026-07-25-termina-ds-phase-4-pause-settings-design.md`
(Plan A shipped and hardware-verified: `docs/verification/2026-07-25-phase-4a-thor.md`)
**Design source:** `docs/design/second-screen-handoff/README.md` §2 (veil), §5 (pause
root menu), §6 (subscreen chrome), §10 (Options)

## 1. What Plan B is

Plan A built the write path and proved it on hardware: a 16-slot SPSC command
mailbox drained on the game thread, schema v2, routing on `saveLoaded`, a PAUSE
control with ack tracking, and a pause-root skeleton with RESUME live.

Plan B finishes the pause experience:

| Area | Notes |
|---|---|
| Full §5 pause-root styling | Diamonds, sub-lines, warm `SONG OF TIME`, OPTIONS lit |
| §6 chrome + §10 Options | Both Graphics categories on real CVars; six categories show the designed empty state |
| Engine-side ImGui PAUSED veil | §2, minus the unachievable backdrop blur (§6 below) |
| Schema v3 | Ten CVar values join the snapshot; retires the unsafe JNI-getter plan |
| Three semantic mailbox opcodes | Resolution / MSAA / texture filter apply live |
| Compose UI test infrastructure | Layered: pure data-model backbone + thin Robolectric smoke |
| Release keystore | Including a latent build-script bug found during research (§8) |

Out of scope, unchanged from Plan A: inventory, map, Song of Time, item
assignment, warps, kaleido replacement.

## 2. Ground truth (verified in-tree, 2026-07-25)

Everything below was read from the tree during the Plan B brainstorm. Nothing
here is inferred.

### 2.1 CVar names and the prefix

| Fact | Evidence |
|---|---|
| `CVAR_PREFIX_SETTING` is `gSettings` | `CMake/2ship-cvars.cmake:7` |
| `CVAR_INTERNAL_RESOLUTION` = `gSettings.InternalResolution` | `CMake/lus-cvars.cmake:3` |
| `CVAR_MSAA_VALUE` = `gSettings.MSAAValue` | `CMake/lus-cvars.cmake:4` |
| `CVAR_TEXTURE_FILTER` = `gSettings.TextureFilter` | `CMake/lus-cvars.cmake:6` |
| The MM-side file overrides the engine defaults (`gInternalResolution` etc.) | `CMake/lus-cvars.cmake:21` includes `engine/cmake/cvars.cmake` *after* setting them |

### 2.2 Settings → Graphics maps 5-for-5

| Design row (§10) | Real CVar | Type | Range / default | Evidence |
|---|---|---|---|---|
| Internal Resolution | `gSettings.InternalResolution` | **float** | 0.5–2.0, def 1.0 | `BenMenu.cpp:594-617` |
| Anti-Aliasing (MSAA) | `gSettings.MSAAValue` | int | 1–8, def 1 | `BenMenu.cpp:619-632` |
| Current FPS | `gInterpolationFPS` | int | 20–360, def 20 | `BenMenu.cpp:634-652` |
| Match Refresh Rate | `gMatchRefreshRate` | int | 0/1, def 0 | `BenMenu.cpp:653-655` |
| Texture Filter | `gSettings.TextureFilter` | int | 0/1/2, def 0 | `BenMenu.cpp:676-678` |

- The design's three texture-filter segments match the engine's options exactly:
  `Three-Point`, `Linear`, `None` — `BenMenu.cpp:145-149`.
- The FPS-lock the design specifies is a real BenMenu behavior:
  `DISABLE_FOR_MATCH_REFRESH_RATE_ON` is keyed on `gMatchRefreshRate`
  (`BenMenu.cpp:2200-2202`) and consumed by the FPS row's `PreFunc`
  (`BenMenu.cpp:644-647`).

### 2.3 Enhancements → Graphics: the design's rows do not exist

The handoff's five rows — Widescreen, High-Res Texture Pack, Anisotropic
Filtering, Post Sharpening, Draw Distance Fog — have **no backing CVars in
2S2H**. The real section (`BenMenu.cpp:1299-1392`) offers Clock Type, 24 Hours
Clock, Alternate Assets, Motion Blur (mode / interpolate / strength), 3D Item
Drops, Authentic Logo, Disable Black Bar Letterboxes, Enemy Health Bars, Fix
Scene Geometry Seams, Disable Scene Geometry Distance Check, Widescreen Actor
Culling, Increase Actor Draw Distance, and N64 Mode.

**Decision (user, 2026-07-25):** re-source the tab from real CVars, preserving
the design's row anatomy, control library, and description-line voice. The
chosen slate is in §5.

### 2.4 Writing a CVar is not enough for three rows

| Fact | Evidence |
|---|---|
| Internal Resolution is applied by a Callback, not by the CVar write | `BenMenu.cpp:596-599` calls `Window->SetResolutionMultiplier` |
| MSAA is applied by a Callback | `BenMenu.cpp:621-623` calls `Window->SetMsaaLevel` |
| Both CVars are read **only at interpreter Init** | `engine/src/fast/interpreter.cpp:4187-4189` |
| Texture filter is read only at window Init | `engine/src/fast/Fast3dWindow.cpp:101-102` |
| …but its setter is live-safe: it clears the texture cache and sets the mode | `engine/src/fast/backends/gfx_opengl.cpp:997-1000` |
| `SetResolutionMultiplier` / `SetMsaaLevel` are on the **abstract** `Ship::Window` — no cast needed | `engine/include/ship/window/Window.h:59-60` |
| `SetTextureFilter` is **not**; it lives on the concrete Fast3D backend | `engine/include/fast/Fast3dWindow.h:54` |
| `GetCurrentRefreshRate()` is on `Ship::Window` — sources the FPS row's max and its `MAX n HZ` chip | `engine/include/ship/window/Window.h:56` |

### 2.5 CVar reads are NOT thread-safe

| Fact | Evidence |
|---|---|
| CVars are an unmutexed `std::unordered_map` | `engine/include/ship/config/ConsoleVariable.h:67` |

This retires the amended spec's §5 sentence "CVar reads are cheap and race-free
enough for display." They are not: the game thread writes that map whenever a
command drains, and a concurrent read from the Android main thread during a
rehash is undefined behavior. See §4.

### 2.6 The ImGui overlay and font seams

| Fact | Evidence |
|---|---|
| Overlay windows are `Ship::GuiWindow` subclasses registered with `AddGuiWindow` + `Show()` | `mm/2s2h/BenGui/BenGui.cpp:187-189` (Notification) |
| A full-screen, input-transparent overlay is an existing pattern | `mm/2s2h/BenGui/Notification.cpp:59-63` (`ImGuiWindowFlags_NoInputs`) |
| Fonts can be embedded with no archive involvement | `engine/src/ship/window/gui/Gui.cpp:127-128` — FontAwesome via `AddFontFromMemoryCompressedBase85TTF` |
| The ResourceManager font path exists but sources fonts from `.o2r` archives | `engine/src/ship/window/gui/GameOverlay.cpp:40-56` |
| Our design fonts are Android resources, unreachable from ImGui | `Android/app/src/main/res/font/{cinzel,chivo_mono}_variable.ttf` |

### 2.7 Test and signing infrastructure

| Fact | Evidence |
|---|---|
| The only test dependency today is JUnit 4 | `Android/app/build.gradle:132` |
| Release signing is consumed correctly by Gradle | `Android/app/build.gradle:57-76` |
| …but neither build script mounts the keystore directory into the container | `tools/build-apk.sh:14-22`, `tools/assemble-apk.sh:17-23` |
| …and `hasReleaseSigning` never checks the file is readable | `Android/app/build.gradle:61` |
| The keystore generator writes to `~/.termina-ds/release-keystore.jks` | `tools/make-keystore.sh:6-7` |

## 3. Schema v3: the ten CVar values ride the snapshot

**This replaces the amended spec's read-only JNI getter.** Rationale in §2.5.

`TDS_SNAP_SCHEMA_VERSION` → 3; `TDS_SNAP_COUNT` 28 → **39**. Eleven new indices
appended after `TDS_SNAP_IDX_PAUSE_STATE` — one per settings row, plus the live
display refresh rate — sampled in `SnapshotPublisher.cpp` on the game thread
alongside everything else:

```
TDS_SNAP_IDX_CVAR_INTERNAL_RES,   // percent, 50..200 (float CVar x100)
TDS_SNAP_IDX_CVAR_MSAA,           // 1..8
TDS_SNAP_IDX_CVAR_FPS,            // 20..360
TDS_SNAP_IDX_CVAR_MATCH_HZ,       // 0/1
TDS_SNAP_IDX_CVAR_TEXTURE_FILTER, // 0..2
TDS_SNAP_IDX_CVAR_CLOCK_TYPE,     // 0..2
TDS_SNAP_IDX_CVAR_BLUR_MODE,      // 0..2
TDS_SNAP_IDX_CVAR_BLUR_STRENGTH,  // 0..255
TDS_SNAP_IDX_CVAR_DRAW_DISTANCE,  // 1..5
TDS_SNAP_IDX_CVAR_3D_ITEM_DROPS,  // 0/1
TDS_SNAP_IDX_DISPLAY_REFRESH_HZ,  // GetCurrentRefreshRate()
```

The last slot is not a CVar: the FPS row needs the live refresh rate for its
maximum and its `MAX n HZ` chip, and `GetCurrentRefreshRate()`
(`engine/include/ship/window/Window.h:56`) is only callable on the game thread.

Why this is better than a getter, not merely safer:

- **No new JNI surface.** The seam stays at three entry points.
- **Race-free by construction** — the seqlock Phase 2 hardware-proved.
- **It makes the amended spec's own requirement fall out for free.** §5 there
  asks that rows "re-read on ack cadence so a dropped command shows the truth."
  The snapshot *is* that cadence. Optimistic-slider-then-reconcile becomes the
  same observe-don't-assume loop `PauseRequestTracker` already runs for pause,
  rather than a second mechanism.

Cost, accepted: the documented atomic schema bump across `GameSnapshot.h`,
`GameSnapshot.kt`, and the tests — in **one commit**, per the Plan A final-review
finding that split schema history is a defect (ledger line 43).

## 4. New mailbox opcodes

Three semantic opcodes, appended after `TDS_CMD_CVAR_SAVE`. Each performs the
CVar write **and** the engine apply inside a single drained command, mirroring
BenMenu's Callback exactly:

| Opcode | Behavior |
|---|---|
| `TDS_CMD_SET_INTERNAL_RES(a = 50…200)` | `CVarSetFloat(CVAR_INTERNAL_RESOLUTION, a/100f)` then `Window->SetResolutionMultiplier(a/100f)` |
| `TDS_CMD_SET_MSAA(a = 1…8)` | `CVarSetInteger(CVAR_MSAA_VALUE, a)` then `Window->SetMsaaLevel(a)` |
| `TDS_CMD_SET_TEXTURE_FILTER(a = 0…2)` | `CVarSetInteger(CVAR_TEXTURE_FILTER, a)` then `Fast3dWindow->SetTextureFilter(a)` |

Design rules this preserves:

- **Absolute.** Each carries the target value, never a delta.
- **Atomic.** One command = one complete effect. A generic
  `CVAR_SET_FLOAT` + separate `GFX_APPLY` pair was rejected: the ring could drop
  the apply and keep the write, producing exactly the silent divergence the
  mailbox exists to prevent.
- **No generic float write.** Nothing gains the ability to set an arbitrary
  float CVar with no apply path.
- The remaining seven rows use the existing `TDS_CMD_CVAR_SET_INT`; none of
  their CVars has a Callback (`BenMenu.cpp:1299-1392`).
- `TDS_CMD_CVAR_SAVE` debounces 2 s after the last change, unchanged.
- `SetTextureFilter` needs `std::static_pointer_cast<Fast::Fast3dWindow>`. This
  is the one concrete-backend coupling Plan B introduces; it is confined to
  `CommandMailbox.cpp` and must be commented as such.
- **Invariant holds:** `CommandMailbox.cpp` and `SnapshotPublisher.cpp` remain
  the only two files that touch game state.

Opcode values are pinned by test on both sides, per the Plan A fix
(`50bb8e17f`).

## 5. Screens

### 5.1 Pause root menu (§5, full styling)

Five rows, `min-height:106px`, label Cinzel 42/700 `letter-spacing:7px`, flanked
by two breathing 9px diamonds at `gap:24px`, list width 760px, `pzMenuIn` entry.

- **Live:** `RESUME`, `OPTIONS` — ink `#e7dcfa`, `text-shadow:0 0 26px rgba(180,140,232,.45)`,
  diamonds `#b48ce8`, sub-line beneath in Chivo Mono 14 `letter-spacing:4px` `#6f6288`.
- **Inert:** `INVENTORY`, `MAP` — ink `#6b6380`, transparent diamonds, no
  sub-line, semantics-disabled ("Inventory, available in a future update").
- **`SONG OF TIME`** keeps its warm treatment even while inert: `#8a7647`.
- **Sub-lines:** `RESUME` → the current scene name from the existing
  `SceneNames.kt` table. **The design's `· AUTOSAVED 4 MIN AGO` is dropped — we
  have no autosave data.** `OPTIONS` → the static `RESOLUTION · MSAA · FRAME RATE`.
- Clock line at `top:34px` and the footer hint
  `TAP A ROW · RESUME RETURNS TO THE GAME` (mono 13) per the amended spec §5 —
  the handoff's Ⓐ/Ⓑ glyphs are controller vocabulary this screen lacks.

### 5.2 Options (§6 chrome + §10 content)

Header (back chevron → root, `OPTIONS` title, `PAUSED` chip, clock, hours chip)
and footer (contextual hint left, `RESUME PLAY` right) per §6. `SETTINGS` ◆
`ENHANCEMENTS` tabs; category chips per tab. Six of eight categories show the
designed empty state (`SETTINGS FOR AUDIO NOT DESIGNED YET`).

**Settings → Graphics**

| # | Row | CVar | Control |
|---|---|---|---|
| 1 | Internal Resolution | `gSettings.InternalResolution` | slider 50–200%, step 5, tick at 100% |
| 2 | Anti-Aliasing (MSAA) | `gSettings.MSAAValue` | segmented `OFF · 2× · 4× · 8×` → 1/2/4/8 |
| 3 | Current FPS | `gInterpolationFPS` | slider 20 → refresh rate, step 5, `MAX n HZ` chip |
| 4 | Match Refresh Rate | `gMatchRefreshRate` | checkbox |
| 5 | Texture Filter | `gSettings.TextureFilter` | segmented `THREE-POINT · LINEAR · NONE` |

**Enhancements → Graphics** (re-sourced per §2.3)

| # | Row | CVar | Control |
|---|---|---|---|
| 1 | Clock Type | `gEnhancements.Graphics.ClockType` | segmented `ORIGINAL · MM3D · TEXT ONLY` |
| 2 | Motion Blur | `gEnhancements.Graphics.MotionBlur.Mode` | segmented `DYNAMIC · OFF · ALWAYS ON` |
| 3 | Actor Draw Distance | `gEnhancements.Graphics.IncreaseActorDrawDistance` | segmented `1× · 2× · 3× · 4× · 5×` |
| 4 | Motion Blur Strength | `gEnhancements.Graphics.MotionBlur.Strength` | slider 0–255, step 5 |
| 5 | 3D Item Drops | `gEnhancements.Graphics.3DItemDrops` | checkbox |

**Two grey-out relationships**, one designed and one earned:

- Row 3 of Settings greys out entirely while Match Refresh Rate is on, per the
  design's full spec (label `#5b5470`, chevrons `#38323f`, rail fill
  `rgba(120,112,140,.2)`, knob `#3b3648`, no glow, readout `#4a4232`,
  description swaps to `LOCKED BY MATCH REFRESH RATE`), mirroring
  `BenMenu.cpp:2200-2202`.
- Row 4 of Enhancements greys out identically unless Motion Blur is *Always On*,
  mirroring BenMenu's `isHidden` PreFunc (`BenMenu.cpp:1336-1341`). Reusing the
  same grey-out component rather than inventing a second treatment.

**Selection model.** Persistent, touch-driven: tapping a row selects it and it
stays selected — breathing gold diamond, top hairline at
`rgba(180,140,232,.46)`, and the wash
`radial-gradient(72% 150% at 16% 50%, rgba(180,140,232,.14), transparent 74%)`.
Chevron steppers adjust the selected row. One selection per tab+category, reset
on switch (the design's own rule). This supersedes the amended spec §5's
"selection wash on the row being touched": a purely transient treatment would
leave the row anatomy's centerpiece invisible at rest and give TalkBack no
focus anchor.

**Value flow.** Rows render from the schema-v3 snapshot slots. Interaction is
optimistic (the slider follows the finger) and reconciles on the next snapshot,
so a dropped command corrects itself visibly. `CVAR_SAVE` debounces 2 s after
the last change.

All geometry in design px via `du`/`dus` with the established `LEGIBILITY`
factor on type and reading-critical glyphs.

## 6. Top-screen veil (§2)

A new `Ship::GuiWindow` subclass under `mm/2s2h/TerminaDS/`, registered with
`AddGuiWindow` + `Show()` following `BenGui.cpp:187-189`, drawn every frame with
`ImGuiWindowFlags_NoInputs` while `gPlayState->frameAdvCtx.enabled` is set.
Rendering continues while frozen — that is exactly why the veil is possible
(amended spec §2 ground truth, `game.c:168`).

Cinzel is embedded as compressed base85 next to the existing FontAwesome call at
`Gui.cpp:118-128`. That one-line inherited edit is recorded in `docs/UPSTREAM.md`.

Reproduced from §2: the ornament (two 120×1px rules flanking a 44px clock with
the `pzHand` oscillation), the `PAUSED` wordmark at Cinzel 800/176
`letter-spacing:.18em` with the Shimmer gradient, `pzSweep` sheen and `pzGlow`
halo, the subtitle `THE CLOCK HOLDS ITS BREATH`, the rule animating to 420px,
`CONTINUE ON THE BOTTOM SCREEN` with its breathing diamond, and the staggered
`pzRise` entrance.

**Known fidelity gap, accepted:** the design's
`backdrop-filter: blur(7px) saturate(.42) brightness(.34)` is **not achievable**
from an ImGui window — there is no framebuffer or shader access. The frozen
render will be dimmed and tinted by layered draw-list rects and a vignette, not
blurred. Everything else in §2 is reachable.

**Lowest-priority element:** the corner labels. `DAY 1 | 60 H LEFT` is trivial
natively; `TERMINA FIELD` needs a native scene-name lookup off
`mm/include/tables/scene_table.h` (the same source `tools/generate-scene-names.py`
already parses). Feasible, but this is the first veil element to drop if it
fights the build.

## 7. Testing

**Layered, spike-first.** The Robolectric dependency is the phase's main
toolchain risk: it fetches `android-all-instrumented` jars at *test-run* time,
needs `unitTests.includeAndroidResources true`, and Compose-on-Robolectric wants
an explicit `GraphicsMode`. **Task 1 is a spike** — add the dependencies and get
one trivial `composeTestRule.setContent { }` assertion green through
`./tools/run-unit-tests.sh` before any real test is written. If it cannot be made
to work in the Docker image, the backbone below still stands and we drop one
layer instead of losing all coverage.

**Backbone (pure JVM, no new dependencies).** The Options screen needs row
view-models regardless — ten rows across four control types — so extracting them
is the design, not extra work:

- Schema v3 decode: every new slot, plus the version-mismatch path.
- Row view-models: value formatting, segment mapping (MSAA 1/2/4/8 ↔ four
  segments), slider quantization and clamping, refresh-rate-derived FPS maximum.
- Both grey-out rules, including the boundary transitions.
- Debounce timing for `CVAR_SAVE`.
- Selection state: per tab+category, reset on switch.
- Mailbox status decoding for the three new opcodes; opcode values pinned
  against the C header on both sides.
- Nav/menu structure as data — the class of assertion that would have caught the
  Phase 3 nav bug at build time had the nav item list been data.

**Robolectric smoke layer (thin).** Gameplay nav renders three tabs plus PAUSE;
pause menu renders five rows with correct enabled/disabled semantics; Options
rows render and emit the expected command on interaction.

**Native-side sanity.** The publisher's existing static_assert-guarded layout
extended to v3.

**Hardware — TalkBack leads.** Deferred three phases; it goes first this time.
Then: each of the ten rows visibly applying on the top screen; both grey-out
relationships; persistence across restart (`CVAR_SAVE`); the veil's appearance,
entrance animation, and legibility; pause/resume round-trip unchanged; the
carried Phase 3 items (>10-hearts visual, framerate spot-check).

## 8. Release keystore

Ordered **last**, so feature work and the TalkBack-led hardware pass run on the
known-good debug key and a signing problem cannot block the phase.

Three pieces:

1. **The user runs `tools/make-keystore.sh`** — it is interactive `keytool` and
   generates secrets. The agent must not handle the passwords.
2. **Fix the build scripts.** Neither `tools/build-apk.sh:14-22` nor
   `tools/assemble-apk.sh:17-23` mounts `~/.termina-ds` into the container, so
   `storeFile file(releaseKeystorePath)` (`Android/app/build.gradle:66`) resolves
   a host path that does not exist inside it. `hasReleaseSigning`
   (`build.gradle:61`) only checks the four variables are non-empty, never that
   the file is readable, so the failure lands late and confusingly. Mount the
   directory read-only and make the check fail loudly and early.
3. **Switch on the Thor.** Changing signing keys forces
   `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: uninstall, fresh install, confirm
   `/sdcard/TerminaDS` game data survived (HANDOFF §10), confirm the *next*
   build still updates in place.

## 9. Invariants

Carried from the amended spec §8, all still binding:

- `SnapshotPublisher.cpp` and `CommandMailbox.cpp` are the only files that
  dereference game state; the mailbox never blocks the game thread and drains a
  bounded count per frame.
- Commands are absolute; the UI observes effects through the snapshot, never
  assumes them.
- The schema bump is atomic across native and Kotlin **in one commit**.
- Main-thread-only for the Presentation and its lifecycle owner (HANDOFF §3.1).
- New native code goes in `mm/2s2h/TerminaDS/`; the veil's font registration is
  the single sanctioned inherited edit, recorded in `docs/UPSTREAM.md`.
- No `contentDescription` regressions; the structural guard test stays.
- The native START pause, BenMenu, and all engine behavior remain untouched
  except the registered hooks and the three engine-apply calls in §4.

## 10. Accepted deviations from the design handoff

Recorded so a later reader does not mistake them for defects:

1. **Enhancements → Graphics rows are re-sourced** from real CVars; the
   handoff's five rows have no backing CVars in 2S2H (§2.3).
2. **No backdrop blur on the veil** — not achievable from ImGui (§6).
3. **No pending-reload bar.** All ten rows apply live, so the design's
   needs-reload concept and its `RELOAD NOW` action — which has no Android
   meaning anyway — have nothing to describe.
4. **MSAA ships four segments** (`OFF · 2× · 4× · 8×` → 1/2/4/8) rather than the
   design's six, because odd sample counts are not universally supported.
5. **The FPS row keeps the engine's default of 20**, not the design's 60. Silently
   changing an engine default is out of scope.
6. **Motion Blur Strength reads 0–255**, its real range, not a normalized percent.
7. **`RESUME`'s sub-line drops `· AUTOSAVED 4 MIN AGO`** — no autosave data exists.
8. **Footer hints use touch vocabulary**, not the handoff's Ⓐ/Ⓑ controller glyphs
   (carried from the amended spec §5).

## 11. What later phases inherit

- Schema v3 establishes the pattern for surfacing engine configuration to the
  bottom screen; further settings categories are new slots, not new machinery.
- The §6 chrome and the control library (sliders, segmented, checkboxes,
  grey-out) become the vocabulary for every future subscreen.
- The veil window is the mount point for any future top-screen overlay,
  including Phase 8's item showcase (§3 of the handoff).
- Compose UI test infrastructure, if the spike succeeds, is available to every
  later phase — the debt Phase 3 called in.
