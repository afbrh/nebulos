plugins {
    id("com.android.library")
}

// :core-smw (Super Nintendo: Super Mario World, US ROM = SHA-1 6b47bb75...). smw (snesrev, MIT: a reimplementation of the game in C) from ../../smw-src (local branch
// `nebulos-local`, never pushed), with SDL2 2.32.10 whose Java package is renamed to com.noryan.nebulos.core.smw.sdl (smw-src/nebulos/rename_sdl.sh; run it once
// before building). The game data is built on the device from the user's own ROM (SmwAssets.kt); nothing from the game is bundled. See README-smw.md.
val port = file("../../smw-src")
val iniDir = file("build/generated/smwAssets")

android {
    namespace = "com.noryan.nebulos.core.smw"
    compileSdk = 37
    ndkVersion = "28.2.13676358"
    defaultConfig {
        minSdk = 30
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { targets("smw_main"); arguments += "-DCMAKE_BUILD_TYPE=Release" } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.31.6" } }
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java", port.resolve("nebulos/deps/SDL2/android-project/app/src/main/java"))
            assets.srcDir(iniDir)
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { jniLibs { useLegacyPackaging = true } }
}

// the default settings file (smw-src/nebulos/smw.ini) -> assets/smw/smw.ini
val packSmwIni = tasks.register<Copy>("packSmwIni") {
    from(port.resolve("nebulos/smw.ini"))
    into(iniDir.resolve("smw"))
}
tasks.named("preBuild") { dependsOn(packSmwIni) }

dependencies {
    api(project(":nebulos-api"))
    api(project(":recomp-common"))
    implementation("androidx.core:core-ktx:1.13.1")
}
