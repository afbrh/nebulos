# :core-jak - Jak and Daxter trilogy (PS2) via OpenGOAL

OpenGOAL (github.com/open-goal/jak-project, **ISC**) turns the games' decompiled GOAL code back into a native program (it is not an
emulator). This core is the **default** for the Jak and Daxter discs (Jak and Daxter: The Precursor Legacy, Jak II, Jak 3, NTSC-U /
PAL / NTSC-J serials in `JakCore.SERIALS`); ARMSX2 stays selectable per game in the core picker. You need your own disc dumps.
Nothing from the games is bundled.

| Thing | Location |
|---|---|
| Android module (this dir) | `nebulos/core-jak`, coreId `jak`, `object JakCore` + `register()` |
| Engine source (local, never pushed) | `/Users/ry/Desktop/claude/jak-android-src`, branch `android-port` (origin is the upstream project) |
| Native build + staging | `core-jak/native/build.sh` -> `src/main/jniLibs/arm64-v8a/{libjak_main.so,libjak_extractor.so}`, `src/main/assets/jak/` (all git-ignored, regenerated) |
| Process | `:jak` (`JakActivity`, a subclass of SDL3's `SDLActivity`) |
| SDL3 Java package | `com.noryan.nebulos.core.jak.sdl` (renamed from `org.libsdl.app`: ARMSX2 ships a different SDL3 release); native side: static SDL3 in `libjak_main.so` built with `-DSDL_JAVA_PREFIX=... -DSDL_JAVA_CLASS_DIR=...` (`-DJAK_NEBULOS=ON`) |

## Native changes in jak-android-src (branch android-port)

Android/arm64 port (NDK 27, GLES 3.2 renderer with offline-translated shaders, AAudio, signed `char`, ARM64 GOAL backend that upstream
already has) plus the Nebulos embedding layer: `game/nebulos_embed.cpp` + `game/nebulos_host.h` (events to `JakActivity.onNebulosEvent`:
0 = game ready, 1 = notice, 2 = fatal; SDL virtual gamepad "Nebulos Pad" 0x5243/0x0001 fed through `JakNative.nativeButton/Axis`; host
pause = the display loop stops presenting so the game thread blocks on vsync, and the audio tick outputs silence),
`OPENGOAL_SAVE_DIR` (memory-card directory, `common/util/FileUtil.cpp`), SDL's JNI package made overridable by compile definition.

## First launch of a game: on-device import

The game code is compiled and the disc's data converted by OpenGOAL's own extractor, shipped as `libjak_extractor.so` (a PIE executable
in jniLibs, started from the app's native-library dir): `libjak_extractor.so -g jakN -e -d -c --instruction-set arm64 --proj-path <dir> <iso>`
(extract the disc, decompile, compile with the ARM64 backend). It prints `[PROGRESS] stage=...` and `[ NN%] [goalc ]` lines that drive
the loading-screen text. The extractor needs `goal_src`, `decompiler/config`, `game/assets` and `custom_assets` under `<dir>`: they are
staged into the APK assets by `native/build.sh` and copied out once per `JakData.DATA_VERSION` (134 MB on disk, ~21 MB in the APK).
Measured on the Nova (12 GB): Jak 1 ~45 s / 3.1 GB peak RAM, Jak II ~2.3 min / 6.9 GB, Jak 3 ~2.7 min / 7.7 GB (all in one pass, the
loading screen shows progress). `iso_data` and `decompiler_out` working folders are deleted afterwards. `JakActivity.getArguments()` runs on SDL's own thread before the engine starts,
so the import blocks there while the Nebulos loading screen shows the extractor's progress. Output: `<externalFilesDir>/jak/out/<game>/
{iso,fr3,obj}` (Jak 1 ~1.7 GB, Jak II ~5.4 GB, Jak 3 ~5.8 GB), marker `.nebulos_extracted_version`. Later launches start at once.
Support files from the APK assets (`game/assets`, GLSL ES shaders) are copied to `<externalFilesDir>/jak` once per `JakData.DATA_VERSION`.
Close Game during an import kills the extractor child process too.

## Fixed launch configuration

`gk --proj-path <dir> --config-path <dir>/user --game jakN -- -boot -fakeiso` plus env `OPENGOAL_SAVE_DIR`,
`SDL_GAMECONTROLLER_IGNORE_DEVICES_EXCEPT=0x5243/0x0001`, `SDL_JOYSTICK_HIDAPI=0`. Nebulos owns every pixel that is not the game: loading
screen, Back menu, notices, errors. The games' own menus (title, file select, pause, settings) are game content and stay.

## Input

`InputRouter -> JakSession.injectButton/injectAxis -> JakNative (JNI) -> SDL virtual gamepad`. Nebulos's PS2 rule: A = Circle, B = Cross,
X = Triangle, Y = Square; L1/R1, analog L2/R2, sticks, L3/R3, Start, Select, D-pad 1:1. The physical controller never reaches SDL
(`JakActivity.dispatchKeyEvent` / `dispatchGenericMotionEvent` consume it).

## Menu / session / saves

Back opens the shared menu. **Save/Load State are N/A** (native port, no machine state: "Jak and Daxter (OpenGOAL) uses the game's own
saves."). Pause freezes presentation, game thread and audio. Close Game = release input, pause, finish, end the `:jak` process.
Saves: memory-card files in `<SaveRoot>/jak-core/<jak1|jak2|jak3>/saves` (portable, see `nebulos-api/SAVEDATA.md`); settings stay in `<proj>/user`.

## Known limits

* Needs a plain 2048-byte-sector `.iso` on a real path (no CHD / `.bin` / `.cue` / compressed images).
* The import needs ~5-14 GB free per game while it runs and several minutes.
* Jak X is not supported by OpenGOAL.
* Rumble is not forwarded; no touch controls.

## Licenses

OpenGOAL: ISC. SDL3: zlib. Other statically linked third parties are those of jak-project's `third-party/` (cubeb ISC, curl, fmt MIT,
imgui MIT, glad, zstd BSD, libco ISC, ...). Nebulos is GPLv3; these are GPL-compatible. No game data is bundled.
