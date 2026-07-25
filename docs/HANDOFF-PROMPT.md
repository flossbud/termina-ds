# Handoff prompt for a fresh agent session

Copy everything below the line into the first message of a new agent
session. It is self-contained but leans on the repo's docs for depth.

---

You are picking up **Termina DS**, an in-progress dual-screen *Majora's
Mask* build for two-screen Android handhelds (primarily the AYN Thor),
built on the 2 Ship 2 Harkinian decompilation. Working directory:
`/srv/projects/2ship2hark`. Repo: `git@github.com:flossbud/termina-ds.git`
(private).

**Start by reading, in order, before touching anything:**
1. `docs/HANDOFF.md` in full — architecture, invariants, build system,
   traps, roadmap, and §10's agent working practices. It is the single
   most important document.
2. `docs/superpowers/specs/2026-07-25-termina-ds-phase-4-pause-settings-design.md`
   — the approved Phase 4 spec (Plan A shipped; Plan B is your work).
3. `docs/verification/2026-07-25-phase-4a-thor.md` — what was just
   hardware-verified, the accepted behaviors, and the carried debts.
4. `.superpowers/codex-sol/progress.md` — the task-by-task execution
   ledger with every review finding.

**Then run the ground-truth checks (HANDOFF §9):** `git log --oneline -15`,
`git status`, `git remote -v`, `command -v docker adb`,
`docker images | grep termina-ds-build`, `adb devices`, and
`./tools/run-unit-tests.sh` (expect 104 tests, 0 failures — prefix with
`sg docker -c '...'` if bare docker is denied). If docker/adb are missing
entirely, run `./tools/bootstrap-build-host.sh` and read HANDOFF §9's
explanation. If the Thor isn't connected, I'll re-pair wireless debugging
when you need the device — ask me.

**Current state:** Phases 0 through 4a are complete and hardware-verified.
The bottom screen shows a designed gameplay HUD (vitals, Termina map,
nav) with a PAUSE control; pause freezes the game via the engine's
frame-advance gate through a native SPSC command mailbox (the sanctioned
write path — absolute commands only, drained on the game thread), with the
snapshot bridge (schema v2, 28 slots) confirming effects. 104 JVM tests.
The last session's full history is in the ledger.

**Your task: Phase 4 Plan B.** Scope and required pre-plan research are in
HANDOFF §11: the design handoff's full pause-menu styling (§5) and Options
subscreen (§10, both Graphics categories on real BenMenu CVars via the
existing mailbox), the engine-side ImGui PAUSED veil, Compose UI test
infrastructure, and the release keystore. The design source of truth is
`docs/design/second-screen-handoff/README.md`. Follow the established
workflow: brainstorm the remaining open decisions with me, write the spec
amendment/plan with pinned file:line ground truth, and propose execution
options before implementing. TalkBack leads Plan B's hardware
verification — it has been deferred three phases.

**Operating constraints (standing):**
- Builds take ~15-20 min full pipeline on this host (Docker; `.cxx` clear
  by design), ~1-3 min via `tools/assemble-apk.sh` for Kotlin/resource-only
  changes — NEVER use the fast path after native-file additions (HANDOFF §5
  explains the GLOB_RECURSE trap). Background long builds; slow ≠ hung.
- Both Thor displays are FLAG_SECURE — no screenshots. Logcat proves
  launch; only I can judge rendering. When my visual report conflicts with
  your model, ask me for a phone photo FIRST.
- A green build doesn't prove a native symbol shipped — verify with
  llvm-nm inside the Docker image (`.superpowers/codex-sol/verify-apk.sh`).
- Never trust raw Gradle console text for tests; only
  `./tools/run-unit-tests.sh`'s XML-derived counts.
- Commits are authored as `jaret <jaretmsanchez@gmail.com>`. **Never
  involve the WheelHouse-Software GitHub account in anything.** Pushes go
  over my SSH key as `flossbud`, and only when I say push.
- I'm hands-on with the Thor next to me — lean on that for any visual,
  latency, or input check rather than guessing.
- I often drive this session from my phone: when you offer choices, end
  your message with a numbered list so I can reply with just a number.

Don't start writing code yet — begin by confirming the ground-truth
checks, then take me through the Plan B brainstorm.
