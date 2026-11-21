# core-mp4: Metroid Prime 4: Beyond 1.1.0 on the Odyssey runtime

The same native-ARM64 runtime as :core-smo / :core-dread (libsmo_main.so from `../../smorecomp-src`, game row GAME_V_MP4 picked by `main`'s build id 8275FBE1EC5191C2; the game's own code
runs on the CPU, NVN -> Vulkan, system services in C). This module only adds the game, and keeps it apart from every other game:

- `Mp4Core` (routing by title "Metroid Prime 4"), `Mp4Activity` (`:mp4` process), `Mp4XciDump` (first launch).
- Own game files: ExeFS + marker in `<external files>/native-dumps/010019A01E2F2000`; the 26 GB RomFS on the XCI's volume in `Nebulos-gamedata/010019A01E2F2000/romfs` (used in place through
  `romfs.override`); the data root is `<files>/mp4`; saves are `<_Nebulos>/mp4-core/save`. Odyssey's folders (`native-dumps/0100000000010000`, `files/smo`) are never touched.
- Debug env file: `<external files>/runtime-env-mp4.txt` (KEY=VALUE per line). Every Prime 4 runtime default (BREV, dynamic attribute loads, storage images for the fog volume, sparse-pool light volumes, the 3328 MB GPU heap) is built into the runtime: no env file is needed. `LARECOMP_MP4_NOFOG=1` skips the volumetric fog composite (a few fps) if a device needs it.

## First launch
`Mp4XciDump` runs hactool (the user's own prod.keys): secure partition -> the two program NCAs (staged next to the RomFS, ~28 GB, removed afterwards); base = largest, update = next; `--basenca` merges
them (BKTR relaxations for SDK 20.5 update NCAs are in `../../hactool-src`). The volume needs ~56 GB free for the first launch, ~27 GB afterwards. `romfs.part` is renamed to `romfs` (and `romfs.complete`
written) only when finished.

## State (Nova)
Boots to the title (60 fps), menu, new game, opening cutscene, first-person gameplay: the world renders with the volumetric fog (SUST.B storage images, rasterizer discard) and the arm cannon is lit (the BC6H / BC1 light volumes are sparse 3D textures: laid out with their 64 KiB tile shape and fed from the pages the game maps). Not done: geometry shaders, transform feedback, vertex format 0x27. See `../../mp4-research/PROGRESS.md`.
