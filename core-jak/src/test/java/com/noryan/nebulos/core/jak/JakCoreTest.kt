package com.noryan.nebulos.core.jak

import com.noryan.nebulos.api.Pad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JakCoreTest {
    @Test fun claimsTheTrilogySerialsOnly() {
        for (s in listOf("SCUS-97124", "SCES-50361", "SCUS-97265", "SCES-51608", "SCUS-97330", "SCES-52460")) assertTrue(s, JakCore.isJakSerial(s))
        assertFalse(JakCore.isJakSerial("SLUS-20000"))   // some other PS2 game
        assertFalse(JakCore.isJakSerial(null))
    }

    @Test fun serialsMapToGames() {
        assertEquals("jak1", JakCore.SERIALS["SCUS-97124"]); assertEquals("jak2", JakCore.SERIALS["SCUS-97265"]); assertEquals("jak3", JakCore.SERIALS["SCUS-97330"])
        assertEquals(3, JakCore.SERIALS.values.toSet().size)
    }

    @Test fun ps2FaceButtonsFollowTheNebulosRule() {
        assertEquals(JakInput.EAST, JakInput.button(Pad.A))    // Circle
        assertEquals(JakInput.SOUTH, JakInput.button(Pad.B))   // Cross
        assertEquals(JakInput.NORTH, JakInput.button(Pad.X))   // Triangle
        assertEquals(JakInput.WEST, JakInput.button(Pad.Y))    // Square
        assertNull(JakInput.button(Pad.L2))                    // analog trigger
        assertEquals(JakInput.AXIS_MIN, JakInput.trigger(0f)); assertEquals(JakInput.AXIS_MAX, JakInput.trigger(1f))
    }
}
