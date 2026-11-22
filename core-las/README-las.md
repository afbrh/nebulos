# core-las: The Legend of Zelda: Link's Awakening (Switch remake)

The Odyssey/TOTKDroid native runtime (`:core-smo`'s libsmo_main.so, source `../smorecomp-src`) with Link's Awakening as its game. The game's own ARM64 code runs
on the CPU; NVN, the system services and the audio renderer are implemented by the runtime. Not an emulator. A one-game core (claims games titled "Link's Awakening");
Eden stays selectable per game in the core picker.

- Versions: base 1.0.0 (main build id `AE16F71E002AF8CB...`) and the update 1.0.1 / 1.1.0 (`909E904AF78AC1B8...`, one main). `GAME_V_LAS` (107) in `runtime/paths.h`.
- Files: the user's own XCI (base + update, merged by hactool: 3,836 RomFS files, 6.2 GB) and `prod.keys`. First launch extracts into `<external files>/native-dumps/01006BB00C6F0000` (about 4.5 min on the Nova); nothing is shared with any other game.
- Own process `:las`, data root `<files>/las`, saves `_Nebulos/las-core/save`, debug env `<external files>/runtime-env-las.txt`.
- NVN 53.207 (as Odyssey); SDK 8.2.0; modules main, sdk, subsdk0 (GLSLC), subsdk1 (movie).
- Controls: the handheld title asks for L+R (hold both ~1 s) before the file select.
- Notes and test logs: `../las-research/` (ANALYSIS.md, PROGRESS.md, scripts).
