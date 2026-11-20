# core-sunshine: Super Mario Sunshine (GameCube, US rev 0 = GMSE01) via the SMS PC port

chasem-dev/sms-pc-port (MIT; built from the doldecomp/sms decompilation) as a one-game Nebulos core; chasem-dev/sms-android's launcher/GameActivity layer is replaced by
`SunshineActivity` (SDL2 activity in the `:sunshine` process). A native port, not an emulator. Claims GMSE01 (`GameSpecificCores.isSuperMarioSunshine`); Dolphin stays selectable.

## Build (this Mac)
1. `../../sms-src/sms-android` = clone of chasem-dev/sms-android with its submodules (`sms-port` + `sms-port/decomp`), never pushed. Do not run its build.sh: the core runs the same CMake.
2. SDL2 2.32.10 source in `sms-port/build/deps` (sha256 5f5993c5...); `../../sms-src/rename_sdl2.sh` renames its Java package to `dev.chasem.sms.sdl` (the APK carries other cores' `org.libsdl.app`);
   the untouched copy is `SDL2-2.32.10-orig`.
3. `./gradlew :app:assembleRelease` (CMake 3.31.6, NDK 28.2.13676358): builds libsms.so, libsmsbootstrap.so, libSDL2.so from `sms-port/CMakeLists.txt`.

## How it works
- Disc: the user's ISO/GCM/CISO is passed straight to the port (`--disc`); no copy. (The standalone app copies it into its files; the port reads CISO itself.) RVZ is not supported by the port.
- Saves: memory card files in `_Nebulos/sunshine-core/card-a` (symlinked from `files/saves/card-a`, `SMS_SAVE_DIR`). Settings in `files/game/settings.txt` (port defaults: 60 fps, English).
- Input: Nebulos router -> `SunshineSession` -> `dev.chasem.sms.sdl.TouchController` (the port's own extra SDL controller; SDL's enumeration of real controllers is skipped so it is the only pad). By the GameCube's labels: A/B/X/Y, Start, R1 = Z, L2/R2 triggers, D-pad, both sticks.
- Verified on the Nova: boots to the title and "Select data", ~60 fps there, controller A skips the intro, D-pad moves Mario, Nebulos Back menu over the game.
- Not tested: gameplay fps in Delfino Plaza and heavy scenes (the user's AYN-class reports say near 60), saves written to the card, texture packs / language packs (the standalone launcher's downloads are not part of this core), Eclipse mod.
