package com.noryan.nebulos.core.sotc

/** JNI into libps2EntryRunner.so (ps2_android_nebulos.cpp in celosia-src). All calls are safe before the library is loaded. */
object SotcNative {
    init {
        // NativeActivity dlopens the library itself, which does not register it for JNI name lookup (nor call JNI_OnLoad):
        // loading it here (the same file, the same handle) does.
        System.loadLibrary("ps2EntryRunner")
    }

    /** Native -> Java events: 0 = the game is up. Set by the activity. */
    @Volatile var listener: ((Int, String) -> Unit)? = null

    @JvmStatic fun onEvent(kind: Int, text: String) { listener?.invoke(kind, text) }

    @JvmStatic external fun nativeDataReady(ok: Boolean)
    @JvmStatic external fun nativeSurfaceCreated(surface: android.view.Surface)
    @JvmStatic external fun nativeSurfaceDestroyed()
    @JvmStatic external fun nativeSetPaused(paused: Boolean)
    @JvmStatic external fun nativeButton(mask: Int, down: Boolean)
    @JvmStatic external fun nativeAxis(index: Int, value: Float)
    @JvmStatic external fun nativeReleaseAll()
}
