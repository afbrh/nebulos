plugins {
    id("com.android.library")
}

// :core-mp4 (Nintendo Switch: Metroid Prime 4: Beyond 1.1.0 only). The same native-ARM64 runtime as :core-smo (libsmo_main.so, built from ../../smorecomp-src; its Java glue and
// SDL3 package are :core-smo's): this module only adds the game: its descriptor, its screen (`:mp4` process) and its first-launch extraction of the cartridge's base
// game plus the update the cartridge carries (hactool, libhactool.so from :core-smo). See README-mp4.md.
android {
    namespace = "com.noryan.nebulos.core.mp4"
    compileSdk = 37
    defaultConfig { minSdk = 30 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    api(project(":core-smo"))
    implementation("androidx.core:core-ktx:1.13.1")
}
