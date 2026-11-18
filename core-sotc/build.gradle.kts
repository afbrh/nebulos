plugins {
    id("com.android.library")
}

// :core-sotc (PS2: Shadow of the Colossus, USA SCUS-97472). Celosia (github.com/HenryKun55/celosia, GPL-3.0): the game's MIPS code
// statically recompiled to C++ and run on a native runtime with an OpenGL ES 3.2 renderer. Built from ../celosia-src (local branch
// `nebulos-android`, never pushed: its origin is the upstream project) by core-sotc/native/build.sh, which stages libps2EntryRunner.so.
android {
    namespace = "com.noryan.nebulos.core.sotc"
    compileSdk = 37
    defaultConfig {
        minSdk = 30
        ndk { abiFilters += "arm64-v8a" }
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "VERSION_NAME", "\"Celosia (SotC USA, nebulos)\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    api(project(":nebulos-api"))
    implementation("androidx.core:core-ktx:1.13.1")
    testImplementation("junit:junit:4.13.2")
}
