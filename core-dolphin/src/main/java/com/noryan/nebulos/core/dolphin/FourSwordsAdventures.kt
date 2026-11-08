package com.noryan.nebulos.core.dolphin

/**
 * The Legend of Zelda: Four Swords Adventures (GameCube, `G4S[EPJ]01`): Link only walks with the control stick (the game ignores the
 * GameCube D-pad), so the D-pad is ALSO added to the left stick as a full-deflection push. The D-pad is still forwarded as the D-pad,
 * so menus behave as before. Pure logic (unit-tested); DolphinSession applies the result to the main stick.
 */
object FourSwordsAdventures {
    fun matches(gameId: String?) = gameId != null && gameId.length == 6 && gameId.startsWith("G4S") && gameId[3] in "EPJ" && gameId.endsWith("01")

    /** Stick (x right, y up, -1..1) plus D-pad directions, each axis clamped to -1..1; opposite D-pad directions cancel. */
    fun combine(x: Double, y: Double, up: Boolean, down: Boolean, left: Boolean, right: Boolean): Pair<Double, Double> {
        val dx = (if (right) 1.0 else 0.0) - (if (left) 1.0 else 0.0)
        val dy = (if (up) 1.0 else 0.0) - (if (down) 1.0 else 0.0)
        return (x + dx).coerceIn(-1.0, 1.0) to (y + dy).coerceIn(-1.0, 1.0)
    }
}
