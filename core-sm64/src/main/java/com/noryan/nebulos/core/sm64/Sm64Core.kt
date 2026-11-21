package com.noryan.nebulos.core.sm64

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * Super Mario 64 (Nintendo 64) via Ghostship (HarbourMasters, MIT: the SM64 decompilation on libultraship/Fast3D; assets are extracted from the user's own ROM by Torch on the
 * device, nothing from the game is bundled). A native port, NOT an emulator. A ONE-GAME core: [isSuperMario64Title] claims Super Mario 64 by title (the N64 ROM list has no
 * disc ID); the ROM's SHA-1 is checked when the game starts (US, or JP). The N64 libretro cores stay selectable per game.
 *
 * Runs in its own `:sm64` process; the one-time ROM conversion runs in `:sm64x`. Own data folder (`files/sm64`), saves in `_Nebulos/sm64-core`.
 */
object Sm64Core : CoreDescriptor {
    override val id = "sm64"
    override val displayName = "Ghostship (Super Mario 64)"
    override val systems = setOf(System.N64)
    override val version: String get() = "Ghostship 3.0.0 (nebulos patches)"
    override val extensions = setOf("z64", "n64", "v64")

    const val EXTRA_ROM_PATH = "nebulos.sm64.path"
    const val GAME_TITLE = "Super Mario 64"
    const val STATE_UNAVAILABLE_REASON = "Super Mario 64 (Ghostship) uses the game's own save files."

    private val TITLE = Regex("super\\s*mario\\s*64(?!\\s*ds)", RegexOption.IGNORE_CASE)
    /** The ROM list has no disc ID for the N64: Super Mario 64 is recognised by its title (the hash is checked at launch). */
    fun isSuperMario64Title(title: String): Boolean = TITLE.containsMatchIn(title)

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, Sm64Activity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", GAME_TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
