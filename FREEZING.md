# Freezing a Switch game's runtime

All Switch cores (smo, dread, mp4, sshd, wonder, sm3dw, las, eow) share one native runtime, `../smorecomp-src` (built by `:core-smo`'s CMake). A
**frozen** game loads its own copy of that runtime, built from a git tag, so later runtime edits for another game cannot regress it.

How it works
- `frozen-runtimes.properties`: `game = tag` (game = core name). Games not listed use the live working tree (`libsmo_main.so`).
- Each tag has a git worktree at `../smorecomp-frozen/<tag>` (outside smorecomp-src; never touches its working tree).
- `core-smo` CMake builds one extra library per tag, `libsmo_main_<tag with - as _>.so`, from that worktree. SDL static, shaderc, FFmpeg and
  libadrenotools always come from the live tree (build deps, not runtime source).
- The game's activity returns `RuntimeLib.forGame("<game>")` from `getLibraries()`: the frozen lib, else `smo_main`. (Each game has its own process,
  so only one runtime is loaded.)
- Still shared, NOT frozen: the Java glue (`SMORecompAndroidActivity`, compiled from the live tree), `recomp-common` (SDL, vpad) and the core modules.
  A change there can still affect a frozen game; keep such changes backward compatible.

## Current state: ONE shared frozen runtime (2026-11-26, shared-v31)

Every Switch game (smo, dread, sm3dw, wonder, las, eow, sshd, mp4) loads the same frozen runtime, tag **`shared-v31`**, worktree
`../smorecomp-frozen/shared-v31`, one library `libsmo_main_shared_v31.so` (no live `libsmo_main.so` in the APK unless a game is thawed).
Earlier versions stay in git with their worktrees: `shared-v1` .. `shared-v30`; the older per-game tags are no longer used.

- v2 = v1 + EoW: loading spinner in the 4:3 corner, held incomplete frames, pipeline cache saved while it grows.
- v3 = v2 + EoW: indirect draws (Castle Town's trees).
- v4 = v3 + EoW: shadows (constant buffers read in place), the frame hold as a real copy of the last whole frame.
- v5 = v4 + EoW 4:3 only (game id 106): the scene's render targets are 4/3 taller (device.c, `LARECOMP_EOW_SCENE_SY`, default 4/3; 1 = as before).
- v6 = v5 + EoW only (game id 106): SHFL lane indices (idx/up/down) are taken inside the lane's own 32 lanes (translate.c `shfl_idx/_up/_down`; Adreno subgroups are 64/128 wide). Removes the lit shaders' light-loop sparkle (green blocks in shadows, white dots on stone). `LARECOMP_EOW_NO_SHFL_FIX=1` = as v5.
- v7 = v6 + EoW only (game id 106): a texture entry whose depth/stencil mode is STENCIL samples the stencil plane (exec.c `stencil_mirror_*`: R8_UINT copy of the D24S8 stencil, refreshed when a draw binds it). The rift decal reads its object flag bits there; the depth plane was read before (mantissa noise: stripes + pixelated purple outline). `LARECOMP_EOW_NO_STENCIL=1` = as v6.
- v8 = v7 + EoW only (game id 106): the "Press Any Button" screen (the only one with exactly 1920x1080 scene targets) is cropped at the sides and zoomed to fill the 4:3 frame: exec.c `vk_eow_title_screen` (latched off for good when those passes stop for 12 frames or a world scene is drawn), widescreen.c `widescreen_present_crop` (uv 0.125,0.125 size 0.75). `LARECOMP_EOW_NO_TITLE_CROP=1` = off.
- v9 = v8 + Metroid Dread only (game 101): BindProgram(NULL, FRAGMENT_BIT) = depth-only draws through the bound vertex stage with an empty fragment shader (exec.c CMD_BIND_PROGRAM, ShaderKey.no_fs). ~140 draws a frame (shadow maps, depth pre-pass) were skipped before: the lit world came out black. `LARECOMP_DREAD_NO_FSLESS=1` = as v8; `LARECOMP_LOG_SKIPWHY=1` logs skip sites.
- v10 = v9 + Dread 4:3 view (loader.c): the docked render size at main+1a1be18 (1600x900) patched to 1200x900, nominal final image 1440x1080 (g_eow_res). `LARECOMP_DREAD_NO_43=1` or the file `aspect169` in the data root = 16:9; `LARECOMP_DREAD_RES=<w>x900`.
- v11 = v10 + Wonder 4:3 view (game 104; loader.c + exec.c): the six docked-size immediates (1920->1440 wide, handheld 1280->960) and the final image's nominal size 1440x1080; the game still renders its whole frame (world + UI) into one 1920x1080 target, which the final composite quad now shows centre-cropped (viewport ox -240: the world is a real 4:3 view, full height) while the UI draws into that target (vs code >= 0x253af0000 and the 2424c9300/2424d5300 blur blit) are scaled 3/4 about the centre, so the 1920 canvas lies inside the 1440 visible columns (a 16:9 band in the 4:3 frame). `LARECOMP_WONDER_NO_43=1` or the file `aspect169` in the data root = 16:9; `LARECOMP_WONDER_UI_SCALE`, `LARECOMP_WONDER_UI_MIN` (hex) for tests.
- v12 = v11 + Wonder UI laid out per pane (game 104, exec.c `wonder_*`): the nn::ui2d pane (560-byte vertex cb0) and text (128-byte) matrices are rewritten to a 3/4 scale (x about the middle, y about the middle in menus / about the top or bottom edge for HUD panes in a frame that has world draws; panes in a world frame between |y| 330 stay as the game placed them). Panes as large as the canvas (backgrounds, fades, the blurred snapshots menus use) are left alone, cropped like the world. This replaces v11's per-draw viewport band, which flipped for some transition draws (flashing 16:9/4:3). The text-shadow blur blits (vs 2424c9300 + fs 2424d5300) are not drawn. `LARECOMP_WONDER_NO_UIBAND=1` = no UI change, `LARECOMP_WONDER_KEEP_SHADOWS=1`, `LARECOMP_WONDER_LOG/LOGC=1` diagnostics.
- v13 = v12 + Wonder start screens fitted (game 104, exec.c): a frame whose 1920x1080 target got 1-39 depth draws (the title / user select / players / characters 3D backdrop; a course or the map has ~280) is composited as the whole 16:9 frame scaled 3/4 into the 4:3 image, bars cleared black, with no UI changes (as the 7c91d40 band iteration). Courses / the map (>= 40 depth draws) and frames with none (Plus menu, in-course pause, Welcome card, transitions) keep v12's per-pane handling. `LARECOMP_WONDER_NO_MENUFIT=1` = v12.
- v14 = v13 without the letterboxed start screens (user: switching between fitted and full frames is jarring). Every Wonder frame is now the same 1440x1080 image. Start screens (1-39 depth draws) use the per-pane menu handling with full-canvas panes scaled 3/4 too (the game's own backdrop draws fill the sides). `LARECOMP_WONDER_MENUFIT=1` = v13's fitted start screens.
- v15 = v14 + Super Mario 3D World + Bowser's Fury native 4:3 (game 105; the same for both games): loader.c patches the render set-up's 1920x1080 immediates/floats (main+40b818/40b81c/40b884/40b890), the handheld pair, the display-mode getters (8a8690..8a8700) and the screen-size object's floats (8a8730..8a8774) to 1920x1440 (g_eow_res); native.c hooks the readers of sead::PerspectiveProjection tan(fovy/2) / fovy (7386fc/7387d8/738808/7386b0) so the camera keeps its 16:9 horizontal view and shows 4/3 more vertically (the 15-degree layout camera near 1 / far 10000 is excluded); device.c: scene targets 4/3 taller; exec.c: nn::ui2d HUD panes/text near the 720-unit canvas's top/bottom are moved out by the free 120 units (ramp 100..300 units from the middle) in frames with >= 40 depth draws (menus untouched). `LARECOMP_SM3DW_NO_43=1` or the file `aspect169` = 16:9; `LARECOMP_SM3DW_RES=<w>x<h>`, `LARECOMP_SM3DW_ZOOM=f`, `LARECOMP_SM3DW_SCENE_SY`, `LARECOMP_SM3DW_NO_UISHIFT`, `LARECOMP_SM3DW_LOG`/`LOGP` for tests.
- v16 = v15 + SM3DW/Fury fixes (game 105): Bowser's Fury's world camera has a fixed 16:9 aspect (3D World's cameras take the frame's) - near 10 / far 150000, fovy 30/45 deg: native.c `tdw_cam` gives it the frame's aspect (also at getAspect 7386c0) and tan * (16/9)/(4/3), so Fury is no longer a stretched 16:9 picture. HUD: the layout panes' projection row is -1.0003 (v15 compared with -1: no pane was moved, only text); positions/shifts are now in ndc (Fury's HUD layouts go through a 0.8 camera), the shift keeps 14 units inside the frame, edge columns (|x| > 450) move as one block, and a loading screen (<= 6 layout draws, no world) moves its corner icon down.
- v17 = v16 + SM3DW/Fury HUD pieces anchored to the bottom keep 0 units inside the frame (the top ones 14): `LARECOMP_SM3DW_BOTTOM_INSET=<units>`.
- v18 = v17 + 3D World's (not Fury's: told apart by Fury's 16:9-aspect world camera, native.c `g_3dw_fury`) bottom-anchored HUD pieces go 60 units further down (`LARECOMP_SM3DW_BOTTOM_EXTRA`), and no shifted layout piece can leave the frame (clamp in ndc, exec.c).
- v19 = v18 + SM3DW start-screen fixes (game 105): HUD shifts only in frames with >= 1500 depth draws (the Resume/New Game screen's 3D backdrop has ~500 and was being HUD-shifted: Fury's logo toggled, 3D World's New Game dropped under the footer); in other frames only the footer (white band, red line, hatching, Back) goes flush to the bottom edge and a loading screen's corner icon moves (8 quiet frames, corner pane, no scene).
- v20 = v19 fix (game 105): the >=1500 depth-draw HUD threshold was wrong (real per-frame depth draws: start-screen backdrop ~500, map ~170, 3D World course ~270, Fury ~595; no threshold separates them) - back to >=40; Resume/New Game stay put because only edge pieces (|x| > 450 of 640) are HUD-shifted; the start screens' footer (white band, red line, hatching, Back) is not drawn (`LARECOMP_SM3DW_KEEP_FOOTER=1` draws it); loading-screen corner icon (pane, |x|>450, no scene) shifted again.
- v21 = v20 + Skyward Sword HD native 4:3 (game 103, loader.c): the render size pair 1920x1080 - data at main+16cac94 and the rodata template it is copied from at main+13a916c (handheld 1280x720 at 13a915c) - patched to 1440x1080 (960x720); the game then lays out the world (more vertical view), HUD and menus for 4:3 itself. `LARECOMP_SSHD_NO_43=1` or the file `aspect169` in the data root = 16:9; `LARECOMP_SSHD_RES=<w>x<h>`.
- v22 = v21 + Super Mario Odyssey native 4:3 (game 100): loader.c patches the docked render size 1600x900 -> 1200x900 (main+51537c/515380/53667c/536680; g_eow_res 1440x1080); native.c reuses the SM3DW sead::PerspectiveProjection hooks (SMO addresses 75fbe0..75fe94, 7607f4; tan x (16/9)/r, aspect r - also for the 16:9 layout camera) so the world gets the taller view and the 1280x720 ui2d layouts a 640x480 half-extent camera; exec.c: full-canvas panes (>=690 units high) stretched 4/3, pieces with |y| > 290 (HUD, footer) moved out to the frame edges (top ones keep 36 units). `LARECOMP_SMO_NO_43=1` / `aspect169` = the old crop, `LARECOMP_SMO_RES`, `LARECOMP_SMO_NO_UISHIFT`, `LARECOMP_SMO_LOGUI`.
- v23 = v22 + Link's Awakening native 4:3 (game 107, same engine as EoW but different offsets): loader.c patches the window/render-size immediates to 1920x1440 (main+1015a04/10, 1019820/24, 101dbe0/e4, handheld 1015a08/0c, the 4 scene-size blocks 913a10.., the factory 10237d8/dc) and the shared 16:9 aspect constant (rodata 16afba4: camera setups) to 4/3; native.c hooks the camera's fov store (main+10918 -> +0x1e0, 20 deg) so tan(fov/2) x (16/9)/(4/3): same horizontal view, more vertical; exec.c: the scene composite quad (vp 1920x1080 into the 1920x1440 window) stretched to the window's height (the 3D targets stay 16:9-sized, the picture squeezed in them, as in EoW); the UI lays itself out for the 1920x1440 frame (hearts top-left). `LARECOMP_LAS_NO_43=1` or the file `aspect169` = 16:9; `LARECOMP_LAS_RES=<w>x<h>`, `LARECOMP_LAS_FOVK`, `LARECOMP_LAS_NO_COMPOSITE_FIX`.
- v24 = v23 + SM3DW/Fury small fixes (game 105): the start screens' 'Erased' note and its box are not drawn (found via the previous frame's footer strip); Fury's coin counter 45 units lower (`LARECOMP_SM3DW_BOTTOM_EXTRA_FURY`): ~82 px from the bottom edge (cat icon ~88 from the top); the loading screen's corner icon ~53 px from the bottom.
- v25 = v24 + SMO HUD fix (game 100): top HUD no longer shifted (it already sits ~45 px from the frame's top; the v22 ramp pushed pieces with |y| > 330 off the frame), bottom: footer pieces (cy < -310) fully, others 60 %, all clamped inside the layout camera's real extents. `LARECOMP_SMO_TOPSHIFT`, `LARECOMP_SMO_SHIFTK`, `LARECOMP_SMO_LOGHUD=<frame>`, `LARECOMP_SMO_LOGSHIFT=<frame>`.
- v26 = v25 + SMO top-left counter row (coin counter + hat counter, game 100) shifted 150 units inward (`LARECOMP_SMO_LEFTSHIFT`): it was clipped at the frame's left edge; the row is now ~45 px from the left like the top margin.
- v27 = v26 + SMO (game 100): (1) the Throw Cap tutorial's guide movie (`PlayGuideMovie*.mp4`) fails to open (`LARECOMP_SMO_GUIDEMOVIE=1` plays it): its player stopped taking decoder output after the first frame and the game froze for good (a busy event loop; reproduced with and without 4:3); (2) the top-right HUD group (life gauge, Menu/Map prompts, compass) shifted 150 units inward (`LARECOMP_SMO_RIGHTSHIFT`): it was entirely off the frame.
- v28 = v27 + SMO (game 100): the 'Super Mario Odyssey' logo (pause menu, startup screens) goes up to its 16:9 spot at the top left (`LARECOMP_SMO_NO_LOGOFIX`).
- v29 = v28 + SMO (game 100): renders at 1280x960 (the Nova's screen) instead of 1200x900; `LARECOMP_SMO_RES=1200x900` goes back. Needs the post-process height maximum raised too (main+a583c0).
- v30 = v29 + Metroid Dread (game 101): the title screen's and main menu's backdrop movie (`video/title/titlescreen*.webm`, 1280x720) fills the 4:3 frame, cropped at the sides with the same scale both ways, instead of sitting in a 16:9 band (movie.c flags the open movie; exec.c enlarges its quad's viewport). The logo is part of that movie and grows with it; menus and prompts are unchanged. `LARECOMP_DREAD_NO_TITLE_ZOOM=1` = as v29.
- v31 = v30 + Metroid Dread (game 101): the game over screen's backdrop (`video/title/gameover*.webm`) fills the 4:3 frame the same way (the flag covers all of `video/title/`). The same off switch.

**Rule:** the runtime is changed only by thawing (`-Pnebulos.thaw=<games>` or removing lines), testing, then retagging a new shared version
(`shared-v32`), then the full-APK install. Testing depends on what changed (policy since 2026-11-25):
- A change confined to a **game-id-gated, off-switchable code path** (it only runs for that game): smoke-test the touched game (look + fps) plus a quick **boot check of the others** (each launches and renders a frame, no crash).
- A change to **ungated / shared code** (anything that runs for every game): a FULL smoke test of all Switch games (boots to gameplay, same look, similar fps) before the new tag is mapped.
A game-specific fix must be gated by game id so it cannot change another game.

Test of shared-v31 on the Nova, 2026-11-26 (full release build; gated to Dread): dread File 1 to a real game over (backdrop fills the frame), Quit to Samus Files (fills), 60 fps; Continue from game over checked on the thawed build; boot check smo 60, sm3dw 60, wonder 60, eow 30, las 30, sshd 30, no crash, saves unchanged; mp4 not installed. gameover2_loop.webm not seen in play. (v31-*.png/log.)

Test of shared-v30 on the Nova, 2026-11-26 (full release build; change gated to Dread): dread title, Press A, Samus Files, File 1, Continue to gameplay, 60 fps, backdrop fills the frame with no letterboxed frame in the recorded transitions (recorded on the thawed build; title and menu rechecked on v30); boot check smo 60, sm3dw 60, wonder 60, eow 30, las 30, sshd 30 (opening movie), no crash, saves unchanged; mp4 not installed. (v30-*.png/log.)

Smoke test of shared-v26 on the Nova, 2026-11-24 (full release build, no thaw): smo 60 fps/14.8k draws, dread 60, sm3dw 60, wonder 60 (title in 4:3; gameplay, map, menus, pause 60 fps in the dev build), las 30, sshd 30; eow not re-run (only game 104 changed); mp4 not installed. (v11-*.png/log.)

Smoke test of shared-v8 on the Nova, 2026-11-24: smo 60 fps/14.7k draws, dread 60, sm3dw 60, wonder 60, las 30, sshd 30 (old stall again; timing-dependent), eow start screen fills 4:3, file select unchanged; mp4 not installed. (v8-*.png/log.)

Smoke test of shared-v7 on the Nova, 2026-11-24: smo 60 fps/14.6k draws, dread 60, sm3dw 60, wonder 60, las 30 (intro), sshd 60 (past its old stall, gameplay), eow rift house renders; mp4 not installed. (v7-*.png / v7-*.log.)

Smoke test of shared-v6 on the Nova, 2026-11-24: smo 60 fps/14.8k draws, dread 60, sm3dw 60, wonder 60, las 30 (intro), sshd 30 (same stall), eow bad spot clean (no sparkle); mp4 not installed. (v6-*.png / v6-*.log in eow-research/shared.)

Smoke test of shared-v5 on the Nova, 2026-11-23 (go.sh in `../eow-research/shared/`, `v5-*.png`, `v5-grid.png`, `v5-*.log`):

| game | result on shared-v5 |
|---|---|
| smo | Resume menu identical, 60 fps (14.7k draws) - pass |
| dread | title identical, 60 fps - pass |
| sm3dw | chooser identical, 60 fps - pass |
| wonder | title identical, 60 fps - pass |
| las | beach intro, 30 fps in the cutscene - pass |
| sshd | opening-movie card (dark), 30 fps; the known stall (paused) - same as before |
| eow | saved spot loads in 4:3, trees + shadows, no black frames in a 50 s walk, ~30 fps in town (about 5% below v4) - pass |
| mp4 | not installed in the Nova's library - not run (EoW-gated changes only) |

## Freeze a game
1. Pick the commit where the game was verified; tag it: `git -C ../smorecomp-src tag -a frozen-<game>-v1 <commit> -m "what was verified"`.
2. Make the checkout: `git -C ../smorecomp-src worktree add --detach ../smorecomp-frozen/frozen-<game>-v1 frozen-<game>-v1`
   (does not touch the live tree; if worktrees misbehave: `git -C ../smorecomp-src archive <tag> --prefix=frozen-<game>-v1/ | tar -x -C ../smorecomp-frozen`).
3. Add `<game>=frozen-<game>-v1` to `frozen-runtimes.properties`.
4. In the game's Activity add
   `override fun getLibraries(): Array<String> = com.noryan.nebulos.core.smo.RuntimeLib.forGame("<game>")` (smo, dread, wonder, sm3dw have it already).
5. Build (take `/Users/ry/Desktop/claude/.build-lock` first): `./gradlew -Pnebulos.cores=<game> :core-<game>:assembleDebug`.

Several games may share one tag (then they get separate but identical libraries; separate tags let you fix one without the other).

## Thaw (back to the live tree)
- One build: `-Pnebulos.thaw=wonder,dread` builds those games from the live tree for that invocation.
- For good: delete the game's line in `frozen-runtimes.properties`. (Optionally `git worktree remove ../smorecomp-frozen/<tag>`; keep the tag.)

## Fix a frozen game (cherry-pick into a tag)
A tag is immutable; make a new one.
```
cd ../smorecomp-frozen/frozen-wonder-v1          # the worktree is a detached HEAD
git cherry-pick <fix-commit>                      # fix commit from the live branch (or commit a hand fix here)
git tag -a frozen-wonder-v2 -m "v1 + <fix>"
cd ../../smorecomp-src && git worktree add --detach ../smorecomp-frozen/frozen-wonder-v2 frozen-wonder-v2
```
Then point `wonder=` at `frozen-wonder-v2` and `git worktree remove ../smorecomp-frozen/frozen-wonder-v1` once v2 is verified. Commits made in a worktree land in
the same repository, so they are reachable by tag only: always tag before removing a worktree.

## Notes
- `-Pnebulos.cores=<list>` only builds the frozen libs of listed games, and the live lib only if a listed Switch game is not frozen.
- The tags and worktrees are local only (smorecomp-src is never pushed). `../smorecomp-frozen/` is not a git repo; it is rebuilt from the tags with step 2.
- Verifying a freeze: `unzip -l` the APK for `lib/arm64-v8a/libsmo_main_<tag>.so`; the activity logs `Running main function SDL_main from library .../libsmo_main_<tag>.so`.
