package com.noryan.nebulos.core.retroarch

import org.junit.Assert.*
import org.junit.Test

class XBr1Test {
    @Test fun metroidGbaNamesMatchWhateverTheSpacing() {
        assertTrue(RaSession.xBr1Game("/storage/x/_ROMS/Metroid - Fusion.gba"))
        assertTrue(RaSession.xBr1Game("/storage/x/_ROMS/Metroid_Fusion.gba"))
        assertTrue(RaSession.xBr1Game("/storage/x/_ROMS/Metroid_-_Zero_Mission.gba"))
        assertTrue(RaSession.xBr1Game("/storage/x/_ROMS/Metroid Zero Mission (USA) [!].gba"))
    }

    @Test fun otherGamesDoNot() {
        assertFalse(RaSession.xBr1Game("/storage/x/_ROMS/Metroid - II Return of Samus.gb"))
        assertFalse(RaSession.xBr1Game("/storage/x/_ROMS/Pokemon - FireRed Version.gba"))
    }
}
