plugins {
    id("com.android.library")
}

// :core-smg2 (Nintendo Wii: Super Mario Galaxy 2, USA SB4E01 revision 0): Astral Italian (CC0 project code, an AI-built continuation of the Garigari decompilation) as a native
// port, NOT an emulator: the game compiled for the device (SDL3 + Aurora/Dawn on Vulkan), the disc read by `nod`. Work copy ../../astral-src (local branch `nebulos-android`, never pushed).
// Its Android Java (SDL3's glue with the package renamed org.libsdl.app -> dev.smg2.sdl, Aurora's AuroraSurface renamed dev.smg2.aurora) is compiled into this library from
// astral-src/build/android-deps (made by ../../smg2-research/rename_sdl.sh); the native library by ../../smg2-research/android_build.sh into astral-src/nebulos-jniLibs. See README-smg2.md.
val port = file("../../astral-src")
val deps = port.resolve("build/android-deps")

android {
    namespace = "dev.smg2.core"
    compileSdk = 37
    defaultConfig {
        minSdk = 30
        ndk { abiFilters += "arm64-v8a" }
    }
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java", deps.resolve("sdl/android-project/app/src/main/java"), deps.resolve("aurora/platforms/android/java"))
            jniLibs.srcDirs(port.resolve("nebulos-jniLibs"))
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { buildConfig = true }
    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    api(project(":nebulos-api"))
    api(project(":recomp-common"))
    implementation("androidx.core:core-ktx:1.13.1")
}
