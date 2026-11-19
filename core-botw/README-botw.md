# :core-botw / :core-totk - Breath of the Wild and Tears of the Kingdom (Switch) via the recomps

BOTWRecompAndroid and TOTKDroid (both MIT, (c) Peiikai) run the games' own ARM64 code natively on the CPU with NVN reimplemented on Vulkan
(not emulators). Like Dusklight for Twilight Princess: each is a ONE-GAME core that is the default for its game (Eden stays selectable).

| Thing | BotW | TotK |
|---|---|---|
| Module | `core-botw` | `core-totk` |
| Source (local, never pushed) | `../botwrecomp-src` branch `nebulos-local` | `../totkdroid-src` branch `nebulos-local` |
| Native lib / process | `libbotw_main.so` / `:botw` | `libtotk_main.so` / `:totk` |
| Supported game version | 1.0.0, 1.6.0, 1.8.2, 1.9.0 | 1.4.3 |
| Title id | 01007EF00011E000 | 0100F2C0115B6000 |

Shared: `recomp-common` (SDL3 Java package `com.noryan.recompsdl`, the SDL virtual gamepad `RecompPad` / `recomp_vpad.c`, `RecompSession`,
`RecompGameFiles`). SDL3 is linked STATICALLY into each library; `libshaderc_shared.so` and FFmpeg's `libav*.so` are shared by both.

## Game files
The runtimes want loose `exefs/` and `romfs/`. On the first launch `RecompGameFiles` starts Eden's `EdenDumpService` (core-eden patch 0009) which
dumps them from the user's XCI/NSP to `<_Nebulos>/native-dumps/<TITLEID>/` (marker `nebulos-dump.json` = Eden's result JSON incl. `displayVersion`).
The data root `<filesDir>/<core>/[v160|v182|v190]/extracted/` gets a copy of exefs and `romfs.link` (a text file naming the dump's romfs: used in
place, no copy). Mode: `LARECOMP_NATIVE=1`, `LARECOMP_NO_LAUNCHER=1`, `LARECOMP_DATA_ROOT`.

## Build from scratch
```
cd botwrecomp-src && git submodule update --init --recursive --depth 1      # libadrenotools (+ same in totkdroid-src)
cd android/deps && ./build_deps_local.sh                                     # SDL3 + shaderc + headers (copy of build_deps.sh for cmake 3.31.6 / macOS tar)
ln -s .../botwrecomp-src/android/deps/out  totkdroid-src/android/deps/out    # (same pinned deps)
cd ../../../totkdroid-src/android/deps && ./build_ffmpeg_local.sh            # FFmpeg 9.0.2 (Opus for TotK)
cd nebulos/recomp-common && ./build_sdl_static.sh                            # renamed static SDL3 -> botwrecomp-src/android/deps/out-static (+ Java sources)
cd nebulos && source tools-build-env.sh && nice -n 15 ./gradlew --max-workers=2 :app:assembleRelease
```
Patches: `core-botw/patches`, `core-totk/patches` (`git am` onto the upstream checkouts).

## Not done / known gaps
- No host pause (the menu opens over the running game); no save states (the games' own saves); saves live in the runtime's data root (not yet in `_Nebulos/*-save-data`).
- TotK needs a lot of RAM: the runtime reserves ~12.8 GB of guest address space.
