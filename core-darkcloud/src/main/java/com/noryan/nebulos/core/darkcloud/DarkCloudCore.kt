package com.noryan.nebulos.core.darkcloud

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * Dark Cloud (PS2) via Chronicle: the game's decompiled code built as a native SDL3 + Vulkan program, NOT an emulator. A one-game core like
 * :core-sotc: the shell reads the disc serial from `SYSTEM.CNF` and routes [SERIALS] here by default; ARMSX2 stays selectable per game.
 *
 * Needs the user's own disc image (never bundled). On the first launch Chronicle extracts the disc's files into the app's private storage
 * (about 10 s); later launches start straight away. The game's saves live in the `_Nebulos` folder. Runs in its own `:darkcloud` process.
 */
object DarkCloudCore : CoreDescriptor {
    override val id = "darkcloud"
    override val displayName = "Chronicle (Dark Cloud)"
    override val systems = setOf(System.PS2)
    override val version: String get() = BuildConfig.VERSION_NAME
    override val extensions = setOf("iso")

    /** Dark Cloud, USA (the NTSC 1.02 release the decompilation matches; Chronicle also reads the PAL disc). */
    val SERIALS = setOf("SCUS-97111")
    const val TITLE = "Dark Cloud"

    const val EXTRA_ROM_PATH = "nebulos.darkcloud.path"
    const val STATE_UNAVAILABLE_REASON = "Dark Cloud (Chronicle) uses the game's own saves."

    fun isDarkCloudSerial(serial: String?): Boolean = serial != null && serial in SERIALS

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, DarkCloudActivity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
