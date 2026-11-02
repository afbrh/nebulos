package com.noryan.romcore.core.cemu

import android.content.Context
import info.cemu.cemu.common.io.unzip
import info.cemu.cemu.nativeinterface.NativeActiveSettings
import info.cemu.cemu.nativeinterface.NativeGraphicPacks
import java.io.File

/**
 * Bundled Cemu graphic packs. Packs only activate for their own titleIds, so installing/enabling is harmless for other games.
 * "RomCore_TPHD_4x3" = Zelda: Twilight Princess HD rendered natively at 4:3 (derived from the official TPHD Resolution pack).
 * "RomCore_TPHD_60fps" = TPHD 60 fps (30 Hz logic, every step drawn twice, in-between frame interpolates model + camera matrices).
 * It is switched on once per install (when the zip is (re)extracted) so the user can turn it off in the graphic pack list.
 * Run in the :cemu process after CemuApplication init and BEFORE vm.initializeEmulation() (the title must not be running).
 */
internal object CemuGamePacks {
    private const val ASSET = "cemu_tphd_4x3.zip"
    private const val DIR = "RomCore_TPHD_4x3"
    private const val DIR60 = "RomCore_TPHD_60fps"
    private const val VERSION = "3"                         // bump when the zip changes
    private const val PRESET = "1280x960 (4:3)"

    fun apply(context: Context) = runCatching {
        val root = File(NativeActiveSettings.getUserDataPath(), "graphicPacks")
        val dir = File(root, DIR)
        val marker = File(dir, ".romcore_version")
        var fresh = false
        if (!marker.exists() || marker.readText() != VERSION) {
            dir.deleteRecursively(); File(root, DIR60).deleteRecursively(); root.mkdirs()
            context.assets.open(ASSET).use { unzip(it, root.toString()) }
            marker.writeText(VERSION)
            fresh = true
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
        // Cemu only persists settings.xml on a clean exit, so an activation made in a session that was killed is lost; the marker is
        // only written once a launch has actually activated the pack, and is wiped with the dir on re-extraction.
        val on60 = File(dir, ".romcore_60fps_on")
        if (fresh || !on60.exists()) NativeGraphicPacks.getGraphicPackBasicInfos()
            .firstOrNull { it.virtualPath.contains("Twilight Princess HD") && it.virtualPath.endsWith("60fps") }
            ?.let { info ->
                if (!info.enabled) NativeGraphicPacks.setGraphicPackActive(info.id, true)
                on60.writeText("1")
            }
    }
}
