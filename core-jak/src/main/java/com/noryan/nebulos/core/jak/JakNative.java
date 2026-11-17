package com.noryan.nebulos.core.jak;

/** Host natives implemented in libjak_main.so (game/nebulos_embed.cpp in jak-android-src). */
public final class JakNative {
    private JakNative() {}
    public static native void nativeButton(int sdlButton, boolean down);
    public static native void nativeAxis(int sdlAxis, int value);
    public static native void nativeReleaseAll();
    public static native void nativeSetPaused(boolean paused);
}
