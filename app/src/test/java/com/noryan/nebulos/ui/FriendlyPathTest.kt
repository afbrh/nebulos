package com.noryan.nebulos.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FriendlyPathTest {
    @Test fun sdCard() = assertEquals("SD Card/_ROMS/_Nebulos", friendlyPath("/storage/6ADF-AE4E/_ROMS/_Nebulos"))
    @Test fun sdCardRoot() = assertEquals("SD Card", friendlyPath("/storage/6ADF-AE4E"))
    @Test fun internal() = assertEquals("Internal Storage/Download/roms", friendlyPath("/storage/emulated/0/Download/roms"))
    @Test fun internalSdcardAlias() = assertEquals("Internal Storage/roms", friendlyPath("/sdcard/roms"))
    @Test fun otherPathKeptWhole() = assertEquals("data/user/0", friendlyPath("/data/user/0"))
}
