package com.noryan.nebulos.core.echoes

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * Metroid Prime 2: Echoes (Nintendo GameCube) via the Echoes native port (a decompilation-based PC port: SDL3 + Dawn/Vulkan, NOT an emulator). A ONE-GAME core:
 * [isEchoesGameId] accepts Echoes' own discs (North America G2ME01, Europe G2MP01) and the Metroid Prime Trilogy discs (R3ME01, R3MP01) that the port plays as Echoes
 * (experimental); only the first two are CLAIMED as the automatic choice (`GameSpecificCores.claims`: the Trilogy stays with PrimeHack by default, and Echoes is a per-game
 * choice for it). Dolphin / PrimeHack stay selectable per game in the core picker.
 *
 * Needs the user's own disc image (ISO, GCM, CISO, RVZ, WIA, GCZ or WBFS, never bundled): the port reads it in place (nod), no copy. Runs in its own `:echoes` process.
 * Derived from the game's executable with no license: personal use only.
 */
object EchoesCore : CoreDescriptor {
    override val id = "echoes"
    override val displayName = "Echoes port (Metroid Prime 2)"
    override val systems = setOf(System.GAMECUBE, System.WII)
    override val version: String get() = "Metroid Prime 2 Echoes port 0.12.0 (nebulos patches)"
    override val extensions = setOf("iso", "gcm", "ciso", "rvz", "wia", "gcz", "wbfs")

    const val EXTRA_ROM_PATH = "nebulos.echoes.path"
    const val GAME_TITLE = "Metroid Prime 2: Echoes"
    const val STATE_UNAVAILABLE_REASON = "Metroid Prime 2: Echoes (native port) uses the game's own memory card saves."

    private val GAME_IDS = setOf("G2ME01", "G2MP01", "R3ME01", "R3MP01")
    fun isEchoesGameId(gameId: String): Boolean = gameId.uppercase() in GAME_IDS

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, EchoesActivity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", GAME_TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
