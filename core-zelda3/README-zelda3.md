# core-zelda3: A Link to the Past (SNES)

Native port: [zelda3](https://github.com/snesrev/zelda3) (MIT), a reimplementation of the game in C. US ROM only
(SHA-1 `6d4f10a8b10e10dbe624cb23cf03b88bb8252973`).

## Sources
- `../zelda3-src`: upstream clone (commit 45a149d), local branch `nebulos-local`, never pushed. Nebulos changes are under `#ifdef NEBULOS`
  (`src/main.c`, `src/zelda_rtl.c/.h`) and in `nebulos/` (JNI bridge, default `zelda3.ini`, SDL rename script).
- SDL2 2.32.10: run `../zelda3-src/nebulos/rename_sdl.sh` once (needs `../sm64-sdl2-orig`, a pristine checkout of the tag). It writes
  `zelda3-src/nebulos/deps/SDL2` with the Java package renamed to `com.noryan.nebulos.core.zelda3.sdl`.
- The native library (`libzelda3_main.so`, SDL2 linked in) is built by Gradle from `src/main/cpp/CMakeLists.txt`.

## Game data
`Zelda3Assets.kt` builds `zelda3_assets.dat` from the ROM on first launch. Its output must stay byte-identical to upstream's
`assets/restool.py --extract-from-rom` (reference for this commit and ROM: SHA-1 `d65587f9fe677909f3f5cac94a0248677594be26`, 683888 bytes).
Bump `BUILDER_VERSION` in `Zelda3Files.kt` when the output changes.

## On the device
- Game folder: `files/zelda3` (`zelda3.ini` is written once and then left alone; the original features and widescreen options are in it).
- `saves/` is a link to `_Nebulos/zelda3-core/saves` (cartridge save `sram.dat`). Save states are the port's snapshots in the shared state folder.
- Picture: the console's 256x224 on a 4:3 area (as on a TV). Only when `ExtendedAspectRatio = 4:3`; the widescreen values keep upstream's square pixels.
