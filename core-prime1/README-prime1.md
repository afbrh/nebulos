# core-prime1: Metroid Prime (GameCube; GM8E01 / GM8P01, and the Trilogy discs R3ME01 / R3MP01's Metroid Prime) via the Metroid Prime 1 native port

The Metroid Prime PC/Android port (a vanished author's decompilation-based port, sibling of the Echoes one; work copy of a backup, v0.36.0) as a one-game Nebulos core. A native port, NOT an emulator
(SDL3 + Aurora/Dawn on Vulkan + the game compiled for the device; the disc is read by `nod`).
**Legally grey: derived from Nintendo's executable, MIT only over the platform layer. Personal use only, never publish, never push.**

## Discs (from platform/disc.cpp `PortDisc::Identify`, the only gate)
| id | revision | accepted |
|---|---|---|
| GM8E01 | 0 / 1 / 2 (USA 1.00 / 1.01 / 1.02) | yes (the main target; the user's `Metroid Prime.ciso` is GM8E01 rev 2) |
| GM8P01 | 0 (PAL) | yes, "experimental" |
| R3ME01 / R3MP01 | 0 (Metroid Prime Trilogy, Wii) | yes, "experimental": `OpenDiscImage` mounts the Trilogy's `MP1/` folder as root and `MP1/rs5mp1_p.dol` as the DOL |
| GM8J01 (Japan), other revisions | | NO ("Pick the PAL or Japanese GameCube disc" is only about the optional Flaahgra music import) |
Prime 1 Remastered is not a disc: it is an optional model/asset import from a Switch NSP/XCI (F1 overlay), unrelated to disc selection.

## Routing
`GameSpecificCores.isMetroidPrime1` accepts GM8E01, GM8P01, R3ME01, R3MP01; only GM8E01 / GM8P01 are *claimed* (`claims()`): the Trilogy stays with PrimeHack by default and the port is a per-game
choice for it. The ROM is read in place (ISO/GCM/CISO/RVZ/WIA/GCZ/WBFS, no copy). Unlike Echoes the port has NO `port_paths.txt`: `Prime1Activity` sets environment variables with `setenv`
before the port's `onCreate` loads the library: `MP_DISC=<rom>` (beats the remembered path), `MP_USER_PATH`, `MP_CACHE_PATH`, `MP_TEXTURES`, `MP_RESOURCES_PATH`,
`MP_NO_DISC_DIALOG=1` (no SDL message boxes: disc errors and the "copy saves from another release" offer), `MP_UPDATE_CHECK=0`.

## Build (this Mac)
1. `../../prime-research/mp1-build` = clone + `../mp1-build.patch` (Dawn prebuilt swap to encounter/dawn v20260807.225922, cmake 3.31.6), built by `../scripts/mp1-probe.sh` (native, ~2 min on 12 jobs) and
   `../scripts/mp1-apk.sh assembleRelease -PmpDebugSigning=true -PcmakeDependencyCache=...` (standalone APK `../MetroidPrime1-debugsigned.apk`, ~2 min).
2. Work copy `../../prime-research/mp1-core-src` (copy of `mp1-build`); our edits are in `../../prime-research/mp1-core-nebulos.patch` (git diff of the work copy, plus the new files `rename_sdl.sh`,
   `build_native.sh`, `platform/android/nebulos_vpad.c`). Deps are local copies in `build/android-deps` (rsync of `../mp1-build/build/android-aarch64/_deps/*-src` and `../mp1-build/build/android-deps/dawn_prebuilt`).
3. `./rename_sdl.sh` regenerates the renames from the pristine `../mp1-build`: SDL3's Java package `org.libsdl.app` -> `dev.prime1.sdl`, `dev.encounter.aurora.AuroraSurface` -> `dev.prime1.aurora`,
   `MetroidPrimeActivity` made non-final, its glyph folder + pipeline cache seed moved to `files/prime1/`, its startup cover (needs the app's R) removed.
4. `./build_native.sh` (NDK 29.0.14206865, CMake 3.31.6, Rust `nod` via Corrosion): builds `libmetroid_prime_port.so` (SDL3 static inside) and `libmport_vkshim.so` into `nebulos-jniLibs/arm64-v8a`.
   The adrenotools hook libs are NOT packed (core-galaxy ships same-named ones).
5. `./gradlew :app:assembleRelease` (module `core-prime1` compiles the port's Java: `org.metroidprime.port` (kept), `dev.prime1.sdl`, `dev.prime1.aurora`; textures + pipeline seed are synced into assets).

## Edits to the port (all small)
- `platform/android/nebulos_vpad.c` (+ CMake): ONE SDL virtual gamepad (vendor 0x5243 / product 1) fed by `Prime1Pad` JNI (copy of the Echoes core's).
- `extern/aurora/lib/input.cpp`, `platform/debug_ui.cpp` (`IsPhysicalInput`: hides the touch overlay), `platform/port_prompts.cpp` (pad vs keyboard prompt glyphs): the Nebulos pad counts as a real pad.
  Not changed: `pad.cpp` (the virtual pad keeps the Standard default mapping, never loaded/saved: the port's remap UI does not apply to it, as for the touch pad), `port_input_devices.cpp` RealPad/ConnectedPads (remap UI only).
- `platform/main.cpp`: `MP_RESOURCES_PATH` overrides the Android resources folder (where `initial_pipeline_cache.db` is read), so the seed stays out of the shared files folder.
- No SDL disc-dialog edit needed: the port already honours `MP_NO_DISC_DIALOG` in `main.cpp` and `DolphinCMemoryCardSys.cpp` (the Echoes build needed the same via `MP2_NO_DISC_DIALOG`).

## How it works
- `Prime1Activity` (`:prime1` process) extends the port's `MetroidPrimeActivity`; Nebulos owns the loading screen (picture gate + 1.5 s), Back menu, notices and errors (`NebulosCoreHost`).
- Input: router -> `Prime1Session` -> `Prime1Pad` -> the SDL virtual pad. Real controllers are hidden from SDL (`SDL_GAMECONTROLLER_IGNORE_DEVICES_EXCEPT`). Face buttons go BY LABEL like Echoes.
- Saves/settings: `files/prime1/user` is a symlink to `_Nebulos/prime1-core/user` (memory card folders `USA/`, `EUR/`; the Trilogy keeps its own R3ME/R3MP card, `port_settings.ini`, mods, save states).

## Status
## Verified on the Nova (RP Nova 12 GB, 2026-11-20)
The module compiled and linked on the first Gradle run (no R8/duplicate-class/lib-name issues). "Metroid Prime" (GM8E01 rev 2, USA 1.02, `Metroid Prime.ciso` in place on the SD card) is listed under RECOMPILED and starts from the list
(log: `disc mounted`, `disc is USA 1.02`); logos, title, attract demo, main menu, New Game, the Orpheon intro cutscenes (Start skips them), then gameplay: stick movement, right-stick look, A fires (target reacts), Nebulos pad via sendevent.
fps (SurfaceFlinger present rate, SurfaceView layer): 60.0 on title, menu, cutscenes and in gameplay; ~560 MB PSS. Nebulos Back menu (Back key) opens over the game; Close Game returns to the list; Save State writes `user/savestates/resume.mpss`
and the next launch offers "Resume where you left off?" (works; the port treats Close Game as an unclean quit). Saves: `_Nebulos/prime1-core/user/USA/Card A/01-GM8E-MetroidPrime A.gci`.
The Trilogy (`Metroid - Prime Trilogy.rvz`, R3ME01) chosen per game (Options -> Core -> Metroid Prime Port) moves to RECOMPILED, boots (`disc is Trilogy (USA) (experimental support)`), title with the 2002-2009 notice, new game and the opening
cutscene at 60 fps; its card is `USA/Card A/01-R3ME-MetroidPrime.gci`. Echoes, Dread and Odyssey still start from the list (60 fps on their first screens).
`claims()` auto-claims GM8E01/GM8P01 only; the Trilogy stays with PrimeHack unless chosen.

Not verified: PAL disc (GM8P01), a manual save at a save station + loading it, long sessions / heavy rooms / bosses, morph ball, scan visor/lock-on (L trigger) beyond the pad mapping, the controller mapping UI, touch-only play.
Known quirk: the resume snapshot (`user/savestates`) is shared between the USA disc and the Trilogy, so the Trilogy offered to resume the USA disc's snapshot (answer No).
