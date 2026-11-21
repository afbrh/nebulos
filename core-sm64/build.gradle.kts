import java.security.MessageDigest

plugins {
    id("com.android.library")
}

// :core-sm64 (Nintendo 64: Super Mario 64, US ROM = SHA-1 9bef1128...; the JP ROM Ghostship also accepts is accepted too). Ghostship (HarbourMasters, MIT; libultraship MIT; Torch MIT) is
// consumed from ../../sm64-src (local branch `nebulos-local`, never pushed). Its native libraries (libsm64_main.so + libSDL2_sm64.so) are built by ../../sm64-src/nebulos/build_native.sh into
// nebulos/jniLibs/arm64-v8a; SDL2's Java glue (package renamed to com.noryan.nebulos.core.sm64.sdl by ../../sm64-src/nebulos/rename_sdl.sh) is compiled into this module.
// Game data (Torch recipes + the engine archive ghostship.o2r) rides in assets/sm64data.zip and is unpacked into files/sm64 on first launch. See README-sm64.md.
val port = file("../../sm64-src")
val dataZipDir = file("build/generated/sm64Assets")

android {
    namespace = "com.noryan.nebulos.core.sm64"
    compileSdk = 37
    defaultConfig {
        minSdk = 30
        ndk { abiFilters += "arm64-v8a" }
    }
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java", port.resolve("nebulos/deps/SDL2/android-project/app/src/main/java"))
            jniLibs.srcDirs(port.resolve("nebulos/jniLibs"))
            assets.srcDir(dataZipDir)
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { buildConfig = true }
    packaging { jniLibs { useLegacyPackaging = true } }   // the engine's libraries are loaded by name from the native library folder
}

// The Torch recipes (config.yml + assets/) and the engine archive, in one reproducible zip (fixed timestamps), plus its digest (the launcher re-unpacks when it changes).
val packSm64Data = tasks.register<Zip>("packSm64Data") {
    archiveFileName.set("sm64data.zip")
    destinationDirectory.set(dataZipDir.resolve("sm64"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(port.resolve("config.yml"))
    from(port.resolve("ghostship.o2r"))
    from(port.resolve("assets")) { into("assets") }
    doLast {
        val zip = archiveFile.get().asFile
        val md5 = MessageDigest.getInstance("MD5").digest(zip.readBytes()).joinToString("") { "%02x".format(it) }
        File(zip.parentFile, "sm64data.version").writeText(md5)
    }
}
tasks.named("preBuild") { dependsOn(packSm64Data) }

dependencies {
    api(project(":nebulos-api"))
    api(project(":recomp-common"))
    implementation("androidx.core:core-ktx:1.13.1")
}
