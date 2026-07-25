# Phase 4a Hardware Verification — AYN Thor

**Date:** 2026-07-25
**Device:** AYN Thor (`kalama`), Android 13, wireless adb (`10.0.0.30:41277`)
**Build:** Phase 4a complete (`50bb8e17f`), `com.terminads.mm`, debug-signed
**Spec:** `docs/superpowers/specs/2026-07-25-termina-ds-phase-4-pause-settings-design.md`

## What Phase 4a delivers

The first write path into the game: a 16-slot SPSC command mailbox drained on
the game thread ahead of the snapshot publish (opcodes `PAUSE_SET`,
`CVAR_SET_INT`, `CVAR_SAVE`, all absolute), schema v2 (28 slots — adds
`pauseState`, `saveLoaded`, `menuOpen`), routing gated on the real
`saveLoaded` flag, and the bottom-screen PAUSE control with ack-tracked
pending/failure states plus the pause root-menu skeleton (RESUME live, four
rows inert).

## Pre-device gates

| Check | Result |
|---|---|
| JVM suite | ✅ 104 tests, 0 failures/errors/skips |
| Full pipeline build (T3, new native files) | ✅ 16m 19s |
| Three JNI symbols `T` in packaged `lib2ship.so` | ✅ uptime, readSnapshot, submitCommand |
| `mm.o2r` absent / `2ship.o2r` present | ✅ |
| Publisher instrument line | ✅ `first publish (schema 2, 28 slots)` |
| Whole-branch review (xhigh) after fixes | ✅ "Ready to merge" |

## Hardware checklist (user-verified)

| # | Check | Result |
|---|---|---|
| 1 | PAUSE control renders right of the nav | ✅ |
| 2 | Tap PAUSE → top screen freezes, no kaleido; bottom shows the root menu | ✅ (see camera note) |
| 3 | RESUME round-trip, repeated, feels instant | ✅ |
| 4 | START does nothing while frozen | ✅ |
| 5 | Native START/SELECT menus gray out the PAUSE control | ✅ |
| 6 | Background while paused → frozen game + menu restored on return | ✅ |
| 7 | Z+R single-step quirk sanity | ✅ no surprise |

## Observations and dispositions

- **Camera settles after pause.** The frame-advance gate freezes the Play
  update (logic, actors, input) but MM's camera smoothing runs on the draw
  side, so it glides to rest against the frozen world. **Accepted and
  documented** — freezing it would require engine camera edits with
  regression risk disproportionate to a cosmetic effect that reads as an
  organic settle. Revisit only if it grates in practice.
- **No top-screen PAUSED veil.** Correct per the Phase 4a spec (the veil
  draws on the game's render surface). **Scope decision (user, 2026-07-25):
  Plan B adds an engine-side ImGui veil** — darkened frame + wordmark +
  subtitle rendered while `pauseState` is set — alongside the full §5 menu
  styling and §10 Options.

## Review-loop notes

The whole-branch review (xhigh) caught three Importants after clean per-task
reviews: the pending state not entering until the next poll (double-submit
window), the schema bump split across two commits (fixed by squashing —
history now atomic at `278dbb43d`), and opcode values tested only against
themselves. All fixed in `50bb8e17f` and re-reviewed clean. Earlier, the
task-level loop had already caught and fixed: synthetic observations
falsely acking resumes, silently discarded non-OK submits, invisible resume
timeouts, and a directionless failure flag cross-contaminating the two
failure hints.

## Carried forward

- Plan B: full §5 pause-menu styling, §10 Options on real BenMenu CVars,
  the ImGui pause veil, Compose UI test infra, release keystore.
- Ledgered debt: no native behavioral test for mailbox boundaries (needs a
  native test seam); TalkBack full pass leads Plan B's verification.
