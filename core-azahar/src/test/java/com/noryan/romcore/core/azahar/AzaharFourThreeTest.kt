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

    @Test fun rowCyclesSingleStackedNebulosAndKeepsTheGlobalMode() {
        // Stacked -> Nebulos: per-game flag on, global mode untouched
        var st = AzaharFourThree.step(Mode.STACKED, Mode.STACKED, true)
        assertEquals(Mode.FILL_PIP, st.mode); assertTrue(st.fillOn); assertTrue(st.pipOn); assertEquals(Mode.STACKED, st.global)
        // Nebulos -> Single Screen: flag off, global becomes Single
        st = AzaharFourThree.step(Mode.FILL_PIP, Mode.STACKED, true)
        assertEquals(Mode.SINGLE, st.mode); assertFalse(st.fillOn); assertEquals(Mode.SINGLE, st.global)
        // Single -> Stacked: global Stacked
        st = AzaharFourThree.step(Mode.SINGLE, Mode.SINGLE, true)
        assertEquals(Mode.STACKED, st.mode); assertFalse(st.fillOn); assertEquals(Mode.STACKED, st.global)
        // backwards from Single wraps to Nebulos
        st = AzaharFourThree.step(Mode.SINGLE, Mode.SINGLE, false)
        assertEquals(Mode.FILL_PIP, st.mode); assertTrue(st.fillOn)
    }

    @Test fun effectiveModeIsNebulosWhenFillIsOn() {
        assertEquals(Mode.FILL_PIP, AzaharFourThree.effectiveMode(true, Mode.STACKED))
        assertEquals(Mode.STACKED, AzaharFourThree.effectiveMode(false, Mode.STACKED))
        assertEquals(Mode.STACKED, AzaharFourThree.effectiveMode(false, Mode.STACKED_FULL))   // a saved Stacked Full shows as Stacked
    }

    @Test fun nebulosDefaultsOnForNewGamesOnFourByThreeScreens() {
        val s = MapStore()
        assertTrue(AzaharFourThree.isEnabled(s, OOT, OOT, 1280, 960))
        assertFalse(AzaharFourThree.isEnabled(s, OOT, OOT, 1920, 1080))
    }
}
