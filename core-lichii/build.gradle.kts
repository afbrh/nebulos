plugins {
    id("com.android.library")
}

android {
    namespace = "com.noryan.nebulos.core.lichii"
    compileSdk = 37
    defaultConfig {
        minSdk = 30
        // Lichii's library declares a "lichiiVersion" flavor dimension (mainline/genshinSpoof/legacy/chromeOS); mainline is the performance build (minSdk 33; the app manifest overrides the library minSdk, Switch 2 games need a recent phone anyway).
        missingDimensionStrategy("lichiiVersion", "mainline")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { jniLibs.useLegacyPackaging = true } // Eden needs extracted libs (libadrenotools driver loading)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // android.jar org.json is a stub on the JVM
    implementation(project(":nebulos-api"))
    // Lichii's android project (../lichii-android-src/src/android, :app converted to a library) via includeBuild in settings.
    // Native lib: liblichii-android.so; Java package com.noryan.lichii (relocated from org.yuzu.yuzu_emu so it can sit beside Eden).
    implementation("com.noryan.lichii:app:embedded")
    // Eden is compiled against Material 1.12.0; newer versions drop R.attr.colorPrimary -> NoSuchFieldError in
    // EmulationFragment (found by RomRunner). Strictly pinned so it propagates to the app.
    implementation("com.google.android.material:material") { version { strictly("1.12.0") } }
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
}
