package com.noryan.nebulos.core.darkcloud

import com.noryan.nebulos.api.Pad
import com.noryan.nebulos.recomp.RecompInput

/**
 * Canonical pad -> the SDL virtual gamepad Chronicle reads. Chronicle uses the PS2 layout by SDL name (SOUTH = Cross, EAST = Circle,
 * WEST = Square, NORTH = Triangle). The handheld's face buttons are labelled the Nintendo way (A, B, X, Y) and the user wants the labels
 * to line up with the PS2 pad's, so A/B and X/Y are swapped relative to the other recomp cores (which map by position):
 * A -> Cross, B -> Circle, X -> Square, Y -> Triangle.
 */
object DarkCloudInput {
    fun button(pad: Pad): Int? = when (pad) {
        Pad.A -> RecompInput.SOUTH   // Cross
        Pad.B -> RecompInput.EAST    // Circle
        Pad.X -> RecompInput.WEST    // Square
        Pad.Y -> RecompInput.NORTH   // Triangle
        else -> RecompInput.button(pad)
    }
}
