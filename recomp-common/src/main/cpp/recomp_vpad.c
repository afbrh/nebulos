// Nebulos: ONE SDL virtual gamepad fed by Nebulos's InputRouter (RecompPad.java), so the recomp runtime's normal SDL gamepad input path
// (bindings, deadzones, rumble-less) is unchanged. Real controllers are hidden from SDL by SDL_GAMECONTROLLER_IGNORE_DEVICES_EXCEPT
// (set by the Activity); Nebulos consumes them and injects the canonical state here. Adapted from Dusklight's nebulos_embed.cpp.
#include <SDL3/SDL.h>
#include <jni.h>
#include <pthread.h>
#include <stdbool.h>
#include <unistd.h>

static pthread_mutex_t g_mu = PTHREAD_MUTEX_INITIALIZER;
static SDL_Joystick *g_joy;
static SDL_JoystickID g_id;
static bool g_buttons[SDL_GAMEPAD_BUTTON_COUNT];
static Sint16 g_axes[SDL_GAMEPAD_AXIS_COUNT] = {0, 0, 0, 0, SDL_JOYSTICK_AXIS_MIN, SDL_JOYSTICK_AXIS_MIN};
static bool g_thread_started;

// SDL assigns virtual joystick button indices in this fixed order (VIRTUAL_JoystickGetGamepadMapping)
static const SDL_GamepadButton k_order[] = {
    SDL_GAMEPAD_BUTTON_SOUTH, SDL_GAMEPAD_BUTTON_EAST, SDL_GAMEPAD_BUTTON_WEST, SDL_GAMEPAD_BUTTON_NORTH, SDL_GAMEPAD_BUTTON_BACK,
    SDL_GAMEPAD_BUTTON_START, SDL_GAMEPAD_BUTTON_LEFT_STICK, SDL_GAMEPAD_BUTTON_RIGHT_STICK, SDL_GAMEPAD_BUTTON_LEFT_SHOULDER,
    SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER, SDL_GAMEPAD_BUTTON_DPAD_UP, SDL_GAMEPAD_BUTTON_DPAD_DOWN, SDL_GAMEPAD_BUTTON_DPAD_LEFT,
    SDL_GAMEPAD_BUTTON_DPAD_RIGHT,
};
#define NB ((int)(sizeof k_order / sizeof k_order[0]))

static int index_of(int b) { for (int i = 0; i < NB; i++) if ((int)k_order[i] == b) return i; return -1; }

static bool attach_locked(void) {
    if (g_joy) return true;
    if (!SDL_WasInit(SDL_INIT_JOYSTICK)) return false;
    SDL_VirtualJoystickDesc d;
    SDL_INIT_INTERFACE(&d);
    d.type = SDL_JOYSTICK_TYPE_GAMEPAD;
    d.vendor_id = 0x5243; d.product_id = 0x0001;
    d.naxes = SDL_GAMEPAD_AXIS_COUNT; d.nbuttons = NB; d.name = "Nebulos Pad";
    for (int i = 0; i < NB; i++) d.button_mask |= 1u << (unsigned)k_order[i];
    for (int a = 0; a < SDL_GAMEPAD_AXIS_COUNT; a++) d.axis_mask |= 1u << (unsigned)a;
    g_id = SDL_AttachVirtualJoystick(&d);
    if (!g_id) return false;
    g_joy = SDL_OpenJoystick(g_id);
    if (!g_joy) { SDL_DetachVirtualJoystick(g_id); g_id = 0; return false; }
    for (int i = 0; i < NB; i++) SDL_SetJoystickVirtualButton(g_joy, i, g_buttons[k_order[i]]);
    for (int a = 0; a < SDL_GAMEPAD_AXIS_COUNT; a++) SDL_SetJoystickVirtualAxis(g_joy, a, g_axes[a]);
    return true;
}

static void *attach_thread(void *arg) {
    (void)arg;
    for (;;) {
        pthread_mutex_lock(&g_mu);
        bool ok = attach_locked();
        pthread_mutex_unlock(&g_mu);
        if (ok) return NULL;
        usleep(100 * 1000);
    }
}

JNIEXPORT void JNICALL Java_com_noryan_recompsdl_RecompPad_nativeStart(JNIEnv *env, jclass c) {
    (void)env; (void)c;
    pthread_mutex_lock(&g_mu);
    bool start = !g_thread_started;
    g_thread_started = true;
    pthread_mutex_unlock(&g_mu);
    if (!start) return;
    pthread_t t;
    if (pthread_create(&t, NULL, attach_thread, NULL) == 0) pthread_detach(t);
}

JNIEXPORT void JNICALL Java_com_noryan_recompsdl_RecompPad_nativeButton(JNIEnv *env, jclass c, jint b, jboolean down) {
    (void)env; (void)c;
    int i = index_of(b);
    if (i < 0) return;
    pthread_mutex_lock(&g_mu);
    g_buttons[b] = down != JNI_FALSE;
    if (g_joy) SDL_SetJoystickVirtualButton(g_joy, i, down != JNI_FALSE);
    pthread_mutex_unlock(&g_mu);
}

JNIEXPORT void JNICALL Java_com_noryan_recompsdl_RecompPad_nativeAxis(JNIEnv *env, jclass c, jint a, jint v) {
    (void)env; (void)c;
    if (a < 0 || a >= SDL_GAMEPAD_AXIS_COUNT) return;
    if (v < SDL_JOYSTICK_AXIS_MIN) v = SDL_JOYSTICK_AXIS_MIN;
    if (v > SDL_JOYSTICK_AXIS_MAX) v = SDL_JOYSTICK_AXIS_MAX;
    pthread_mutex_lock(&g_mu);
    g_axes[a] = (Sint16)v;
    if (g_joy) SDL_SetJoystickVirtualAxis(g_joy, a, (Sint16)v);
    pthread_mutex_unlock(&g_mu);
}

JNIEXPORT void JNICALL Java_com_noryan_recompsdl_RecompPad_nativeReleaseAll(JNIEnv *env, jclass c) {
    (void)env; (void)c;
    pthread_mutex_lock(&g_mu);
    for (int i = 0; i < SDL_GAMEPAD_BUTTON_COUNT; i++) g_buttons[i] = false;
    for (int a = 0; a < SDL_GAMEPAD_AXIS_COUNT; a++) g_axes[a] = a >= SDL_GAMEPAD_AXIS_LEFT_TRIGGER ? SDL_JOYSTICK_AXIS_MIN : 0;
    if (g_joy) {
        for (int i = 0; i < NB; i++) SDL_SetJoystickVirtualButton(g_joy, i, false);
        for (int a = 0; a < SDL_GAMEPAD_AXIS_COUNT; a++) SDL_SetJoystickVirtualAxis(g_joy, a, g_axes[a]);
    }
    pthread_mutex_unlock(&g_mu);
}
