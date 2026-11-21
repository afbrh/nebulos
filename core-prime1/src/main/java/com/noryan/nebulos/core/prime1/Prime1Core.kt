package com.noryan.nebulos.core.prime1

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * Metroid Prime (Nintendo GameCube) via the Metroid Prime 1 native port (a decompilation-based PC port: SDL3 + Dawn/Vulkan, NOT an emulator). A ONE-GAME core:
 * [isPrime1GameId] accepts the GameCube discs the port supports (North America GM8E01, revisions 1.00 / 1.01 / 1.02; Europe GM8P01 "experimental") and the Metroid Prime Trilogy
 * discs (R3ME01 USA, R3MP01 PAL; the port mounts the Trilogy's MP1 folder and rs5mp1_p.dol, "experimental"); only the GameCube ones are CLAIMED as the automatic choice
 * (`GameSpecificCores.claims`: the Trilogy stays with PrimeHack by default, and the port is a per-game choice for it). The Japanese disc (GM8J01) is NOT supported by the port.
 * Dolphin / PrimeHack stay selectable per game in the core picker.
 *
 * Needs the user's own disc image (ISO, GCM, CISO, RVZ, WIA, GCZ or WBFS, never bundled): the port reads it in place (nod), no copy. Runs in its own `:prime1` process.
 * Derived from the game's executable with no license: personal use only.
 */
object Prime1Core : CoreDescriptor {
    override val id = "prime1"
    override val displayName = "Metroid Prime port"
    override val systems = setOf(System.GAMECUBE, System.WII)
    override val version: String get() = "Metroid Prime 1 port 0.36.0 (nebulos patches)"
    override val extensions = setOf("iso", "gcm", "ciso", "rvz", "wia", "gcz", "wbfs")

    const val EXTRA_ROM_PATH = "nebulos.prime1.path"
    const val GAME_TITLE = "Metroid Prime"
    const val STATE_UNAVAILABLE_REASON = "Metroid Prime (native port) uses the game's own memory card saves."

    private val GAME_IDS = setOf("GM8E01", "GM8P01", "R3ME01", "R3MP01")
    fun isPrime1GameId(gameId: String): Boolean = gameId.uppercase() in GAME_IDS

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, Prime1Activity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", GAME_TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
