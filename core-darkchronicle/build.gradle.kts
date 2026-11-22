plugins {
    id("com.android.library")
}

// :core-darkchronicle (PS2: Dark Cloud 2 / Dark Chronicle, PAL SCES-51190). ChronicleTwo (github.com/TheMoonPeople/ChronicleTwo, the PAL decompilation and its
// native SDL3 + Vulkan port) from ../../ChronicleTwo (local branch `nebulos-local`, never pushed: its origin is the upstream project). The native library
// (libdarkchronicle_main.so, SDL3 linked statically, renamed Java package) is built OUTSIDE Gradle by ./build_native.sh (CMake + Cargo: the port's Rust
// extractor and C++ bridge) and picked up from ChronicleTwo/nebulos-jniLibs, so a shared Gradle build never waits on the multi-hour native build.
// See README-darkchronicle.md.
val src = file("../../ChronicleTwo")
val jniStage = src.resolve("nebulos-jniLibs")
val dcAssets = layout.buildDirectory.dir("generated/dcAssets").get().asFile

android {
    namespace = "com.noryan.nebulos.core.darkchronicle"
    compileSdk = 37
    defaultConfig {
        minSdk = 30
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField("String", "VERSION_NAME", "\"ChronicleTwo (Dark Chronicle, nebulos patches)\"")
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDir(jniStage)
            assets.srcDir(dcAssets)     // scripts/, glyphs/ and fonts/ the game reads beside its run directory
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { buildConfig = true }
}

// The port's non-disc runtime files (Luau scripts, button glyphs, fonts) come from the repository, not from the disc.
val prepareDcAssets = tasks.register<Copy>("prepareDarkChronicleAssets") {
    into(dcAssets)
    from(src.resolve("port/src/luau")) { into("darkchronicle/scripts") }
    from(src.resolve("port/assets/glyphs")) { into("darkchronicle/glyphs") }
    from(src.resolve("port/assets/fonts")) { into("darkchronicle/fonts") }
    // the build's gameplay seed archive: the extractor checks the disc conversion against it (copied beside data/ only while extracting)
    from(src.resolve("port/build/android/assets")) { include("data.zip"); into("darkchronicle-seed") }
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach { dependsOn(prepareDcAssets) }

dependencies {
    api(project(":nebulos-api"))
    api(project(":recomp-common"))
    implementation("androidx.core:core-ktx:1.13.1")
}
