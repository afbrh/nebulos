package com.noryan.nebulos.core.smg2;

/** JNI side of the disc importer (native/app/android_jni.cpp): probes and extracts the user's disc image (nod: ISO, GCM, CISO, GCZ, WBFS, RVZ, WIA ...). */
public final class Smg2Native {
    private Smg2Native() {}
    public interface Progress { boolean onProgress(float fraction, String file); }
    /** "" when the file is the supported disc (USA, SB4E01 revision 0), otherwise the problem in plain English. */
    public static native String nativeProbe(String disc);
    /** Extracts files/ + sys/ into {@code dest} (absent or empty). "" on success, "cancelled", or an error message. */
    public static native String nativeImport(String disc, String dest, Progress progress);
}
