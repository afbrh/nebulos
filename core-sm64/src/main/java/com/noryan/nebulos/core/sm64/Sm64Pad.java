package com.noryan.nebulos.core.sm64;

/** JNI side of the SDL2 virtual game controller (sm64-src/nebulos/nebulos_vpad.c, built into libsm64_main.so). Indices are SDL_GameControllerButton / SDL_GameControllerAxis. */
public final class Sm64Pad {
    private Sm64Pad() {}
    public static native void nativeStart();
    public static native void nativeButton(int sdlButton, boolean down);
    public static native void nativeAxis(int sdlAxis, int value);
    public static native void nativeReleaseAll();
}
