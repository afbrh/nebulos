plugins {
    id("com.android.library")
}

// :core-smo (Nintendo Switch: Super Mario Odyssey 1.0.0 only). The TOTKDroid/BOTWRecompAndroid runtime (MIT, (c) Peiikai) with Odyssey Recomp's game
// support, consumed from ../../smorecomp-src (local branch `smo`, never pushed): its Java glue is compiled into this library (SDL3 package renamed,
// see :recomp-common) and its runtime is built with CMake into libsmo_main.so. Native-code mode
// only (the game's own ARM64 code runs on the CPU; no game code build on the device). See README-botw.md.
val src = file("../../smorecomp-src")
val deps = src.resolve("android/deps/out")                    // android/deps/build_deps.sh
val gen = layout.buildDirectory.dir("generated/smo")

android {
    namespace = "com.smorecompandroid"       // the upstream Java glue's R (driver dialog strings)
    compileSdk = 37
    ndkVersion = "30.0.16248370"
    defaultConfig {
        minSdk = 30
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                targets("smo_main")
                arguments += "-DSMO_GEN_DIR=${gen.get().asFile.resolve("inc").absolutePath}"
            }
        }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.31.6" } }
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java", src.resolve("android/smorecompandroid/app/src/main/java"))
            jniLibs.srcDirs(gen.get().asFile.resolve("jniLibs"))
            assets.srcDirs(gen.get().asFile.resolve("assets"))
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { buildConfig = true }
    buildTypes {
        // the runtime is far too slow at -O0: the CMake file forces -O2 -g whatever the build type
        release { externalNativeBuild { cmake { arguments("-DCMAKE_BUILD_TYPE=Release") } } }
        debug { externalNativeBuild { cmake { arguments("-DCMAKE_BUILD_TYPE=RelWithDebInfo") } } }
    }
}

// What the native build and the APK need: the SMAA shader as a C string (smaa_hlsl.inc), libshaderc_shared.so (runtime shader
// compiler) as a jniLib and the system-font substitute (DejaVu Sans) as an asset the Activity copies to <files>/res.
val prepareSmo by tasks.registering {
    val smaa = src.resolve("third_party/smaa/SMAA.hlsl")
    val shaderc = deps.resolve("lib/libshaderc_shared.so")
    val dejavu = deps.resolve("fonts/DejaVuSans.ttf")
    val baloo = src.resolve("third_party/fonts/Baloo2/Baloo2-SemiBold.ttf")
    val ffmpeg = file("../../smorecomp-src/android/deps/ffmpeg/lib")
    val ffLibs = listOf("libavcodec.so", "libavformat.so", "libavutil.so", "libswscale.so", "libswresample.so")
    inputs.files(smaa, shaderc, dejavu, baloo)
    inputs.files(ffLibs.map { ffmpeg.resolve(it) }.filter { it.exists() })
    outputs.dir(gen)
    doLast {
        val out = gen.get().asFile
        val bytes = smaa.readBytes().filter { it != '\r'.code.toByte() }.map { if (it < 0) '?'.code.toByte() else it }
        val lines = String(bytes.toByteArray(), Charsets.US_ASCII).split('\n').let { if (it.last().isEmpty()) it.dropLast(1) else it }
        val inc = lines.joinToString("") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\\n\"\n" }
        out.resolve("inc").mkdirs()
        out.resolve("inc/smaa_hlsl.inc").writeText(inc)
        shaderc.copyTo(out.resolve("jniLibs/arm64-v8a/libshaderc_shared.so"), overwrite = true)
        for (l in ffLibs) if (ffmpeg.resolve(l).exists()) ffmpeg.resolve(l).copyTo(out.resolve("jniLibs/arm64-v8a/$l"), overwrite = true)
        dejavu.copyTo(out.resolve("assets/res/DejaVuSans.ttf"), overwrite = true)
        baloo.copyTo(out.resolve("assets/res/Baloo2-SemiBold.ttf"), overwrite = true)
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(prepareSmo) }

dependencies {
    api(project(":nebulos-api"))
    api(project(":recomp-common"))
    implementation("androidx.core:core-ktx:1.13.1")
    testImplementation("junit:junit:4.13.2")
}
tasks.matching { it.name.startsWith("configureCMake") || it.name.startsWith("buildCMake") }.configureEach { dependsOn(prepareSmo) }
