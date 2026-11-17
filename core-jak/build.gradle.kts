plugins {
    id("com.android.library")
}

// :core-jak (PS2: Jak and Daxter trilogy). OpenGOAL (ISC license, github.com/open-goal/jak-project) is a native port of the games'
// decompiled GOAL code, built from ../jak-android-src (local branch `android-port`, never pushed: its origin is the upstream project).
// Native libraries (not committed, produced by core-jak/native/build.sh): libjak_main.so (the game; SDL3 statically linked, SDL's Java/JNI
// package renamed to com.noryan.nebulos.core.jak.sdl) and libjak_extractor.so (an executable: ISO -> game data, run on the device).
android {
    namespace = "com.noryan.nebulos.core.jak"
    compileSdk = 37
    defaultConfig {
        minSdk = 30
        ndk { abiFilters += "arm64-v8a" }
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "VERSION_NAME", "\"OpenGOAL (jak-project android-port, nebulos)\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    api(project(":nebulos-api"))
    implementation("androidx.core:core-ktx:1.13.1")
    testImplementation("junit:junit:4.13.2")
}
