package com.noryan.nebulos.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseOrderTest {
    private fun sorted(series: String, vararg titles: String) =
        titles.sortedWith(compareBy({ ReleaseOrder.rank(series, it) }, { it.lowercase() }))

    @Test fun metroidInReleaseOrder() = assertEquals(
        listOf("Metroid", "Metroid - Zero Mission", "Metroid - II Return of Samus", "Metroid - Samus Returns", "Metroid - Super Metroid", "Metroid - Fusion", "Metroid - Prime Trilogy", "Metroid - Other M", "Metroid - Dread"),
        sorted("Metroid", "Metroid - Dread", "Metroid", "Metroid - Fusion", "Metroid - Samus Returns", "Metroid - Other M", "Metroid - Zero Mission", "Metroid - Super Metroid", "Metroid - Prime Trilogy", "Metroid - II Return of Samus"))

    @Test fun pokemonFireRedIsNotRed() = assertEquals(
        listOf("Pokemon - Red Version", "Pokemon - FireRed", "Pokemon - FireRed Version", "Pokemon - Silver Version", "Pokemon - Ruby Version", "Pokemon - Emerald", "Pokemon - Brilliant Diamond"),
        sorted("Pokemon", "Pokemon - Emerald", "Pokemon - FireRed Version", "Pokemon - Red Version", "Pokemon - Brilliant Diamond", "Pokemon - FireRed", "Pokemon - Ruby Version", "Pokemon - Silver Version"))

    @Test fun zeldaRemakesSitBelowTheirOriginal() = assertEquals(
        listOf("The Legend of Zelda", "The Legend of Zelda II - The Adventure of Link", "The Legend of Zelda - Ocarina of Time", "The Legend of Zelda - Ocarina of Time 3D", "The Legend of Zelda - Twilight Princess", "The Legend of Zelda - Twilight Princess HD"),
        sorted("The Legend of Zelda", "The Legend of Zelda - Twilight Princess HD", "The Legend of Zelda - Ocarina of Time 3D", "The Legend of Zelda - Twilight Princess", "The Legend of Zelda II - The Adventure of Link", "The Legend of Zelda - Ocarina of Time", "The Legend of Zelda"))

    @Test fun windWakerSitsBetweenFourSwordsAndItsHdRemake() = assertEquals(
        listOf("The Legend of Zelda - Oracle of Ages", "The Legend of Zelda - The Wind Waker", "The Legend of Zelda - The Wind Waker HD", "The Legend of Zelda - Twilight Princess"),
        sorted("The Legend of Zelda", "The Legend of Zelda - Twilight Princess", "The Legend of Zelda - The Wind Waker HD", "The Legend of Zelda - The Wind Waker", "The Legend of Zelda - Oracle of Ages"))

    @Test fun marioAndOthers() {
        assertEquals(listOf("Super Mario Bros", "Super Mario Bros 3", "Super Mario World 2 - Yoshi's Island", "Super Mario Bros New", "Super Mario Bros New 2", "Super Mario Bros U Deluxe", "Super Mario 3D World + Bowser's Fury", "Super Mario Bros Wonder"),
            sorted("Super Mario", "Super Mario 3D World + Bowser's Fury", "Super Mario Bros U Deluxe", "Super Mario Bros Wonder", "Super Mario Bros New 2", "Super Mario Bros New", "Super Mario World 2 - Yoshi's Island", "Super Mario Bros 3", "Super Mario Bros"))
        assertEquals(listOf("Ratchet & Clank", "Ratchet & Clank - Going Commando", "Ratchet & Clank 3 - Up Your Arsenal", "Ratchet - Deadlocked"),
            sorted("Ratchet & Clank", "Ratchet - Deadlocked", "Ratchet & Clank 3 - Up Your Arsenal", "Ratchet & Clank", "Ratchet & Clank - Going Commando"))
        assertEquals(listOf("Jak and Daxter The Precursor Legacy", "Jak II", "Jak 3", "Jak X - Combat Racing"), sorted("Jak and Daxter", "Jak X - Combat Racing", "Jak 3", "Jak II", "Jak and Daxter The Precursor Legacy"))
    }

    @Test fun seriesNameIsDroppedInsideAGroup() {
        assertEquals("A Link to the Past", titleInSeries("The Legend of Zelda - A Link to the Past"))
        assertEquals("The Adventure of Link", titleInSeries("The Legend of Zelda II - The Adventure of Link"))
        assertEquals("Red Version", titleInSeries("Pokemon - Red Version"))
        assertEquals("Super Mario Land 2 - 6 Golden Coins", titleInSeries("Super Mario Land 2 - 6 Golden Coins"))
        assertEquals("Super Mario Land", titleInSeries("Super Mario Land"))
        assertEquals("Super Mario World 2 - Yoshi's Island", titleInSeries("Super Mario World 2 - Yoshi's Island"))
        assertEquals("The Legend of Zelda", titleInSeries("The Legend of Zelda"))
        assertEquals("Super Mario Bros 3", titleInSeries("Super Mario Bros 3"))
    }

    @Test fun metroidSpinOffsSitByReleaseDate() {
        val titles = listOf("Metroid - Dread", "Metroid Prime - Federation Force", "Metroid - Other M", "Metroid Prime - Hunters", "Metroid - Prime Trilogy", "Metroid - Fusion")
        val sorted = titles.sortedBy { ReleaseOrder.rank("Metroid", it) }
        assertEquals(listOf("Metroid - Fusion", "Metroid - Prime Trilogy", "Metroid Prime - Hunters", "Metroid - Other M", "Metroid Prime - Federation Force", "Metroid - Dread"), sorted)
    }
}
