# :core-shipwright - The Legend of Zelda: Ocarina of Time (N64) via Ship of Harkinian

Ship of Harkinian (SoH, decomp-based **native port**, not an emulator) through the community Android fork
`please-be-nice/Shipwright-android` (branch `android-port-923`, base SoH 9.2.3). The upstream project has no Android code.
You need your own ROM (`.z64` / `.n64` / `.v64`, NTSC-U 1.0/1.1/1.2, PAL, Master Quest); nothing from the game is bundled.

## What is where

| Thing | Location |
|---|---|
| Android module (this dir) | `nebulos/core-shipwright`, coreId `shipwright`, package `com.noryan.nebulos.core.shipwright`, `object ShipwrightCore` + `register()` |
| Engine source (local, never pushed) | `/Users/ry/Desktop/claude/shipwright-src`, branch `nebulos-local` (+ `libultraship` submodule branch `nebulos-local`) |
| Patches | `core-shipwright/patches/{shipwright,libultraship}/` (base: shipwright `f8dc69741`, libultraship `3a9af4f`; restored 2026-11-21 with `git apply` on a clean checkout. The libultraship patch applies with `git apply`, not `git am`; the SDL2 library rename is the last patch of each series) |
| Gradle wiring | `settings.gradle.kts` `includeBuild("../shipwright-src/Android")` (module `com.dishii:app`, built as a library: `BUILD_AS_LIBRARY=true`) |
| Native libraries | `libsoh.so`, `libSDL2soh.so` (renamed from libSDL2.so 2026-11-21: the Sunshine core ships a libSDL2.so in the same APK) |
| SDL2 Java package | `org.libsdl.appsoh` (relocated from `org.libsdl.app`, which Eden/Azahar already ship); SDL's JNI names are patched at fetch time by `libultraship/cmake/dependencies/patches/nebulos-sdl2-java-package.cmake` |
| Process | `:shipwright` (`ShipwrightActivity`, a subclass of SoH's `MainActivity`) |

**Never push shipwright-src anywhere** (its push URLs are set to `DISABLED`).

## Build

`soh.o2r` (the game-independent asset archive) is not in git: generate it once on the Mac and drop it where Gradle packages it.

```
export PATH="$ANDROID_HOME/cmake/3.31.6/bin:$PATH"
cd shipwright-src
cmake -S . -B build-host -G Ninja -DCMAKE_BUILD_TYPE=Release -DNEBULOS_HOST_TOOLS_ONLY=ON \
      -DCMAKE_PREFIX_PATH=../shipwright-hostdeps/prefix -DCMAKE_CXX_FLAGS="-DFMT_CONSTEVAL="
cmake --build build-host --target GenerateSohOtr          # -> soh/soh.o2r (-DROMCORE_HOST_TOOLS_ONLY is now -DNEBULOS_HOST_TOOLS_ONLY)
cp soh/soh.o2r Android/app/src/main/assets/soh.o2r
cd ../nebulos && ./gradlew :app:assembleDebug             # first native build ~10 min
```
(`../shipwright-hostdeps/prefix` holds the host-tool dependencies built from source: SDL2 2.30.8, GLEW 2.2.0, libpng 1.6.43,
libzip 1.10.1, tinyxml2 10, nlohmann_json 3.11.3. `-DFMT_CONSTEVAL=` works around the bundled fmt on the very new Xcode libc++.)
The library's debug variant builds the native code as Release (-O3): at -O0 the game is far too slow. The app minifies even debug
builds, so `Android/app/consumer-rules.pro` keeps `org.libsdl.appsoh.**` and `com.dishii.soh.**` (JNI looks them up by name).

## UI ownership (Nebulos owns every pixel that is not the game)

`ShipwrightActivity` sets `NEBULOS_EMBED=1` before the native library starts (`Ship::Mobile::NebulosEmbedded()`):
* the stock touch overlay (on-screen buttons, eye toggle) is never created; touch is swallowed by the Activity;
* SoH's ImGui menu cannot open (`Gui::DrawMenu` ignores ESC / controller Back); no Java dialogs (`showAlertDialog` is a no-op,
  file picker disabled); every SoH toast (`Notification::Emit`) is dropped; the SoH config pins notifications/autosave toasts off;
* the first-launch ROM -> `oot.o2r` conversion runs headless (`NebulosExtract` in `OTRGlobals.cpp`, no popups, no file picker,
  no ImGui frames) with its progress sent to Nebulos's loading screen (event 3 -> `Hooks.setLoadingProgress`); SoH errors
  (`Extractor::ShowErrorBox`, unsupported ROM) become Nebulos's blocking error panel (event 2);
* `onGameReady` fires after 6 game frames (event 0); the shared menu, notices, errors, hotkeys and Back handling come from
  `NebulosCoreHost.install`.

## Defaults (`ShipwrightFiles.PINNED`, written into `shipofharkinian.json` on every launch)

* **60 fps**: `gSettings.InterpolationFPS=60` (frame interpolation; the game logic stays at its native 20 Hz, game speed unchanged),
  `MatchRefreshRate=0`. Measured on the Nova: 60.0 fps presented (SurfaceFlinger latency), intro timing identical to the 20 fps setting.
* Vanilla gameplay; no cheats or enhancements are enabled by the core.

## Input

`InputRouter -> ShipwrightSession.injectButton/injectAxis -> MainActivity.setButton/setAxis (JNI) -> SDL2 virtual joystick`
(libultraship `attachController`, standard SDL layout). Mapping: A/B 1:1, **L1 = L, R1 = R, L2 or R2 = Z**, Start, D-pad,
left stick = control stick, right stick = the four C buttons, **X = C-right, Y = C-down** (also on the right-stick axes); Select/L3/R3 unused. The physical controller never reaches SDL
(`ShipwrightActivity` consumes every key and joystick motion).

## Menu / session

Back opens the shared menu (Close Game, Change Controller Mapping, Set Hotkeys). **Save/Load State are N/A** (native port, no machine
state). Pause = the game thread blocks at the next frame boundary (`NebulosOnGameFrame`). Close Game = release input, pause, finish
the Activity, end the `:shipwright` process.

## Portable saves

`<filesDir>/shipwright/Save` is a symlink to `<root>/shipwright/Save` (`file1.sav`, `global.sav`, backups), see `nebulos-api/SAVEDATA.md`.
Local only: `shipofharkinian.json`, `oot.o2r` / `oot-mq.o2r` (rebuilt from the ROM when the core version changes), `soh.o2r`,
`assets/extractor`, `gamecontrollerdb.txt`, `logs/`.

## Routing

`GameSpecificCores` claims N64 games whose title matches "Ocarina of Time" for this core (auto choice); Mupen64Plus-Next stays
selectable per game. `ShipwrightCore.sniff` also recognises the ROM header (`THE LEGEND OF ZELDA` + `CZL`) in any byte order.

## Known limits

* Needs a real file path (Nebulos holds All files access).
* No save states, no fast forward, no rumble forwarding through Nebulos (SoH's own rumble still drives the physical pad).
* SoH's enhancements, cosmetics, randomizer and mods UI are unreachable by design.

## Licenses

* Ship of Harkinian (HarbourMasters) and libultraship: see their repositories (source-available; no copyrighted Nintendo data is bundled,
  the user's ROM supplies all game assets). SDL2 (zlib), ImGui (MIT), spdlog (MIT), StormLib (MIT), libopus (BSD-3).
* Nebulos itself is GPLv3.

## Restored 2026-11-21 (state after the restore)

Removed 2026-11-10 to shrink the app, restored on request. Rebuild recipe: clone `please-be-nice/Shipwright-android` (branch `android-port-923`, commit `f8dc69741`)
into `../shipwright-src` with submodules, branch `nebulos-local`, apply the patches in `patches/`, build `soh.o2r` as above, then
`source tools-build-env.sh && ./gradlew :app:assembleRelease` (about 18 min cold). APK cost: `libsoh.so` 37.9 MB + `libSDL2soh.so` 1.4 MB + `soh.o2r` 4.6 MB uncompressed.
Tested on the Nova (release APK): the first-launch ROM conversion runs in-process (no Torch crash here, unlike core-sm64); boot, title, file select
(File 1 from the old portable saves loads), the new-game intro, Kokiri Forest walking, A/B/Start/R1 and the left stick; 60 fps (`fps.sh`).
Test helpers: `fps.sh <layer> [s]` (SurfaceFlinger present times), `press.sh <evdev code> [hold ms] [n] [gap s]` (sendevent button taps; hold at least 120 ms).
Not tested: Z / C-button mapping in play, Master Quest, PAL ROM.
