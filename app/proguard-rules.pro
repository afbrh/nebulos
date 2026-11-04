# Cores are registered reflectively by name (CoreBootstrap).
-keep class com.noryan.nebulos.core.** { *; }
-keep class com.noryan.nebulos.api.** { *; }
-keep class com.noryan.nebulos.NebulosApp { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn **
-ignorewarnings
# Azahar's SettingsFile reads/writes config.ini through ini4j, which instantiates its parser/handlers reflectively
# (org.ini4j.spi.IniParser/IniBuilder/... via ServiceFinder + META-INF services). R8 stripping them silently dropped
# every Nebulos default (GL/4x/dual display).
-keep class org.ini4j.** { *; }
-keepclassmembers class * implements org.ini4j.spi.* { <init>(...); }
# libwwhd.so looks up Native's static reverse-call methods and registers its natives by exact name in JNI_OnLoad.
-keep class org.wwhdrecomp.app.Native { *; }
-keep class org.wwhdrecomp.app.Native$Host { *; }

# The embedded engines are reached from native code (JNI FindClass/GetMethodID by exact name) and through reflection / Hilt / manifest
# class names. R8 in the release build renamed or removed them (DS: ClassNotFoundException me.magnum.melonds.MelonDSApplication;
# Azahar / Eden: SIGABRT looking up DiskShaderCacheProgress$LoadCallbackStage). Debug builds kept the names, which hid it.
# Keep every engine package whole (APK size is dominated by the native libraries, so this costs little).
-keep class me.magnum.** { *; }
-keep class org.citra.** { *; }
-keep class org.yuzu.** { *; }
-keep class org.libsdl.** { *; }
-keep class org.dolphinemu.** { *; }
-keep class info.cemu.** { *; }
-keep class com.armsx2.** { *; }
-keep class com.retroarch.** { *; }
-keep class dev.twilitrealm.** { *; }
-keep class dev.encounter.** { *; }
-keep class com.dishii.** { *; }
-keep class com.twoshipfork.** { *; }
-keep class org.wwhdrecomp.** { *; }
# Looked up by name from native code (FindClass), with no Java reference R8 could see: Eden's initJvm asks for kotlin/Pair.
-keep class kotlin.Pair { *; }
-keep class androidx.core.util.Pair { *; }
