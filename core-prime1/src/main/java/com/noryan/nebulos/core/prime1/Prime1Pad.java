package com.noryan.nebulos.core.prime1;

/** JNI side of the SDL virtual gamepad (platform/android/nebulos_vpad.c, built into libmetroid_prime_port.so). Indices are SDL_GamepadButton / SDL_GamepadAxis. */
public final class Prime1Pad {
    private Prime1Pad() {}
    public static native void nativeStart();
    public static native void nativeButton(int sdlButton, boolean down);
    public static native void nativeAxis(int sdlAxis, int value);
    public static native void nativeReleaseAll();
}
