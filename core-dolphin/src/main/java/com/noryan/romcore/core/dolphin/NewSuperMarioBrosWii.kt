package com.noryan.romcore.core.dolphin

/**
 * New Super Mario Bros. Wii (SMN[EPJK]01) with the Nunchuk attached (our default): jump = Wii A, run/fireball = Wii B (as tested on the Nova).
 * Handheld layout: A and B both press Wii A, X and Y both press Wii B (DolphinSession routes them; R1/R2 keep their default mapping).
 */
object NewSuperMarioBrosWii {
    fun matches(gameId: String?) = gameId != null && gameId.length == 6 && gameId.startsWith("SMN") && gameId[3] in "EPJK" && gameId.endsWith("01")
}
