# core-eow: The Legend of Zelda: Echoes of Wisdom on the Odyssey runtime

The same native-ARM64 runtime as :core-smo / :core-dread / :core-sm3dw (libsmo_main.so from `../../smorecomp-src`, game row GAME_V_EOW picked by `main`'s build id a581406bc9858651...;
SDK 16.2.0; modules main, sdk, subsdk0 = GLSLC, subsdk1 = movie; the game's own code runs on the CPU, NVN -> Vulkan, system services in C). Not core-echoes (that is Metroid Prime 2).
This module only adds the game, apart from every other game:

- `EowCore` (routing by title "Echoes of Wisdom"), `EowActivity` (`:eow` process), `EowXciDump` (first launch).
- Own game files: ExeFS + RomFS (10,963 files, 8 GB) + marker in `<external files>/native-dumps/01008CF01BAAC000`; data root `<files>/eow`; saves `<_Nebulos>/eow-core/save`;
  debug env file `<external files>/runtime-env-eow.txt` (KEY=VALUE per line).
- First launch: hactool (the user's own prod.keys) takes the secure partition out of the XCI, then the base program NCA (the largest, 5.8 GB): the RomFS is stored through the NCA compression layer
  (BucketTree + LZ4), which hactool-src's `nca_comp.c` reads. Version 1.0.0 only (a cartridge's update is ignored). Needs ~15 GB free during the extraction.

See `../../eow-research/PROGRESS.md` for the state and measurements.

## Frame rate
The game asks for a 30 fps present interval (its Switch 1 design target; cutscenes run 60). The runtime holds the interval at **1 by default: 60 fps** (smorecomp-src nvn/objects.c, `eow_apply_interval`; no menu row, no setting). In the Suthorn Ruins room the GPU needs ~14 ms a frame (the 14.6 ms "copy texture" row seen at 30 fps in LARECOMP_GPUPROF_AT is the idle wait for the next present, not copy work), so it holds 60.0 fps (max frame 18 ms), and the world's step is time-based: walking a fixed time covers the same distance at 30 and 60. Heavier scenes (Hyrule Castle Town, ~21 ms on the Thor) are still to be measured. `LARECOMP_FORCE_INTERVAL=2` in `runtime-env-eow.txt` brings back 30.

## 4:3 FILL (menu row, default off)
The Nova's screen is 4:3, the picture 16:9 (bars above and below by default). The row crops the middle of the picture to fill the screen (flag file `aspect43`, polled live). A real taller view (more vertical field of view, nothing cropped) needs camera/frustum/HUD hooks the game does not offer yet; see eow-research/PROGRESS.md.
