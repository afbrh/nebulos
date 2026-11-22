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
The game asks for a 30 fps present interval (its Switch 1 design target; cutscenes run 60). In the Suthorn Ruins room the GPU needs ~14 ms a frame (LARECOMP_GPUPROF_AT: the 14.6 ms "copy texture" row at 30 fps is the idle wait for the next present, not copy work), so `LARECOMP_FORCE_INTERVAL=1` in `runtime-env-eow.txt` gives a steady 60 fps (max frame 18 ms) there, and walking speed stays the same (the game's world step is time-based). Heavier scenes (Hyrule Castle Town, ~21 ms on the Thor) were not tested at interval 1, so the default stays the game's own choice.
