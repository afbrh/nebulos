# core-smw: Super Mario World (SNES)

Native port: [snesrev/smw](https://github.com/snesrev/smw) (MIT), a reimplementation of the game in C. US ROM only
(SHA-1 `6b47bb75d16514b6a476aa0c73a683a2a4c18765`).

## Sources
- `../smw-src`: upstream clone (commit eae20c6), local branch `nebulos-local`, never pushed. Nebulos changes are under `#ifdef NEBULOS`
  (`src/main.c`: Android hooks, 4:3 picture, only the virtual pad becomes a player; `src/common_rtl.c`: state files, and the picture state saved
  from the right PPU when the C code runs alone) and in `nebulos/`.
- Fixes for reads past the end of a table, found with AddressSanitizer (they apply on every platform): the pipe travel speed (`smw_00.c`), an empty
  overworld sprite slot (`smw_04.c`), the fireplace smoke table (`smw_02.c`), and the four level layout tables, now one array in the cartridge's
  order (`smw_05.c`).
- SDL2 2.32.10: run `../smw-src/nebulos/rename_sdl.sh` once (needs `../sm64-sdl2-orig`).
- The native library (`libsmw_main.so`) is built by Gradle from `src/main/cpp/CMakeLists.txt`.

## Game data
`SmwAssets.kt` builds `smw_assets.dat` from the ROM on first launch, without the ROM inside it, so the C code alone runs the game. Its output must
stay byte-identical to `assets/restool.py --no-include-rom` (reference for this commit and ROM: SHA-1 `4d1308b22022a79705fc45d8ddf35907b4fbea6f`,
353820 bytes). Bump `BUILDER_VERSION` in `SmwFiles.kt` when the output changes.

## Testing the game code (no device needed)
`../smw-src/nebulos/levelwalk.c` loads all 192 level codes and plays them with random input, each in its own process:

    clang -O2 -w -fno-strict-aliasing -I. -I../zelda3-src/nebulos/deps/SDL2/include -o levelwalk nebulos/levelwalk.c \
        $(ls smb1/*.c smbll/*.c src/*.c src/snes/*.c | grep -v -e '^src/main.c' -e opengl.c -e glsl_shader.c -e '^src/config.c') -lm
    mkdir -p run/saves && cd run && ../levelwalk <smw_assets.dat> 7200 10 <seed>

With a data file that includes the ROM (`restool.py` without `--no-include-rom`) it compares the C code with the original every frame.
Results on 2026-11-26 (Mac, arm64): after the fixes, no difference in 8 runs of 192 levels x 7200 frames; AddressSanitizer clean on the C code alone.

## On the device
- Game folder: `files/smw` (`smw.ini` written once, then left alone). One player.
- `saves/` is a link to `_Nebulos/smw-core/saves` (cartridge save `smw.srm`). Save states are the port's snapshots in the shared state folder.
- Picture: the console's 256x224 on a 4:3 area.
