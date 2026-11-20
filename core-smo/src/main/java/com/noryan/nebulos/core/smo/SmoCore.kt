package com.noryan.nebulos.core.smo

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * Super Mario Odyssey (Nintendo Switch) via the Odyssey Recomp runtime (MIT; the LAS Recomp / TOTKDroid line, (c) Peiikai and contributors): a static recompilation / native
 * ARM64 runtime of the game, NOT an emulator. A ONE-GAME core: [isSmoTitle] claims only Super Mario Odyssey, the shell routes it here by
 * default (Eden stays selectable per game in the core picker).
 *
 * Needs the user's own XCI/NSP (never bundled). On the first launch Eden (already holding the keys and the update) dumps the game's
 * ExeFS + RomFS once to `<_Nebulos>/native-dumps/0100F2C0115B6000`; the runtime then reads them in place ([SmoActivity], native-code
 * mode: the game's own ARM64 code runs on the CPU, nothing is compiled on the device). Supported game version: 1.0.0 only (the base game of the cartridge, not the update).
 *
 * Runs in its own `:smo` process; native library `libsmo_main.so` (SDL3 static, Java package com.noryan.recompsdl).
 */
object SmoCore : CoreDescriptor {
    override val id = "smo"
    override val displayName = "Odyssey Recomp (Super Mario Odyssey)"
    override val systems = setOf(System.SWITCH)
    override val version: String get() = "SMO recomp (nebulos patches)"
    override val extensions = setOf("xci", "nsp")

    const val EXTRA_ROM_PATH = "nebulos.smo.path"
    const val TITLE_ID = "0100000000010000"
    const val GAME_TITLE = "Super Mario Odyssey"
    const val STATE_UNAVAILABLE_REASON = "Super Mario Odyssey (recomp) uses the game's own saves."

    private val TITLE = Regex("super\\s*mario\\s*odyssey", RegexOption.IGNORE_CASE)
    fun isSmoTitle(title: String): Boolean = TITLE.containsMatchIn(title)

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, SmoActivity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", GAME_TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
