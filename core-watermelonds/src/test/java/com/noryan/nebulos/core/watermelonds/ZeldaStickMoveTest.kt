package com.noryan.nebulos.core.watermelonds

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ZeldaStickMoveTest {
    @Test fun supportedGameCodes() {
        for (c in listOf("AZEE", "AZEP", "AZEJ", "BKIE", "BKIP", "BKIJ")) assertTrue(c, ZeldaStickMove.isSupportedGameCode(c))
        for (c in listOf("AZ", "ABCD", null, "YZXE")) assertFalse(c.toString(), ZeldaStickMove.isSupportedGameCode(c))
    }

    @Test fun readsGameCodeFromHeader() {
        val f = File.createTempFile("rom", ".nds").apply { deleteOnExit() }
        val h = ByteArray(32); "AZEE".toByteArray().copyInto(h, 12); f.writeBytes(h)
        assertEquals("AZEE", ZeldaStickMove.gameCode(f.path))
        assertNull(ZeldaStickMove.gameCode("/nonexistent/x.nds"))
    }

    @Test fun fullPushReachesTheScreenEdge() {
        assertEquals(128f, ZeldaStickMove.edgeDistance(1f, 0f), 0.01f)   // right edge
        assertEquals(96f, ZeldaStickMove.edgeDistance(0f, -1f), 0.01f)   // top edge
        val d = 0.7071f
        assertEquals(108.6f, ZeldaStickMove.edgeDistance(d, d), 0.3f)     // diagonal stays well inside the corner
    }
}
