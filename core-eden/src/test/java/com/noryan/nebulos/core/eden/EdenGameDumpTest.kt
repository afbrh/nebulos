package com.noryan.nebulos.core.eden

import org.junit.Assert.*
import org.junit.Test

class EdenGameDumpTest {
    @Test fun parsesASuccessfulResult() {
        val r = EdenGameDump.parse("""{"ok":true,"titleId":"01007EF00011E000","name":"Zelda","versionRaw":393216,"version":"0.6.0","displayVersion":"1.6.0",
            "updateSource":"installed update","updateAppliedExefs":true,"updateAppliedRomfs":true,"exefsFiles":6,"romfsFiles":100,"romfsBytes":14000000000,"seconds":12.5}""", "/x")
        assertTrue(r.ok); assertEquals("01007EF00011E000", r.titleId); assertEquals(14000000000L, r.romfsBytes)
        assertTrue(r.updateAppliedRomfs); assertEquals("/x", r.outDir)
    }

    @Test fun parsesAnErrorAndGarbage() {
        val e = EdenGameDump.parse("""{"ok":false,"error":"not enough free space"}""", null)
        assertFalse(e.ok); assertEquals("not enough free space", e.error)
        assertFalse(EdenGameDump.parse("not json", null).ok)
    }
}
