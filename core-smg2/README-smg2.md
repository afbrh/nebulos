# core-smg2: Super Mario Galaxy 2 (Wii, USA SB4E01) via Astral Italian

Astral Italian (github.com/classicrob/Astral-Italian; project code CC0, an AI-agent-built continuation of the Garigari decompilation of Super Mario Galaxy 2) as a one-game Nebulos core. A native
port, NOT an emulator: the game compiled for the device (SDL3 + Aurora/Dawn on Vulkan, the same layer the Echoes/Prime ports and Dusklight use), the disc read by `nod`.
**Derived from Nintendo's code (a decompilation); no assets are in the repository. Personal use only, never publish, never push.**

## Routing
`GameSpecificCores.isSuperMarioGalaxy2` accepts the USA disc (SB4E01 revision 0) only, the one disc the port supports; PAL (SB4P01) and Japanese (SB4J01) stay with Dolphin. Galaxy 1 (RMG*) is `core-galaxy`.
The disc image is read once (ISO, GCM, CISO, GCZ, WBFS, RVZ, WIA ...): the first launch extracts the DATA partition (3,940 files, 1.34 GB, about 11 s on a Mac) into this core's own folder and every
later launch reuses it.

## Folders (nothing shared with any other game)
- `/sdcard/Android/data/<pkg>/files/native-dumps/SB4E01/{files,sys}` + `nebulos-dump.json`: the extracted disc (internal volume).
- `files/smg2/cache`: Dawn + pipeline caches. `files/smg2/support`: a link to `_Nebulos/smg2-core`: the NAND (save file), settings, controls, logs.

## Build (this Mac)
1. Work copy `../../astral-src` (local branch `nebulos-android`, edits listed in `../../smg2-research/PROGRESS.md`; nothing is pushed).
2. `../../smg2-research/rename_sdl.sh`: copies of the pinned SDL 3.4.10 and Aurora with the Java package `org.libsdl.app` -> `dev.smg2.sdl` and `dev.encounter.aurora` -> `dev.smg2.aurora`
   (the Nebulos APK also carries other cores' copies of those classes) into `astral-src/build/android-deps`.
3. `../../smg2-research/android_configure.sh` then `../../smg2-research/build_lib.sh` (NDK 29, Ninja, Rust `nod` via Corrosion; a clean build is ~12 minutes on 12 cores): builds `libnebula_smg2.so`
   (SDL3 static inside, exports `SDL_main`, the JNI of `Smg2Native` and `Smg2Pad`) into `astral-src/nebulos-jniLibs/arm64-v8a`.
4. `./gradlew :app:assembleRelease` (module `core-smg2` compiles the SDL and Aurora Java from `astral-src/build/android-deps`).

## How it works
- `Smg2Activity` (`:smg2` process) extends SDL3's `SDLActivity` with Aurora's surface. SDL's thread asks `getArguments()` before the game starts; that waits for `prepare()` (disc check, the
  one-time extraction with progress on the Nebulos loading screen, the environment the native entry reads: `PETARI_GAME_DIR`, `ASTRAL_SUPPORT_DIR`, `PETARI_CACHE_DIR`).
- Input: router -> `Smg2Session` -> `Smg2Pad` -> ONE SDL virtual gamepad (vendor 0x5243 / product 1, `native/app/android_vpad.c`). Face buttons go by label: Nebulos A = bottom = jump, B = right, X = left = spin,
  Y = top = Start; right shoulder = spin; L2 = Z; L1 = C; right stick = Star Pointer; left stick moves. Real controllers are hidden from SDL.
