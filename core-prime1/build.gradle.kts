plugins {
    id("com.android.library")
}

// :core-prime1 (Nintendo GameCube: Metroid Prime, GM8E01 / GM8P01, and the Metroid Prime Trilogy discs R3ME01 / R3MP01, whose Metroid Prime part the port plays). The Metroid Prime 1
// native port (a vanished author's decompilation-based PC port, derived from the game's executable, no license: PERSONAL USE ONLY, never published) as a one-game core.
// Consumed from ../../prime-research/mp1-core-src (work copy of the port, edits listed in README-prime1.md): its Android Java (port activity, touch overlay, Aurora surface, SDL3's
// Java glue with the package renamed org.libsdl.app -> dev.prime1.sdl) is compiled into this library, and its native libraries are built by
// ../../prime-research/mp1-core-src/build_native.sh into nebulos-jniLibs/arm64-v8a. See README-prime1.md.
val port = file("../../prime-research/mp1-core-src")

android {
    namespace = "dev.prime1.core"
    compileSdk = 37
    defaultConfig {
        minSdk = 30
        ndk { abiFilters += "arm64-v8a" }
    }
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java", port.resolve("android/app/src/main/java"))
            jniLibs.srcDirs(port.resolve("nebulos-jniLibs"))
            assets.srcDir(file("build/generated/prime1Assets"))   // the button glyphs (textures/) and the pipeline cache seed, unpacked by the port's activity into files/prime1
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { buildConfig = true }
    packaging { jniLibs { useLegacyPackaging = true } }   // the port loads the Vulkan shim and the driver hooks by their path in the native library folder
}

val syncPrime1Assets = tasks.register<Sync>("syncPrime1Assets") {
    into(file("build/generated/prime1Assets"))
    from(port.resolve("textures")) { into("textures") }
    from(port.resolve("assets/initial_pipeline_cache.db"))
}
tasks.named("preBuild") { dependsOn(syncPrime1Assets) }

dependencies {
    api(project(":nebulos-api"))
    api(project(":recomp-common"))
    implementation("androidx.core:core-ktx:1.13.1")
}
