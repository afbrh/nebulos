package com.noryan.nebulos.core.galaxy

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * Super Mario Galaxy (Nintendo Wii, revision 0) via Nebula (GPL-3.0, keepvaibin/nebula): the SMGCommunity Petari decompilation compiled for the device, with an
 * SDL3/Vulkan renderer. A native port, NOT an emulator. A ONE-GAME core: [isGalaxyGameId] claims only Super Mario Galaxy's three revision-0 discs (North America
 * RMGE01, Europe RMGP01, Japan RMGJ01); Dolphin stays selectable per game in the core picker. Super Mario Galaxy 2 is not this core's game.
 *
 * Needs the user's own disc image (ISO, RVZ, WIA, WBFS, CISO or GCZ, never bundled). The first launch sets the game up once (the importer copies the game's files out
 * of the disc image into `game.pak`, about 3.9 GB, a few minutes); later launches start straight away. Runs in its own `:galaxy` process.
 */
object GalaxyCore : CoreDescriptor {
    override val id = "galaxy"
    override val displayName = "Nebula (Super Mario Galaxy)"
    override val systems = setOf(System.WII)
    override val version: String get() = "Nebula 2.0.0 (nebulos patches)"
    override val extensions = setOf("iso", "rvz", "wia", "wbfs", "ciso", "gcz")

    const val EXTRA_ROM_PATH = "nebulos.galaxy.path"
    const val EXTRA_GAME_ID = "nebulos.galaxy.gameId"
    const val GAME_TITLE = "Super Mario Galaxy"
    const val STATE_UNAVAILABLE_REASON = "Super Mario Galaxy (Nebula) uses the game's own saves."

    private val GAME_IDS = setOf("RMGE01", "RMGP01", "RMGJ01")
    fun isGalaxyGameId(gameId: String): Boolean = gameId.uppercase() in GAME_IDS

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, GalaxyActivity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", GAME_TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
