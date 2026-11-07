package com.noryan.nebulos.core.armsx2

import android.content.SharedPreferences
import kr.co.iefriends.pcsx2.NativeApp

/**
 * Per-game 60 fps for PS2 games that run at 30 fps and whose community pnach in patches.zip carries a proper 60 fps group.
 * The group is enabled through ARMSX2's own per-game `[Patches] Enable` list (gamesettings/<serial>_<crc>.ini), so nothing
 * is bundled and other games are untouched. Menu row "60 FPS" (on by default), live via [NativeApp.reloadPatches].
 *
 * ICO (NTSC-U, SCUS-97113, CRC 6F8545DB): group "60 fps" (synce / Agrippa) writes the game's own vsync-per-frame variable
 * (0x00274EC4: 1 outside cutscenes, 2 inside, keyed off the cutscene flag at 0x006325B4). Measured on the Nova: 30 -> 60 presented
 * fps and the same world speed (the engine scales its time step by that variable), cutscenes stay at their authored rate.
 */
internal object Armsx2SixtyFps {
    private data class Spec(val patchName: String)

    /** `<serial>_<CRC>` (as in the game's INI file name) -> the patch group that gives 60 fps. */
    private val SPECS = mapOf(
        "SCUS-97113_6F8545DB" to Spec("60 fps"), // ICO (NTSC-U)
    )

    private const val KEY_PREFIX = "sixty_fps_"

    /** "SCUS-97113 (6F8545DB)" (NativeApp.getPauseGameSerial) -> "SCUS-97113_6F8545DB"; null while no disc is up. */
    fun keyFrom(pauseSerial: String?): String? {
        val m = Regex("""^\s*([A-Z]{4}-\d{5})\s*\(([0-9A-Fa-f]{8})\)\s*$""").find(pauseSerial ?: return null) ?: return null
        val crc = m.groupValues[2].uppercase()
        if (crc == "00000000") return null
        return m.groupValues[1] + "_" + crc
    }

    fun isSupported(key: String?) = key != null && key in SPECS

    /** On by default for games that have it; the menu row turns it off. */
    fun isEnabled(p: SharedPreferences, key: String): Boolean = p.getBoolean(KEY_PREFIX + key, true)
    fun setEnabled(p: SharedPreferences, key: String, on: Boolean) { p.edit().putBoolean(KEY_PREFIX + key, on).apply() }

    /** Writes this game's INI enable list and re-applies patches on the running VM. Returns false if it could not. */
    fun apply(key: String, on: Boolean): Boolean {
        val spec = SPECS[key] ?: return false
        return runCatching {
            val serial = key.substringBefore('_')
            NativeApp.setEnabledPatches(false, arrayOf(spec.patchName), if (on) arrayOf(spec.patchName) else emptyArray(), serial)
            NativeApp.reloadPatches()
            true
        }.getOrDefault(false)
    }
}
