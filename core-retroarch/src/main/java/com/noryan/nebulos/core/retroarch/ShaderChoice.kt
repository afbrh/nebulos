package com.noryan.nebulos.core.retroarch

import android.content.Context
import com.noryan.nebulos.api.System

/**
 * The picture filter for the home consoles up through N64 / PS1 (NES, SNES, Genesis, Master System, PC Engine, Atari, N64, PS1) and the Game Boy family, chosen from the in-game menu's
 * "Shader" row. The choice is remembered per system (`retro_shader_<SYSTEM>` in the shared "nebulos" prefs file) and applied live
 * through the RetroArch bridge (op 8) and again at the next launch. The shaders are Nebulos's own GLSL, bundled in assets.
 *
 * Defaults: the Scanlines look on every console above (a saved "crt" or "sharp" pick from older versions is ignored and falls back to this), the LCD look on GBA, the same LCD look on GBC, the green DMG screen on GB; the
 * old `retro_crt_filter` / `retro_gba_filter` booleans still turn the default off.
 */
internal object ShaderChoice {
    enum class Pick(val id: String, val label: String, val presetFile: String?) {
        OFF("off", "Off", null),
        SCANLINES("scanlines", "Scanlines", CrtShader.SCANLINES_PRESET_FILE),
        LCD("lcd", "Handheld LCD", CrtShader.GBA_PRESET_FILE),
        DMG("dmg", "DMG Green", CrtShader.DMG_PRESET_FILE);

        fun next(): Pick = entries[(ordinal + 1) % entries.size]
        fun previous(): Pick = entries[(ordinal + entries.size - 1) % entries.size]
    }

    /** Home consoles up through the N64 / PS1 era (Scanlines by default) plus the Game Boy family (LCD / DMG looks). */
    val SYSTEMS: Set<System> = setOf(System.NES, System.SNES, System.N64, System.GENESIS, System.MASTER_SYSTEM, System.PCE, System.ATARI, System.PS1,
        System.GB, System.GBC, System.GBA)

    fun applies(system: System?) = system != null && system in SYSTEMS

    /**
     * The preset file a system runs for [pick], or null for "no shader". Game Boy always runs through the border-crop pass (a
     * Super Game Boy game would otherwise show its frame), so Off there is the crop-only preset and every other look is wrapped.
     */
    fun presetFile(system: System, pick: Pick): String? = when {
        system == System.GB -> pick.presetFile?.let { CrtShader.gbWrapped(it) } ?: CrtShader.GBCROP_PRESET_FILE
        else -> pick.presetFile
    }

    fun fromId(id: String?): Pick? = Pick.entries.firstOrNull { it.id == id }

    fun prefKey(system: System) = "retro_shader_${system.name}"

    /** What a system gets before the user ever opens the row. [crtOn] / [lcdOn] are the legacy boolean prefs. */
    fun defaultFor(system: System, crtOn: Boolean = true, lcdOn: Boolean = true): Pick = when (system) {
        System.NES, System.SNES, System.N64, System.GENESIS, System.MASTER_SYSTEM, System.PCE, System.ATARI, System.PS1 -> if (crtOn) Pick.SCANLINES else Pick.OFF
        System.GBA -> if (lcdOn) Pick.LCD else Pick.OFF
        System.GBC -> Pick.LCD
        System.GB -> Pick.DMG
        else -> Pick.OFF
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(CrtShader.PREFS_FILE, Context.MODE_PRIVATE)

    fun current(ctx: Context, system: System): Pick {
        val p = prefs(ctx)
        fromId(runCatching { p.getString(prefKey(system), null) }.getOrNull())?.let { return it }
        val crtOn = runCatching { p.getBoolean(CrtShader.PREF_KEY, true) }.getOrDefault(true)
        val lcdOn = runCatching { p.getBoolean(CrtShader.GBA_PREF_KEY, true) }.getOrDefault(true)
        return defaultFor(system, crtOn, lcdOn)
    }

    fun save(ctx: Context, system: System, pick: Pick) { prefs(ctx).edit().putString(prefKey(system), pick.id).apply() }
}
