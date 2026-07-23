# Termina DS

A dual-screen build of *The Legend of Zelda: Majora's Mask*, for two-screen
Android handhelds — primarily the **AYN Thor**. Built on the
[2 Ship 2 Harkinian](https://github.com/HarbourMasters/2ship2harkinian)
decompilation of the N64 game, Termina DS runs the game on the top screen and
uses the bottom screen for maps, live game info, settings, and reference
material — a Majora's Mask 3D-style layout, reimagined for modern handhelds and
built out with accessibility in mind.

Termina DS is its own project. It uses 2S2H as a starting point and is diverging
from it deliberately; it does not aim to stay in sync with upstream.

## Status

Actively in development. What works today:

- **Full 2S2H parity on Android** — the complete game runs, with the 2S2H
  feature set, on-screen touch controls, controller support, and scalable menus.
- **Second screen foundation** — Termina DS detects the handheld's secondary
  display and renders its own UI there (currently a placeholder that proves the
  pipeline), without disturbing input or framerate on the game screen.
- **One-time import** from an existing 2 Ship 2 Harkinian install, so you don't
  re-extract your ROM or lose saves.

On the roadmap for the bottom screen: an interactive area/world map (including
Song of Soaring warp selection), a live HUD (hearts, rupees, items), a
navigable in-game walkthrough, and eventually the full item/pause menu — plus a
handheld-tuned settings reskin and accessibility features throughout.

## Requirements

- Android 7.0+ with OpenGL ES 3.0+
- A two-screen Android handheld for the dual-screen features (tested on the AYN
  Thor, Snapdragon 8 Gen 2, Android 13). Runs as a normal single-screen port on
  other devices.
- Your own legally obtained `MM.z64` ROM. **No copyrighted game assets are
  included in this project** — the app generates its asset archive (`mm.o2r`)
  on-device from the ROM you provide.

## First run

1. Install the APK (see *Building* — there are no public releases).
2. Open the app once. Grant "All files access" when prompted so it can create
   its data folder.
3. If an existing `2S2H` data folder is found, Termina DS offers to import it
   (ROM and saves come across). Otherwise, select your `MM.z64` ROM and it will
   generate `mm.o2r`.
4. Later launches start straight into the game.

Open the in-game menu with the Back/Select/minus button or the Android back
gesture. Navigate with touch or a controller.

## Data folder

User data lives in `/storage/emulated/0/TerminaDS` by default. You can change
the location (including to an SD card) under **Settings > General**. Mods and
presets go in the corresponding folders inside the data folder.

Termina DS uses its own data folder, separate from a stock 2 Ship 2 Harkinian
install, so the two coexist without touching each other's saves.

## Building

The repository is self-contained — the engine and asset tooling are vendored, so
a plain clone builds with no submodules:

```bash
git clone git@github.com:flossbud/termina-ds.git
cd termina-ds
./tools/build-apk.sh          # builds the release APK in a Docker toolchain image
```

The build runs entirely inside a reproducible Docker image
(`docker/Dockerfile.android`) — NDK 26, CMake 3.30.3, JDK 17. The output APK is
at `Android/app/build/outputs/apk/release/app-release.apk`. It is debug-signed by
default; see `tools/make-keystore.sh` for release signing. The build never
bundles a ROM or `mm.o2r`.

## Lineage & license

Termina DS descends from the 2 Ship 2 Harkinian project and its Android port:

- [HarbourMasters/2ship2harkinian](https://github.com/HarbourMasters/2ship2harkinian)
- [Waterdish/2ship2harkinian-Android](https://github.com/Waterdish/2ship2harkinian-Android)
- [linkzenic/2ship2harkinian-Android](https://github.com/linkzenic/2ship2harkinian-Android)

Those projects are released under CC0-1.0 (public domain dedication). Termina DS
ships no copyrighted Nintendo assets.

## Known issues

- Orientation handling is limited by SDL on Android
  ([SDL#6090](https://github.com/libsdl-org/SDL/issues/6090)).
- Near-plane clipping can occur when the camera is very close to walls.
