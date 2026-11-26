package com.noryan.nebulos.core.zelda3

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * The Legend of Zelda: A Link to the Past (Super Nintendo) via zelda3 (snesrev, MIT): a reimplementation of the game in C, not an emulator. Its data is
 * built from the user's own US ROM on the device; nothing from the game is bundled. A ONE-GAME core: [isLinkToThePastTitle] claims the game by title (the
 * SNES list has no disc ID); the ROM's SHA-1 is checked when the game starts. The SNES libretro cores stay selectable per game.
 *
 * Runs in its own `:zelda3` process. Own data folder (`files/zelda3`), saves in `_Nebulos/zelda3-core`.
 */
object Zelda3Core : CoreDescriptor {
    override val id = "zelda3"
    override val displayName = "zelda3 (A Link to the Past)"
    override val systems = setOf(System.SNES)
    override val version: String get() = "zelda3 45a149d (nebulos patches)"
    override val extensions = setOf("sfc", "smc")

    const val EXTRA_ROM_PATH = "nebulos.zelda3.path"
    const val GAME_TITLE = "A Link to the Past"

    private val TITLE = Regex("link\\W+to\\W+the\\W+past", RegexOption.IGNORE_CASE)
    fun isLinkToThePastTitle(title: String): Boolean = TITLE.containsMatchIn(title)

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, Zelda3Activity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", GAME_TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
