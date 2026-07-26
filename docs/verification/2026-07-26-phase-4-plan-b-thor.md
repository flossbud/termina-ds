# Phase 4 Plan B Hardware Verification — AYN Thor

**Date:** 2026-07-26
**Device:** AYN Thor (`kalama`), Android 13, wireless adb (`10.0.0.30:33635`)
**Build:** `a879df0c3`, `com.terminads.mm`, **release-signed**
(SHA-256 `94dd1374593fcf1a9ce36d5c0a0038836aaf22a7110784482309f6d503c5dcd4`)
**Spec:** `docs/superpowers/specs/2026-07-25-termina-ds-phase-4-plan-b-design.md`
**Branch:** `78b0e0d8d..a879df0c3` (14 commits)

> **This document records a PARTIAL verification.** The Options subscreen and the
> command path are confirmed working on hardware. TalkBack, the veil's motion,
> and the pause-lifecycle fix are **not yet verified** and are listed as open in
> §5. Do not treat this phase as fully hardware-verified until those close.

## 1. What Plan B delivers

- Schema v3 (39 slots): ten graphics settings plus the live display refresh rate
- Three semantic mailbox opcodes (internal resolution, MSAA, texture filter) that
  perform the CVar write **and** the engine apply in one drained command
- The full §5 pause root menu and the §10 Options subscreen — chrome, tabs,
  category chips, empty states, and a control library of sliders, segmented
  controls and checkboxes — bound to real BenMenu CVars
- An engine-side ImGui PAUSED veil on the top screen
- Compose UI test infrastructure (Robolectric)
- Release keystore plumbing
- A dynamic display-refresh-rate fix (added during this session — see §4)

## 2. Pre-device gates

| Check | Result |
|---|---|
| JVM suite | ✅ 183 tests in 21 suites, 0 failures/errors/skips |
| Full native pipeline | ✅ 16m 34s, 0 compile errors |
| Three JNI symbols `T` in packaged `lib2ship.so` | ✅ uptime, readSnapshot, submitCommand |
| `TerminaDS_LoadVeilFont` `T` | ✅ (veil exports no JNI; needs its own gate) |
| `TerminaDS_{Get,Set}DisplayRefreshHz` `T` | ✅ |
| `mm.o2r` absent / `2ship.o2r` present | ✅ |
| Release signing, fingerprint matches keystore | ✅ |
| Whole-branch review (xhigh) after fixes | ✅ "Ready to merge" |

## 3. Verified on hardware

| # | Check | Result |
|---|---|---|
| 1 | Release-signed install; game data survives the key change | ✅ `/sdcard/TerminaDS` unchanged, 14 entries / 2.8 GB before and after uninstall |
| 2 | Second screen starts on the external display | ✅ `Showing second screen on display 4 (Screen-2)` |
| 3 | Schema v3 bridge live | ✅ `Snapshot: publisher registered, first publish (schema 3, 39 slots)` |
| 4 | App stable across a full session | ✅ 0 crashes in logcat |
| 5 | Options rows reach the engine and persist | ✅ config shows `MSAAValue=4`, `ClockType=1`, `3DItemDrops=1`, `IncreaseActorDrawDistance=2` |
| 6 | `CVAR_SAVE` debounce persists to disk | ✅ implied by #5 — values survived to `2ship2harkinian.json` |
| 7 | Three control types work | ✅ segmented (Clock Type, Draw Distance), checkbox (3D Item Drops), MSAA |
| 8 | FPS row greys out under Match Refresh Rate | ✅ user confirmed greyed **and inert** |
| 9 | In-place update on the same release key | ✅ no uninstall needed for the second install |

The user's overall assessment after exercising the Options subscreen: *"it seems
to work well."*

## 4. Root cause found and fixed during verification

**Symptom.** After changing FPS settings the game ran in **slow motion**.
Sleeping and waking the device cleared it.

**Root cause.** `GfxWindowBackendSDL2::GetActiveWindowRefreshRate`
(`engine/src/fast/backends/gfx_sdl2.cpp:268-274`) calls
`SDL_GetCurrentDisplayMode`, which returns SDL's **cached** mode. SDL refreshes
that cache only when the surface is reconfigured. The Thor switches between 60 Hz
and 120 Hz — for power saving, and by user setting — **without** reconfiguring
the surface, so SDL reports a stale rate indefinitely. Sleep/wake recreates the
surface, which is why it appeared to self-heal.

**Proof.** With the app running, the Thor was set to 120 Hz; the app's own FPS
chip still read 60.

**Consequence.** With `gMatchRefreshRate` enabled,
`OTRGlobals::GetInterpolationFPS` (`mm/2s2h/BenPort.cpp:330-332`) returns that
stale rate and `Graph_ProcessGfxCommands` (`:946`) paces the game to it. The app
cached 120 at launch, the panel dropped to 60, and the game paced for 120 against
a 60 Hz panel. The engine's own loop is correct — it resets its accumulator when
the target changes (`:958`, `:988`). Its **input** was wrong.

**Fix** (`a879df0c3`): Android is authoritative.

- New `mm/2s2h/TerminaDS/DisplayRefresh.{h,cpp}` — an atomic store. **0 means
  "not yet known"** and every consumer falls back to the engine's own query, so a
  plumbing failure degrades to today's behavior rather than to zero Hz.
- New opcode `TDS_CMD_SET_DISPLAY_HZ = 7`, absolute, range-checked 1–1000.
- `SnapshotPublisher` prefers it — our FPS chip and slider maximum stop lying.
- `OTRGlobals::GetInterpolationFPS` prefers it — the engine paces correctly.
  **Inherited-file edit, recorded in `docs/UPSTREAM.md`.**
- Kotlin `MainDisplayRefreshReporter` reports the **main** display's rate, not
  the presentation display's — they can differ on this device, and the engine
  paces the game on the main one. Sourced from the active `Mode` (not
  `Display.getRefreshRate()`, which is unreliable for presentation displays),
  pushed on initial registration and on every change, rounded and deduplicated.

**Design consequence, adopted:** on this device the display refresh rate may
change at any moment. It is now treated as a supported runtime condition rather
than a startup constant.

## 5. NOT YET VERIFIED — open

These are the phase's highest-value remaining checks. **TalkBack was the stated
lead priority for this phase and has now been deferred a fourth time.**

| # | Check | Why it matters |
|---|---|---|
| 1 | **TalkBack across gameplay, pause, and Options** | Deferred since Phase 2. Accessibility is a first-class requirement of this design |
| 2 | **Slider drag chatter under TalkBack** | Open adjudicated question: row semantics embed values in `contentDescription` per spec `:163`. A reviewer argued for splitting into `stateDescription`. Deferred to be settled by ear. If the label re-announces on every tick, the fix is designed and ready |
| 3 | **Active tab/category announced as selected** | The `selected` semantics added in `495fd8d57` |
| 4 | **Veil motion on the top screen** | Ornament clock, hand oscillation, wordmark entrance, shimmer, glow, rule growth, staggered rise, breathing diamond. **Zero automated coverage** — code review was the only gate |
| 5 | **Veil never intercepts input** | It draws to the foreground draw list with no window |
| 6 | **Pause lifecycle** — `Options → RESUME PLAY → pause again` lands on root, including via the game's own START | The bug fixed in `495fd8d57`; START is the path a callback-level fix would have missed |
| 7 | **Refresh-rate fix end to end** — change the Thor 60↔120 mid-game and confirm the chip follows and the game holds full speed, with no sleep/wake | The fix built this session; §4's root cause |
| 8 | Internal Resolution and Texture Filter rows | No written value in the config; appear untouched |
| 9 | Top-screen framerate with the second screen active | Carried from Phase 3. `GetCurrentRefreshRate()` is sampled every frame by deliberate choice |
| 10 | The >10-hearts visual | Carried from Phase 3 |

## 6. Review-loop notes

Every task passed an individual review; the whole-branch review (xhigh) returned
**Ready with fixes** — 0 Critical, 4 Important — and all four were fixed and
re-reviewed to **Ready to merge**.

Two findings were worth the entire review process:

- **The veil would have been invisible.** It used
  `ImGuiWindowFlags_NoBringToFrontOnFocus`, which sounds like "stay on top" and
  does the opposite: ImGui `push_front`s such windows (`imgui.cpp:6583`) and
  renders that list in order, so front is drawn *first* — underneath. The game
  render is itself an ImGui window (`Gui.cpp:732-736`) blitting an opaque
  framebuffer over the viewport. Pause would have worked, the bottom screen would
  have looked perfect, and the top screen would simply never change, with nothing
  in any log. Fixed by drawing to the viewport foreground draw list.
- **Settings persistence could be silently lost forever.** The save debouncer
  consumed its pending state *before* the submit was known to have succeeded, so
  a momentarily full command ring dropped the write permanently — the setting
  stayed applied live and vanished on restart. `CVAR_SAVE` is the only command in
  this system with no observable effect in the snapshot, so its submit status is
  the only evidence it happened. Fixed by separating "is due" from "consume", with
  retry on `FULL`.

Eleven defects in the implementation briefs were caught by implementers who
stopped to ask rather than transcribing them, including an `OptionKey` mapping
gap that would have thrown on the first chevron tap of a live slider row, and a
negative-`padding` knob placement that would have crashed on any row at its
minimum.

## 7. Carried debt

- **`GLOB_RECURSE` trap fired for real.** Adding `DisplayRefresh.cpp` failed at
  **link** because `tools/run-unit-tests.sh` does not clear `.cxx` (only
  `tools/build-apk.sh` does). We were lucky it was loud — two translation units
  referenced the new symbols. A new `.cpp` that nothing references links fine and
  ships without its code. **Consider adding the clear, or a glob guard, to the
  test runner.** Note `.cxx` is root-owned and must be removed from inside a
  container.
- `verify-apk.sh` checks only the three JNI symbols. The veil and the refresh-rate
  store export none, so they need their own symbol checks — done ad hoc here,
  worth folding into the script.
- Exact design copy is only spot-checked in `OptionsModelTest`; table-driven
  assertions over all ten rows were recommended.
- `nextSegmentValue` in `OptionsCommands.kt` is unused production code — the
  screen is touch-only and the design cycles segments by keyboard.
- A keystore path ending in `/` makes `readlink -f` exit non-zero under `set -e`,
  losing a descriptive error. Malformed input; fails safely.
- No native test seam, so the mailbox's range boundaries have no native tests.
  Carried from Phase 4a.
- The publisher calls `GetCurrentRefreshRate()` every frame (two SDL calls, one
  iterating displays). Deliberate — live sampling beats caching on a device that
  changes rate. Mitigation if it ever matters: sample every N frames.
