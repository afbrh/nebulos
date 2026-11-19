package com.noryan.nebulos.recomp

import com.noryan.nebulos.api.Pad

/**
 * Nebulos canonical pad -> the SDL virtual gamepad the recomp runtimes see. The runtimes' default bindings are the Nintendo layout BY
 * POSITION (hle/bindings.c: east = A, south = B, north = X, west = Y, triggers = ZL/ZR, start = Plus, back = Minus), and canonical
 * Pad.A/B/X/Y are the handheld's Nintendo labels, so A -> EAST, B -> SOUTH, X -> NORTH, Y -> WEST.
 */
object RecompInput {
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
        Pad.A -> EAST
        Pad.B -> SOUTH
        Pad.X -> NORTH
        Pad.Y -> WEST
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
        else -> null // L2/R2 arrive as axes; HOME/BACK belong to Nebulos
    }

    fun trigger(v: Float): Int = (AXIS_MIN + v.coerceIn(0f, 1f) * (AXIS_MAX - AXIS_MIN)).toInt()
    fun stick(negative: Float, positive: Float): Int =
        ((positive.coerceIn(0f, 1f) - negative.coerceIn(0f, 1f)) * AXIS_MAX).toInt().coerceIn(AXIS_MIN, AXIS_MAX)
}
