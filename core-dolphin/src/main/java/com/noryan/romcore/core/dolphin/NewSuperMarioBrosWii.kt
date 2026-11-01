package com.noryan.romcore.core.dolphin

/**
 * New Super Mario Bros. Wii (SMN[EPJK]01) with the Nunchuk attached (our default): jump = Wii B, run/fireball = Wii A.
 * Handheld layout: A and B both jump, X and Y both run (DolphinSession routes them; R1/R2 keep their default Wii A/B mapping).
 */
object NewSuperMarioBrosWii {
    fun matches(gameId: String?) = gameId != null && gameId.length == 6 && gameId.startsWith("SMN") && gameId[3] in "EPJK" && gameId.endsWith("01")
}
