# core-dread: Metroid Dread 2.1.0 on the Odyssey runtime

The same native-ARM64 runtime as :core-smo (libsmo_main.so from `../../smorecomp-src`, branch `dread-probe`; the game's own code runs on the CPU, NVN -> Vulkan, system services in C).
This module only adds the game: `DreadCore` (routing by title "Metroid Dread"), `DreadActivity` (`:dread` process, a copy of SmoActivity with Dread's data) and `DreadXciDump`.

## First launch
The user's XCI carries the base game (1.0.0) and the 2.1.0 update. `DreadXciDump` runs hactool (libhactool.so from :core-smo, the user's own prod.keys): secure partition -> NCAs; base = the largest program NCA,
update = the next; `--basenca` merges them: exefs (main build id 646761F6...) + the merged RomFS (9774 files, 5.3 GB) into `<external files>/native-dumps/010093801237C000`; a marker file records it (RecompGameFiles.ensure
writes it for any dumper now: it used to extract again on every launch for custom dumpers). hactool needed two local fixes for Android (`../../hactool-src`): memmove for the overlapping BKTR bucket moves, a real FILE for `--basefake`.

## Runtime changes it needed (smorecomp-src, all local, never pushed)
- Dread's `main` has a 1.8 GB bss: sdk and subsdk0 load after it (they used fixed bases inside it).
- The vsync timer starts when the game first waits for a vsync in its present (Dread never asks for the display's vsync event).
- movie::Extractor / Decoder constructor overloads (cache size, C2 variants) so Dread's cutscene/logo players use the host decoder, not the Switch's multimedia library.
- nvnCommandBufferBindProgram with a NULL program.
- Diagnostics: LARECOMP_WATCHDOG (guest frames + handles + host backtrace of stuck threads), LARECOMP_LOG_IPC, slow/failed romfs read logs; core-smo/core-dread read `<external files>/runtime-env.txt` (KEY=VALUE lines) as environment, NEBULOS_FORCE_READY=<ms> drops the loading screen.

## State (Nova)
First launch extracts, boots, the menus work with the controller, a new game starts and plays its opening scene. Known: the startup logos/videos show black until a button; the picture is cropped to fill the 4:3 screen (subtitles clipped);
28 fps in the first scene while pipelines build (3000 draws skipped at first); the loading screen is time-based (14 s) because the picture gate would wait for a lit picture while the screen holds the buttons back; combat/stick/saves untested.
