package com.noryan.nebulos.core.jak

import com.noryan.nebulos.api.Pad

/**
 * Nebulos canonical pad -> the SDL virtual gamepad OpenGOAL sees (game/nebulos_embed.cpp). The game's default bindings are
 * the DualShock layout (SDL SOUTH = Cross, EAST = Circle, WEST = Square, NORTH = Triangle). Canonical Pad.A/B/X/Y are the
 * labels on the handheld and Nebulos's PS2 rule is A = Circle, B = Cross, X = Triangle, Y = Square ([com.noryan.nebulos.api.PsFace]).
 */
internal object JakInput {
    // SDL_GamepadButton
    const val SOUTH = 0; const val EAST = 1; const val WEST = 2; const val NORTH = 3
    const val BACK = 4; const val START = 6; const val LEFT_STICK = 7; const val RIGHT_STICK = 8
    const val LEFT_SHOULDER = 9; const val RIGHT_SHOULDER = 10
    const val DPAD_UP = 11; const val DPAD_DOWN = 12; const val DPAD_LEFT = 13; const val DPAD_RIGHT = 14

    // SDL_GamepadAxis
    const val AXIS_LEFTX = 0; const val AXIS_LEFTY = 1; const val AXIS_RIGHTX = 2; const val AXIS_RIGHTY = 3
    const val AXIS_LTRIG = 4; const val AXIS_RTRIG = 5

    const val AXIS_MIN = -32768
    const val AXIS_MAX = 32767

    fun button(pad: Pad): Int? = when (pad) {
        Pad.A -> EAST        // Circle
        Pad.B -> SOUTH       // Cross
        Pad.X -> NORTH       // Triangle
        Pad.Y -> WEST        // Square
        Pad.L1 -> LEFT_SHOULDER
        Pad.R1 -> RIGHT_SHOULDER
        Pad.L3 -> LEFT_STICK
        Pad.R3 -> RIGHT_STICK
        Pad.START -> START
        Pad.SELECT -> BACK
        Pad.DPAD_UP -> DPAD_UP
        Pad.DPAD_DOWN -> DPAD_DOWN
        Pad.DPAD_LEFT -> DPAD_LEFT
        Pad.DPAD_RIGHT -> DPAD_RIGHT
        else -> null          // L2/R2 arrive as analog axes
    }

    /** Trigger value 0..1 -> SDL axis value (virtual-joystick triggers rest at AXIS_MIN). */
    fun trigger(v: Float): Int = (AXIS_MIN + v.coerceIn(0f, 1f) * (AXIS_MAX - AXIS_MIN)).toInt()

    /** Stick direction pair (negative, positive) each 0..1 -> SDL axis value. */
    fun stick(negative: Float, positive: Float): Int =
        ((positive.coerceIn(0f, 1f) - negative.coerceIn(0f, 1f)) * AXIS_MAX).toInt().coerceIn(AXIS_MIN, AXIS_MAX)
}
