package com.noryan.nebulos.core.darkchronicle

import com.noryan.nebulos.api.Pad
import com.noryan.nebulos.recomp.RecompInput

/**
 * Canonical pad -> the SDL virtual gamepad ChronicleTwo reads, by physical POSITION like the other PS2 cores: the handheld's south button is the
 * PS2 Cross, east = Circle, west = Square, north = Triangle (the handheld's labels are the Nintendo ones: B south, A east, Y west, X north).
 */
object DarkChronicleInput {
    fun button(pad: Pad): Int? = RecompInput.button(pad)
}
