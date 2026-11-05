package com.noryan.nebulos.api

import org.junit.Assert.*
import org.junit.Test

class TitleMatchTest {
    @Test fun cleansPathsExtensionsBracketsAndUnderscores() {
        assertEquals("Super Mario Odyssey", TitleMatch.clean("/storage/x/_ROMS/Super_Mario__Odyssey (EU) [UPDATE].xci"))
        assertEquals("Metroid - Fusion", TitleMatch.clean("Metroid - Fusion.gba"))
        assertEquals("NoExtension", TitleMatch.clean("/a/b/NoExtension"))
        assertEquals("Super Mario Bros. Wonder", TitleMatch.clean("Super Mario Bros. Wonder"))   // a title without an extension keeps its dot
        assertEquals("Super Mario Bros. Wonder", TitleMatch.clean("Super_Mario_Bros._Wonder.xci"))
    }

    @Test fun settleOnlyForTheKnownEarlyHitchGames() {
        assertEquals(7_000L, StartupSettle.forPath("/storage/x/_ROMS/Super Smash Bros Melee.ciso"))
        assertEquals(4_500L, StartupSettle.forPath("/storage/x/_ROMS/Super Mario Bros Wii.rvz"))
        assertEquals(4_000L, StartupSettle.forPath("/storage/x/_ROMS/Metroid - Prime Trilogy.rvz"))
        assertEquals(0L, StartupSettle.forPath("/storage/x/_ROMS/Super Mario Galaxy 2.rvz"))
        assertEquals(0L, StartupSettle.forPath("/storage/x/_ROMS/Super Mario Sunshine.ciso"))
        assertEquals(0L, StartupSettle.forPath("/storage/x/_ROMS/Super Mario Bros New.nds"))
    }
}

class PictureGateRuleTest {
    private fun frame(litPixels: Int) = IntArray(64 * 36).also { for (i in 0 until litPixels) it[i] = 0xFF505050.toInt() }
    @Test fun aLoneSpinnerIsNotPictureButADialogIs() {
        assertFalse(PictureGate.isPicture(frame(0)))
        assertFalse("spinner on black (~0.7%)", PictureGate.isPicture(frame(16)))
        assertFalse(PictureGate.isPicture(frame(PictureGate.MIN_LIT - 1)))
        assertTrue("a small dialog (~3%)", PictureGate.isPicture(frame(75)))
        assertTrue(PictureGate.isPicture(frame(2304)))
        assertFalse("strict threshold ignores a ~3% logo screen", PictureGate.isPicture(frame(75), PictureGate.MIN_LIT_STRICT))
        assertTrue(PictureGate.isPicture(frame(PictureGate.MIN_LIT_STRICT), PictureGate.MIN_LIT_STRICT))
    }
}
