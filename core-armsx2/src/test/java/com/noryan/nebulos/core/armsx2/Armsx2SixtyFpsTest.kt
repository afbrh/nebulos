package com.noryan.nebulos.core.armsx2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Armsx2SixtyFpsTest {
    @Test fun parsesPauseSerial() {
        assertEquals("SCUS-97113_6F8545DB", Armsx2SixtyFps.keyFrom("SCUS-97113 (6f8545db)"))
        assertEquals("SCUS-97113_6F8545DB", Armsx2SixtyFps.keyFrom("SCUS-97113 (6F8545DB)"))
    }

    @Test fun noDiscYet() {
        assertNull(Armsx2SixtyFps.keyFrom(null))
        assertNull(Armsx2SixtyFps.keyFrom(" (00000000)"))
        assertNull(Armsx2SixtyFps.keyFrom("SCUS-97113 (00000000)"))
        assertNull(Armsx2SixtyFps.keyFrom("garbage"))
    }

    @Test fun onlyIcoNtscUIsSupported() {
        assertTrue(Armsx2SixtyFps.isSupported("SCUS-97113_6F8545DB"))
        assertFalse(Armsx2SixtyFps.isSupported("SCUS-97113_00000001")) // another revision: addresses unverified
        assertFalse(Armsx2SixtyFps.isSupported("SLUS-20000_6F8545DB"))
        assertFalse(Armsx2SixtyFps.isSupported(null))
    }
}
