# libps2EntryRunner.so looks these up by name through JNI (JNI_OnLoad / RegisterNatives by symbol name).
-keep class com.noryan.nebulos.core.sotc.SotcNative { *; }
-keep class com.noryan.nebulos.core.sotc.SotcActivity { *; }
