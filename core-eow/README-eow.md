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
Town road: ~39.8 fps at 4:3 vs 41.3 at 16:9. The camera is punched in 1.2x (constant `eow_zoom`, env LARECOMP_EOW_ZOOM for tests): plaza 40.6 fps vs 37 at 16:9 (30.8k draws), 4:3 unzoomed 32 fps (43k draws). 1600x1200 gives the same fps as 1920x1440 and a broken UI, so it is not used. Loading spinner sits in the old 16:9 band corner.
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


## Fixed: speckles on lit stone / carpet (Hyrule Castle)
Bright 2-3 px dots (also in 16:9) came from half-precision instructions whose F32 result was in the fp16 denormal range: the GPU flushes those to zero, our fp32 emulation kept them and the lit shader's divisions blew them up. Now flushed (translate.c `hpackf`, EoW only). The reflection cubemap filter compute also really writes its images now. Details: eow-research/NOTES-ghidra.md.

## Fixed 2026-11-23 (shared-v2): loading spinner, black flashes in Castle Town
- Loading spinner: its dots go round (y -490..-506 within a turn) and the corner shift only caught part of the turn, so it jumped between the old 16:9 corner and the 4:3 corner about every half second. The shift band is now 470-530: it stays in the 4:3 corner (before/after: `eow-research/rec/maxload_before.png` / `maxload_after.png`, the spinner positions over the whole load).
- Black flashes in the first ~30 s in Castle Town: draws were skipped while their pipelines were still building (new materials seen for the first time), which left a frame black. A frame with skipped scene-size draws is now not shown; the last whole frame is shown again (up to 240 in a row). Warm runs: 0 flashes and no hitch over 50 s (before: 1 flash in 2 of 4 runs, ~230 skipped draws); the first-ever (cold) walk still skips ~10k draws while shaders compile (held frames, up to a 2 s pause). The pipeline cache is now saved while it grows (at most once a minute), so the next launch is warm even if the game is killed. LARECOMP_EOW_NOHOLD=1 turns the hold off, LARECOMP_EOW_WAIT=1 makes draws wait for their pipelines instead (half-second hitches), LARECOMP_LOG_WAIT / LARECOMP_LOG_SPIN log them.

## Fixed 2026-11-23 (shared-v3): Castle Town's trees were not drawn
The game draws its foliage (trees, bushes) with indirect draw commands whose instance counts the GPU's culling compute writes into an argument buffer; the runtime only logged "DrawElementsIndirect: not done" (4 times, then silently) and drew nothing. Indirect draws are now done for EoW: the draw reads its argument buffer (count, instance count, ...) straight from the placed guest heap on the GPU (vkCmdDrawIndexedIndirect on the heap buffer), so the same-frame culling result is used; DispatchComputeIndirect reads its counts from guest memory. At the user's saved orange-house spot two trees now stand in front of the character (before / after: `eow-research/spot-before-notrees.png`, `spot-after-trees.png`; same in 16:9, so not a 4:3 culling issue). LARECOMP_EOW_NO_INDIRECT=1 turns it off, LARECOMP_LOG_INDIRECT=<frame> logs the arguments. The thin purple dotted lines the user saw on the house edges are not there any more in the fresh session (not investigated further).

## Fixed 2026-11-23 (shared-v4): shadows, black areas, speckle
- **Shadows were missing** because the shadow pass drew with the wrong light data: the game's constant buffers were copied when each draw was recorded, but the light matrix of the shadow pass (and of the receivers) is written into that memory by a compute shader the same frame - the copy still had the old values, every caster landed outside the shadow map (an empty map), nothing was in shadow. EoW's constant buffers are now read in place from the heap by the GPU (LARECOMP_EOW_NO_UBO_DIRECT=1: copies as before).
- **Black areas while moving** were frames shown with draws missing (the new shaders compile on first sight, the frame hold only worked for one frame in a row because the game draws the next frame into the image just shown). The hold is now a copy of the last whole frame, kept in an image of its own (up to 240 frames; LARECOMP_EOW_NOHOLD=1: off).
- **White speckle on shadows, trees and lit ground** came from removing the 16-bit rounding of the shadow-compare reference (a test of mine): without it the compare flipped between lit and shadow on nearly equal depths. The rounding stays (LARECOMP_EOW_NO_DREF_ROUND=1 shows the speckle).
- Evidence: `eow-research/shadow-before-noshadows.png`, `shadow-after.png`, `shadow-regress-black-speckle.png` (the intermediate build), `st-fin1-pair.png` (two stills 1.5 s apart, no twinkle).
