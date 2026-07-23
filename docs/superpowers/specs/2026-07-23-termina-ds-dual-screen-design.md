# Termina DS — Dual-Screen Android Fork: Phase 0 + 1 Design

**Date:** 2026-07-23
**Status:** Approved for implementation planning
**Scope:** Phase 0 (fork & baseline) and Phase 1 (second-screen shell) only

---

## 1. Overview

Termina DS is a fork of the unofficial Android port of 2 Ship 2 Harkinian (2S2H),
targeting dual-screen Android handhelds — primarily the AYN Thor. The end state
is a Majora's Mask 3D-style dual-screen experience: game on the top display,
with maps, live game info, settings, and reference material on the bottom.

This document specifies only the first two phases. Later phases get their own
specs, written once their foundations exist.

**Phase 0 done when:** an APK built from this repo boots Majora's Mask on the Thor.

**Phase 1 done when:** a Compose UI is under our control on the Thor's bottom
display, and the game on the top display is measurably unaffected.

## 2. Non-goals for this spec

- Any bottom-screen feature. Phase 1 ships a deliberate placeholder.
- Reading or writing game state. That is Phase 2 (read) and Phase 5 (write).
- Map, walkthrough, HUD, or item-menu work.
- Supporting dual-screen devices other than the Thor. The display-selection
  design accommodates them; verification does not cover them.
- Google Play distribution. See §8.3 — it is permanently out of reach.

## 3. Base selection

We fork **linkzenic/2ship2harkinian-Android** (`android` branch), not
HarbourMasters upstream.

The requirement is full 2S2H feature parity on Android. That parity *is* the
linkzenic fork. Starting from `HarbourMasters/develop` would mean rebuilding the
Android layer — NDK toolchain, SDLActivity glue, scoped-storage handling,
on-device ROM extraction, touch controls, menu scaling — before writing a single
line of our own. That work is done and shipping.

| | HarbourMasters | linkzenic |
|---|---|---|
| Branch | `develop`, 4,490 commits | `android`, 3,371 commits |
| Latest release | 2026-03-02 | v4.0.2-android.3.5, 2026-06-15 |
| Last commit | — | 2026-06-19 |
| License | CC0-1.0 | CC0-1.0 |

Lineage: HarbourMasters → Waterdish → linkzenic. Note that linkzenic is **not a
GitHub fork** (`parent: null`) — it is a standalone repo. There is no fork
relationship to inherit; we wire remotes by hand.

**License:** CC0-1.0 (public domain dedication) throughout. We owe no
attribution and may relicense freely. Attribution will be given regardless, as a
courtesy.

## 4. Inherited codebase facts

Verified against `linkzenic/2ship2harkinian-Android@android`:

| Property | Value |
|---|---|
| Android project root | `Android/` (standard Gradle layout) |
| Application ID | `com.twoshipfork.mm` |
| Main activity | `MainActivity extends SDLActivity`, ~820 lines |
| SDL | 2.28.5, Java layer vendored at `org/libsdl/app/` |
| compileSdk / targetSdk / minSdk | 33 / 33 / 24 |
| NDK | 26.0.10792818 |
| CMake | 3.30.3 |
| Gradle | 8.3.2 |
| JDK | 17 (Temurin) |
| ABI | `arm64-v8a` only |
| Languages | Java only — no Kotlin, no Compose |
| Deps | `androidx.core:core:1.7.0`, `androidx.constraintlayout:2.1.4` |
| Default data root | `/storage/emulated/0/2S2H` |
| Release APK | `Android/app/build/outputs/apk/release/app-release.apk` |

Two inherited details materially reduce later risk:

1. **A JNI bridge already exists in both directions.** `MainActivity` declares
   `private native void nativeHandleSelectedFile(String)` and exposes
   `public static` methods called *from* native (`waitForSetupFromNative`,
   `getDataRootPathFromNative`). Phase 2 extends an established pattern rather
   than introducing one.
2. **Android Views already composite over the SDL surface.** Touch controls are
   `res/layout/touchcontrol_overlay.xml`, not ImGui. The precedent for native
   Android UI coexisting with the SDL render surface is already in the tree.

The repo's `Dockerfile` is **not** an Android build environment — it is
upstream's desktop Linux dev container (32-bit multilib, X11, GLEW). The
authoritative Android build recipe is `.github/workflows/android-release.yml`.

## 5. Target hardware

**AYN Thor** (in hand, available for verification):

| Display | Size | Resolution | Refresh | Role |
|---|---|---|---|---|
| Top | 6″ AMOLED | 1080×1920 | 120 Hz | **Primary** |
| Bottom | 3.92″ AMOLED | 1080×1240 | 60 Hz | Secondary |

SoC: Snapdragon 865 (entry) or 8 Gen 2 (higher tier).

The bottom panel is exposed to Android as a **separate display**, not as part of
one tall logical surface. Existing emulators with dual-screen support (melonDS,
Cemu, DraStic) each implemented screen targeting manually in custom forks; no
standard API does this for us. Note that the Thor treats the *top* screen as
primary, which is inverted relative to some other dual-screen handhelds — the
display-selection policy must not assume either arrangement.

## 6. Roadmap context

Phases 0 and 1 are the foundation for the following. Listed for context only;
each gets its own spec.

| # | Sub-project | Risk |
|---|---|---|
| 0 | Fork, build, rebrand, upstream remotes | low |
| 1 | `Presentation` on external display + Compose shell | **high** — device behavior |
| 2 | Read-only state bridge (JNI, per-frame snapshot) | medium |
| 3 | Live info HUD (hearts, rupees, magic, C-items) | low |
| 4 | Settings reskin | low |
| 5 | Command bridge (screen 2 → game, frame-safe) | **high** — state mutation |
| 6 | Map subsystem (area + world map, soaring warp select) | **high** — content |
| 7 | Walkthrough viewer | blocked on §8.1 |
| 8 | Item / pause menu takeover | **high** — deep engine surgery |

Accessibility is not a phase. It is a property of choosing Compose (§7) and is
verified within each phase.

## 7. Architecture decision: how the second screen is driven

**Decision: Android `Presentation` on the secondary display, hosting Jetpack
Compose, communicating with the C++ core over JNI.**

### Alternatives considered

**B — Second EGL surface, ImGui rendered from native.** Obtain a `Surface` from
a `Presentation`, pass it to native via JNI, create a second `EGLSurface`
sharing the main GL context, render ImGui there.

*Rejected.* Its only unique capability is engine-rendered content on screen two,
which nothing on the roadmap requires — a map is an image plus a dot, drawable
in Compose from position data. In exchange it costs threading and
context-currency complexity, couples screen two to the game's render loop, makes
text-heavy UI (walkthrough, settings) painful to build, and forfeits every
Android accessibility API.

**C — One tall logical surface.** *Rejected.* The Thor exposes genuinely
separate displays. Not applicable.

### Rationale for A

- **Text and navigation are Compose's home turf.** A scrolling, searchable,
  linkable walkthrough is a `LazyColumn`. In ImGui it is a rewrite of a text
  engine.
- **Accessibility is won or lost here.** Compose provides TalkBack, system font
  scaling, high-contrast, and switch access by default. ImGui draws pixels — the
  OS cannot see its contents at all. Given accessibility is a stated project
  goal, this decision is most of it.
- **The bottom screen is 60 Hz and largely static.** Coupling it to the game's
  render loop buys nothing and risks frame timing on the screen that matters.
- **The boundary is clean.** A Kotlin/C split at a narrow JNI seam is far easier
  to keep mergeable against upstream than edits scattered through shared ImGui
  menu code.

### Accepted tradeoff

The bottom screen is Kotlin; the game is C. This boundary is permanent and must
be maintained. It is accepted deliberately — a narrow, explicit seam is an asset
for upstream tracking (§9), not a liability.

## 8. Constraints and known landmines

### 8.1 Walkthrough content is a licensing problem

GameFAQs guides are copyrighted by their individual authors and are not
redistributable. Automated fetching also violates GameFAQs' terms, which they
enforce with Cloudflare. Bundling or scraping guides would expose the project to
takedown and would break on every markup change.

**Design for Phase 7 (not specced here):**

- **User-supplied import.** Primary path. User drops a `.txt`/`.html` into the
  library folder. No legal exposure.
- **In-app browser plus save hook.** The app embeds a WebView pointed at
  GameFAQs; the user searches and saves. The fetch is user-initiated and
  personal-use. The app is an importer, not a mirror.
- **Android share-target.** Guides downloaded anywhere else can be shared in.
- **Optional bundled content from CC BY-SA sources** (e.g. Zelda Wiki), which
  may be redistributed with attribution and share-alike.

No scraper ships in the APK.

### 8.2 Map assets are a content problem, not a code problem

Majora's Mask's N64 map data is crude; MM3D's maps are hand-authored art.
Nothing in the ROM yields MM3D-quality maps. Phase 6 will require either
extracting and heavily cleaning N64 map data or authoring maps per region, plus
a scene-ID → map-asset registry and a per-map coordinate projection. This is
expected to be the largest time investment on the roadmap and is mostly not
programming. Flagged now so it is not discovered late.

### 8.3 The app cannot ship on Google Play

The inherited manifest requires `MANAGE_EXTERNAL_STORAGE` ("All files access"),
which Play restricts to file-manager-class apps. Distribution is direct APK,
consistent with every project in this space. This is not a constraint we intend
to remove.

`targetSdk` therefore stays at 33 through Phase 1. Raising it changes
storage-permission behavior and would destabilize the inherited data-root logic
at exactly the moment we need a known-good baseline.

### 8.4 Nintendo IP

Neither upstream ships copyrighted assets; users supply their own ROM, and
`mm.o2r` is generated on-device. Termina DS preserves this invariant absolutely —
enforced in the build by the inherited `verifyBundledAssets` Gradle task (§9.3).
The project name deliberately avoids Nintendo trademarks, consistent with
HarbourMasters' naming practice.

---

## 9. Phase 0 design — fork, build, baseline

### 9.1 Repository

`/srv/projects/2ship2hark` is an initialized but empty repo on `master`.

1. Rename `master` → `main`.
2. Commit this spec as the initial commit.
3. Add fetch-only remotes:
   - `upstream-android` → `https://github.com/linkzenic/2ship2harkinian-Android`
   - `upstream-core` → `https://github.com/HarbourMasters/2ship2harkinian`
4. Fetch `upstream-android/android` and join histories with
   `git merge --allow-unrelated-histories`. One merge commit; provenance stays
   explicit — the repo begins with our intent, then absorbs the upstream tree.
5. Initialize submodules (`libultraship`, `OTRExporter`, `ZAPDTR`), left pointed
   at their origins and pinned by SHA.

Remote hosting and visibility are deferred to implementation planning.

### 9.2 Build container

`docker/Dockerfile.android`, derived from `android-release.yml`. The inherited
`Dockerfile` is left untouched for desktop builds.

Contents:

- Ubuntu base, JDK 17 (Temurin)
- Android cmdline-tools → SDK platform 34, build-tools, NDK `26.0.10792818`,
  CMake `3.30.3`
- Host build deps: `.github/workflows/apt-deps.txt` plus `libzip-dev`,
  `zipcmp`, `zipmerge`, `ziptool`
- SDL **2.28.5** built from source with `hidapi-libusb` (host tools)
- tinyxml2 **10.0.0** built from source (apt package removed first)
- vcpkg, triplet `arm64-android`: `zlib libpng libogg libvorbis opus opusfile`

Layer ordering must keep toolchain installation above source checkout so
iterative rebuilds hit cache. Build host has 12 cores / 31 GB RAM / 179 GB free —
ample.

### 9.3 Build script

`tools/build-apk.sh`, one command, two stages:

1. **Host stage** — CMake configure, build target `Generate2ShipOtr`, producing
   `2ship.o2r`; copy to `Android/app/src/main/assets/`.
2. **Android stage** — `cd Android && ./gradlew :app:assembleRelease`.

The inherited `verifyBundledAssets` task is retained unchanged. It fails the
build if `2ship.o2r` is missing *or* if `mm.o2r` is present, enforcing §8.4
mechanically.

### 9.4 Rebrand

| Item | From | To |
|---|---|---|
| App label | 2 Ship 2 Harkinian | Termina DS |
| `applicationId` / `namespace` | `com.twoshipfork.mm` | `com.terminads.mm` |
| Data root | `/storage/emulated/0/2S2H` | `/storage/emulated/0/TerminaDS` |

A distinct application ID and data root are both deliberate: Termina DS must
install **alongside** the stock port so parity can be A/B tested on one device,
with neither build touching the other's saves or configuration.

To avoid re-extracting the ROM, add a one-time optional import that copies an
existing `2S2H` data folder into the new root. Java package renaming
(`com.twoshipfork.mm` → `com.terminads.mm`) touches `MainActivity`,
`AssetCopyUtil`, the manifest, and JNI symbol names — the last of these must be
updated in the native sources in lockstep or the app will link but crash on
first native call.

Launcher iconography is out of scope for Phase 0; inherited icons remain until a
later cosmetic pass.

### 9.5 Signing

Generate a release keystore. Store it **outside the repository**; never commit
it. Supply via the four environment variables the inherited Gradle config
already reads: `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Absent these, the build falls back
to debug signing, which is acceptable for local iteration.

### 9.6 Deployment

The build host has no `adb`. Install `android-tools-adb`, pair to the Thor over
Wi-Fi debugging. Docker builds the APK; `adb install -r` deploys it.

### 9.7 Phase 0 verification

Termina DS is only a baseline if it matches the stock port. Verify on hardware:

1. APK installs alongside the stock linkzenic build without conflict.
2. First launch creates `/storage/emulated/0/TerminaDS` and prompts for the ROM.
3. ROM selection generates `mm.o2r` on-device.
4. Game boots to title and into gameplay.
5. Touch controls, controller input, and menu scaling behave as stock.
6. Data-root relocation to SD card works.
7. `mm.o2r` is absent from the built APK (`unzip -l | grep mm.o2r` → empty).

## 10. Phase 1 design — second-screen shell

### 10.1 Components

Three units with deliberately narrow boundaries, so Phases 2+ slot in without
rework.

**`SecondScreenManager`** (Kotlin) — owned by `MainActivity`.
- Enumerates displays via `DisplayManager`; tracks changes via `DisplayListener`.
- Applies the target-display selection policy and creates/destroys the
  `Presentation`.
- Handles display hotplug, configuration changes, and `onPause`/`onResume`.
- **Knows nothing about game state.** Its entire interface is "start", "stop",
  and "which display".

**`SecondScreenHost`** (Compose) — the bottom screen's root composable and
navigation scaffold.
- **Knows nothing about SDL, JNI, or displays.** Receives state, emits events.

**`NativeBridge`** — the sole seam to C. Stubbed in Phase 1 with a single
liveness heartbeat; Phase 2 fills it in. Nothing else in the Kotlin layer is
permitted to call native directly.

### 10.2 Display selection policy

The Thor makes the *top* screen primary; other dual-screen handhelds invert
this. The policy must not hardcode either.

Default: select the first non-default display reported by `DisplayManager`.
Persist a user override so a device with different topology, or a user with a
different preference, can retarget without a rebuild. Handle zero secondary
displays gracefully — the app must run normally as a single-screen port, since
that is also how it behaves when docked or on non-dual-screen hardware.

### 10.3 Gradle changes

- Add the Kotlin Android plugin and Compose (BOM-managed), set
  `buildFeatures.compose true`.
- `compileSdk` 33 → **34** (Compose requires ≥34).
- `targetSdk` stays **33** — see §8.3.
- `minSdk` stays **24**.
- Add `androidx.lifecycle`, `androidx.savedstate`, and Compose UI dependencies.

The existing `prepareKotlinBuildScriptModel` stub task in `app/build.gradle`
should be re-evaluated once Kotlin is genuinely present; it is a workaround for
Kotlin's absence.

### 10.4 The two known failure modes, designed for

**`ComposeView` inside a `Presentation` crashes by default.** A `Presentation`
is a `Dialog`, and its decor view has no `LifecycleOwner`,
`ViewModelStoreOwner`, or `SavedStateRegistryOwner` attached — all three are
required by `ComposeView`. Mitigation: a `PresentationLifecycleOwner` shim
implementing all three, installed on the decor view via the `ViewTree*Owner`
setters before the `ComposeView` is attached. Its lifecycle must be driven from
the `Presentation`'s own show/dismiss, not the host activity's.

**Focus theft.** Touch input on the bottom screen must not pull input focus from
SDL's surface, pause the game, or disturb the immersive-fullscreen state that
`MainActivity.applyImmersiveFullscreen()` maintains. Mitigation: create the
`Presentation` window with `FLAG_NOT_FOCUSABLE` and relax it only where a
specific interaction proves to require focus. This interacts with the manifest's
`launchMode="singleInstance"` and `screenOrientation="landscape"`, and with the
inherited known issue that SDL constrains orientation handling on Android.

This is the highest-risk unknown in the phase and the primary reason hardware
verification is non-negotiable.

### 10.5 Phase 1 deliverable

A placeholder screen, deliberately not a feature: display metrics (id, name,
resolution, density, refresh rate), a small number of test controls proving
touch input is received, and a heartbeat counter driven from native proving the
`NativeBridge` seam is live end to end.

Building a real feature here would conflate "does the foundation work" with
"does the feature work" — and if the foundation does not hold on real hardware,
the architecture in §7 must be revisited before anything is built on it.

### 10.6 Phase 1 verification

All on the Thor, all against the running game:

1. Both displays enumerate correctly; the bottom screen is selected.
2. The Compose UI renders correctly at 1080×1240 without clipping or letterbox.
3. **Game framerate on the top screen is unchanged** versus a Phase 0 build,
   measured, not eyeballed.
4. Touch on the bottom screen does not pause the game, disturb input, or break
   immersive fullscreen.
5. Survives sleep/wake, app switch and return, and screen rotation attempts.
6. Behaves correctly when the secondary display is absent (USB-C docked, or
   single-screen device).
7. TalkBack reads the placeholder UI — confirming the accessibility premise of
   §7 holds in practice.

## 11. Upstream tracking discipline

Termina DS tracks linkzenic, which tracks HarbourMasters. Rebase debt is the
principal long-term cost of this fork and must be managed from the first commit.

- **Prefer new files over edits to inherited ones.** Phase 1's Kotlin lands
  entirely in new files; the only inherited file touched is `MainActivity`
  (to own `SecondScreenManager`) plus Gradle config.
- **Keep hooks into inherited code minimal and obvious** — ideally one call site,
  clearly marked.
- Maintain `docs/UPSTREAM.md`: every inherited file we have modified, why, and
  what to watch when merging.
- Merge upstream on a regular cadence rather than in large infrequent batches.

The Kotlin/C boundary from §7 helps materially here: upstream churns C and ImGui
code, and almost none of our work lives there.

## 12. Open questions

Deferred to implementation planning; none block Phase 0.

1. **Remote hosting** — where the repo is pushed, and whether it is public. Public
   invites contribution; private avoids attention while the project is immature.
2. **Launcher icon and branding pass** — deferred, cosmetic.
3. **CI** — whether to adopt a Termina DS equivalent of `android-release.yml`, or
   build locally in Docker only until the project stabilizes.

---

## Appendix: sources

- [HarbourMasters/2ship2harkinian](https://github.com/HarbourMasters/2ship2harkinian)
- [linkzenic/2ship2harkinian-Android](https://github.com/linkzenic/2ship2harkinian-Android)
- [Kenix3/libultraship](https://github.com/Kenix3/libultraship)
- [Retro Game Corps — Dual-Screen Android Handheld Guide](https://retrogamecorps.com/2025/10/27/dual-screen-android-handheld-guide/)
- [DROIX — AYN Thor Review](https://droix.net/blogs/ayn-thor-handheld-review/)
