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
HUD placement: the Ui2d canvas is a fixed 1920x1080 inside the 1920x1440 frame, so the runtime moves each HUD pane rigidly outward by 180 units when its y (the pane's translation in its vertex constants, nvn/vulkan/exec.c
`draw_`, game 106 only) is 330..560 from the canvas middle: the hearts go to the frame's top-left corner, the item buttons (and their Y/X/A labels) to the top-right; panes in the middle stay. A frame that has a full-screen pane
(title, menus, load screens) or a talk's box is left as the game made it, and shifted panes are put back (the dialogue box and its arrow stay one piece). Changing the Ui2d layout size (lyt1) had no effect.
Full-frame panes: a pane that fills the whole 16:9 band (the pause menu's blurred world and darkening, a loading screen's black, a fade) is made 4/3 as tall about the frame's middle, so the pause menu shows the (blurred)
live world over the whole frame and a loading screen is black over the whole frame; the title's 16:9 picture (fs 28129be4) is left alone. A loading screen's corner pieces and strips (fs 248be7e1 / a0a885df, anchored at the
band's top and bottom edges) go out to the frame's edges, so the loading icon sits at the frame's bottom-left corner.
Camera hooks cover the projection object and the camera controller's own fov / aspect copies (+0x640 / +0x644, culling and framing).
Known: the blurred backdrop behind the load / save lists is upside down (pre-existing); the ground shimmer reported in 4:3 town (walking) was treated by hooking the camera controller's fov/aspect copies (a culling that
used the 16:9 vertical field) - not confirmed gone; the title's picture was black with garbage under it in some builds (a 1920x1440 movie texture over the decoder's 1920x1080 one): movie frames now also go into a taller alias
texture over the same memory when there is one (none was found; the title works in the final build, cause not pinned down).
Not checked: Suthorn Ruins rooms in 4:3, the map screen, a cutscene with letterboxing.
