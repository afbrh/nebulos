# core-sm3dw: Super Mario 3D World + Bowser's Fury on the Odyssey runtime

The same native-ARM64 runtime as :core-smo / :core-dread / :core-mp4 (libsmo_main.so from `../../smorecomp-src`, game row GAME_V_3DW picked by `main`'s build id: the update v65536
891687f016a18f17... or the cartridge's base 1.0.0 9f7efc2fb9653e5c...; SDK 10.4.0; modules main + sdk only; the game's own code runs on the CPU, NVN -> Vulkan, system services in C).
This module only adds the game, apart from every other game:

- `Sm3dwCore` (routing by title "3D World" / "Bowser's"), `Sm3dwActivity` (`:sm3dw` process), `Sm3dwXciDump` (first launch).
- Own game files: ExeFS + RomFS (2.9 GB, 3,778 files) + marker in `<external files>/native-dumps/010028600EBDA000`; data root `<files>/sm3dw`; saves `<_Nebulos>/sm3dw-core/save`;
  debug env file `<external files>/runtime-env-sm3dw.txt` (KEY=VALUE per line).
- First launch: hactool (the user's own prod.keys): secure partition -> base program NCA (largest) + update NCA; `--basenca` merges them (about 1 minute on the Nova). A cartridge without an update runs the base.
- Runtime facts specific to this game (smorecomp-src): `nvnDeviceGetInteger` answers NVN 53.313 (the game's init requires >= 53.313), `runtime/hle/swkbd_inline.c` makes the inline software keyboard
  a silent no-op (its Initialize waited for a keyboard library applet forever). Controls: a player joins with L+R pressed together, then A.

See `../../3dworld-research/PROGRESS.md` for the state and measurements.
