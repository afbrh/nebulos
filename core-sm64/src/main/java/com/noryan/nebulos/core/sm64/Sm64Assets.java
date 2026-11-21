package com.noryan.nebulos.core.sm64;

/** JNI side of the first-launch extraction (sm64-src/src/port/android/AndroidBridge.cpp). Runs in the :sm64x process only. */
public final class Sm64Assets {
    private Sm64Assets() {}
    /** Torch: the user's ROM -> sm64.o2r in destDir (recipes in sourceDir). Returns null on success, else a message. Blocking. */
    public static native String nativeGenerateGameArchive(String rom, String sourceDir, String destDir);
    /** Torch's running asset counter. */
    public static native int nativeProgress();
}
