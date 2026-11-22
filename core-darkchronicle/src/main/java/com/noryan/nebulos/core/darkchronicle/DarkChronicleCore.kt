package com.noryan.nebulos.core.darkchronicle

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * Dark Chronicle (Dark Cloud 2, PS2, PAL) via ChronicleTwo: the game's decompiled code built as a native SDL3 + Vulkan program, NOT an emulator.
 * A one-game core like :core-darkcloud: the shell reads the disc serial from `SYSTEM.CNF` and routes [SERIALS] here by default; ARMSX2 stays
 * selectable per game. ChronicleTwo is PAL-only: the NTSC-U disc (SCUS-97213) is not claimed and keeps its current route.
 *
 * Needs the user's own disc image (never bundled). On the first launch the port's extractor converts the disc's files into the app's private
 * storage; later launches start straight away. The game's saves live in the `_Nebulos` folder. Runs in its own `:darkchronicle` process.
 */
object DarkChronicleCore : CoreDescriptor {
    override val id = "darkchronicle"
    override val displayName = "ChronicleTwo (Dark Chronicle)"
    override val systems = setOf(System.PS2)
    override val version: String get() = BuildConfig.VERSION_NAME
    override val extensions = setOf("iso")

    /** Dark Chronicle, PAL (SCES_511.90, the release ChronicleTwo's decompilation matches). */
    val SERIALS = setOf("SCES-51190")
    const val TITLE = "Dark Chronicle"

    const val EXTRA_ROM_PATH = "nebulos.darkchronicle.path"
    const val STATE_UNAVAILABLE_REASON = "Dark Chronicle (ChronicleTwo) uses the game's own saves."

    fun isDarkChronicleSerial(serial: String?): Boolean = serial != null && serial.uppercase().replace('_', '-').replace(".", "") in SERIALS

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, DarkChronicleActivity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
