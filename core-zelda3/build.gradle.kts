plugins {
    id("com.android.library")
}

// :core-zelda3 (Super Nintendo: The Legend of Zelda: A Link to the Past, US ROM = SHA-1 6d4f10a8...). zelda3 (snesrev, MIT: a reverse-engineered reimplementation of
// the game in C) from ../../zelda3-src (local branch `nebulos-local`, never pushed), with SDL2 2.32.10 whose Java package is renamed to
// com.noryan.nebulos.core.zelda3.sdl (zelda3-src/nebulos/rename_sdl.sh; run it once before building). The game data is built on the device from the user's own ROM
// (Zelda3Assets.kt); nothing from the game is bundled. See README-zelda3.md.
val port = file("../../zelda3-src")
val iniDir = file("build/generated/zelda3Assets")

android {
    namespace = "com.noryan.nebulos.core.zelda3"
    compileSdk = 37
    ndkVersion = "28.2.13676358"
    defaultConfig {
        minSdk = 30
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { targets("zelda3_main"); arguments += "-DCMAKE_BUILD_TYPE=Release" } }
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

// the default settings file (zelda3-src/nebulos/zelda3.ini) -> assets/zelda3/zelda3.ini
val packZelda3Ini = tasks.register<Copy>("packZelda3Ini") {
    from(port.resolve("nebulos/zelda3.ini"))
    into(iniDir.resolve("zelda3"))
}
tasks.named("preBuild") { dependsOn(packZelda3Ini) }

dependencies {
    api(project(":nebulos-api"))
    api(project(":recomp-common"))
    implementation("androidx.core:core-ktx:1.13.1")
}
