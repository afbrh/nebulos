package com.noryan.nebulos.api.save

import java.io.File

/**
 * Whether a game has a Save State (the quick slot) to resume, for the home screen's launch panel. Shared-menu cores keep it as
 * `<root>/states/<core>/<game>/slot1`; native cores (3DS, PS2, Wii U ...) only know their slots while running, so the in-game host also
 * leaves a [MARKER] next to it whenever it sees or writes the quick state.
 */
object QuickState {
    const val MARKER = "quick.marker"
    /** Same slot as NebulosCoreHost.QUICK_SLOT (that class lives in the parent package and pulls in the UI). */
    private const val QUICK_FILE = "slot1"

    /** The folder holding one game's states under [root]. Not created. */
    fun dir(root: File, coreId: String, gameKey: String): File =
        File(root, "states/${SaveRoot.coreFolder(coreId)}/${SaveRoot.sanitizeSegment(gameKey)}")

    fun exists(root: File, coreId: String, gameKey: String): Boolean {
        val d = dir(root, coreId, gameKey)
        return File(d, MARKER).isFile || File(d, QUICK_FILE).let { it.isFile && it.length() > 0 }
    }

    /** Host side: record that the game does ([on]) or does not have a quick state. Best effort. */
    fun note(root: File, coreId: String, gameKey: String, on: Boolean) {
        runCatching {
            val m = File(dir(root, coreId, gameKey), MARKER)
            if (on) { m.parentFile?.mkdirs(); if (!m.exists()) m.createNewFile() } else m.delete()
        }
    }
}
