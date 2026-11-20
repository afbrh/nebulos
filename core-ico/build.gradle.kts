plugins {
    id("com.android.library")
}

// :core-ico (PS2: ICO). ico-pc (github.com/nathanialf/ico-pc, MIT: the ICO decompilation as a native SDL3 + Vulkan port) from ../../ico-pc
// (local branch `nebulos-local`, never pushed). Its CMake build (the android-arm64 build) is run from this module on the Mac with a
// source-built DXC (tools/toolchain/deps/dxc, see README-ico.md); SDL3 is linked STATICALLY (renamed Java package com.noryan.recompsdl,
// :recomp-common) so the library is libico_main.so. The app's own Java (IcoActivity, ExitRecords) is compiled in, package com.defnf.icopc.
val src = file("../../ico-pc")
val sdlStatic = file("../../botwrecomp-src/android/deps/out-static")           // recomp-common/build_sdl_static.sh
val vpad = file("../recomp-common/src/main/cpp/recomp_vpad.c")
val adrenotools = file("../../ico-pc/tools/toolchain/deps/adrenotools/8fae8ce254dfc1344527e05301e43f37dea2df80")
val gen = layout.buildDirectory.dir("generated/ico")

android {
    namespace = "com.noryan.nebulos.core.ico"
    compileSdk = 37
    ndkVersion = "28.2.13676358"          // the NDK ico-pc pins
    defaultConfig {
        minSdk = 30
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField("String", "VERSION_NAME", "\"ico-pc v0.11.12+ (nebulos patches)\"")
        externalNativeBuild {
            cmake {
                targets("ico_pc", "main_hook", "hook_impl", "file_redirect_hook")
                arguments += listOf(
                    "-DCMAKE_TOOLCHAIN_FILE=${src.absolutePath}/cmake/toolchains/android-arm64.cmake",
                    "-DANDROID_STL=c++_static", "-DICO_HEADLESS=OFF", "-DICO_LINK_EXE=ON", "-DICO_VERSION_LABEL=v0.11.12", "-DICO_RC_TOOLS=OFF",
                    "-DICO_RHI_D3D12=OFF", "-DICO_RHI_D3D11=OFF", "-DICO_RHI_OPENGL=OFF", "-DICO_SHADERS_DXIL=OFF", "-DICO_SHADERS_GLSL=OFF",
                    "-DICO_ADRENOTOOLS_SRC=${adrenotools.absolutePath}",
                    "-DICO_NEBULOS_SDL3_STATIC=${sdlStatic.absolutePath}", "-DICO_NEBULOS_VPAD_SOURCE=${vpad.absolutePath}",
                    "-DCMAKE_JOB_POOLS=compile=3;link=1", "-DCMAKE_JOB_POOL_COMPILE=compile", "-DCMAKE_JOB_POOL_LINK=link",
                )
            }
        }
    }
    externalNativeBuild { cmake { path = src.resolve("CMakeLists.txt"); version = "4.5.0" } }
    sourceSets { getByName("main") { assets.srcDirs(gen.get().asFile.resolve("assets")) } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { buildConfig = true }
    buildTypes {
        release { externalNativeBuild { cmake { arguments("-DCMAKE_BUILD_TYPE=RelWithDebInfo") } } }
        debug { externalNativeBuild { cmake { arguments("-DCMAKE_BUILD_TYPE=RelWithDebInfo") } } }
    }
}

// ico-pc's APK assets: the legacy save-state tables (release entries only) and a VERSION.txt
val prepareIco by tasks.registering {
    val legacy = src.resolve("port/state_legacy")
    inputs.dir(legacy)
    outputs.dir(gen)
    doLast {
        val out = gen.get().asFile.resolve("assets")
        out.deleteRecursively(); out.mkdirs()
        val index = legacy.resolve("index.txt")
        if (index.isFile) {
            val kept = mutableListOf<String>()
            val outLegacy = out.resolve("state_legacy").also { it.mkdirs() }
            index.forEachLine { line ->
                val t = line.trim().split(Regex("\\s+"))
                if (line.startsWith("#") || t.size != 4 || t[1] == "rc") return@forEachLine
                kept += line
                legacy.resolve(t[2]).copyRecursively(outLegacy.resolve(t[2]), overwrite = true)
            }
            outLegacy.resolve("index.txt").writeText(kept.joinToString("") { it + "\n" })
        }
        out.resolve("VERSION.txt").writeText("ico-pc v0.11.12 (Nebulos core)\n")
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(prepareIco) }

dependencies {
    api(project(":nebulos-api"))
    api(project(":recomp-common"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.window:window:1.4.0")
    implementation("androidx.window:window-java:1.4.0")
    testImplementation("junit:junit:4.13.2")
}
