package com.noryan.nebulos.core.sunshine

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * Super Mario Sunshine (Nintendo GameCube, US revision 0 = GMSE01) via the Super Mario Sunshine PC port (chasem-dev/sms-pc-port, MIT; built from the doldecomp/sms
 * decompilation; chasem-dev/sms-android's launcher layer is replaced by Nebulos). A native port, NOT an emulator. A ONE-GAME core: [isSunshineGameId] claims only
 * GMSE01; Dolphin stays selectable per game in the core picker.
 *
 * Needs the user's own disc image (ISO/GCM played in place; a CISO is unpacked once into the app's files, since the port reads a plain image). Runs in its own `:sunshine` process.
 */
object SunshineCore : CoreDescriptor {
    override val id = "sunshine"
    override val displayName = "SMS PC port (Super Mario Sunshine)"
    override val systems = setOf(System.GAMECUBE)
    override val version: String get() = "SMS PC port (nebulos patches)"
    override val extensions = setOf("iso", "gcm", "ciso")

    const val EXTRA_ROM_PATH = "nebulos.sunshine.path"
    const val GAME_TITLE = "Super Mario Sunshine"
    const val STATE_UNAVAILABLE_REASON = "Super Mario Sunshine (PC port) uses the game's own saves."

    fun isSunshineGameId(gameId: String): Boolean = gameId.uppercase() == "GMSE01"

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, SunshineActivity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", GAME_TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
