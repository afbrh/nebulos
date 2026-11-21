package com.noryan.nebulos.core.smg2

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * Super Mario Galaxy 2 (Nintendo Wii) via Astral Italian, a native port of the Garigari decompilation (SDL3 + Aurora/Dawn on Vulkan, NOT an emulator). A ONE-GAME core:
 * [isSmg2GameId] accepts the USA disc (SB4E01 revision 0) only, which is all the port supports; the PAL and Japanese discs stay with Dolphin.
 *
 * Needs the user's own disc image (ISO, GCM, CISO, RVZ, WIA, GCZ or WBFS, never bundled): the first launch extracts the game's files from it (about 1.3 GB, one time, into this core's own
 * folder) and later launches reuse them. Runs in its own `:smg2` process. Derived from the game's decompiled code (CC0 project code; Nintendo's rights in the game are not covered): personal use only.
 */
object Smg2Core : CoreDescriptor {
    override val id = "smg2"
    override val displayName = "Astral Italian (Super Mario Galaxy 2)"
    override val systems = setOf(System.WII)
    override val version: String get() = "Astral Italian 5.4.2 (Super Mario Galaxy 2, nebulos patches)"
    override val extensions = setOf("iso", "gcm", "ciso", "rvz", "wia", "gcz", "wbfs")

    const val EXTRA_ROM_PATH = "nebulos.smg2.path"
    const val GAME_TITLE = "Super Mario Galaxy 2"
    const val STATE_UNAVAILABLE_REASON = "Super Mario Galaxy 2 (native port) uses the game's own save file."

    private val GAME_IDS = setOf("SB4E01")
    fun isSmg2GameId(gameId: String): Boolean = gameId.uppercase() in GAME_IDS

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, Smg2Activity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", GAME_TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
