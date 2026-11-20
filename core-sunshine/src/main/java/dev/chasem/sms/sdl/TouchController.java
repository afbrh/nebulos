package dev.chasem.sms.sdl;

import android.view.MotionEvent;

/**
 * The on-screen controller as one more SDL controller. SDL adds Android's
 * controllers when it first polls for them, once its joystick system is up;
 * this one is added in the same poll, so the game opens it like any other.
 * It lives in SDL's package to wrap SDL's own (package-private) handler.
 */
public final class TouchController {
    /** Never an Android input device's id, which are not negative. */
    public static final int DEVICE_ID = -0x534d53;
    // SDL_CONTROLLER_BUTTON_* bits: A, B, X, Y, Start, right shoulder (Z) and
    // the D-pad. SDL makes the mapping from these and the six axes: both
    // sticks, then the triggers (L and R).
    private static final int BUTTONS = 0xf | 1 << 6 | 1 << 10 | 0xf << 11, AXES = 6;
    private TouchController() {}

    public static void install() {
        final SDLJoystickHandler inner = SDLControllerManager.mJoystickHandler;
        if (inner == null) return;
        SDLControllerManager.mJoystickHandler = new SDLJoystickHandler() {
            private boolean added;
            @Override public boolean handleMotionEvent(MotionEvent event) { return false; }   // Nebulos: real controllers reach the game only through Nebulos's router
            @Override public void pollInputDevices() {
                // Nebulos: SDL's own enumeration of the real controllers is skipped, so this is the only controller the game sees
                if (added) return;
                added = SDLControllerManager.nativeAddJoystick(DEVICE_ID, "Touch screen controls",
                    "sms-touch-controls", 0, 0, false, BUTTONS, AXES, (1 << AXES) - 1, 0, 0) >= 0;
                android.util.Log.i("SunshineCore", added ? "touch screen controls added" : "touch screen controls not added");
            }
        };
    }
    public static void button(int keycode, boolean down) {
        if (down) SDLControllerManager.onNativePadDown(DEVICE_ID, keycode);
        else SDLControllerManager.onNativePadUp(DEVICE_ID, keycode);
    }
    /** Android's range, -1 to 1 for every axis (a trigger rests at -1). */
    public static void axis(int axis, float value) { SDLControllerManager.onNativeJoy(DEVICE_ID, axis, value); }
}
