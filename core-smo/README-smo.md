# :core-smo - Super Mario Odyssey 1.0.0 (Switch) as a native recomp

The TOTKDroid / BOTWRecompAndroid runtime (MIT, (c) Peiikai) with Odyssey Recomp's game support (rwwo8/OdysseyRecomp, MIT, same LAS Recomp lineage).
Native-code mode: the game's own ARM64 code runs on the CPU (nothing is compiled on the device); NVN -> Vulkan through the Turnip driver.

| Thing | Where |
|---|---|
| Runtime source (local, never pushed) | `../smorecomp-src`, branch `smo` (a clone of `../totkdroid-src` with TOTK -> SMO renames) |
| SMO changes in the runtime | `runtime/loader.c` (program id 0100000000010000, build id 3ca12dfa..., subsdk0 loaded as module 2), `runtime/main.c` (subsdk0 init before main), `runtime/native.c` (fix: sead heaps lock always on), `runtime/nvn/objects.c` (NVN API 53.207, [57] = 0x1000), `runtime/paths.h` (GAME_V_SMO 100) |
| Native library | `libsmo_main.so` (CMake from this module, SDL3 static, package `com.smorecompandroid`) |
| Process | `:smo` (`SmoActivity`) |
| Game files | First launch, `SmoXciDump`: libhactool.so (hactool, ISC, built from `../../hactool-src` by this module's CMake) reads the user's XCI with the user's own `<externalFilesDir>/keys/prod.keys`, takes the cartridge's BASE program NCA (the largest) and writes `<externalFilesDir>/native-dumps/0100000000010000/{exefs,romfs}` + `nebulos-dump.json`; main's build id must be 1.0.0's. About 2 minutes, once. (Eden's dump would apply the cartridge's update, a version this runtime does not run.) XCI only for now |
| Saves | `<files>/smo/save` -> `<_Nebulos>/smo-core/save` |

Only game version 1.0.0 (main build id 3ca12dfaaf9c82da). Status 2026-11-20: boots, plays from the title screen into Cap Kingdom at a steady 60 fps (60 is the default: the game logic is tied to the frame rate, 30 ran it at half speed). Untested: later kingdoms, sound quality, the Back menu, performance in heavy scenes.
