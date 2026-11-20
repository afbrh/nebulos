package com.noryan.nebulos.core.darkcloud

import com.noryan.nebulos.api.Pad
import com.noryan.nebulos.recomp.RecompInput

/**
 * Canonical pad -> the SDL virtual gamepad Chronicle reads, by physical POSITION like the other PS2 cores (Jak, Celosia): the handheld's
 * south button is the PS2 Cross, east = Circle, west = Square, north = Triangle. The handheld's labels are the Nintendo ones, so
 * B (south) = Cross, A (east) = Circle, Y (west) = Square, X (north) = Triangle.
 * (An earlier build mapped by label, A = Cross, which put the PS2 Cross on the east button.)
 */
object DarkCloudInput {
    fun button(pad: Pad): Int? = RecompInput.button(pad)
}
