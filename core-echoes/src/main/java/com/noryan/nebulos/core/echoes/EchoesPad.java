package com.noryan.nebulos.core.echoes;

/** JNI side of the SDL virtual gamepad (platform/android/nebulos_vpad.c, built into libmetroid_prime2_port.so). Indices are SDL_GamepadButton / SDL_GamepadAxis. */
public final class EchoesPad {
    private EchoesPad() {}
    public static native void nativeStart();
    public static native void nativeButton(int sdlButton, boolean down);
    public static native void nativeAxis(int sdlAxis, int value);
    public static native void nativeReleaseAll();
}
