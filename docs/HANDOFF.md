# Termina DS — Handoff for the Next Agent

You are picking up an in-progress project. **Phase 0 (fork + build + rebrand),
Phase 1 (second-screen foundation), and Phase 2 (read-only game-state bridge)
are complete and hardware-verified. Phase 3 (live HUD) is complete and
hardware-verified (`docs/verification/2026-07-25-phase-3-thor.md`), including
five hardware-driven amendments. Phase 4a is complete and
hardware-verified (`docs/verification/2026-07-25-phase-4a-thor.md`; camera
draw-side settle accepted, PAUSED veil moved to Plan B). Plan B (full
pause-menu styling + Options + ImGui veil) next.**
This doc is what you need to be productive without re-discovering it all. Read it
fully before touching anything.

---

## 1. What Termina DS is

A dual-screen build of *Majora's Mask* for two-screen Android handhelds
(primarily the **AYN Thor**). The game runs on the top screen; the bottom screen
is a Kotlin/Jetpack-Compose UI for maps, live game info, settings, and reference
material — a Majora's Mask 3D-style layout. It is built on the **2 Ship 2
Harkinian (2S2H)** decompilation and is diverging into its own product; it does
**not** track upstream anymore.

- Repo: `git@github.com:flossbud/termina-ds.git` (private, owned by `flossbud`).
- Package: `com.terminads.mm`. App label: "Termina DS".
- Commits are authored as `jaret <jaretmsanchez@gmail.com>`. **Do not involve the
  `WheelHouse-Software` GitHub account in anything** — the user was explicit. Push
  is over the user's SSH key as `flossbud` (`ssh -T git@github.com` → "Hi flossbud!").

## 2. The single most important fact

**2S2H is a decompilation, not an emulator.** The game's state lives in real C
structs in the same process as the Android UI: `gSaveContext` (hearts, rupees,
magic, masks, items, C-button assignments), `PlayState` (current scene/room),
`Player` (position). Reading them is a struct field access over the JNI bridge —
no memory scanning, no fragile offsets. **Every planned bottom-screen feature
(HUD, map, walkthrough sync) is downstream of reading these structs.** This is
why the whole thing is feasible. **The snapshot bridge is read-only; the command
mailbox is the sanctioned write path (absolute commands, drained on the game
thread).**

## 3. Architecture and the load-bearing invariants

The second screen is **native Android UI (Jetpack Compose) in an
`android.app.Presentation` on the external display**, talking to the C++ core
over a narrow JNI seam. It is NOT ImGui and NOT a second GL surface. Rationale:
Compose gives accessibility (TalkBack, font scaling) for free, which ImGui
cannot; and text/nav/maps are Compose's home turf.

Invariants you must not break:

1. **Main-thread only for the Presentation/lifecycle owner.**
   `PresentationLifecycleOwner` uses `LifecycleRegistry.createUnsafe(this)`,
   which *drops* the main-thread assertion. So everything driving it must run on
   the main thread. The `DisplayListener` is registered with a
   `Handler(Looper.getMainLooper())`; Presentation callbacks are framework
   main-thread. **Never introduce a background thread/executor that touches the
   Presentation or its lifecycle owner** — it would silently corrupt lifecycle
   state instead of crashing.
2. **`mm.o2r` (extracted copyrighted game data) must never ship in the APK.**
   Enforced by the inherited `verifyBundledAssets` Gradle task. Don't weaken it.
3. **`compileSdk 34`, `targetSdk 33` (do NOT raise — it changes storage-permission
   behavior), `minSdk 24`, `arm64-v8a` only.**
4. **New native code goes in `mm/2s2h/TerminaDS/`** — a recursive CMake glob
   picks it up with no CMakeLists edit. See the build gotcha in §5.

## 4. Key files and where things live

| Path | What |
|---|---|
| `docs/superpowers/specs/2026-07-23-termina-ds-dual-screen-design.md` | The design/spec — read for the full phase roadmap and rationale |
| `docs/superpowers/plans/2026-07-23-termina-ds-phase-0-1.md` | The Phase 0+1 implementation plan (done) |
| `docs/verification/2026-07-23-phase-{0,1}-thor.md` | Hardware verification results |
| `docs/UPSTREAM.md` | Ledger of inherited-file edits + the vendoring/rename record |
| `engine/` | The vendored engine (was the `libultraship` submodule) |
| `OTRExporter/`, `ZAPDTR/` | Vendored asset-build tools (were submodules) |
| `docs/superpowers/specs/2026-07-23-termina-ds-phase-2-state-bridge-design.md` | Phase 2 spec (state bridge) |
| `docs/superpowers/plans/2026-07-23-termina-ds-phase-2.md` | Phase 2 implementation plan (done) |
| `mm/2s2h/TerminaDS/GameSnapshot.h` | **Layout contract.** The index enum IS the payload; Kotlin mirrors it by hand. Current schema is v2 (28 slots), bumped atomically on both sides in Phase 4a Tasks 1–2 |
| `mm/2s2h/TerminaDS/SnapshotPublisher.cpp` | Game-thread snapshot sampler + seqlock; with `CommandMailbox.cpp`, one of only two files that touch game state |
| `mm/2s2h/TerminaDS/CommandMailbox.{h,cpp}` | Sanctioned game-state write path: a fixed SPSC ring of absolute commands, drained on the game thread; with `SnapshotPublisher.cpp`, the only two files that touch game state |
| `mm/2s2h/TerminaDS/NativeBridge.cpp` | JNI seam, native side (uptime + snapshot read + command submit) |
| `Android/app/src/main/java/com/terminads/mm/NativeBridge.kt` | JNI seam, Kotlin side — the ONLY thing that calls native |
| `Android/app/src/main/java/com/terminads/mm/CommandBridge.kt` | The only Kotlin writer; submits absolute commands as the command ring's main-thread single producer |
| `Android/app/src/main/java/com/terminads/mm/GameSnapshot.kt` | Kotlin mirror of the layout + pure decoder (unit-tested) |
| `Android/app/src/main/java/com/terminads/mm/GameSnapshotPoller.kt` | 10 Hz main-thread poll, staleness, `BridgeState` classification |
| `Android/app/src/main/java/com/terminads/mm/secondscreen/` | All second-screen code (manager, presentation, policy, host, lifecycle shim) |
| `Android/.../secondscreen/DesignFrame.kt` | Pure design-frame scaling (1240x1080 reference) |
| `Android/.../secondscreen/TerminaDesign.kt` | Design tokens: colors, bundled fonts, type specs, DesignRoot |
| `Android/.../secondscreen/SceneNames.kt` | GENERATED sceneId -> name table (tools/generate-scene-names.py) |
| `Android/.../secondscreen/HudModel.kt` | Pure snapshot -> HUD model, route(), diagnostic strings |
| `Android/.../secondscreen/GameplayScreen.kt` | The §4 gameplay HUD (vitals, map, nav) |
| `Android/.../secondscreen/PauseRequestTracker.kt` | Observe-don't-assume acknowledgement and timeout tracking for pause/resume commands |
| `Android/.../secondscreen/PauseMenuScreen.kt` | Plan A pause-root skeleton: live RESUME plus inert Inventory, Map, Song of Time, and Options rows |
| `Android/app/src/main/java/com/terminads/mm/MainActivity.java` | Inherited game activity; second screen wired into onCreate/onStart/onResume/onStop/onDestroy |
| `tools/build-apk.sh`, `docker/Dockerfile.android` | The reproducible build |
| `tools/assemble-apk.sh` | UI-only APK assembly -- NEVER after native changes (stale glob) |
| `tools/generate-scene-names.py` | Regenerates SceneNames.kt from scene_table.h |
| `docs/design/second-screen-handoff/` | The committed design handoff (README.md is the visual source of truth) |
| `tools/bootstrap-build-host.sh` | Recreates docker+adb+build image on a fresh host |
| `tools/deploy-apk.sh`, `tools/make-keystore.sh` | Deploy + release signing |

The second-screen foundation pieces (all built, unit-tested where possible):
`DisplayInfo` (data class) + `DisplaySelectionPolicy` (picks the non-default
display), `PresentationLifecycleOwner` (the ComposeView-in-Presentation shim),
`SecondScreenPresentation` (hosts the ComposeView, `FLAG_NOT_FOCUSABLE`),
and `SecondScreenManager` (display discovery + Presentation lifecycle). Phase 3
replaced the placeholder host content with routed gameplay, loading, ROM-missing,
and bridge-error screens driven by the live snapshot model.

## 5. How to build, deploy, and verify (and the traps)

**Build:** `./tools/build-apk.sh` — runs entirely in Docker (`termina-ds-build:latest`).
- **Latest measured full pipeline: 15m 21s on the current wheelhouse VM
  (4 cores).** Run it backgrounded and poll the log; do not abandon it as
  "hung".
- It does `rm -rf Android/app/.cxx` every build **on purpose**: CMake's
  `GLOB_RECURSE` freezes the native source list at configure time and AGP won't
  reconfigure when a *new* `.cpp` appears, so a new native file compiles green
  but ships without its code. Clearing `.cxx` forces a re-glob. Cost: full native
  recompile each build. (First thing to optimize if build time hurts — make the
  clear conditional on native-source changes. Deferred, documented.)
- `tools/assemble-apk.sh` exists precisely because it skips the `.cxx` clear —
  it is the fast path for UI work and a foot-gun after native work.
- Output: `Android/app/build/outputs/apk/release/app-release.apk`. Debug-signed
  by default via a persistent keystore volume (`termina-ds-android-home`) so
  builds update installs in place.

**Deploy:** `./tools/deploy-apk.sh` (uses `adb`). The Thor connects over Wi-Fi
(`adb pair` / `adb connect`); the pairing may need re-establishing each session
(the user reads the code off the device). Last known address: `10.0.0.30`.

**Verify natives landed:** run `tools/verify-apk.sh` inside the build container.
It uses `llvm-nm -D` on the packaged `arm64-v8a` library
(`lib/arm64-v8a/lib2ship.so`) and checks every JNI and Termina DS native export.
This is how the JNI rename and each new native symbol were confirmed — a green
build is not proof the symbol shipped.

**Pause/frame-step quirk:** Termina DS implements its pause by enabling the
engine's frame-advance gate. While paused this way, holding Z+R single-steps
frames (the engine's development affordance at `mm/src/code/z_pause.c:44`).
This is accepted, discoverable, and harmless; it is not a failed pause.

> ⚠️ **`llvm-nm` is NOT on the build host.** It ships inside the NDK in the
> `termina-ds-build:latest` image — run the check there, or use host `nm -D` on
> the extracted `.so`. Run bare on the host with stderr suppressed, `llvm-nm`
> prints nothing, which is byte-identical to the symptom of the real
> `GLOB_RECURSE` failure this check exists to catch. **Never suppress stderr
> here.** The working invocation is in the Phase 2 plan, Task 6 Step 2.

**Unit tests:** `./tools/run-unit-tests.sh` (added in Phase 2) — 183 JVM tests
in 21 suites (display policy, lifecycle owner, snapshot decoder/poller, command
bridge, pause routing/tracking, design scaling, scene names, HUD model,
structural guards). Gradle also invokes the incremental debug native build; with
an unchanged native source list the full run is about 40 seconds. No device or
APK assembly is involved. Extra arguments still reach Gradle, so
`./tools/run-unit-tests.sh --tests '*PollerTest*'` works.

> ⚠️ Gradle prints `BUILD SUCCESSFUL` with `testDebugUnitTest UP-TO-DATE`
> **while running no tests at all.** Console text is not evidence. The script
> now defends against this itself: it forces `--rerun-tasks`, wipes the previous
> results, takes its counts from
> `Android/app/build/test-results/testDebugUnitTest/*.xml`, and exits non-zero
> on any failure, error, or missing XML. Trust its `PASS`/`FAIL` line and the
> counts it prints — not Gradle's. Anything invoking Gradle directly still has
> to apply the guard by hand.

**⚠️ You cannot screenshot the second screen.** Both Thor displays are
`FLAG_SECURE`, so `screencap` returns black/empty. Logcat can prove the
Presentation launched (`TerminaDS/SecondScreen: Showing second screen on display
4 (Screen-2)`), but **only the user can confirm what actually renders on the
bottom panel.** Plan device verification around the user's eyes. The Thor also
runs its own second-screen shell, `rip.moth.cocoonshell`, which the Presentation
coexists with.

## 6. Working style that has worked here

- The user is hands-on and has the Thor. Any bottom-screen visual, framerate
  feel, or touch-doesn't-steal-focus check needs them looking at
  the physical device. Ask; don't guess.
- Verify claims on real hardware, not just a green build. `llvm-nm` for symbols,
  logcat for lifecycle, the user's eyes for rendering.
- The build is slow; batch changes and background the build.
- The Phase 1-2 placeholder UI existed only to prove the pipeline; Phase 3
  replaced it with the live HUD.

## 7. What's next (roadmap, in dependency order)

From the spec (§6 there). Phases 0-3 are complete and hardware-verified.

**Phase 4a — Command mailbox + pause:** complete and hardware-verified
(`docs/verification/2026-07-25-phase-4a-thor.md`). The write path is the
SPSC command mailbox (absolute commands, drained on the game thread);
pause rides the engine frame-advance gate. Accepted behaviors: the camera
settles on the draw side after pausing; Z+R single-steps while frozen.
**Plan B next:** full §5 pause-menu styling, §10 Options on real BenMenu
CVars, the engine-side ImGui PAUSED veil, Compose UI test infra, release
keystore.


- **Phase 5 — Command bridge expansion** (bottom screen → game, frame-safe):
  warps and item assignment. High risk — this MUTATES game state; preserve the
  mailbox's game-thread drain and absolute-command rules.
- **Phase 6 — Map subsystem** (area + world map; Song of Soaring warp select).
  High risk, mostly a *content* problem — MM's N64 maps are crude; MM3D-quality
  maps must be extracted-and-cleaned or authored, plus a scene-ID→asset registry
  and per-map coordinate projection. Expect this to be the largest time sink.
- **Phase 7 — Walkthrough viewer.** Licensing-constrained: user-supplied file
  import + an in-app WebView + save/share hook. **Do NOT build a scraper**;
  GameFAQs guides are author-copyrighted and their terms forbid automated
  fetching. Zelda Wiki (CC BY-SA) is bundleable with attribution.
- **Phase 8 — Item/pause menu takeover** on the bottom screen. Deep engine
  surgery. MM3D parity.

Each phase gets its own spec → plan → build cycle (see the existing spec/plan as
templates).

## 8. Loose ends carried forward (none blocking)

- **Deep internal engine rename.** The vendored engine folder is `engine/`, but
  its CMake target name is still `libultraship`, its C++ namespace is `Ship`/`LUS`
  (173 files), and headers are `#include <libultraship/...>` (390 files). This is
  a ~500-file rename — do it *deliberately and incrementally* as the engine
  diverges, not big-bang. There's no upstream to conflict with anymore, so it's
  now safe to do; it's just large.
- **Deferred Minors (all reviewed non-blocking):** Dockerfile GPG-key pinning /
  unversioned `platform-tools` / no `pipefail`; a dead `prepareKotlinBuildScriptModel`
  stub in `Android/app/build.gradle`; a missing "2+ non-default displays" unit
  test; `(void)env/(void)thiz` boilerplate; a dead reset in the `SecondScreenManager`
  show() catch; the `.cxx`-every-build cost (§5).
- **Untested verification tail:** top-screen framerate measurement with the
  second screen active; the Phase
  4a Thor checklist in §7; USB-C external-display-out takeover behavior.
- **Screen-reader verification is out of scope (owner decision, 2026-07-26).**
  Earlier phases listed "TalkBack leads hardware verification" as a standing
  item. It was never runnable: the Thor ships without a screen reader installed
  (`pm list packages` finds none; `dumpsys accessibility` shows only the vendor's
  game-assistant service), and `uiautomator dump` returns a null root node for
  the second-screen `Presentation`. Accessibility *correctness* is still asserted
  in CI — exact semantics strings, a determinism test, disabled-row semantics,
  `selected` on active tabs, and a structural guard. What is unknown and accepted
  as such is whether a screen reader can reach a `Presentation` on a secondary
  display at all. Anyone reviving this should answer that question first; if the
  answer is no, the remedy is architectural. See
  `docs/verification/2026-07-26-phase-4-plan-b-thor.md` §5.1.
- **No `LICENSE` file yet** for Termina DS itself. The upstream base is CC0-1.0.
  The user's choice; ask before adding one.

## 9. Ground truth to re-check at session start

- `git log --oneline -15` and `git status` — confirm the branch state.
- `git remote -v` — `origin` should be `flossbud/termina-ds`; the `upstream-*`
  remotes are reference-only and push-disabled.
- `adb devices` — is the Thor connected? If not, the user re-pairs it.
- `docker images | grep termina-ds-build` — is the build image present?
- **If `docker` or `adb` is missing entirely**, the session is on a host that
  never had the toolchain (this happened when wheelhouse migrated VMs on
  2026-07-24: the repo migrates, the toolchain does not). Fix it with
  `./tools/bootstrap-build-host.sh` — one idempotent command that installs
  docker + adb and rebuilds the image from `docker/Dockerfile.android`. Do
  not go hunting for the old host's toolchain; the image is fully
  reproducible from the repo.

## 10. Working practices for agent sessions (read before executing anything)

Everything in this section was learned the hard way across Phases 2-4a.

**Environment.** Sessions run on the wheelhouse VM (`wheelhouse.mimilab.lan`,
4 cores/16 GB). Docker group membership may not be active in your shell —
prefix docker-touching scripts with `sg docker -c '<command>'` if bare
`docker` is denied. The Thor is reached over **wireless adb** at
`10.0.0.30` (connect port changes per session; after a device or host
reboot the pairing may be gone entirely — the user runs Settings →
Developer options → Wireless debugging → "Pair device with pairing code"
and sends you the pairing IP:port + 6-digit code for `adb pair`, then the
main-screen port for `adb connect`).

**Signing.** A normal build uses the persistent release keystore at
`~/.termina-ds/release-keystore.jks`, reads the password from
`~/.termina-ds/pass`, and defaults the alias to `termina-ds`. Both files stay
outside the repository and Docker volumes. The build scripts mount the
keystore directory read-only at `/keystore` and rewrite the host path for
Gradle. Set `ANDROID_KEYSTORE_DIR` to override the mounted directory when using
the default filename; an explicit `ANDROID_KEYSTORE_PATH` is resolved (including
symlinks) and instead mounts the target's parent directory so the path and mount
cannot disagree. Explicit
`ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and
`ANDROID_KEY_PASSWORD` values take precedence over the file-based defaults. If
the password file is absent and the signing variables are unset, local builds
retain the debug-signing fallback. The mode-`600` password file protects the
password from other unprivileged host users, not from root or docker-group
members; those principals can already read the source file directly. Switching
from the debug key to the release key forces one uninstall/reinstall on the
device; game data in `/sdcard/TerminaDS` survives.

**Verification discipline.**
- Both Thor displays are FLAG_SECURE: you cannot screenshot them. Logcat
  proves launch and the publisher line proves the bridge
  (`Snapshot: publisher registered, first publish (schema 2, 28 slots)`);
  only the user can judge rendering. **When a visual report and your
  mental model disagree, ask for a phone photo of the panel first** — a
  photo found in seconds a layout bug that two code reviews missed
  (Phase 3's nav underline).
- A green build is not proof a native symbol shipped: verify with llvm-nm
  inside the Docker image (see `tools/verify-apk.sh` for the exact six-symbol +
  mm.o2r gate).
- `./tools/run-unit-tests.sh` is self-verifying from JUnit XML (183 tests
  as of Phase 4a). Never trust raw Gradle console text: it prints BUILD
  SUCCESSFUL with zero tests run when tasks are UP-TO-DATE.

**Execution workflow.** The project uses the superpowers skills:
brainstorm → spec (docs/superpowers/specs/) → plan
(docs/superpowers/plans/) → per-task execution with reviews. Phases 3-4a
ran Codex GPT-5.6-Sol subagents (`codex exec`) with a durable ledger at
`.superpowers/codex-sol/progress.md` — read it for the full task-by-task
history including every review finding and accepted debt. If you use that
workflow: subagent sandboxes have **no network by default and can never
reach docker** — the orchestrator runs every build/test and relays
verbatim output into the session as RED/GREEN evidence; and build each
phase's reviewer constraints from THAT phase's plan (a reviewer handed a
previous phase's "no native changes" constraints will confidently flag
compliant work as violations).

**User rules (standing, from the project owner).**
- Commits authored as `jaret <jaretmsanchez@gmail.com>`. Never involve the
  WheelHouse-Software GitHub account. Pushes go over the user's SSH key as
  `flossbud`, and only when the user asks.
- The user is hands-on with the Thor next to them — lean on that for any
  visual/latency/input check instead of guessing.
- Sessions may be driven from a phone (wheelhouse mobile): when offering
  choices, end the message with a numbered list (1./2./3.) so the user can
  reply with just a number.

**Accepted behaviors (do not "fix" without asking).**
- While paused, the camera settles to rest (frame-advance gates the Play
  update; camera smoothing runs on the draw side). Accepted 2026-07-25.
- While paused, holding Z+R single-steps frames (engine dev affordance,
  z_pause.c:44).
- The pre-save intro/title publish garbage save slots by design; routing
  gates on the schema-v2 `saveLoaded` flag.

## 11. Next up: Phase 4 Plan B

Scope (user-approved 2026-07-25): the full §5 pause-root-menu styling and
§10 Options subscreen from the design handoff
(`docs/design/second-screen-handoff/README.md`), both Graphics categories
bound to real CVars; the engine-side ImGui PAUSED veil (darkened frame +
wordmark + subtitle while `pauseState` is set); Compose UI test
infrastructure (Robolectric smoke tests — the class of tooling that would
have caught the Phase 3 nav bug at build time); and the release keystore.

Research the plan must pin before writing (spec §2 lists them):
- The 10-row CVar table (names, ranges, defaults, live-vs-restart) read
  from `mm/2s2h/BenGui/BenMenu.cpp`'s Settings→Graphics and
  Enhancements→Graphics sections (known so far: `gInterpolationFPS` :635,
  `gMatchRefreshRate` :654, and the disable-when pattern at :2201 that
  mirrors the design's FPS lock).
- The ImGui overlay seam: how 2S2H registers always-on-top draw windows
  (`mm/2s2h/BenGui/BenGui.cpp` setup around :99) and its custom-font
  loading, for the veil.
- The Options screens write CVars through the existing command mailbox
  (`CVAR_SET_INT` + debounced `CVAR_SAVE`) — no new native machinery
  expected beyond the veil's draw hook reading `pauseState`.
