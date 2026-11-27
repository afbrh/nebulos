# :core-darkchronicle - Dark Cloud 2 / Dark Chronicle (PS2, PAL SCES_511.90) via ChronicleTwo

**Parked since 2026-11-26** (user's choice): not built into the app, the disc runs in ARMSX2. The code, engine source, saves and extracted game data are kept. To restore: remove `darkchronicle` from `parkedCores` in `settings.gradle.kts` and add `"darkchronicle"` back to `CoreBootstrap.CORE_IDS`.

ChronicleTwo (github.com/TheMoonPeople/ChronicleTwo) is the PAL decompilation of Dark Chronicle plus a native SDL3 + Vulkan port (not an emulator). One-game core,
routed by the disc serial `SCES-51190`. ChronicleTwo is PAL-only: the NTSC-U disc (`SCUS_972.13`) is not claimed and stays on ARMSX2.

| Thing | Where |
|---|---|
| Engine source (local, never pushed: origin is the upstream project) | `../ChronicleTwo`, branch `nebulos-local` (+ `tools/kazarov` branch `nebulos-local`) |
| Native library | `libdarkchronicle_main.so`, built by `./build_native.sh` (CMake `PLATFORM=ANDROID` + Cargo for the Rust extractor), staged in `../ChronicleTwo/nebulos-jniLibs/arm64-v8a`; SDL3 static (renamed Java package, `recomp-common/build_sdl_static.sh`) |
| FFmpeg | `../ChronicleTwo/.kazarov/ffmpeg/android-arm64` (LGPL-only static libs): `ANDROID_NDK_HOME=<ndk> python3 -I tools/kazarov/tools/ffmpeg/kz-ffmpeg --out .kazarov/ffmpeg/android-arm64 --target android-arm64` |
| Local patches | CMake ANDROID platform; `android_extract.cpp` (JNI `nativeExtract` = `dc2data extract`); `main.cpp` SDL_main; ARM64 ELF fiber switch (`eekernel_threads.cpp`); `vkCmdWriteTimestamp2` forward; `KZ_ANDROID_EXECUTABLE`; kz-ffmpeg `android-arm64` target; Rust: no desktop UI, API-30 flags for the cxx crate |
| Process | `:darkchronicle` (`DarkChronicleActivity`, extends the renamed `SDLActivity`) |
| Data | `filesDir/darkchronicle/{data.zip,data.json,scripts,glyphs,fonts}` (extracted from the ISO on first launch, a stamp skips it later); saves + config in `<_Nebulos>/darkchronicle-core/save` |
| Input | Nebulos router -> `RecompSession` -> SDL virtual gamepad, face buttons by position like the other PS2 cores |

Build prerequisites: NDK 29.0.14206865, CMake 3.31.6 + Ninja (Android SDK), rustup with `aarch64-linux-android`, `~/android-dev/bin/glslangValidator` (16.6), the static SDL3 at
`../botwrecomp-src/android/deps/out-static`, the Android FFmpeg above. The APK is built as usual (`./gradlew :app:assembleRelease`, release key) after `build_native.sh`.

## Status (2026-11-22, Retroid Pocket Nova, release APK)
- Routes from the Nebulos library (RECOMPILED, serial SCES-51190). First launch: the port's extractor converts the disc in about 85 s (progress text on the loading screen; 2 conversion jobs, ISO memory-mapped, mimalloc as Rust's heap), later launches start in ~5 s.
- Boots through the language select, Level-5 logo, title and opening movie into New Game: costume select, then the playable prologue (Monica in the castle, 3D, combat, Game Over screen all seen). 60 fps (tick 60/60, 255 draws at the costume screen); 120 fps uncapped in cutscenes before the 60 fps cap default.
- Controls: Nebulos router -> SDL virtual pad works. The game polls once per frame, so a press must be HELD (>= ~100 ms): `adb shell input gamepad keyevent` is an instant down/up and is lost. For tests use `sendevent /dev/input/event7 1 305 1; ...syn; sleep 0.25; ... 1 305 0` (B = south = Cross = confirm in PAL; A = east = Circle = cancel; Start 315). The analog stick is ABS_X/ABS_Y (0/1). Menus only react to the stick/hat, not the BTN_DPAD keys.
- Not done / unverified: full play session, saves round trip (memory-card folders mc0/mc1 are in `_Nebulos/darkchronicle-core/save`), audio by ear, the NTSC-U disc (not supported by ChronicleTwo), a debug-free first-launch error path for a wrong disc, shader-compile stalls (21,860 pipelines build at start, ~1.5 s, then cached in pipeline_cache.bin).
- The native lib is built outside Gradle: run `./build_native.sh` before `./gradlew :app:assembleRelease`, and do not run two Gradle builds at once (shared `.build-lock`).
