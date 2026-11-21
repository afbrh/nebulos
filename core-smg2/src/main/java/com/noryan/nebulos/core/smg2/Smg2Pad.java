package com.noryan.nebulos.core.smg2;

/** JNI side of the SDL virtual gamepad (native/app/android_vpad.c, built into libnebula_smg2.so). Indices are SDL_GamepadButton / SDL_GamepadAxis. */
public final class Smg2Pad {
    private Smg2Pad() {}
    public static native void nativeStart();
    public static native void nativeButton(int sdlButton, boolean down);
    public static native void nativeAxis(int sdlAxis, int value);
    public static native void nativeReleaseAll();
}
