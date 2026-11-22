plugins {
    id("com.android.library")
}

// :core-eow (Nintendo Switch: The Legend of Zelda: Echoes of Wisdom 1.0.0 (SDK 16.2.0)). The same native-ARM64 runtime as :core-smo (libsmo_main.so, built from ../../smorecomp-src; its Java glue and
// SDL3 package are :core-smo's): this module only adds the game: its descriptor, its screen (`:eow` process) and its first-launch extraction of the cartridge's base game program
// (hactool, libhactool.so from :core-smo; the game's RomFS is stored compressed, which hactool reads through nca_comp.c). See README-eow.md.
android {
    namespace = "com.noryan.nebulos.core.eow"
    compileSdk = 37
    defaultConfig { minSdk = 30 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    api(project(":core-smo"))
    implementation("androidx.core:core-ktx:1.13.1")
}
