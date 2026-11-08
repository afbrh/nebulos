package com.noryan.nebulos.api.save

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class QuickStateTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun noStateByDefault() {
        assertFalse(QuickState.exists(tmp.root, "dolphin", "Some Game"))
    }

    @Test fun slotFileCounts() {
        val d = QuickState.dir(tmp.root, "dolphin", "Some Game"); d.mkdirs()
        File(d, "slot1").writeText("state")
        assertTrue(QuickState.exists(tmp.root, "dolphin", "Some Game"))
    }

    @Test fun emptySlotFileDoesNotCount() {
        val d = QuickState.dir(tmp.root, "dolphin", "Some Game"); d.mkdirs()
        File(d, "slot1").writeText("")
        assertFalse(QuickState.exists(tmp.root, "dolphin", "Some Game"))
    }

    @Test fun markerSetAndCleared() {
        QuickState.note(tmp.root, "azahar", "Game", true)
        assertTrue(QuickState.exists(tmp.root, "azahar", "Game"))
        QuickState.note(tmp.root, "azahar", "Game", false)
        assertFalse(QuickState.exists(tmp.root, "azahar", "Game"))
    }

    @Test fun otherCoreAndGameAreSeparate() {
        QuickState.note(tmp.root, "azahar", "Game", true)
        assertFalse(QuickState.exists(tmp.root, "dolphin", "Game"))
        assertFalse(QuickState.exists(tmp.root, "azahar", "Other"))
    }

    @Test fun lookupDoesNotCreateFolders() {
        QuickState.exists(tmp.root, "azahar", "Game")
        assertEquals(0, tmp.root.list()!!.size)
    }
}
