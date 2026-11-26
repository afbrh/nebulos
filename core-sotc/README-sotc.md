# :core-sotc — Shadow of the Colossus (PS2, USA SCUS-97472) via Celosia

**Parked since 2026-11-26** (user's choice): not built into the app, the game runs in ARMSX2. The code, engine source and saves are kept. To restore: remove `sotc` from `parkedCores` in `settings.gradle.kts` and add `"sotc"` back to `CoreBootstrap.CORE_IDS`.

[Celosia](https://github.com/HenryKun55/celosia) (GPL-3.0) statically recompiles the game's MIPS code (boot ELF + the three XFF
modules) to C++ with PS2Recomp and runs it on a native runtime: the CPU is not emulated; the graphics are drawn by a native
renderer (OpenGL ES 3.2 here), sound and the IOP are host code. Source: `../celosia-src`, local branch `nebulos-android`
(never pushed; its origin is the upstream project). `ENGINE-SOURCES.md` has the entry.

## How it is built

1. **Generated game code (Mac, once, needs the user's own disc):** in `celosia-src`: `./celosia setup --iso <SotC USA .iso>`.
   This produces `build/runner` (recompiled code) and `build/vu1rec`. It is specific to the USA disc.
   Local fixes (not upstream): `celosia` (LIFTED_REGEX also excludes `decomp_chara10`), `game/overrides/decomp_pf141.cpp` (a9sweep include).
2. **Android runtime:** configure `celosia-src/build-android` once (NDK 27, arm64, android-29, `-DPS2X_RUNNER_DIRS=<build/runner;build/vu1rec;game/overrides>`,
   unity batch 8, RelWithDebInfo -O2; see the commit message / `apk-android/pack.sh`), then `core-sotc/native/build.sh` builds
   `libps2EntryRunner.so`, strips it and stages it (with `libc++_shared.so`) in `src/main/jniLibs/arm64-v8a` (git-ignored).
3. `./gradlew :app:assembleRelease` (release only, like the other cores).

## How it runs in Nebulos

- `SotcActivity` is a `NativeActivity` in the `:sotc` process. It gives the window's surface and input queue back to the view system,
  shows the game in a `SurfaceView`, and hands that Surface to the native app glue (`ps2_android_nebulos.cpp`: `nativeSurfaceCreated`).
  Nebulos's loading screen / Back menu / notices are views above it. The physical controller is consumed by Nebulos (InputRouter) and
  injected as a PS2 pad (`SotcInput`: A=Circle, B=Cross, X=Triangle, Y=Square; right stick inverted on both axes).
- First launch: `SotcData` copies the disc's files out of the ISO (plain 2048-byte-sector ISO9660) into `<files>/sotc/disc`; the
  runtime's main thread waits for `nativeDataReady`. Pause freezes the guest at its next checkpoint and shifts its clocks on resume.
- Saves: memory card images in `<_Nebulos>/sotc-core/memcards` (portable, see `nebulos-api/SAVEDATA.md`).
- `PS2X_*` / `SOTC_*` switches: `<files>/sotc/env.txt` (KEY=VALUE lines), e.g. `PS2X_STICK_DEADZONE=0.2`.
