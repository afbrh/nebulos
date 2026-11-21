plugins {
    id("com.android.library")
}

// :core-echoes (Nintendo GameCube: Metroid Prime 2 Echoes, G2ME01 / G2MP01, and the Metroid Prime Trilogy discs R3ME01 / R3MP01). The Metroid Prime 2 Echoes native
// port (Odrannnn's decompilation-based PC port, derived from the game's executable, no license: PERSONAL USE ONLY, never published) as a one-game core. Consumed from
// ../../prime-research/echoes-core-src (work copy of the port, edits listed in README-echoes.md): its Android Java (port activity, touch overlay, Aurora surface, SDL3's
// Java glue with the package renamed org.libsdl.app -> dev.echoes.sdl) is compiled into this library, and its native libraries are built by
// ../../prime-research/echoes-core-src/build_native.sh into nebulos-jniLibs/arm64-v8a. See README-echoes.md.
val port = file("../../prime-research/echoes-core-src")

android {
    namespace = "dev.echoes.core"
    compileSdk = 37
    defaultConfig {
        minSdk = 30
        ndk { abiFilters += "arm64-v8a" }
    }
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java", port.resolve("android/app/src/main/java"))
            jniLibs.srcDirs(port.resolve("nebulos-jniLibs"))
            assets.srcDir(file("build/generated/echoesAssets"))   // the button glyphs (textures/), unpacked by the port's activity
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { buildConfig = true }
    packaging { jniLibs { useLegacyPackaging = true } }   // the port loads the Vulkan shim and the driver hooks by their path in the native library folder
}

val syncEchoesAssets = tasks.register<Sync>("syncEchoesAssets") {
    into(file("build/generated/echoesAssets"))
    from(port.resolve("textures")) { into("textures") }
}
tasks.named("preBuild") { dependsOn(syncEchoesAssets) }

dependencies {
    api(project(":nebulos-api"))
    api(project(":recomp-common"))
    implementation("androidx.core:core-ktx:1.13.1")
}
