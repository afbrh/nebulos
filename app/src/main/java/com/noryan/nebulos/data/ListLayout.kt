package com.noryan.nebulos.data

/** How the game list is organised (Settings > GAME LIST). */
enum class ListLayout(val label: String) {
    /** Series tabs (Zelda, Mario, Metroid, PS2, ...) with everything else under OTHER. */
    OPTIMIZED("OPTIMIZED"),
    /** One A-Z list of every game, no tabs. */
    ALPHABETICAL("ALPHABETICAL"),
    /** One tab per console, each listing that console's games. */
    SYSTEM("SYSTEM"),
    /** Two tabs: every game that runs on a native port / recompilation core, and every game that is emulated. */
    NATIVE_EMULATED("RECOMPILED / EMULATED");

    companion object {
        fun parse(name: String?): ListLayout = entries.firstOrNull { it.name == name } ?: OPTIMIZED
    }
}
