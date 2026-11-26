plugins {
    id("com.android.library")
}

// :core-supermetroid (Super Nintendo: Super Metroid, Japan/USA ROM = SHA-1 da957f0d...). The snesrev/sm decompilation (MIT, the game's logic in C) with the fixes
// of nickdesaulniers/sm, from ../../sm-snes-src (local branch `nebulos-local`, never pushed), with SDL2 2.32.10 whose Java package is renamed to
// com.noryan.nebulos.core.supermetroid.sdl (sm-snes-src/nebulos/rename_sdl.sh; run it once before building). The game reads the user's own ROM; nothing
// from the game is bundled. See README-supermetroid.md.
val port = file("../../sm-snes-src")
val iniDir = file("build/generated/supermetroidAssets")

android {
    namespace = "com.noryan.nebulos.core.supermetroid"
    compileSdk = 37
    ndkVersion = "28.2.13676358"
    defaultConfig {
        minSdk = 30
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { targets("supermetroid_main"); arguments += "-DCMAKE_BUILD_TYPE=Release" } }
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

// the default settings file (sm-snes-src/nebulos/sm.ini) -> assets/supermetroid/sm.ini
val packSupermetroidIni = tasks.register<Copy>("packSupermetroidIni") {
    from(port.resolve("nebulos/sm.ini"))
    into(iniDir.resolve("supermetroid"))
}
tasks.named("preBuild") { dependsOn(packSupermetroidIni) }

dependencies {
    api(project(":nebulos-api"))
    api(project(":recomp-common"))
    implementation("androidx.core:core-ktx:1.13.1")
}
