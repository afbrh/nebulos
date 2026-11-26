package com.noryan.nebulos.core.zelda3;

/**
 * JNI side of zelda3-src/nebulos/nebulos_bridge.c (in libzelda3_main.so): the SDL2 virtual game controller (indices are SDL_GameControllerButton /
 * SDL_GameControllerAxis), pause, and save states.
 */
public final class Zelda3Native {
    private Zelda3Native() {}
    public static native void nativeStart();
    public static native void nativeButton(int sdlButton, boolean down);
    public static native void nativeAxis(int sdlAxis, int value);
    public static native void nativeReleaseAll();
    public static native void nativeSetPaused(boolean paused);
    /** Blocks until the game thread has saved (or loaded) the state; false when it could not. Never call on the main thread. */
    public static native boolean nativeState(String path, boolean save);
}
