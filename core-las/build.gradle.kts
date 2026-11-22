plugins {
    id("com.android.library")
}

// :core-las (Nintendo Switch: The Legend of Zelda: Link's Awakening (the 2019 remake; base 1.0.0 and the update, 1.0.1/1.1.0, both run)). The same native-ARM64 runtime as :core-smo (libsmo_main.so, built from ../../smorecomp-src; its Java glue and
// SDL3 package are :core-smo's): this module only adds the game: its descriptor, its screen (`:las` process) and its first-launch extraction of the cartridge's base
// game plus the update the cartridge carries (hactool, libhactool.so from :core-smo). See README-las.md.
android {
    namespace = "com.noryan.nebulos.core.las"
    compileSdk = 37
    defaultConfig { minSdk = 30 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    api(project(":core-smo"))
    implementation("androidx.core:core-ktx:1.13.1")
}
