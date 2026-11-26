# core-supermetroid: Super Metroid (SNES)

Native port: the [snesrev/sm](https://github.com/snesrev/sm) decompilation (the game's logic in C). Japan/USA ROM only
(SHA-1 `da957f0d63d14cb441d215462904c4fa8519c613`).

## Sources
- `../sm-snes-src` (not `sm64-src`): local branch `nebulos-local`, never pushed (push URL disabled). Base: nickdesaulniers/sm `gba-port` c4b8c21,
  which keeps the PC build and fixes many bugs of upstream 578f90b (its `docs/UPSTREAM-ISSUES.md`), among them the overlapping `memcpy` that
  corrupts large rooms wherever the C library is not glibc (Android is such a place).
- Added on top: upstream issues 8 and 10 (that document gives them without applying them), the Kraid fix of snesrev/sm PR 50, the soft reset when
  the C code runs alone (snesrev/sm#45), and the Nebulos changes under `#ifdef NEBULOS` and in `nebulos/` (JNI bridge, `sm.ini`, SDL rename).
- SDL2 2.32.10: run `../sm-snes-src/nebulos/rename_sdl.sh` once (needs `../sm64-sdl2-orig`).
- The native library (`libsupermetroid_main.so`) is built by Gradle from `src/main/cpp/CMakeLists.txt`.

## Which code runs
The app sets the run mode to "mine": the C code alone. The source also contains the SNES emulator the decompilation is checked against.

## Testing the game code (no device needed)
`../sm-snes-src/nebulos/roomwalk.c` sends Samus through all 581 doors (255 rooms) with every item and random input, each in its own process:

    clang -O2 -w -fno-strict-aliasing -I. -I../zelda3-src/nebulos/deps/SDL2/include -o roomwalk nebulos/roomwalk.c \
        $(ls src/*.c src/snes/*.c | grep -v -e main.c -e opengl.c -e glsl_shader.c -e config.c) -lm
    mkdir -p run/saves && cd run && ../roomwalk <rom> 900 both    # or `mine`

`both` compares the C code with the original every frame. `tools/hostrun.c` replays an input trace the same way. Results on 2026-11-26: no
difference in the attract demos (40000 frames) or a new game through the intro and Ceres. Room walks in `both` mode differ in a few rooms: in
`d5a7` and `d617` a few background tiles differ for a frame (cause not found); the other differences come with invalid addresses logged after the
tool drops Samus outside a room. The three crashes seen with the C code alone (Mac and Nova) were traced to that artifact; the original crashes
there too. AddressSanitizer: no memory error.

## On the device
- Game folder: `files/supermetroid` (`sm.ini` written once, then left alone; `sm.smc` is the checked copy of the ROM).
- `saves/` is a link to `_Nebulos/supermetroid-core/saves` (cartridge save `sm.srm`). Save states are the port's snapshots in the shared state folder.
- Picture: the console's 256x224 on a 4:3 area. `EnhancedMode7` must stay 0 (the port's frame copy does not handle the 4x buffer).
