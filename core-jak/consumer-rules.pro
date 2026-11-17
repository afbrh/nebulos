# libjak_main.so (SDL3, static) looks these up by name through JNI.
-keep class com.noryan.nebulos.core.jak.sdl.** { *; }
-keep class com.noryan.nebulos.core.jak.JakNative { *; }
-keep class com.noryan.nebulos.core.jak.JakActivity { public void onNebulosEvent(int, java.lang.String, java.lang.String); }
