# Termina DS — Handoff for the Next Agent

You are picking up an in-progress project. **Phase 0 (fork + build + rebrand),
Phase 1 (second-screen foundation), and Phase 2 (read-only game-state bridge)
are complete and hardware-verified.**
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
why the whole thing is feasible.

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
| `mm/2s2h/TerminaDS/GameSnapshot.h` | **Layout contract.** The index enum IS the payload; Kotlin mirrors it by hand |
| `mm/2s2h/TerminaDS/SnapshotPublisher.cpp` | Game-thread sampler + seqlock. **The only file that dereferences game pointers** |
| `mm/2s2h/TerminaDS/NativeBridge.cpp` | JNI seam, native side (uptime + `nativeReadSnapshot`) |
| `Android/app/src/main/java/com/terminads/mm/NativeBridge.kt` | JNI seam, Kotlin side — the ONLY thing that calls native |
| `Android/app/src/main/java/com/terminads/mm/GameSnapshot.kt` | Kotlin mirror of the layout + pure decoder (unit-tested) |
| `Android/app/src/main/java/com/terminads/mm/GameSnapshotPoller.kt` | 10 Hz main-thread poll, staleness, `BridgeState` classification |
| `Android/app/src/main/java/com/terminads/mm/secondscreen/` | All second-screen code (manager, presentation, policy, host, lifecycle shim) |
| `Android/app/src/main/java/com/terminads/mm/MainActivity.java` | Inherited game activity; second screen wired into onCreate/onResume/onDestroy |
| `tools/build-apk.sh`, `docker/Dockerfile.android` | The reproducible build |
| `tools/deploy-apk.sh`, `tools/make-keystore.sh` | Deploy + release signing |

The second-screen pieces (all built, unit-tested where possible):
`DisplayInfo` (data class) + `DisplaySelectionPolicy` (picks the non-default
display), `PresentationLifecycleOwner` (the ComposeView-in-Presentation shim),
`SecondScreenPresentation` (hosts the ComposeView, `FLAG_NOT_FOCUSABLE`),
`SecondScreenManager` (display discovery + Presentation lifecycle),
`SecondScreenHost` (the placeholder Compose UI — display metrics + tap counter +
uptime heartbeat).

## 5. How to build, deploy, and verify (and the traps)

**Build:** `./tools/build-apk.sh` — runs entirely in Docker (`termina-ds-build:latest`).
- **It takes ~8-19 minutes** and exceeds a typical 10-minute shell timeout. Run
  it backgrounded and poll the log; do not abandon it as "hung".
- It does `rm -rf Android/app/.cxx` every build **on purpose**: CMake's
  `GLOB_RECURSE` freezes the native source list at configure time and AGP won't
  reconfigure when a *new* `.cpp` appears, so a new native file compiles green
  but ships without its code. Clearing `.cxx` forces a re-glob. Cost: full native
  recompile each build. (First thing to optimize if build time hurts — make the
  clear conditional on native-source changes. Deferred, documented.)
- Output: `Android/app/build/outputs/apk/release/app-release.apk`. Debug-signed
  by default via a persistent keystore volume (`termina-ds-android-home`) so
  builds update installs in place.

**Deploy:** `./tools/deploy-apk.sh` (uses `adb`). The Thor connects over Wi-Fi
(`adb pair` / `adb connect`); the pairing may need re-establishing each session
(the user reads the code off the device). Last known address: `10.0.0.30`.

**Verify natives landed:** `llvm-nm -D` on the packaged `arm64-v8a` library
(`lib/arm64-v8a/lib2ship.so`), grepping for `Java_com_terminads_mm_*`. This is
how the JNI rename and each new native symbol were confirmed — a green build is
not proof the symbol shipped.

> ⚠️ **`llvm-nm` is NOT on the build host.** It ships inside the NDK in the
> `termina-ds-build:latest` image — run the check there, or use host `nm -D` on
> the extracted `.so`. Run bare on the host with stderr suppressed, `llvm-nm`
> prints nothing, which is byte-identical to the symptom of the real
> `GLOB_RECURSE` failure this check exists to catch. **Never suppress stderr
> here.** The working invocation is in the Phase 2 plan, Task 6 Step 2.

**Unit tests:** `./tools/run-unit-tests.sh` (added in Phase 2) — 34 fast JVM
tests (display policy, lifecycle owner, snapshot decoder, poller). No NDK, no
device, about a minute.

> ⚠️ Gradle prints `BUILD SUCCESSFUL` with `testReleaseUnitTest UP-TO-DATE`
> **while running no tests at all.** Console text is not evidence. Pass
> `--rerun-tasks` and read counts from
> `Android/app/build/test-results/testReleaseUnitTest/*.xml`.

**⚠️ You cannot screenshot the second screen.** Both Thor displays are
`FLAG_SECURE`, so `screencap` returns black/empty. Logcat can prove the
Presentation launched (`TerminaDS/SecondScreen: Showing second screen on display
4 (Screen-2)`), but **only the user can confirm what actually renders on the
bottom panel.** Plan device verification around the user's eyes. The Thor also
runs its own second-screen shell, `rip.moth.cocoonshell`, which the Presentation
coexists with.

## 6. Working style that has worked here

- The user is hands-on and has the Thor. Any bottom-screen visual, framerate
  feel, touch-doesn't-steal-focus check, or TalkBack test needs them looking at
  the physical device. Ask; don't guess.
- Verify claims on real hardware, not just a green build. `llvm-nm` for symbols,
  logcat for lifecycle, the user's eyes for rendering.
- The build is slow; batch changes and background the build.
- Placeholder UI is intentionally throwaway — don't over-invest in it; it exists
  to prove the pipeline. Real features replace it.

## 7. What's next (roadmap, in dependency order)

From the spec (§6 there). Phases 0-2 done.

- **Phase 3 — Live HUD** (hearts, rupees, magic, C-items) on the bottom screen.
  First real payoff. **Start here.** Phase 2 hands you a `GameSnapshot` data
  class updating at 10 Hz as Compose state, with explicit validity flags; Phase 3
  deletes the debug readout in `SecondScreenHost.kt` and renders from that
  object, adding **no new native code**. Read
  `docs/verification/2026-07-23-phase-2-thor.md` first — it lists the engine
  representation quirks (an empty C-button is `255`, `roomNum` can be `-1`, yaw
  is a signed binary angle) that the HUD must handle. Low risk.

  Extending the payload later costs: one enum entry in `GameSnapshot.h`, one
  field in the publisher, one in the decoder, one test, and a
  `TDS_SNAP_SCHEMA_VERSION` bump **on both sides**.
- **Phase 4 — Settings reskin** for the handheld.
- **Phase 5 — Command bridge** (bottom screen → game, frame-safe): warps, item
  assignment, pause. High risk — this MUTATES game state; needs frame-safety.
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
  second screen active; TalkBack reading the bottom screen (the accessibility
  premise — the placeholder already carries a `contentDescription`); USB-C
  external-display-out takeover behavior.
- **No `LICENSE` file yet** for Termina DS itself. The upstream base is CC0-1.0.
  The user's choice; ask before adding one.

## 9. Ground truth to re-check at session start

- `git log --oneline -15` and `git status` — confirm the branch state.
- `git remote -v` — `origin` should be `flossbud/termina-ds`; the `upstream-*`
  remotes are reference-only and push-disabled.
- `adb devices` — is the Thor connected? If not, the user re-pairs it.
- `docker images | grep termina-ds-build` — is the build image present?
