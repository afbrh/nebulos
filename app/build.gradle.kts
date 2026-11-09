import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.3.12"
    id("com.google.dagger.hilt.android") version "2.60.1"
}

android {
    // NebulosApp extends NebulosHiltApplication, which :core-watermelonds provides; a build without that core gets a plain stand-in.
    if (rootProject.findProject(":core-watermelonds") == null) sourceSets.getByName("main").kotlin.directories.add("src/noWatermelon/java")
    namespace = "com.noryan.nebulos"
    compileSdk = 37
    ndkVersion = "30.0.16248370"   // the NDK Eden is built with; AGP also needs one to find the strip tool for the native libraries

    defaultConfig {
        applicationId = "com.noryan.nebulos"
        minSdk = 30
        targetSdk = 35
        // Release numbering is a plain count: every release adds 1 to versionName (the 9th release is "9"). versionCode also adds 1 per
        // release; it is 17 for release 15 (release 14 was 16, release 13 was 15, release 12 was 14, release 11 was 13, release 10 was 12, release 9 was 11) because 0.7.0 already used 10 and Android never installs a lower code over a higher one.
        versionCode = 17
        versionName = "15"
        ndk { abiFilters += "arm64-v8a" }
        missingDimensionStrategy("edenVersion", "legacy")
    }
    // Release signing: key + passwords live outside the repo (~/.nebulos-signing.properties, falling back to the legacy ~/.romcore-signing.properties); without it the release build is unsigned.
    val signingProps = Properties()
    listOf(".nebulos-signing.properties", ".romcore-signing.properties").map { File(System.getProperty("user.home"), it) }
        .firstOrNull { it.exists() }?.inputStream()?.use { signingProps.load(it) }
    signingConfigs {
        if (signingProps.isNotEmpty()) create("release") {
            storeFile = file(signingProps.getProperty("storeFile")); storePassword = signingProps.getProperty("storePassword")
            keyAlias = signingProps.getProperty("keyAlias"); keyPassword = signingProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        // R8 is ON in debug too: ARMSX2's Settings class fails bytecode verification un-shrunk (VerifyError).
        getByName("debug") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("release") {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { checkReleaseBuilds = false }   // lintVital trips over core-dolphin's asset staging task wiring
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        // Several cores (adrenotools users: azahar, armsx2, eden, cemu, dolphin) load hook libs from nativeLibraryDir.
        jniLibs.useLegacyPackaging = true
        jniLibs.pickFirsts += listOf(
            "lib/arm64-v8a/libhook_impl.so", "lib/arm64-v8a/libmain_hook.so",
            "lib/arm64-v8a/libgsl_alloc_hook.so", "lib/arm64-v8a/libfile_redirect_hook.so",
            "lib/arm64-v8a/libc++_shared.so", "lib/arm64-v8a/libusb-1.0.so",
            "lib/arm64-v8a/libVkLayer_khronos_validation.so", "lib/arm64-v8a/libSPIRV-Tools-shared.so",
            "lib/arm64-v8a/libshaderc_shared.so",
        )
    }
}

dependencies {
    implementation(project(":nebulos-api"))
    // Core modules are optional at build time: each is linked in only if its module exists in settings.gradle.kts.
    // CoreBootstrap.kt finds and registers them reflectively, so nothing here is referenced from code directly.
    // Override with -Pnebulos.cores=none or -Pnebulos.cores=dolphin,eden to link a subset (e.g. while a core is mid-work).
    val wanted = (findProperty("nebulos.cores") as String?)?.split(",")?.map { it.trim() }
    listOf("retroarch", "dolphin", "primehack", "cemu", "eden", "azahar", "watermelonds", "armsx2", "dusklight", "shipwright", "twoship", "wwhd").forEach { id ->
        if ((wanted == null || id in wanted) && rootProject.findProject(":core-$id") != null) implementation(project(":core-$id"))
    }

    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-android-compiler:2.60.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
}

// Eden is compiled against Material 1.12.0 (newer drops R.attr.colorPrimary -> NoSuchFieldError); other cores ask for
// 1.13-1.14. One Material in the APK: force the old one.
configurations.configureEach {
    resolutionStrategy.force("com.google.android.material:material:1.12.0")
    // com.atlassian.commonmark:commonmark 0.13 and org.commonmark:commonmark 0.22 ship identical packages.
    exclude(group = "com.atlassian.commonmark")
}

// Security policy: the packaged manifest must never request network access. Fails assemble if any library re-adds it.
tasks.register("verifyNoNetworkPermission") {
    val manifest = layout.buildDirectory.file("intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml")
    doLast {
        val text = manifest.get().asFile.readText()
        check(!text.contains("android.permission.INTERNET")) { "Merged manifest requests android.permission.INTERNET; Nebulos must not use the network." }
    }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy("verifyNoNetworkPermission") }
