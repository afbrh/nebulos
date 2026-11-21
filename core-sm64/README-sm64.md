# core-sm64: Super Mario 64 (Nintendo 64, US or JP ROM) via Ghostship

Ghostship (github.com/HarbourMasters/Ghostship, 3.0.0 "Nautilus", MIT; libultraship + Fast3D, Torch) as a one-game Nebulos core. A native port of the SM64 decompilation, NOT an emulator.
Nothing from the game is bundled: Nebulos hands the user's ROM over, the SHA-1 is checked (US `9bef1128717f958171a4afac3ed78ee2bb4e86ce`, JP `8a20a5c8...`, big-endian .z64) and on the
first launch Torch converts it into `sm64.o2r` (on the device, as a child process, ~4 s on the Nova, with a progress bar on Nebulos's loading screen; cached in `files/sm64`, runs once). Licenses: see `NOTICE`.

## What is where
| Thing | Location |
|---|---|
| Module | `nebulos/core-sm64`, coreId `sm64`, package `com.noryan.nebulos.core.sm64`, `object Sm64Core` + `register()`; routed by TITLE (`GameSpecificCores.isSuperMario64`, N64 system only; the libretro N64 cores stay selectable) |
| Engine source (local, never pushed) | `/Users/ry/Desktop/claude/sm64-src`, branch `nebulos-local` (+ `libultraship` branch `nebulos-local`; push URLs of the repo and both submodules are a dummy) |
| Native libs | `libsm64_main.so` (was `libmain.so`) + `libSDL2_sm64.so` (was `libSDL2.so`), arm64-v8a only, `c++_static`; built by `sm64-src/nebulos/build_native.sh` into `sm64-src/nebulos/jniLibs/arm64-v8a` |
| SDL2 Java glue | `com.noryan.nebulos.core.sm64.sdl` (renamed from `org.libsdl.app` by `sm64-src/nebulos/rename_sdl.sh`; the JNI names in libSDL2_sm64.so follow), compiled into this module |
| Processes | `:sm64` (game, `Sm64Activity`); the one-time Torch extraction is a child process of it: `libtorch_exec.so` = Torch built as a standalone arm64 executable (`sm64-src/nebulos/build_torch_exec.sh`), run from the native library folder with `o2r <rom> -u 3.0.0`. Torch linked INTO the engine library (Ghostship's own `nativeGenerateGameArchive`) crashed on the Nova (SIGSEGV freeing parsed paintings, ~12 s in) while the same Torch sources as a standalone executable run clean (also under ASan on the Nova), so the in-library path is not used |
| Game data | `files/sm64/` (own folder): `config.yml`, `assets/` (Torch recipes), `ghostship.o2r` (unpacked from `assets/sm64/sm64data.zip`), `sm64.o2r` (extracted once, cached here), config/logs, `shadercache/opengl` |
| Saves | `_Nebulos/sm64-core/saves` (`save_N.json`), `files/sm64/saves` is a symlink to it (`RecompSaveData.link`) |

## Edits to Ghostship (all in `nebulos-local`)
- CMake: Android output `sm64_main`, SDL2 output `SDL2_sm64`, `ENABLE_SCRIPTING OFF` on Android (no TCC/mods scripting), build with `-DUSE_SATELLA=OFF -DUSE_NETWORKING=OFF` (no online relay/mbedTLS/ixwebsocket); no INTERNET permission anywhere.
- libultraship `Context.cpp`: `GHOSTSHIP_DATA_DIR` overrides the Android app/bundle directory (`files/sm64`); `Saves.cpp` already honours `GHOSTSHIP_SAVES_DIR`.
- `AndroidBridge.cpp`: JNI renamed to `Sm64Assets` (unused now, see Processes); the Mods-button hook is gone.
- `Engine.cpp` / `GhostshipMenuSettings.cpp`: interpolation default 60 fps on Android (the game logic is 30 fps; the port interpolates), otherwise it is locked at 30.
- `nebulos/nebulos_vpad.c`: the ONE SDL2 virtual game controller (vendor 0x5243 / product 1) Nebulos feeds (copy of core-echoes' SDL3 one). Real controllers are hidden from SDL (`SDL_GAMECONTROLLER_IGNORE_DEVICES_EXCEPT`).
- `TouchControls.cpp`: the engine's own touch overlay is off by default on Android (Nebulos is controller-first).
- Not used from Ghostship's Android app: LauncherActivity / document picker, Mods / Saves activities, ModStore, the update flow, armeabi-v7a.

## Build
```
cd /Users/ry/Desktop/claude/sm64-src
./nebulos/rename_sdl.sh                                   # SDL2 release-2.32.10 clone (../sm64-sdl2-orig) -> nebulos/deps/SDL2 with the Java package renamed
cmake -S Torch -B Torch/cmake-build-release -G Ninja -DCMAKE_BUILD_TYPE=Release -DBUILD_SM64=ON -DBUILD_NAUDIO=ON -DBUILD_MK64=OFF -DBUILD_SF64=OFF -DBUILD_FZERO=OFF -DBUILD_MARIO_ARTIST=OFF -DBUILD_PM64=OFF -DBUILD_BK64=OFF -DBUILD_OOT=OFF && cmake --build Torch/cmake-build-release
cp -r libultraship/src/fast/shaders/. port/shaders/ && Torch/cmake-build-release/torch pack port ghostship.o2r o2r -u 3.0.0     # the engine archive (no ROM needed)
./nebulos/build_native.sh                                 # libsm64_main.so + libSDL2_sm64.so: NDK 29.0.14206865, CMake 3.31.6; ~6 min cold; FetchContent pulls the deps from GitHub
./nebulos/build_torch_exec.sh                             # libtorch_exec.so (Torch for Android, the first-launch converter)
cd ../nebulos && source tools-build-env.sh && ./gradlew :app:assembleRelease
```
Only the Mac-side Torch is used to pack `ghostship.o2r`; the user's ROM is never touched by the build.

## Input (Nova)
Nebulos router -> `Sm64Session` -> one SDL2 virtual controller -> Ghostship's default libultraship mapping: A -> N64 A (Y too), B -> N64 B (X too), left stick, L2/L1 -> Z, R2/R1 -> R, right stick -> C buttons,
Start -> Start, D-pad. A press is held at least 70 ms (the game samples the controller once per 30 fps logic frame; a shorter tap would be lost). Touch is not used (the engine's overlay is off).

## Verified on the Nova (RP Nova 12 GB, release APK)
Listed under RECOMPILED, starts from the list; first launch: bundled data unpack + ROM hash + Torch conversion ~4 s, game on screen ~6 s after the tap; warm launch ~2 s; title, file select, Peach's letter,
castle fly-over, Mario on the castle grounds (messages, walking, jump, punch); 60 fps (SurfaceFlinger present times, see sm64-research/PROGRESS.md); Back menu over the game, Close Game returns to the list and ends the process;
save written to `_Nebulos/sm64-core/saves/save_0.json`.
Not verified: the JP ROM, `.n64` / `.v64` byte orders (not accepted: big-endian .z64 only), the right stick as C buttons (no way to inject it over adb), long sessions, other levels, controller remapping UI, mods (not built).
