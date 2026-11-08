package com.noryan.nebulos.core.cemu

import info.cemu.cemu.nativeinterface.NativeActiveSettings
import info.cemu.cemu.nativeinterface.NativeGraphicPacks
import java.io.File

/**
 * Nebulos no longer ships any Cemu graphic packs: Twilight Princess HD (like every Wii U game) runs on stock Cemu. Earlier builds
 * installed "Nebulos_TPHD_4x3" and "Nebulos_TPHD_60fps" into the user's graphicPacks folder and switched them on at every launch, so this
 * removes them once (and refreshes Cemu's pack list) from devices that still have them.
 * Run in the :cemu process after CemuApplication init and BEFORE vm.initializeEmulation() (the title must not be running).
 */
internal object CemuGamePacks {
    private val REMOVED = listOf("Nebulos_TPHD_4x3", "Nebulos_TPHD_60fps")

    fun removeLegacyPacks() = runCatching {
        val root = File(NativeActiveSettings.getUserDataPath(), "graphicPacks")
        val found = REMOVED.map { File(root, it) }.filter { it.exists() }
        if (found.isEmpty()) return@runCatching
        found.forEach { it.deleteRecursively() }
        NativeGraphicPacks.refreshGraphicPacks()
    }
}
