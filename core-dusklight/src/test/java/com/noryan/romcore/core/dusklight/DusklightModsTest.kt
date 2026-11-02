package com.noryan.romcore.core.dusklight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DusklightModsTest {
    /** Engine escaping: id `org.twilight.hd_hud` -> `.`=`_`, `_`=`__`. */
    @Test fun hudConfigPrefixMatchesModId() {
        val id = "org.twilight.hd_hud"
        val escaped = id.flatMap { if (it == '.') listOf('_') else if (it == '_') listOf('_', '_') else listOf(it) }.joinToString("")
        assertEquals("mod.$escaped", DusklightArgs.HUD)
    }

    @Test fun modNeverChecksForUpdates() {
        assertEquals("false", DusklightArgs.CVARS["${DusklightArgs.HUD}.check-for-updates"])
    }

    @Test fun cvarsAreEmittedAsPairs() {
        val argv = DusklightArgs.build("/x.iso", "/u")
        assertTrue(argv.toList().windowed(2).any { it == listOf("--cvar", "${DusklightArgs.HUD}.check-for-updates=false") })
    }

    @Test fun assetNameIsTheUpdaterName() = assertEquals("twilight_hd_hud.dusk", DusklightMods.ASSET)
}
