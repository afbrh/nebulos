# :core-smo - Super Mario Odyssey 1.0.0 (Switch) as a native recomp

The TOTKDroid / BOTWRecompAndroid runtime (MIT, (c) Peiikai) with Odyssey Recomp's game support (rwwo8/OdysseyRecomp, MIT, same LAS Recomp lineage).
Native-code mode: the game's own ARM64 code runs on the CPU (nothing is compiled on the device); NVN -> Vulkan through the Turnip driver.

| Thing | Where |
|---|---|
| Runtime source (local, never pushed) | `../smorecomp-src`, branch `smo` (a clone of `../totkdroid-src` with TOTK -> SMO renames) |
| SMO changes in the runtime | `runtime/loader.c` (program id 0100000000010000, build id 3ca12dfa..., subsdk0 loaded as module 2), `runtime/main.c` (subsdk0 init before main), `runtime/native.c` (fix: sead heaps lock always on), `runtime/nvn/objects.c` (NVN API 53.207, [57] = 0x1000), `runtime/paths.h` (GAME_V_SMO 100) |
| Native library | `libsmo_main.so` (CMake from this module, SDL3 static, package `com.smorecompandroid`) |
| Process | `:smo` (`SmoActivity`) |
| Game files | `<externalFilesDir>/native-dumps/0100000000010000/{exefs,romfs}` + `nebulos-dump.json` (displayVersion 1.0.0). Eden's own dump applies the cartridge's update (not 1.0.0), so the base game was extracted on the Mac (hactool) and pushed: `adb push` into Android/data is refused, push to /sdcard/smo-tmp then `adb shell cp -r` |
| Saves | `<files>/smo/save` -> `<_Nebulos>/smo-core/save` |

Only game version 1.0.0 (main build id 3ca12dfaaf9c82da). Status 2026-11-20: boots, plays from the title screen into Cap Kingdom at a steady 60 fps (60 is the default: the game logic is tied to the frame rate, 30 ran it at half speed). Untested: later kingdoms, sound quality, the Back menu, performance in heavy scenes.
