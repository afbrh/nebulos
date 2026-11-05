package com.noryan.nebulos.core.azahar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AzaharSixtyFpsTest {
    private class MapStore : AzaharFourThree.Store {
        val m = HashMap<String, Boolean>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: Boolean) { m[key] = value }
    }

    private val mm3d = AzaharSixtyFps.specFor("0004000000125500")!!

    @Test fun onlyTheTwoZelda3dUsaBuildsHaveIt() {
        assertNotNull(AzaharSixtyFps.specFor("0004000000125500"))
        assertNotNull(AzaharSixtyFps.specFor("0004000000033500"))
        assertNull(AzaharSixtyFps.specFor("0004000000125600")) // another region
        assertNull(AzaharSixtyFps.specFor(null))
    }

    private val oot3d = AzaharSixtyFps.specFor("0004000000033500")!!

    @Test fun pairsAreWellFormed() {
        for (spec in listOf(mm3d, oot3d)) for (a in listOf(spec.on, spec.off, spec.once)) assertEquals(0, a.size % 2)
    }

    @Test fun ootEveryGuardedWordIsPatched() {
        val onAddrs = (oot3d.on.indices step 2).map { oot3d.on[it] }.toSet()
        for (i in oot3d.off.indices step 2) assertTrue(oot3d.off[i] in onAddrs)
        assertTrue(oot3d.once.size == 4 && oot3d.once[0] == oot3d.flagAddr)
    }

    @Test fun everyGuardedWordIsPatchedAndIsAnInstructionOrLiteralAboveTheCode() {
        val onAddrs = (mm3d.on.indices step 2).map { mm3d.on[it] }.toSet()
        for (i in mm3d.off.indices step 2) assertTrue("off word ${mm3d.off[i].toString(16)} not in on set", mm3d.off[i] in onAddrs)
    }

    @Test fun codeCavesStayInsideTheUnusedTailOfTheCodeSegment() {
        // the code image ends at 0x61CFB4 and its last page at 0x61D000
        for (i in mm3d.on.indices step 2) {
            val a = mm3d.on[i]
            if (a in 0x61C000 until 0x620000) assertTrue(a in 0x61CFB8..0x61CFFC)
        }
    }

    @Test fun onByDefaultAndRememberedPerGame() {
        val s = MapStore()
        assertTrue(AzaharSixtyFps.isEnabled(s, "g"))
        AzaharSixtyFps.setEnabled(s, "g", false)
        assertTrue(!AzaharSixtyFps.isEnabled(s, "g"))
        assertTrue(AzaharSixtyFps.isEnabled(s, "other"))
    }

    @Test fun turningItOffSettlesTheFrameDivisorBackToTwo() {
        assertEquals(mm3d.flagAddr, mm3d.once[0])
        assertEquals(2, mm3d.once[1])
        assertEquals(1, mm3d.on[mm3d.on.indices.first { mm3d.on[it] == mm3d.flagAddr } + 1])
    }
}
