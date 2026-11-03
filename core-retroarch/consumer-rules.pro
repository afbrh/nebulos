# RetroArch's native side calls back into its Java layer and our bridge by name over JNI.
-keep class com.retroarch.** { *; }
-keep class com.libretro.** { *; }
-keep class com.noryan.nebulos.core.retroarch.RaBridge { *; }
-keep class com.noryan.nebulos.core.retroarch.NebulosRetroActivity { *; }
