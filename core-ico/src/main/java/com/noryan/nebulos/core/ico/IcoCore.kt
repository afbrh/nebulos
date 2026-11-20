package com.noryan.nebulos.core.ico

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * ICO (PS2) via ico-pc (MIT): the game's decompiled code built as a native SDL3 + Vulkan program, NOT an emulator. A one-game core like
 * :core-darkcloud: the shell reads the disc serial from `SYSTEM.CNF` and routes [SERIALS] here by default; ARMSX2 stays selectable per game.
 *
 * Needs the user's own disc image (never bundled; any of the seven discs ico-pc accepts). On the first launch the game prepares its
 * graphics (a few minutes, once) and extracts the disc into the app's storage; later launches start straight away. The game's memory
 * cards and save states live in the `_Nebulos` folder. Runs in its own `:ico` process.
 */
object IcoCore : CoreDescriptor {
    override val id = "ico"
    override val displayName = "ico-pc (ICO)"
    override val systems = setOf(System.PS2)
    override val version: String get() = BuildConfig.VERSION_NAME
    override val extensions = setOf("iso", "chd", "bin", "cue")

    /** USA, Europe, Japan, Korea, Asia, and the 2001 preview / magazine demo disc. */
    val SERIALS = setOf("SCUS-97113", "SCES-50760", "SCPS-11003", "SCPS-56001", "SCPS-55001", "SCUS-97158")
    const val TITLE = "ICO"

    const val EXTRA_ROM_PATH = "nebulos.ico.path"
    const val STATE_UNAVAILABLE_REASON = "ICO (ico-pc) uses the game's own saves."

    fun isIcoSerial(serial: String?): Boolean = serial != null && serial in SERIALS

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, IcoCoreActivity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
