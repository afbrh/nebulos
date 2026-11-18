package com.noryan.nebulos.core.sotc

import com.noryan.nebulos.api.Pad

/**
 * Nebulos canonical pad -> the PS2 pad Celosia reads (masks of ps2_pad.cpp). Nebulos's PS2 face rule: handheld A = Circle,
 * B = Cross, X = Triangle, Y = Square ([com.noryan.nebulos.api.PsFace]); the right stick (camera) is inverted on both axes.
 */
internal object SotcInput {
    const val LEFT = 0x0080; const val DOWN = 0x0040; const val RIGHT = 0x0020; const val UP = 0x0010
    const val START = 0x0008; const val R3 = 0x0004; const val L3 = 0x0002; const val SELECT = 0x0001
    const val SQUARE = 0x8000; const val CROSS = 0x4000; const val CIRCLE = 0x2000; const val TRIANGLE = 0x1000
    const val R1 = 0x0800; const val L1 = 0x0400; const val R2 = 0x0200; const val L2 = 0x0100

    const val AXIS_LX = 0; const val AXIS_LY = 1; const val AXIS_RX = 2; const val AXIS_RY = 3

    /** Trigger value above which the PS2 shoulder button counts as pressed. */
    const val TRIGGER_ON = 0.35f

    fun button(pad: Pad): Int? = when (pad) {
        Pad.A -> CIRCLE
        Pad.B -> CROSS
        Pad.X -> TRIANGLE
        Pad.Y -> SQUARE
        Pad.L1 -> L1
        Pad.R1 -> R1
        Pad.L3 -> L3
        Pad.R3 -> R3
        Pad.START -> START
        Pad.SELECT -> SELECT
        Pad.DPAD_UP -> UP
        Pad.DPAD_DOWN -> DOWN
        Pad.DPAD_LEFT -> LEFT
        Pad.DPAD_RIGHT -> RIGHT
        else -> null   // L2/R2 arrive as analog values
    }

    /** One stick axis from its two directions (each 0..1); +1 = right / down. */
    fun axis(negative: Float, positive: Float): Float = (positive.coerceIn(0f, 1f) - negative.coerceIn(0f, 1f))
}
