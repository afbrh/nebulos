package com.noryan.nebulos.core.sotc

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * Shadow of the Colossus (PS2, USA, SCUS-97472) via Celosia (GPL-3.0): the game's MIPS code statically recompiled to C++ ahead
 * of time and run on a native runtime with an OpenGL ES 3.2 renderer, NOT an emulator of the game's CPU. A one-game core like
 * :core-dusklight: the shell reads the disc serial from `SYSTEM.CNF` ([com.noryan.nebulos.api.Ps2DiscId]) and routes
 * [SERIALS] here by default; ARMSX2 stays selectable per game.
 *
 * Needs the user's own disc image (never bundled). On first launch the disc's files are copied out of the ISO on the device
 * ([SotcData], one time, about a minute); later launches start straight away. Runs in its own `:sotc` process ([SotcActivity]).
 */
object SotcCore : CoreDescriptor {
    override val id = "sotc"
    override val displayName = "Celosia (Shadow of the Colossus)"
    override val systems = setOf(System.PS2)
    override val version: String get() = BuildConfig.VERSION_NAME
    override val extensions = setOf("iso")

    /** The recompiled game code is specific to this release. */
    val SERIALS = setOf("SCUS-97472")
    const val TITLE = "Shadow of the Colossus"

    const val EXTRA_ROM_PATH = "nebulos.sotc.path"
    const val STATE_UNAVAILABLE_REASON = "Shadow of the Colossus (Celosia) uses the game's own saves."

    fun isSotcSerial(serial: String?): Boolean = serial != null && serial in SERIALS

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, SotcActivity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
