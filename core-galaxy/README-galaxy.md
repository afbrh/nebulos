# core-galaxy: Super Mario Galaxy (Wii, revision 0) via Nebula

Nebula (GPL-3.0, keepvaibin/nebula: the SMGCommunity Petari decompilation compiled for the device + an SDL3/Vulkan renderer) as a one-game Nebulos core.
A native port, not an emulator. Claims the discs RMGE01 / RMGP01 / RMGJ01 (`GameSpecificCores.isSuperMarioGalaxy`); Dolphin stays selectable per game.

## Build (this Mac)
1. `../../smg-nebula` = clone of keepvaibin/nebula with two local commits (the SDL virtual pad `platform/sdl/nebulos_vpad.c`, and the SDL Java package renamed to
   `dev.nebula.sdl` by `../../smg-nebula-hosttools/rename_sdl.sh` because the APK also carries other cores' `org.libsdl.app`). Never pushed.
2. Deps in `../../smg-nebula-deps` (SDL3, glslang, Vulkan headers, volk, VMA, libadrenotools); host shader tools from `../../smg-nebula-hosttools` (CMake project, macOS).
3. `../../smg-nebula-hosttools/build_android.sh` cross-builds libsmg_game(_plain).so, libnebula_main.so (Nebula's libmain.so renamed: Dusklight owns libmain.so),
   libSDL3.so, libnebula_mods.so, libnebula_import.so (Rust) and the adrenotools hooks into `smg-nebula/platform/android/jniLibs/arm64-v8a`; this module packages them.
   NDK 28.2.13676358, JDK 17 for Gradle, `rustup target add aarch64-linux-android`.
4. `./gradlew :app:assembleRelease`.

## How it works
- `GalaxyActivity` (`:galaxy` process) extends Nebula's `GameActivity`. `getArguments()` runs on SDL's thread before the game starts: it links `files/saves` to
  `_Nebulos/galaxy-core/saves`, and on the first launch sets the game up from the user's disc image (`NebulosBridge` -> Nebula's importer, ~70 s, 3.9 GB `game.pak` in
  the app's files; ISO/RVZ/WIA/WBFS/CISO/GCZ).
- Input: Nebulos's router -> `GalaxySession` -> SDL virtual gamepad (`GalaxyPad`, `nebulos_vpad.c`); real controllers are hidden from SDL. Touch still goes to Nebula's own handling.
- Verified on the Nova: 60 steps/s, title -> file select -> new file with saves in `_Nebulos`, Nebulos Back menu over the game.
- Not tested: long play sessions, Nebula's touch HOME button next to the Nebulos menu, the optional Turnip driver, GC/Wii pointer feel with the right stick.
