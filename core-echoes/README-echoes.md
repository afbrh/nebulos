# core-echoes: Metroid Prime 2 Echoes (GameCube; G2ME01 / G2MP01, and the Trilogy discs R3ME01 / R3MP01 as Echoes) via the Echoes native port

The Metroid Prime 2 Echoes PC/Android port (Odrannnn's decompilation-based port; the author's repo is gone, work copy of a backup) as a one-game Nebulos core. A native port, NOT an emulator
(SDL3 + Aurora/Dawn on Vulkan + the game compiled for the device; the disc is read by `nod`).
**Legally grey: derived from Nintendo's executable, no license. Personal use only, never publish, never push.**

## Routing
`GameSpecificCores.isMetroidPrime2Echoes` accepts G2ME01, G2MP01, R3ME01, R3MP01 (the port plays the Trilogy disc's Echoes, "experimental"); only G2ME01 / G2MP01 are *claimed*
(`claims()`): the Trilogy stays with PrimeHack by default and Echoes is a per-game choice for it. The ROM is read in place (ISO/GCM/CISO/RVZ/WIA/GCZ/WBFS, no copy):
`EchoesActivity` writes `files/port_paths.txt` (`disc=<rom>`, `user=`, `cache=`, `env.MP2_TEXTURES`, `env.MP2_NO_DISC_DIALOG=1`) before the port's `onCreate`.

## Build (this Mac)
1. Work copy `../../prime-research/echoes-core-src` (copy of `echoes-build`, itself a clone + `../echoes-build.patch`); all our edits are in `../../prime-research/echoes-core-nebulos.patch` (git diff of the work copy)
   plus the new files `rename_sdl.sh`, `build_native.sh`, `platform/android/nebulos_vpad.c`. Deps are local copies in `build/android-deps` (FETCHCONTENT_SOURCE_DIR_*; SDL is the patched one).
2. `./rename_sdl.sh` regenerates the renames from the pristine `../echoes-build`: SDL3's Java package `org.libsdl.app` -> `dev.echoes.sdl` (sources of SDL, the vendored Java copy, the JNI names of the
   static SDL), `dev.encounter.aurora.AuroraSurface` -> `dev.echoes.aurora` (Dusklight carries the same class; JNI names in `extern/aurora/lib/window.cpp`), `MetroidPrime2Activity` made non-final,
   its glyph folder moved to `files/echoes/textures`.
3. `./build_native.sh` (NDK 29.0.14206865, CMake 3.31.6, Rust `nod` via Corrosion; ~5 min incremental, a clean build is longer): builds `libmetroid_prime2_port.so` (SDL3 static inside) and
   `libmp2_vkshim.so` into `nebulos-jniLibs/arm64-v8a`. The adrenotools hook libs (`libhook_impl.so`, `libmain_hook.so`, ...) are NOT packed: core-galaxy ships same-named ones in the shared lib folder
   (custom GPU drivers are unused here).
4. `./gradlew :app:assembleRelease` (module `core-echoes` compiles the port's Java: `org.metroidprime2.port` (kept), `dev.echoes.sdl`, `dev.echoes.aurora`; textures are synced into assets).

## Edits to the port (all small)
- `platform/android/nebulos_vpad.c` (+ CMake): ONE SDL virtual gamepad (vendor 0x5243 / product 1) fed by `EchoesPad` JNI (copy of core-galaxy's `nebulos_vpad.c`).
- `extern/aurora/lib/input.cpp`: the Nebulos pad counts as a real pad when ports are assigned (otherwise the port's touch gamepad took player 1 on a race and the title ignored the buttons).
- `platform/port_events.cpp`: the Nebulos pad counts as physical input (the port's touch overlay hides itself after the first button press).
- `env.MP2_NO_DISC_DIALOG=1`: no SDL message box (the "copy saves from another release" offer, which crashed in `PortSave::Import` and cannot be answered by a controller).

## How it works
- `EchoesActivity` (`:echoes` process) extends the port's `MetroidPrime2Activity`; Nebulos owns the loading screen (picture gate + 1.5 s), Back menu, notices and errors (`NebulosCoreHost`).
- Input: router -> `EchoesSession` -> `EchoesPad` -> the SDL virtual pad. Real controllers are hidden from SDL (`SDL_GAMECONTROLLER_IGNORE_DEVICES_EXCEPT`). Face buttons go BY LABEL
  (Nebulos A -> the port's A = SDL south, etc.; the port's prompts are Xbox glyphs), unlike the other recomp cores (by position). Triggers analog (L = lock-on), sticks, d-pad.
- Saves/settings: `files/echoes/user` is a symlink to `_Nebulos/echoes-core/user` (memory card `USA/Card A/01-G2ME-MetroidPrime2.gci`, `settings.cfg`, `controls.toml`, `touch.cfg`, quick-resume state).
  The USA release's G2ME and the Trilogy's R3ME saves share that card folder (the port converts between them on request, F1 menu).

## Verified on the Nova (RP Nova 12 GB)
List shows "Metroid Prime 2 - Echoes" under RECOMPILED; starts from the list; disc read in place from the SD card (.ciso; the Trilogy .rvz also boots as Echoes); logos, title, attract demo, main menu,
Single Player, new game, intro, Temple Grounds with movement and firing; 60 fps (SurfaceFlinger present rate; Nebulos' counter in cutscenes: game 60 / host 60); ~600 MB RSS; Nebulos Back menu opens
over the game and Close Game returns to the list; memory card is written to `_Nebulos/echoes-core`; a second launch with an existing card works.
Not verified: a manual save at a save station and loading it, long sessions / heavy rooms (Dark Aether, bosses), the Trilogy disc chosen per game after the claim change, PAL disc, touch-only play,
controller remapping UI, the port's own F1 overlay (not reachable with a controller).
