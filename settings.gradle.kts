pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "Nebulos"
include(":app", ":nebulos-api")

// -Pnebulos.cores=eden (or dolphin,eden ...) includes only those cores; without it every core is included. Needed when only some of the
// sibling *-src checkouts exist. app/build.gradle.kts reads the same property to link the matching modules.
val wantedCores = providers.gradleProperty("nebulos.cores").orNull?.split(",")?.map { it.trim() }
fun want(id: String) = wantedCores == null || id in wantedCores
// Each core agent adds its own line here when its module first builds:
// include(":core-retroarch", ":core-dolphin", ":core-primehack",
//         ":core-eden", ":core-azahar", ":core-watermelonds", ":core-armsx2")

// :core-eden (Switch). Eden's android project is consumed as a composite build (its :app was converted to a library).
if (want("eden")) include(":core-eden")
if (want("eden")) includeBuild("../eden-src/src/android") {
    name = "eden"
    dependencySubstitution { substitute(module("dev.eden_emu:app")).using(project(":app")) }
}

// :core-lichii (Switch 2). Lichii, via pavelgajdo/Lichii-Android (Hokkaido), GPLv3: ../lichii-android-src/src/android (:app converted to a library,
// package relocated to com.noryan.lichii, native library liblichii-android.so) as a composite build.
if (want("lichii")) include(":core-lichii")
if (want("lichii")) includeBuild("../lichii-android-src/src/android") {
    name = "lichii"
    dependencySubstitution { substitute(module("com.noryan.lichii:app")).using(project(":app")) }
}

// :core-watermelonds (Nintendo DS/DSi). WatermelonDS (melonDS-android fork) consumed as a composite build; its :app is a library.
// Local patches (NebulosBridge seam, JDK17/arm64-only) live in ../watermelonds-src (local commits, never pushed upstream).
if (want("watermelonds")) include(":core-watermelonds")
if (want("watermelonds")) includeBuild("../watermelonds-src") {
    name = "watermelonds"
    dependencySubstitution { substitute(module("me.magnum:app")).using(project(":app")) }
}

// :core-armsx2 (PS2). ARMSX2 (PCSX2 ARM fork, GPLv3): its android project (../armsx2-src/platforms/android, :app converted to a
// library) is consumed as a composite build. Explicit name: several included builds end in ".../android".
if (want("armsx2")) include(":core-armsx2")
if (want("armsx2")) includeBuild("../armsx2-src/platforms/android") {
    name = "armsx2"
    dependencySubstitution { substitute(module("com.armsx2:app")).using(project(":app")) }
}

// :core-dolphin (GameCube/Wii). Mainline Dolphin: native code from ../dolphin-src (CMake, branch `nebulos`),
// Java layer package-relocated into this module. See core-dolphin/README-dolphin.md.
if (want("dolphin")) include(":core-dolphin")

// :core-azahar (3DS). Azahar (Citra lineage, GPL-2.0-only): ../azahar-src/src/android (:app already a library) as a composite build.
if (want("azahar")) include(":core-azahar")
if (want("azahar")) includeBuild("../azahar-src/src/android") {
    name = "azahar"
    dependencySubstitution { substitute(module("org.azahar_emu:app")).using(project(":app")) }
}

// :core-retroarch (libretro host). Local RetroArch build (retroarch-src, with the nebulos JNI bridge patch) as a composite build.
if (want("retroarch")) include(":core-retroarch")
if (want("retroarch")) includeBuild("../retroarch-src/pkg/android/phoenix") {
    name = "retroarch"
    dependencySubstitution { substitute(module("com.retroarch:phoenix")).using(project(":")) }
}

// :core-primehack (GameCube/Wii, Metroid Prime trilogy). PrimeHack (shiiion/dolphin) via a local Nebulos-patched clone
// (../primehack-romcore-src; made by core-primehack/tools/relocate_primehack.py + core-primehack/patches). Module coordinates
// nebulos.primehack:app deliberately differ from org.dolphinemu:app so :core-dolphin can substitute its own.
if (want("primehack")) include(":core-primehack")
if (want("primehack")) includeBuild("../primehack-romcore-src/Source/Android") {
    name = "primehack"
    dependencySubstitution { substitute(module("nebulos.primehack:app")).using(project(":app")) }
}

// :core-dusklight (GameCube, The Legend of Zelda: Twilight Princess ONLY). Dusklight (CC0-1.0, decomp-based native port)
// from ../dusklight-src (local branch `nebulos-local`, never pushed): its platforms/android module is a library
// consumed as a composite build; native lib libdusklight_main.so, SDL3 Java package dev.twilitrealm.dusksdl.
// Native build: see core-dusklight/README-dusklight.md (cmake --preset android-arm64 in ../dusklight-src, NDK 29).
if (want("dusklight")) include(":core-dusklight")
if (want("dusklight")) includeBuild("../dusklight-src/platforms/android") {
    name = "dusklight"
    dependencySubstitution { substitute(module("dev.twilitrealm:app")).using(project(":app")) }
}

// :core-jak (PS2, Jak and Daxter trilogy). OpenGOAL native port from ../jak-android-src (local branch `android-port`, never pushed);
// native libs are produced by core-jak/native/build.sh (NDK 27). See core-jak/README-jak.md.
if (want("jak")) include(":core-jak")

// :core-sotc (PS2, Shadow of the Colossus USA only). Celosia (GPL-3.0; static recompilation + native runtime with a GLES 3.2 renderer)
// from ../celosia-src (local branch `nebulos-android`, never pushed: its origin is the upstream project); libps2EntryRunner.so is
// produced by core-sotc/native/build.sh (NDK 27). See core-sotc/README-sotc.md.
if (want("sotc")) include(":core-sotc")

// :core-wwhd (Wii U: The Wind Waker HD only). Native runtime from ../wwhd-src (CMake, local branch `nebulos`), no game code in the library;
// the game is translated on the device from the user's own .wua. See core-wwhd/README-wwhd.md.
if (want("wwhd")) include(":core-wwhd")

// :core-darkcloud (PS2, Dark Cloud (the first game) only). Chronicle (the decomp + native SDL3/Vulkan port) from ../Chronicle (local branch
// `nebulos-local`, never pushed); its CMake build runs from this module, SDL3 static from recomp-common/build_sdl_static.sh. See core-darkcloud/README-darkcloud.md.
if (want("darkcloud")) include(":recomp-common", ":core-darkcloud")

// :core-ico (PS2, ICO). ico-pc (the ICO decomp + native SDL3/Vulkan port, MIT) from ../ico-pc (local branch `nebulos-local`, never pushed); its CMake build runs
// from this module (needs a source-built DXC under ico-pc/tools/toolchain/deps/dxc, see core-ico/README-ico.md), SDL3 static from recomp-common.
if (want("ico")) include(":recomp-common", ":core-ico")

// :core-smo (Switch, Super Mario Odyssey 1.0.0 only): the TOTKDroid runtime (../smorecomp-src, local branch `smo`) with Odyssey Recomp's game support.
if (want("smo")) include(":recomp-common", ":core-smo")

// :core-galaxy (Wii, Super Mario Galaxy revision 0 only): Nebula (GPL-3.0, ../smg-nebula, local commit, never pushed); native libs by ../smg-nebula-hosttools/build_android.sh.
if (want("galaxy")) include(":recomp-common", ":core-galaxy")

// :core-dread (Switch, Metroid Dread 2.1.0 only): the Odyssey runtime (:core-smo) with Dread as its game.
if (want("dread")) include(":recomp-common", ":core-smo", ":core-dread")

// :core-mp4 (Switch, Metroid Prime 4: Beyond 1.1.0 only): the Odyssey runtime (:core-smo) with Prime 4 as its game, its own process, game files and saves.
if (want("mp4")) include(":recomp-common", ":core-smo", ":core-mp4")

// :core-sshd (Switch, The Legend of Zelda: Skyward Sword HD 1.0.1 only): the Odyssey runtime (:core-smo) with Skyward Sword HD as its game, its own process, game files and saves.
if (want("sshd")) include(":recomp-common", ":core-smo", ":core-sshd")

// :core-sunshine (GameCube, Super Mario Sunshine GMSE01 only): the SMS PC port (../sms-src/sms-android/sms-port, local checkout, never pushed), built by CMake from the module.
if (want("sunshine")) include(":recomp-common", ":core-sunshine")

// :core-sm64 (Nintendo 64, Super Mario 64 US/JP ROM): Ghostship (HarbourMasters, MIT; ../sm64-src, local branch nebulos-local, never pushed); native libs by ../sm64-src/nebulos/build_native.sh.
if (want("sm64")) include(":recomp-common", ":core-sm64")

// :core-echoes (GameCube, Metroid Prime 2 Echoes G2ME01 / G2MP01 + Trilogy R3ME01 / R3MP01): the Echoes native port, work copy ../prime-research/echoes-core-src (never published: personal use only);
// native libs by ../prime-research/echoes-core-src/build_native.sh (after ./rename_sdl.sh).
if (want("echoes")) include(":recomp-common", ":core-echoes")

// :core-prime1 (GameCube, Metroid Prime GM8E01 / GM8P01 + Trilogy R3ME01 / R3MP01): the Metroid Prime 1 native port, work copy ../prime-research/mp1-core-src (never published: personal use only);
// native libs by ../prime-research/mp1-core-src/build_native.sh (after ./rename_sdl.sh).
if (want("prime1")) include(":recomp-common", ":core-prime1")

// :recomp-common + :core-totk (Switch, Tears of the Kingdom only). TOTKDroid (MIT, (c) Peiikai / mamcjr) from ../totkdroid-src (local commits, never pushed):
// its Java glue is compiled into :core-totk, its runtime built with CMake into libtotk_main.so (SDL3 static, Java package com.noryan.recompsdl in
// :recomp-common). Prerequisites: ../totkdroid-src/android/deps/build_deps.sh (+ build_ffmpeg_mac.sh) and recomp-common/build_sdl_static.sh.
if (want("botw")) include(":recomp-common", ":core-botw")   // BOTWRecompAndroid (../botwrecomp-src, branch nebulos-local), same layout as :core-totk
if (want("totk")) include(":recomp-common", ":core-totk")

// :core-sm3dw (Switch, Super Mario 3D World + Bowser's Fury, update v65536 or the base 1.0.0): the Odyssey runtime (:core-smo) with 3D World as its game, its own process, game files and saves.
if (want("sm3dw")) include(":recomp-common", ":core-smo", ":core-sm3dw")

// :core-eow (Switch, The Legend of Zelda: Echoes of Wisdom 1.0.0): the Odyssey runtime (:core-smo) with Echoes of Wisdom as its game, its own process, game files and saves.
if (want("eow")) include(":recomp-common", ":core-smo", ":core-eow")
