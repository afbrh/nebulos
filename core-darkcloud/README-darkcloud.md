# :core-darkcloud - Dark Cloud (PS2, USA) via Chronicle

Chronicle (github.com/TheMoonPeople/Chronicle) is the Dark Cloud decompilation plus a native SDL3 + Vulkan port (not an emulator). One-game core, routed
by the disc serial `SCUS-97111` (ARMSX2 stays selectable). Chronicle also reads the PAL disc; add its serial to `DarkCloudCore.SERIALS` to route it.

| Thing | Where |
|---|---|
| Engine source (local, never pushed: origin is the upstream project) | `../Chronicle`, branch `nebulos-local` (+ `tools/kazarov` branch `nebulos-local`) |
| Native library | `libchronicle_main.so`, built by Chronicle's CMake (`PLATFORM=ANDROID`) from this module; SDL3 static (renamed Java package, `recomp-common/build_sdl_static.sh`) |
| Local patches | Kazarov: `vkCmdWriteTimestamp2` shim. Chronicle: static-SDL build option, 60 fps default on Android, never delete the user's own disc image after import |
| Process | `:darkcloud` (`DarkCloudActivity`, extends the renamed `SDLActivity`) |
| Data | `filesDir/darkcloud/data` (extracted from the ISO on first launch, read in place from its real path); saves in `<_Nebulos>/darkcloud-core/save` |
| Input | Nebulos router -> `RecompSession` -> SDL virtual gamepad. Face buttons: A = Cross, B = Circle, X = Square, Y = Triangle (`DarkCloudInput`; the other recomp cores map by position) |

Build prerequisites: NDK 27.0.12077973, CMake 3.31.6, `~/android-dev/bin/glslangValidator`, and `../botwrecomp-src/android/deps/out-static/lib/libSDL3.a`.
An existing install is signed with the release key: use `./gradlew :app:assembleRelease` to update in place.
