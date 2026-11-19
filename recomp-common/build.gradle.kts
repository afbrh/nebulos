plugins {
    id("com.android.library")
}

// :recomp-common - what the two Switch recomp cores (:core-botw = BOTWRecompAndroid, :core-totk = TOTKDroid; both MIT) share:
//  * the SDL3 Java side with its package renamed org.libsdl.app -> com.noryan.recompsdl (SDL3 itself is linked STATICALLY into each
//    core's native library, built by build_sdl_static.sh), so it never clashes with ARMSX2's SDL3 classes;
//  * RecompPad (the JNI side of the SDL virtual gamepad Nebulos feeds, src/main/cpp/recomp_vpad.c);
//  * input mapping + the CoreSession both cores use.
android {
    namespace = "com.noryan.nebulos.recomp"
    compileSdk = 37
    defaultConfig { minSdk = 30; ndk { abiFilters += "arm64-v8a" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    api(project(":nebulos-api"))
    implementation("androidx.core:core-ktx:1.13.1")
    testImplementation("junit:junit:4.13.2")
}
