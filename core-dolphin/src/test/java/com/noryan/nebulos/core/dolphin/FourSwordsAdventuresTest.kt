package com.noryan.nebulos.core.dolphin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FourSwordsAdventuresTest {
    @Test fun matchesOnlyFourSwordsAdventuresIds() {
        for (id in listOf("G4SE01", "G4SP01", "G4SJ01")) assertTrue(id, FourSwordsAdventures.matches(id))
        for (id in listOf(null, "", "GZLE01", "G4SK01", "G4SE02", "RMGE01")) assertFalse(id.toString(), FourSwordsAdventures.matches(id))
    }

    @Test fun dpadWalksLikeTheStick() {
        assertEquals(0.0 to 1.0, FourSwordsAdventures.combine(0.0, 0.0, true, false, false, false))
        assertEquals(-1.0 to -1.0, FourSwordsAdventures.combine(0.0, 0.0, false, true, true, false))
    }

    @Test fun oppositeDpadDirectionsCancelAndStickIsKept() {
        assertEquals(0.0 to 0.0, FourSwordsAdventures.combine(0.0, 0.0, true, true, true, true))
        assertEquals(0.5 to -0.25, FourSwordsAdventures.combine(0.5, -0.25, false, false, false, false))
        assertEquals(1.0 to 0.0, FourSwordsAdventures.combine(0.6, 0.0, false, false, false, true))
    }
}
