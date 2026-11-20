plugins {
    id("com.android.library")
}

// :core-sunshine (Nintendo GameCube: Super Mario Sunshine, US revision 0 = GMSE01). The Super Mario Sunshine PC port (chasem-dev/sms-pc-port, MIT; built from the
// doldecomp/sms decompilation) as consumed by chasem-dev/sms-android: ../../sms-src/sms-android/sms-port (local checkout, never pushed). Its CMake build runs from this
// module (targets sms, smsbootstrap, SDL2), with SDL2's Java package renamed org.libsdl.app -> dev.chasem.sms.sdl by ../../sms-src/rename_sdl2.sh (the APK also carries other
// cores' org.libsdl.app). See README-sunshine.md.
val port = file("../../sms-src/sms-android/sms-port")
val sdl = port.resolve("build/deps/SDL2-2.32.10")

android {
    namespace = "dev.chasem.sms.core"
    compileSdk = 37
    ndkVersion = "28.2.13676358"
    defaultConfig {
        minSdk = 30
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DSMS_ARCH=64", "-DSMS_GX_BUILD_TESTS=OFF", "-DSMS_SDL2_SOURCE=${sdl.absolutePath}", "-DSMS_BUNDLE_DISC=")
                targets("sms", "smsbootstrap", "SDL2")
            }
        }
    }
    externalNativeBuild { cmake { path = port.resolve("CMakeLists.txt"); version = "3.31.6" } }
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java", sdl.resolve("android-project/app/src/main/java"))
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { jniLibs { useLegacyPackaging = true } }   // the game loads its libraries by path
}

dependencies {
    api(project(":nebulos-api"))
    api(project(":recomp-common"))
    implementation("androidx.core:core-ktx:1.13.1")
}
