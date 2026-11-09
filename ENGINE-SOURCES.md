# Engine source trees: how to recreate them

Nebulos builds against local, patched checkouts of every emulator engine, placed next to this repo (`../eden-src` and so on, see
`settings.gradle.kts`). They are NOT in this repo and must never be pushed to the upstream projects. This file records how each one
was rebuilt on 2026-11-08 after the original machine was lost, so it can be done again. Patch files live in each `core-*/patches`
folder (`patches-rebuilt` where an older, partial set already existed).

Build environment used: Android Gradle Plugin 9.2.1, Kotlin 2.4.0, Gradle 9.4.1, JDK 17, cmake 3.31.6 + ninja from the Android SDK.
NDKs: 27.3.13750724 (Dolphin, PrimeHack, Wind Waker HD LLVM, 2 Ship), 28.0.13004108 (WatermelonDS), 29.0.14206865 (Dusklight, ARMSX2),
30.0.16248370 (Eden, and the app's `ndkVersion` so native libraries get stripped). `glslangValidator` must be on the PATH; Rust
(rustup, target aarch64-linux-android) is needed by Dusklight and others.

A build with only some engines present: `./gradlew -Pnebulos.cores=eden :app:assembleRelease` (settings.gradle.kts and
app/build.gradle.kts read the same property; `app/src/noWatermelon` supplies a stand-in Application base class when WatermelonDS is absent).

| Engine | Upstream and base | Patches | State |
|---|---|---|---|
| eden-src | Eden `master` ae60f8e7f (version string 4.1.0-dev) | `core-eden/patches` | Java-side hooks rebuilt from the Release 15 decompile, UDP-socket and NSO-bss fixes re-applied. Pokemon Brilliant Diamond (0100000011D90000) is presented at 60 FPS: the composer forces its swap interval of 2 to 1 (patch 0008), and Nebulos disables fast forward for it. Not recreated: vibration stubs, `InputOverlay` no-overlay flag, EmuReady launch paths. The flavor dimension is named `edenVersion` (WatermelonDS has its own `version` dimension). |
| dolphin-src | dolphin-emu/dolphin d57e268691 (2026-10-10) | `core-dolphin/patches`, `patches-rebuilt` | Patches 0001-0006 applied; two earlier patches (shader compile progress hook, shake override) were never exported and are RECONSTRUCTED in `patches-rebuilt`. |
| primehack-romcore-src | shiiion/dolphin, branch `nebulos` | `core-primehack/patches` | Survived intact (only file modes were flipped; `git config core.fileMode false`). |
| dusklight-src | TwilitRealm/dusklight b245c6bef8, aurora 08122911e8, borealis 8a1c87eb89 | `core-dusklight/patches` | README recipe followed; dusklight 0003 skipped (already contained in 0002). |
| shipwright-src | please-be-nice/Shipwright-android f8dc69741, libultraship 3a9af4f | `core-shipwright/patches` | Applied cleanly. `soh.o2r` generated with the host tools (host deps built from source into `../shipwright-hostdeps/prefix`). |
| twoship-src | linkzenic/2ship2harkinian-Android e6b1de75e, libultraship 45081d07 | `core-twoship/patches` | Applied cleanly. vcpkg at a073c28e5. |
| armsx2-src | ARMSX2 `rebase-2.9`, HEAD 22cae50af7 | `core-armsx2/patches` | Survived; 9,595 missing files restored from git; shaderc third_party dirs refilled from the revisions in shaderc's DEPS. The 16 KB-page library is not built. |
| cemu-src | SSimco/Cemu `android-port` 8759323e | `core-cemu/patches` | Library conversion + `setMLCPath`/`initializeForEmbedding` hooks RECONSTRUCTED; libusb vcpkg overlay port added for macOS hosts. JNI symbols match the old build (189/189). |
| azahar-src | azahar-emu/azahar 6b4e7f2e6 | `core-azahar/patches` | Hooks RECONSTRUCTED. Renderer half of 4:3 Fill REBUILT 2026-11-09 (patches 0003-0004: vertex shader scales the rotated top-screen axis by 0.8, 2D edge-anchored; applies to 240x400 and 480x400 (stereo eyes side by side) framebuffers; verified on OoT3D by comparing the title logo's aspect with the 5:3 view). NOT recovered: draw-blending half of the 60 FPS patch, `svcprof`, the perf_stats frame-limiter fix, RomRunner-era edits listed in `core-azahar/LICENSE-NOTES.md`. Portable save paths (`!`-prefixed absolute paths) are a guess. |
| watermelonds-src | SapphireRhodonite/WatermelonDS tag 0.8.0.rc2 (6f716d78), NOT rafaelvcaetano's repo | `core-watermelonds/patches` | Nebulos seam, DSi/DSiWare changes RECONSTRUCTED; JNI symbols match (129/129). Stand-in `m3_split_button_chevron_avd` icon because Nebulos pins Material 1.12.0. Guessed: `notice()` long duration, `nebulosClose()`. |
| retroarch-src | libretro/RetroArch 4c61f9fede (2026-09-29, inferred from the old binary) | `core-retroarch/patches`, `patches-rebuilt` | `nebulos_bridge.c` ops 0-6 RECONSTRUCTED from the old binary's disassembly (op 2 omits an OSD message after a state load). JNI symbols match (14/14). |
| wwhd-src | Nycolazs/WindWakerHD-Android-Plus tag v1.11.1 | `core-wwhd/patches` | One CMake fix for Mac hosts. LLVM 20.1.8 built at `../wwhd-llvm/install`. JNI symbols match (48/48). |

## Things that only showed up when the engines ran together on the device (2026-11-08)

- Several libraries define resources with the same names (layouts, navigation graphs, menus). The merged app keeps one of each, so Eden's
  18 colliding layouts, 2 navigation graphs and 4 menus are prefixed `eden_` in eden-src (as the old build did). If a new engine
  is added, check `res/layout`, `res/navigation`, `res/menu` for name clashes first.
- Eden's library must NOT run R8 on itself (`isMinifyEnabled = false`): its own rules stripped `NebulosBridge` and reshaped `Game`'s
  constructors. The app's R8 does the shrinking, with the keep rules in `app/proguard-rules.pro`.
- Do not use Kotlin default arguments when calling an engine class from a Nebulos core (`EdenCore` calls `Game`'s full constructor).
- Azahar save states are tied to the Azahar build that made them ("Savestate version mismatch" on load): a rebuilt Azahar cannot load
  states made by the old one. In-game saves are unaffected.
- On-device test harness: scripts in the session scratchpad drove the Nova with controller key events (R1 switches tab, D-pad down +
  A opens the launch panel). The Nova must be kept awake (`adb shell svc power stayon true`).

Not an engine: PPSSPP (`ppsspp-src`) has no Nebulos core module; it was not rebuilt.

Nothing here has been run on a device since the rebuild: test every engine on hardware before releasing.
