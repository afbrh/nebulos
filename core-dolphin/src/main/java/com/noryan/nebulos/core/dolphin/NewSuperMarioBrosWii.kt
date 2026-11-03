package com.noryan.nebulos.core.dolphin

/**
 * New Super Mario Bros. Wii (SMN[EPJK]01): played with the Wii Remote held sideways and no Nunchuk.
 * A / B -> Wii 2 (jump), Y -> Wii 1 (run / fireball), X -> remote shake (spin with the propeller suit), D-pad -> D-pad.
 * DolphinSession routes the buttons; [DolphinRuntime] / [OtherM.prepareEarly] write the sideways, no-extension remote INI.
 */
object NewSuperMarioBrosWii {
    fun matches(gameId: String?) = gameId != null && gameId.length == 6 && gameId.startsWith("SMN") && gameId[3] in "EPJK" && gameId.endsWith("01")
}
