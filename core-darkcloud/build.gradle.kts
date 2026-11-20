plugins {
    id("com.android.library")
}

// :core-darkcloud (PS2: Dark Cloud, the first game). Chronicle (github.com/TheMoonPeople/Chronicle, the Dark Cloud decompilation and its native
// SDL3 + Vulkan port) from ../../Chronicle (local branch `nebulos-local`, never pushed: its origin is the upstream project). Its CMake
// build (PLATFORM=ANDROID) is run from here; SDL3 is linked STATICALLY (renamed Java package com.noryan.recompsdl, :recomp-common), and the
// library is libchronicle_main.so. See README-darkcloud.md.
val src = file("../../Chronicle")
val sdlStatic = file("../../botwrecomp-src/android/deps/out-static")           // recomp-common/build_sdl_static.sh
val vpad = file("../recomp-common/src/main/cpp/recomp_vpad.c")
val glslang = File(System.getProperty("user.home"), "android-dev/bin/glslangValidator")

android {
    namespace = "com.noryan.nebulos.core.darkcloud"
    compileSdk = 37
    ndkVersion = "27.0.12077973"          // the NDK Chronicle's own Android build pins (C++26)
    defaultConfig {
        minSdk = 30
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField("String", "VERSION_NAME", "\"Chronicle (Dark Cloud, nebulos patches)\"")
        externalNativeBuild {
            cmake {
                targets("chronicle_main")
                arguments += listOf(
                    "-DPLATFORM=ANDROID", "-DANDROID_STL=c++_static", "-DDC_BUILD_TESTS=OFF",
                    "-DDC_NEBULOS_SDL3_STATIC=${sdlStatic.absolutePath}", "-DDC_NEBULOS_VPAD_SOURCE=${vpad.absolutePath}",
                    "-DGLSLANG_VALIDATOR=${glslang.absolutePath}",
                )
                cppFlags += "-std=c++26"
            }
        }
    }
    externalNativeBuild { cmake { path = src.resolve("CMakeLists.txt"); version = "3.31.6" } }
    sourceSets { getByName("main") { assets.srcDirs(src.resolve("port/android/app/src/main/assets")) } }   // translated strings, fonts, button glyphs
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { buildConfig = true }
    buildTypes {
        // the port is far too slow at -O0: optimise both variants
        release { externalNativeBuild { cmake { arguments("-DCMAKE_BUILD_TYPE=Release") } } }
        debug { externalNativeBuild { cmake { arguments("-DCMAKE_BUILD_TYPE=RelWithDebInfo") } } }
    }
}

dependencies {
    api(project(":nebulos-api"))
    api(project(":recomp-common"))
    implementation("androidx.core:core-ktx:1.13.1")
    testImplementation("junit:junit:4.13.2")
}
