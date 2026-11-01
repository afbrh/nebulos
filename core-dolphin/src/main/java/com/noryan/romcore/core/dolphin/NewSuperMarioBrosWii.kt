package com.noryan.romcore.core.dolphin

/**
 * New Super Mario Bros. Wii (SMN[EPJK]01): the game is played with the remote held sideways, jump = Wii 2, run/fireball = Wii 1.
 * Handheld layout: A and B both jump, X and Y both run (DolphinSession routes them; the rest keeps the default Wii mapping).
 */
object NewSuperMarioBrosWii {
    fun matches(gameId: String?) = gameId != null && gameId.length == 6 && gameId.startsWith("SMN") && gameId[3] in "EPJK" && gameId.endsWith("01")
}
