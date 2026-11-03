package com.noryan.nebulos.core.azahar

import com.noryan.nebulos.core.azahar.AzaharScreenLayouts.Mode
import com.noryan.nebulos.core.azahar.AzaharScreenLayouts.Rect
import org.junit.Assert.*
import org.junit.Test

class AzaharScreenLayoutsTest {
    @Test fun stackedOnTheNova() {
        val l = AzaharScreenLayouts.compute(Mode.STACKED, false, 1280, 960)
        assertEquals(Rect(40, 0, 1200, 720), l.topSlot)          // top screen 3x
        assertEquals(Rect(480, 720, 320, 240), l.bottomSlot)     // bottom screen 1x directly below
    }

    @Test fun stackedSwappedPutsTheBottomScreenBig() {
        val l = AzaharScreenLayouts.compute(Mode.STACKED, true, 1280, 960)
        assertEquals(Rect(160, 0, 960, 720), l.topSlot)          // bottom screen 3x
        assertEquals(Rect(440, 720, 400, 240), l.bottomSlot)     // top screen 1x
    }

    @Test fun singleScreenFillsTheWidthAndHugsTheTop() {
        assertEquals(Rect(0, 0, 1280, 768), AzaharScreenLayouts.compute(Mode.SINGLE, false, 1280, 960).topSlot)   // top screen, 5:3
        assertEquals(Rect(0, 0, 1280, 960), AzaharScreenLayouts.compute(Mode.SINGLE, true, 1280, 960).topSlot)    // bottom screen, 4:3
        assertTrue(AzaharScreenLayouts.compute(Mode.SINGLE, false, 1280, 960).bottomSlot.x >= 1280)               // parked off the window
        assertEquals(Rect(0, 0, 1080, 648), AzaharScreenLayouts.compute(Mode.SINGLE, false, 1080, 1920).topSlot)  // portrait-shaped window: width-limited
    }

    @Test fun stackedFullOnTheNova() {
        val l = AzaharScreenLayouts.compute(Mode.STACKED_FULL, false, 1280, 960)
        assertEquals(Rect(0, 0, 1280, 768), l.topSlot)           // top screen fills the width, touching the top
        assertEquals(Rect(512, 768, 256, 192), l.bottomSlot)     // bottom screen fills the 192 px left, 4:3
        val s = AzaharScreenLayouts.compute(Mode.STACKED_FULL, true, 1280, 960)
        assertEquals(Rect(128, 0, 1024, 768), s.topSlot)         // bottom screen big in the same 768 px
        assertEquals(Rect(480, 768, 320, 192), s.bottomSlot)     // top screen in the 192 px, 5:3
    }

    @Test fun modesCycleBothWays() {
        // the row offers Single Screen, Stacked and Nebulos only; the retired FILL / STACKED_FULL map to their neighbours
        val offered = listOf(Mode.SINGLE, Mode.STACKED, Mode.FILL_PIP)
        for (m in offered) { assertEquals(m, m.next().previous()); assertEquals(m, m.previous().next()) }
        assertEquals(Mode.STACKED, Mode.SINGLE.next())
        assertEquals(Mode.FILL_PIP, Mode.STACKED.next())
        assertEquals(Mode.SINGLE, Mode.FILL_PIP.next())
        assertEquals(Mode.SINGLE, Mode.FILL.next())            // a saved plain Fill is Nebulos
        assertEquals(Mode.SINGLE, Mode.STACKED_FULL.previous()) // a saved Stacked Full is Stacked
        assertEquals(listOf("Single Screen", "Stacked", "Nebulos"), offered.map { it.label })
    }

    @Test fun screensKeepTheirAspectRatios() {
        for ((w, h) in listOf(1280 to 960, 1920 to 1080, 1080 to 1920, 800 to 480, 1600 to 1200)) for (mode in Mode.values().filter { !it.perGame }) for (swap in listOf(false, true)) {
            val l = AzaharScreenLayouts.compute(mode, swap, w, h)
            for ((r, sw) in listOf(l.topSlot to (if (swap) 320 else 400), l.bottomSlot to (if (swap) 400 else 320))) {
                assertTrue("$w x $h $mode $swap $r", Math.abs(r.width * 240 - r.height * sw) <= sw)   // within 1 px
            }
        }
    }

    @Test fun alwaysInsideTheWindowAndWholeNumberWhenItFits() {
        for ((w, h) in listOf(1280 to 960, 1920 to 1080, 1080 to 1920, 800 to 480, 1600 to 1200)) for (mode in listOf(Mode.SINGLE, Mode.STACKED, Mode.STACKED_FULL)) for (swap in listOf(false, true)) {
            val r = AzaharScreenLayouts.compute(mode, swap, w, h).topSlot
            assertTrue("$w x $h $mode $swap: $r", r.x >= 0 && r.y >= 0 && r.x + r.width <= w && r.y + r.height <= h)
            val bigW = if (swap) 320 else 400
            if (mode == Mode.STACKED && w >= bigW && h >= 480) assertEquals("$w x $h $mode $swap: scale", 0, r.width % bigW)
        }
    }

    @Test fun fillIsTheWholeWindowOnA43Screen() {
        val t = AzaharScreenLayouts.compute(Mode.FILL, false, 1280, 960)
        assertEquals(Rect(0, 0, 1280, 960), t.topSlot)             // top screen: native 4:3 picture, whole window
        assertTrue(t.bottomSlot.x >= 1280)                         // other screen parked off the window
        val b = AzaharScreenLayouts.compute(Mode.FILL, true, 1280, 960)
        assertEquals(Rect(0, 0, 1280, 960), b.topSlot)             // swapped: bottom screen (4:3) fills the window
        assertTrue(b.bottomSlot.x >= 1280)
    }

    @Test fun fillIsAFittedCentred43BoxElsewhere() {
        assertEquals(Rect(160, 0, 960, 720), AzaharScreenLayouts.compute(Mode.FILL, false, 1280, 720).topSlot)
        assertEquals(Rect(0, 555, 1080, 810), AzaharScreenLayouts.compute(Mode.FILL, true, 1080, 1920).topSlot)
        for ((w, h) in listOf(1920 to 1080, 800 to 480, 1600 to 1200, 1080 to 1920)) {
            val r = AzaharScreenLayouts.compute(Mode.FILL, false, w, h).topSlot
            assertTrue("$w x $h $r", r.x >= 0 && r.y >= 0 && r.x + r.width <= w && r.y + r.height <= h && Math.abs(r.width * 3 - r.height * 4) <= 4)
        }
    }

    @Test fun pipIsTheBottomScreenAt1xInTheUpperRightOfTheFilledTop() {
        val l = AzaharScreenLayouts.compute(Mode.FILL_PIP, false, 1280, 960)
        assertEquals(Rect(0, 0, 1280, 960), l.topSlot)                 // top screen fills the display
        assertEquals(Rect(1280 - 320 - 16, 16, 320, 240), l.bottomSlot) // bottom screen 1:1, 16 px from the top and right edges
        val s = AzaharScreenLayouts.compute(Mode.FILL_PIP, true, 1280, 960)
        assertEquals(Rect(0, 0, 1280, 960), s.topSlot)                 // swapped: bottom screen big
        assertEquals(Rect(1280 - 400 - 16, 16, 400, 240), s.bottomSlot) // top screen small at 1x
    }

    @Test fun pipStaysInsideTheBoxAndHasNoNegativeCoordinates() {
        for ((w, h) in listOf(1280 to 960, 1920 to 1080, 800 to 480, 1600 to 1200, 1080 to 1920, 320 to 240)) for (swap in listOf(false, true)) {
            val l = AzaharScreenLayouts.compute(Mode.FILL_PIP, swap, w, h)
            val box = l.topSlot; val p = l.bottomSlot
            assertTrue("$w x $h $swap: $p", p.x >= 0 && p.y >= 0 && p.width == (if (swap) 400 else 320) && p.height == 240)
            if (w >= 1280) assertTrue("$w x $h $swap: $p in $box", p.x >= box.x && p.x + p.width <= box.x + box.width && p.y >= box.y && p.y + p.height <= box.y + box.height)
        }
        // 16:9 window: the small window sits in the corner of the centred 4:3 box, not in the pillarbox
        assertEquals(Rect(160 + 960 - 320 - 16, 16, 320, 240), AzaharScreenLayouts.compute(Mode.FILL_PIP, false, 1280, 720).bottomSlot)
    }

    @Test fun nativeFourThreeTopPictureOnlyWhenTheTopScreenIsBig() {
        assertTrue(AzaharScreenLayouts.usesNativeFourThree(Mode.FILL, false))
        assertTrue(AzaharScreenLayouts.usesNativeFourThree(Mode.FILL_PIP, false))
        assertFalse(AzaharScreenLayouts.usesNativeFourThree(Mode.FILL_PIP, true))
        assertFalse(AzaharScreenLayouts.usesNativeFourThree(Mode.STACKED, false))
    }
}
