package com.noryan.nebulos.core.galaxy;

/** JNI side of the SDL virtual gamepad (nebulos_vpad.c, built into Nebula's libmain.so). Indices are SDL_GamepadButton / SDL_GamepadAxis. */
public final class GalaxyPad {
    private GalaxyPad() {}
    public static native void nativeStart();
    public static native void nativeButton(int sdlButton, boolean down);
    public static native void nativeAxis(int sdlAxis, int value);
    public static native void nativeReleaseAll();
}
