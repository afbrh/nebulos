package com.noryan.nebulos.data

/** How the game list is organised (Settings > GAME LIST). */
enum class ListLayout(val label: String) {
    /** Series tabs (Zelda, Mario, Metroid, PS2, ...) with everything else under OTHER. */
    OPTIMIZED("OPTIMIZED"),
    /** One A-Z list of every game, no tabs. */
    ALPHABETICAL("ALPHABETICAL"),
    /** One tab per console, each listing that console's games. */
    SYSTEM("SYSTEM");

    companion object {
        fun parse(name: String?): ListLayout = entries.firstOrNull { it.name == name } ?: OPTIMIZED
    }
}
