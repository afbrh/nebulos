package com.noryan.nebulos.data

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.SubCore
import com.noryan.nebulos.api.System
import org.junit.Assert.*
import org.junit.Test

class CoreRouterTest {
    private open class Fake(override val id: String, override val displayName: String, override val systems: Set<System>,
                            override val subCores: List<SubCore> = emptyList()) : CoreDescriptor {
        override val extensions: Set<String> = emptySet()
        override fun launchIntent(ctx: Context, request: LaunchRequest): Intent = throw UnsupportedOperationException()
    }
    private fun sub(id: String, name: String, vararg s: System) = SubCore(id, name, s.toSet(), emptySet(), "1", "GPL")

    private val lib = Fake("retroarch", "Libretro cores", setOf(System.NES, System.GBA), listOf(
        sub("nestopia", "Nestopia UE", System.NES), sub("fceumm", "FCEUmm", System.NES), sub("mgba", "mGBA", System.GBA)))
    private val other = Fake("dolphin", "Dolphin", setOf(System.GAMECUBE))
    private val cores = listOf(lib, other)

    @Test fun expandsSubCoresPerSystem() {
        assertEquals(listOf("retroarch:nestopia", "retroarch:fceumm"), CoreRouter.candidates(System.NES, cores).map { it.id })
        assertEquals(listOf("retroarch:mgba"), CoreRouter.candidates(System.GBA, cores).map { it.id })
        assertEquals(listOf("dolphin"), CoreRouter.candidates(System.GAMECUBE, cores).map { it.id })
        assertEquals("FCEUmm", CoreRouter.candidates(System.NES, cores)[1].displayName)
    }

    @Test fun overrideResolution() {
        val c = CoreRouter.candidates(System.NES, cores)
        assertEquals("retroarch:fceumm", CoreRouter.resolveOverride("retroarch:fceumm", c)!!.id)
        // legacy id stored by older builds -> the engine's default entry
        assertEquals("retroarch:nestopia", CoreRouter.resolveOverride("retroarch", c)!!.id)
        assertNull(CoreRouter.resolveOverride("retroarch:mgba", c))
        assertNull(CoreRouter.resolveOverride(null, c))
    }

    @Test fun autoChoiceIsDefaultSubCore() {
        assertEquals("retroarch:nestopia", CoreRouter.autoChoice(System.NES, "Mario", CoreRouter.candidates(System.NES, cores))!!.id)
    }

    // ---- Twilight Princess -> Dusklight (routing by disc game ID) ----
    private val prime = Fake("primehack", "PrimeHack", setOf(System.GAMECUBE, System.WII))
    private val dusk = Fake("dusklight", "Dusklight (Twilight Princess)", setOf(System.GAMECUBE))
    private val gc = listOf(other, prime, dusk)   // registry order of the real app: dolphin, primehack, dusklight

    @Test fun twilightPrincessGameCubeIdsAutoRouteToDusklight() {
        for (id in listOf("GZ2E01", "GZ2P01", "GZ2J01")) {
            assertEquals(id, "dusklight", CoreRouter.autoChoice(System.GAMECUBE, "Whatever", CoreRouter.candidates(System.GAMECUBE, gc), id)!!.id)
        }
    }

    @Test fun otherDiscsKeepTheirRouting() {
        val c = CoreRouter.candidates(System.GAMECUBE, gc)
        assertEquals("dolphin", CoreRouter.autoChoice(System.GAMECUBE, "Super Mario Sunshine", c, "GMSE01")!!.id)
        assertEquals("dolphin", CoreRouter.autoChoice(System.GAMECUBE, "Unknown Game", c, null)!!.id)
        assertEquals("primehack", CoreRouter.autoChoice(System.GAMECUBE, "Metroid Prime", c, "GM8E01")!!.id)
        // a title that merely says Twilight Princess is not enough: only the disc ID routes (the Wii release is not claimed)
        assertEquals("dolphin", CoreRouter.autoChoice(System.GAMECUBE, "The Legend of Zelda - Twilight Princess", c, "RZDE01")!!.id)
        assertEquals("dolphin", CoreRouter.autoChoice(System.GAMECUBE, "The Legend of Zelda - Twilight Princess", c, null)!!.id)
    }

    @Test fun overrideStillWinsAndDusklightCannotHijackOtherGames() {
        // per-game override to Dolphin beats the Dusklight default
        assertEquals("dolphin", CoreRouter.choose(System.GAMECUBE, "TP", "GZ2E01", "dolphin", null, gc)!!.id)
        // per-system override to Dolphin beats it too
        assertEquals("dolphin", CoreRouter.choose(System.GAMECUBE, "TP", "GZ2P01", null, "dolphin", gc)!!.id)
        // no override: Dusklight
        assertEquals("dusklight", CoreRouter.choose(System.GAMECUBE, "TP", "GZ2J01", null, null, gc)!!.id)
        // choosing Dusklight for the whole system must not break other GameCube games
        assertEquals("dolphin", CoreRouter.choose(System.GAMECUBE, "Sunshine", "GMSE01", null, "dusklight", gc)!!.id)
        assertEquals("dusklight", CoreRouter.choose(System.GAMECUBE, "TP", "GZ2E01", null, "dusklight", gc)!!.id)
        // ... and a stale per-game Dusklight override on a non-TP disc falls back to automatic
        assertEquals("dolphin", CoreRouter.choose(System.GAMECUBE, "Sunshine", "GMSE01", "dusklight", null, gc)!!.id)
    }

    @Test fun pickerOffersDusklightOnlyForTwilightPrincess() {
        assertEquals(listOf("dolphin", "primehack", "dusklight"), CoreRouter.candidatesFor(System.GAMECUBE, "GZ2E01", gc).map { it.id })
        assertEquals(listOf("dolphin", "primehack"), CoreRouter.candidatesFor(System.GAMECUBE, "GMSE01", gc).map { it.id })
        assertEquals(listOf("dolphin", "primehack"), CoreRouter.candidatesFor(System.GAMECUBE, null, gc).map { it.id })
        assertEquals("Dusklight (Twilight Princess)", CoreRouter.candidatesFor(System.GAMECUBE, "GZ2E01", gc).last().displayName)
    }

    // ---- Wind Waker HD -> wwhd core (routing by title; a .wua has no cheap disc ID) ----
    private val cemu = Fake("cemu", "Cemu", setOf(System.WIIU))
    private val wwhd = Fake("wwhd", "Wind Waker HD", setOf(System.WIIU))
    private val wiiu = listOf(cemu, wwhd)

    @Test fun windWakerHdRoutesToWwhdOnlyByTitle() {
        val c = CoreRouter.candidates(System.WIIU, wiiu)
        assertEquals("wwhd", CoreRouter.autoChoice(System.WIIU, "The Legend of Zelda: The Wind Waker HD", c)!!.id)
        assertEquals("cemu", CoreRouter.autoChoice(System.WIIU, "Mario Kart 8", c)!!.id)
        // a .wua renamed without "HD" is still the only Wind Waker on Wii U (it lost its 60 fps core and its save when named this way)
        assertEquals("wwhd", CoreRouter.autoChoice(System.WIIU, "The Legend of Zelda - The Wind Waker", c)!!.id)
        assertEquals(listOf("cemu", "wwhd"), CoreRouter.candidatesFor(System.WIIU, null, wiiu, "Zelda - Wind Waker HD").map { it.id })
        assertEquals(listOf("cemu"), CoreRouter.candidatesFor(System.WIIU, null, wiiu, "Mario Kart 8").map { it.id })
        // per-game override to Cemu wins for Wind Waker HD; a stale wwhd override on another game falls back
        assertEquals("cemu", CoreRouter.choose(System.WIIU, "Wind Waker HD", null, "cemu", null, wiiu)!!.id)
        assertEquals("cemu", CoreRouter.choose(System.WIIU, "Mario Kart 8", null, "wwhd", null, wiiu)!!.id)
    }

    // ---- N64: Ocarina of Time -> shipwright (Ship of Harkinian), everything else (incl. Majora's Mask) stays on Mupen64Plus-Next ----
    private val n64Libretro = Fake("retroarch", "Libretro cores", setOf(System.N64), listOf(sub("mupen64plus_next", "Mupen64Plus-Next", System.N64)))
    private val shipwright = Fake("shipwright", "Ship of Harkinian (Ocarina of Time)", setOf(System.N64))
    private val n64 = listOf(n64Libretro, shipwright)

    @Test fun n64OcarinaOfTimeRoutesToShipwright() {
        val c = CoreRouter.candidates(System.N64, n64)
        assertEquals("retroarch:mupen64plus_next", CoreRouter.autoChoice(System.N64, "The Legend of Zelda - Majora's Mask", c)!!.id)
        assertEquals("shipwright", CoreRouter.autoChoice(System.N64, "The Legend of Zelda - Ocarina of Time", c)!!.id)
        assertEquals("retroarch:mupen64plus_next", CoreRouter.autoChoice(System.N64, "Super Mario 64", c)!!.id)
        // the native port only offers its own game; Mupen64Plus-Next stays selectable for it
        assertEquals(listOf("retroarch:mupen64plus_next", "shipwright"), CoreRouter.candidatesFor(System.N64, null, n64, "Ocarina of Time").map { it.id })
        assertEquals(listOf("retroarch:mupen64plus_next"), CoreRouter.candidatesFor(System.N64, null, n64, "GoldenEye 007").map { it.id })
        assertEquals("retroarch:mupen64plus_next", CoreRouter.choose(System.N64, "Ocarina of Time", null, "retroarch:mupen64plus_next", null, n64)!!.id)
    }

    // ---- Jak and Daxter -> OpenGOAL (routing by PS2 disc serial) ----
    private val ps2 = Fake("armsx2", "ARMSX2", setOf(System.PS2))
    private val jak = Fake("jak", "OpenGOAL (Jak and Daxter)", setOf(System.PS2))
    private val ps2Cores = listOf(ps2, jak)

    @Test fun jakSerialsAutoRouteToOpenGoalOthersToArmsx2() {
        for (id in listOf("SCUS-97124", "SCUS-97265", "SCUS-97330", "SCES-50361"))
            assertEquals(id, "jak", CoreRouter.autoChoice(System.PS2, "Jak", CoreRouter.candidates(System.PS2, ps2Cores), id)!!.id)
        for (id in listOf("SLUS-20000", null))
            assertEquals(id, "armsx2", CoreRouter.autoChoice(System.PS2, "Other", CoreRouter.candidates(System.PS2, ps2Cores), id)!!.id)
        assertEquals("armsx2", CoreRouter.choose(System.PS2, "Jak", "SCUS-97124", "armsx2", null, ps2Cores)!!.id)   // per-game override keeps ARMSX2
        assertEquals("armsx2", CoreRouter.choose(System.PS2, "X", "SLUS-20000", "jak", null, ps2Cores)!!.id)         // OpenGOAL cannot be forced onto other games
    }

    // ---- Shadow of the Colossus -> Celosia (routing by PS2 disc serial) ----
    private val sotc = Fake("sotc", "Celosia (Shadow of the Colossus)", setOf(System.PS2))
    private val ps2CoresWithSotc = listOf(ps2, jak, sotc)

    @Test fun sotcUsaSerialAutoRoutesToCelosiaOthersDoNot() {
        assertEquals("sotc", CoreRouter.autoChoice(System.PS2, "Shadow of the Colossus", CoreRouter.candidates(System.PS2, ps2CoresWithSotc), "SCUS-97472")!!.id)
        for (id in listOf("SCES-53326", "SLUS-20000", null))
            assertEquals(id, "armsx2", CoreRouter.autoChoice(System.PS2, "Other", CoreRouter.candidates(System.PS2, ps2CoresWithSotc), id)!!.id)
        assertEquals("armsx2", CoreRouter.choose(System.PS2, "Shadow of the Colossus", "SCUS-97472", "armsx2", null, ps2CoresWithSotc)!!.id)   // per-game override keeps ARMSX2
        assertEquals("armsx2", CoreRouter.choose(System.PS2, "X", "SLUS-20000", "sotc", null, ps2CoresWithSotc)!!.id)   // Celosia cannot be forced onto other games
    }
}
