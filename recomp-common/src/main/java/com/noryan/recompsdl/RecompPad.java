package com.noryan.recompsdl;

/** JNI side of the SDL virtual gamepad (recomp_vpad.c, linked into libbotw_main.so / libtotk_main.so). Indices are SDL_GamepadButton / SDL_GamepadAxis. */
public final class RecompPad {
    private RecompPad() {}
    /** Starts the thread that attaches the virtual gamepad as soon as SDL's joystick subsystem is up. Safe to call twice. */
    public static native void nativeStart();
    public static native void nativeButton(int sdlButton, boolean down);
    public static native void nativeAxis(int sdlAxis, int value);
    public static native void nativeReleaseAll();
}
