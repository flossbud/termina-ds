# 2 Ship 2 Harkinian Android

An unofficial Android fork of 2 Ship 2 Harkinian, based on Waterdish's Android port and the HarbourMasters upstream project.

This fork keeps the Android build moving while preserving the original project's expectations: no copyrighted game assets are included, and users must provide their own legally obtained copy of game.

Original Android port: https://github.com/Waterdish/2ship2harkinian-Android  
Original upstream project: https://github.com/HarbourMasters/2ship2harkinian

## Current Release

2 Ship Android 1.2.2

Android package id: `com.twoshipfork.mm`  
App label: `2 Ship 2 Harkinian`

## What's Different In This Fork

- Restores save compatibility with newer upstream 2 Ship 2 Harkinian saves.
- Adds upstream cheat and enhancement features that were missing from the Android port.
- Adds modifier button support
- Adds support for speed modifier controls.
- Reduces the Android menu scale so the settings menus are easier to navigate.
- Builds arm64 release APKs with Android-focused settings.

## Installation

1. Install the APK from this fork's GitHub Releases.
2. Open the app once. It will create the folder it needs for your ROM. Allow file permissions, then close and reopen the app.
3. When prompted, choose to generate an O2R file and select your legally obtained rom.
4. After extraction, future launches should go straight into the game.

Use the Back/Select/- controller button, or the Android back gesture/button, to open the Enhancements menu. Use touch controls to navigate menus.

## FAQ

**Where do I add mods?**  
Use the `2S2H` folder at the root of the device.

**Why is it immediately crashing?**  
Try deleting and re-extracting the O2R file, usually named `mm.o2r`.

**The game opened once, but now it is just a black screen.**  
Reinstall and do not raise MSAA above 1 in `Settings -> Graphics`.

**Gyro aim?**  
It works. Press any controller button when the app asks for input. It will default to the phone's gyro if the controller does not support gyro.

**My controller is not doing anything.**  
Close the Enhancements menu. If the Enhancements menu is not open, open it with the Android back button and check `Settings -> Controller -> Controller Mapping`. If the controller is detected, press refresh.

## Known Bugs

- Orientation lock does not work because of an upstream SDL issue: https://github.com/libsdl-org/SDL/issues/6090
- Near-plane clipping can appear when the camera is close to walls.
- Picto box images may render black.

## Build Notes

1. Open the `Android` project in Android Studio.
2. Install NDK 26 and CMake 3.30.3 through Android Studio's SDK Manager.
3. Build the app from Android Studio, or run the Gradle release build.

This repository contains Android fixes inside the `libultraship` submodule. See [PUBLISHING.md](PUBLISHING.md) for the recommended fork setup.

If the submodule fork is not available yet, apply `patches/libultraship-android-fork.patch` after initializing submodules:

```sh
git submodule update --init --recursive
git -C libultraship apply ../patches/libultraship-android-fork.patch
```

## Legal And Credits

This is an unofficial community fork. It is not affiliated with or endorsed by HarbourMasters or Waterdish.

No ROM, ROM-derived O2R/OTR file, extracted game assets, or copyrighted content is included in this repository. Release APKs may include the no-ROM `2ship.o2r` support archive generated from the port's custom assets. No piracy is condoned or encouraged.

See [NOTICE.md](NOTICE.md) and the included license files for attribution.
