# Super Mario 64 at 60 fps (N64 core)

`src/main/assets/sm64_60fps_v2_mupen.ips` (2 KB, applied by `Sm64SixtyFps.kt` to the user's own US `.z64` at launch; source CRC32 3ce60709,
result CRC32 80145e58). It is Kaze Emanuar's "60 FPS Patch" v2 (https://sm64romhacks.com/hacks/60-fps-patch, frame interpolation: game
logic stays at 30 Hz, Mario/objects/camera are interpolated on the in-between frames) plus a Mupen64Plus-Next fix.

## Why the fix
The patch makes `read_controller_inputs` (RAM 0x80248638) run only on logic frames (hook at 0x802F7924, called from 0x80248BC4). The stock
game also starts an SI read (`osContStartReadData(&gSIEventMesgQueue)`, gated by gControllerBits) at 0x80248BAC on EVERY frame, so two
reads are started per one collected. Project64 tolerates that; Mupen64Plus-Next then reports no buttons (A, START, stick all dead), the
same symptom as the libretro forum threads about 60 fps patches. The fix:
* ROM 0x3BA0: `beqz t7` -> `b` (the stock every-frame read start is skipped),
* ROM 0xB2924..0xB296C (the hook, 18 words): flip the toggle byte at 0x8024BD5B; on logic frames start the read (if gControllerBits) and
  then call read_controller_inputs,
* the ROM is padded back to 8 MiB with the stock tail and the CIC-6102 header CRC is recomputed.
The first working variant of this fix was found by a separate agent on Kaze's V1; it was ported to V2 here.

Verified on the Nova: START opens file select, A starts the game, the stick moves Mario, the intro timing matches the stock ROM, and every
recorded frame during play is a distinct image (stock alternates real/duplicate frames).
