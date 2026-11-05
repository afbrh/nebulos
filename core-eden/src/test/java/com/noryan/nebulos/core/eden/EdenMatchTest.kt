package com.noryan.nebulos.core.eden

import org.junit.Assert.*
import org.junit.Test

class EdenMatchTest {
    @Test fun spacedAndUnderscoredNamesPickTheSameMod() {
        val odyssey = "0100000000010000/4-3 Aspect Ratio"
        assertEquals(odyssey, EdenMods.aspectDirName("Super Mario Odyssey"))
        assertEquals(odyssey, EdenMods.aspectDirName("Super_Mario_Odyssey"))
        assertEquals(odyssey, EdenMods.aspectDirName("Super Mario Odyssey (USA) [v1.3.0]"))
        assertEquals("01006BB00C6F0000/4-3 Aspect Ratio", EdenMods.aspectDirName("The_Legend_of_Zelda_-_Link's_Awakening"))
        assertEquals("010093801237C000/4-3 Aspect Ratio", EdenMods.aspectDirName("Metroid_-_Dread"))
    }

    @Test fun unrelatedTitlesGetNoMod() {
        assertNull(EdenMods.aspectDirName("Some Other Game"))
    }

    @Test fun matchTitleCleansBracketsUnderscoresAndSpaces() {
        assertEquals("Super Mario Odyssey", EdenMods.matchTitle("Super_Mario__Odyssey  (EU) [UPDATE]"))
    }
}
