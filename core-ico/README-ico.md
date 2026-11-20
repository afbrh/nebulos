# :core-ico - ICO (PS2) via ico-pc

ico-pc (github.com/nathanialf/ico-pc, MIT) is the ICO decompilation built as a native SDL3 + Vulkan port (not an emulator). One-game core, routed by the
disc serial (`IcoCore.SERIALS`: USA SCUS-97113, Europe SCES-50760, Japan SCPS-11003, Korea SCPS-56001, Asia SCPS-55001, the 2001 preview/demo SCUS-97158);
ARMSX2 stays selectable. The user's own disc image is read in place (raw 2352-byte CD images work: `Ps2DiscId` reads them too).

| Thing | Where |
|---|---|
| Engine source (local, never pushed) | `../ico-pc`, branch `nebulos-local` |
| Native library | `libico_main.so` (ico-pc's CMake from this module; SDL3 static from `../botwrecomp-src/android/deps/out-static`, renamed Java package `com.noryan.recompsdl`) + adrenotools' hook libraries |
| Java | ico-pc's `IcoActivity` + `ExitRecords` (package `com.defnf.icopc`, JNI names unchanged), imports the renamed SDLActivity; `sNebulosDisc` answers the first-run picker with the launched disc path |
| Activity / process | `IcoCoreActivity` (Nebulos host, virtual pad), process `:ico` |
| First-run settings | `IcoSetup.seedConfig`: resolution 2x, FSR 1 at sharpness 100, 60 fps, Analogue stick fix (only when config.toml does not exist yet) |
| Saves | `<files>/memcard` and `<files>/states` are symlinks to `<_Nebulos>/ico-core/` |
| Input | Nebulos router -> `RecompSession` -> SDL virtual gamepad, face buttons by position (B/south = Cross) |

Mac build prerequisites (all done once, under `../ico-pc/tools/toolchain/`): the EUC-JP-MS checkout fix (docs/BUILDING.md "Checking out on a Mac"),
`tools/fetch_deps.sh` (`SKIP_*` flags, `CMAKE_BIN`), `tools/fetch_android.sh` (`ICO_CMAKE`, `SKIP_SDK_INSTALL=1`), a **host DXC built from source**
(tag v1.9.2602 commit 21d28f72, recipe in ico-pc's `.github/workflows/apple-build.yml`, copied to `deps/dxc/{bin,lib}`), NDK 28.2.13676358.
Build with `PATH=../ico-pc/.venv/bin:$PATH ./gradlew :app:assembleRelease` (the installed Nebulos is release-signed).
