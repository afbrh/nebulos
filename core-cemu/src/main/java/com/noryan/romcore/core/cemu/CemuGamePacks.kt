package com.noryan.romcore.core.cemu

import android.content.Context
import info.cemu.cemu.common.io.unzip
import info.cemu.cemu.nativeinterface.NativeActiveSettings
import info.cemu.cemu.nativeinterface.NativeGraphicPacks
import java.io.File

/**
 * Bundled Cemu graphic packs. Packs only activate for their own titleIds, so installing/enabling is harmless for other games.
 * "RomCore_TPHD_4x3" = Zelda: Twilight Princess HD rendered natively at 4:3 (derived from the official TPHD Resolution pack).
 * Run in the :cemu process after CemuApplication init and BEFORE vm.initializeEmulation() (the title must not be running).
 */
internal object CemuGamePacks {
    private const val ASSET = "cemu_tphd_4x3.zip"
    private const val DIR = "RomCore_TPHD_4x3"
    private const val VERSION = "1"                         // bump when the zip changes
    private const val PRESET = "1280x960 (4:3)"

    fun apply(context: Context) = runCatching {
        val root = File(NativeActiveSettings.getUserDataPath(), "graphicPacks")
        val dir = File(root, DIR)
        val marker = File(dir, ".romcore_version")
        if (!marker.exists() || marker.readText() != VERSION) {
            dir.deleteRecursively(); root.mkdirs()
            context.assets.open(ASSET).use { unzip(it, root.toString()) }
            marker.writeText(VERSION)
        }
        NativeGraphicPacks.refreshGraphicPacks()
        NativeGraphicPacks.getGraphicPackBasicInfos()
            .firstOrNull { it.virtualPath.contains("Twilight Princess HD") && it.virtualPath.endsWith("4x3") }
            ?.let { info ->
                if (!info.enabled) NativeGraphicPacks.setGraphicPackActive(info.id, true)
                NativeGraphicPacks.getGraphicPack(info.id)?.presets?.forEach { p ->
                    if (PRESET in p.presets && p.activePreset != PRESET) p.activePreset = PRESET
                }
            }
    }
}
