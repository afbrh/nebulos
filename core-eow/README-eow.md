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

## 4:3 (menu row "4:3 (NEXT LAUNCH)", ON by default)
The Nova's screen is 4:3. Default (and fresh installs): the game renders a real 4:3 frame at 1920x1440 - the same width as 16:9, more world above and below (nothing cropped, no stretch), the HUD (hearts, rupees, prompts,
dialogue) and the pause menus at their normal proportions in a 16:9 band in the middle of the frame. Off (Back menu row): the plain 16:9 picture with bars above and below. The render size is read once when the game starts, so
the row applies after closing and reopening the game. The choice is the flag file `<data root>/aspect169`: present = 16:9 (off), absent = 4:3 (on) - so existing and fresh installs get 4:3 (the old `aspect43` crop flag is ignored).
Town road: ~39.8 fps at 4:3 vs 41.3 at 16:9.
How it works (smorecomp-src loader.c / native.c, details and addresses in eow-research/NOTES-ghidra.md): the game's render-size constants and the docked size pair are patched, the 1080 height compares that pick the viewport object
are moved, and every load of a camera's aspect / vertical field of view sees a 4:3 camera with the original 16:9 horizontal view. The old 4:3 FILL crop (cut the HUD edges) is gone.
Known: the HUD sits in the 16:9 band (hearts 30 px below the band's top, ~150 px below the frame's top), not in the frame's corners: the Ui2d panes are placed in a fixed 1920x1080 canvas and changing the layout size does not
move them (tried: lyt1 layout size 1920x1440, no effect). Corner anchoring would need a per-pane shift (pane centre beyond +-420 of the canvas moved 180 px outward) in the pane transform code or the UI vertex shader.
Not checked: Suthorn Ruins rooms in 4:3, the map screen, a cutscene with letterboxing.
