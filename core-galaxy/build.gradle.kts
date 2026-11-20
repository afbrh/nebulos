plugins {
    id("com.android.library")
}

// :core-galaxy (Nintendo Wii: Super Mario Galaxy, revision 0 only: RMGE01 / RMGP01 / RMGJ01). Nebula (GPL-3.0, keepvaibin/nebula: the SMGCommunity Petari
// decompilation compiled for the host + an SDL3/Vulkan renderer), consumed from ../../smg-nebula (local commit on top of upstream, never pushed): its Android
// game activity (Java), SDL3's Java half and its native libraries are built into this library. The native side is built by
// ../../smg-nebula-hosttools/build_android.sh (it fills smg-nebula/platform/android/jniLibs/arm64-v8a); see README-galaxy.md.
val nebula = file("../../smg-nebula")
val sdl = file("../../smg-nebula-deps/SDL3-3.4.18")

android {
    namespace = "dev.nebula.galaxy"        // Nebula's Java package: libmain.so finds GameActivity's native methods by this name
    compileSdk = 37
    defaultConfig {
        minSdk = 30
        ndk { abiFilters += "arm64-v8a" }
    }
    sourceSets {
        getByName("main") {
            java.srcDirs(
                "src/main/java",
                nebula.resolve("platform/android/app/src/main/java"),
                nebula.resolve("platform/android/app/src/shipped/java"),
                sdl.resolve("android-project/app/src/main/java"),
            )
            jniLibs.srcDirs(nebula.resolve("platform/android/jniLibs"))
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { buildConfig = true }
    packaging { jniLibs { useLegacyPackaging = true } }   // the host loads the game module by its path
}

dependencies {
    api(project(":nebulos-api"))
    api(project(":recomp-common"))
    implementation("androidx.core:core-ktx:1.13.1")
}
