package com.noryan.romcore.core.azahar

import com.noryan.romcore.core.azahar.AzaharScreenLayouts.Mode
import org.junit.Assert.*
import org.junit.Test

class AzaharFourThreeTest {
    private class MapStore : AzaharFourThree.Store {
        val m = HashMap<String, Boolean>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: Boolean) { m[key] = value }
    }
    private val OOT = "0004000000033500"

    @Test fun defaultsOnOnA43ScreenOffElsewhere() {
        assertTrue(AzaharFourThree.defaultOn(OOT, 1280, 960))     // Retroid Pocket Nova
        assertTrue(AzaharFourThree.defaultOn(null, 960, 1280))
        assertFalse(AzaharFourThree.defaultOn(OOT, 1920, 1080))
        assertFalse(AzaharFourThree.defaultOn(OOT, 0, 0))
    }

    @Test fun denyListTurnsTheDefaultOffButNotTheUsersChoice() {
        for (id in AzaharFourThree.DENY.keys) {
            val s = MapStore()
            assertFalse(AzaharFourThree.isEnabled(s, id, id, 1280, 960))
            AzaharFourThree.setEnabled(s, id, true)
            assertTrue(AzaharFourThree.isEnabled(s, id, id, 1280, 960))
        }
    }

    @Test fun choiceIsPerGameAndPersists() {
        val s = MapStore()
        assertTrue(AzaharFourThree.isEnabled(s, OOT, OOT, 1280, 960))
        AzaharFourThree.setEnabled(s, OOT, false)
        assertFalse(AzaharFourThree.isEnabled(s, OOT, OOT, 1280, 960))
        assertTrue(AzaharFourThree.isEnabled(s, "0004000000030800", "0004000000030800", 1280, 960))   // another game keeps the default
        AzaharFourThree.setEnabled(s, OOT, true)
        assertTrue(AzaharFourThree.isEnabled(s, OOT, OOT, 1280, 960))
    }

    @Test fun gameKeyIsTheTitleIdOrTheFileName() {
        assertEquals(OOT, AzaharFourThree.gameKey(OOT, "/storage/x/_ROMS/Zelda.cci"))
        assertEquals("Game.cia", AzaharFourThree.gameKey(null, "/storage/x/_ROMS/Game.cia"))
    }

    @Test fun rowCyclesThroughFillAndKeepsTheGlobalMode() {
        // STACKED_FULL -> 4:3 Fill: per-game flag on, global mode untouched
        var st = AzaharFourThree.step(Mode.STACKED_FULL, Mode.STACKED_FULL, true)
        assertEquals(Mode.FILL, st.mode); assertTrue(st.fillOn); assertEquals(Mode.STACKED_FULL, st.global)
        // 4:3 Fill -> 4:3 Fill + PiP: still per-game, global untouched
        st = AzaharFourThree.step(Mode.FILL, Mode.STACKED_FULL, true)
        assertEquals(Mode.FILL_PIP, st.mode); assertTrue(st.fillOn); assertTrue(st.pipOn); assertEquals(Mode.STACKED_FULL, st.global)
        // PiP -> Single Screen: flag off, global becomes Single
        st = AzaharFourThree.step(Mode.FILL_PIP, Mode.STACKED_FULL, true)
        assertEquals(Mode.SINGLE, st.mode); assertFalse(st.fillOn); assertEquals(Mode.SINGLE, st.global)
        // backwards from Single wraps to PiP; from PiP to Fill; from Fill goes to Stacked Full
        st = AzaharFourThree.step(Mode.SINGLE, Mode.SINGLE, false)
        assertEquals(Mode.FILL_PIP, st.mode); assertTrue(st.fillOn)
        st = AzaharFourThree.step(Mode.FILL_PIP, Mode.SINGLE, false)
        assertEquals(Mode.FILL, st.mode); assertTrue(st.fillOn); assertFalse(st.pipOn)
        st = AzaharFourThree.step(Mode.FILL, Mode.SINGLE, false)
        assertEquals(Mode.STACKED_FULL, st.mode); assertFalse(st.fillOn); assertEquals(Mode.STACKED_FULL, st.global)
    }

    @Test fun effectiveModeIsFillOnlyWhenOn() {
        assertEquals(Mode.FILL, AzaharFourThree.effectiveMode(true, Mode.STACKED))
        assertEquals(Mode.STACKED, AzaharFourThree.effectiveMode(false, Mode.STACKED))
    }

    @Test fun pipDefaultsOnForNewGamesButNotOverASavedFillChoice() {
        val s = MapStore()
        assertTrue(AzaharFourThree.isPipEnabled(s, OOT, OOT, 1280, 960))      // new game on the Nova
        assertFalse(AzaharFourThree.isPipEnabled(s, OOT, OOT, 1920, 1080))    // 16:9 screens: off
        AzaharFourThree.setEnabled(s, OOT, true)                              // a game that already has a saved Fill choice keeps plain Fill
        assertFalse(AzaharFourThree.isPipEnabled(s, OOT, OOT, 1280, 960))
        AzaharFourThree.setPipEnabled(s, OOT, true)
        assertTrue(AzaharFourThree.isPipEnabled(s, OOT, OOT, 1280, 960))
        assertEquals(Mode.FILL_PIP, AzaharFourThree.effectiveMode(true, Mode.STACKED, true))
        assertEquals(Mode.FILL, AzaharFourThree.effectiveMode(true, Mode.STACKED, false))
        assertEquals(Mode.STACKED, AzaharFourThree.effectiveMode(false, Mode.STACKED, true))
    }
}
