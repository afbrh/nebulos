package com.noryan.nebulos.core.smw

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * Super Mario World (Super Nintendo) via smw (snesrev, MIT): a reimplementation of the game in C, not an emulator. Its data is built from the user's
 * own US ROM on the device; nothing from the game is bundled. A ONE-GAME core: [isSuperMarioWorldTitle] claims the game by title (the SNES list has
 * no disc ID); the ROM's SHA-1 is checked when the game starts. The SNES libretro cores stay selectable per game.
 *
 * Runs in its own `:smw` process. Own data folder (`files/smw`), saves in `_Nebulos/smw-core`.
 */
object SmwCore : CoreDescriptor {
    override val id = "smw"
    override val displayName = "smw (Super Mario World)"
    override val systems = setOf(System.SNES)
    override val version: String get() = "smw eae20c6 (nebulos patches)"
    override val extensions = setOf("sfc", "smc")

    const val EXTRA_ROM_PATH = "nebulos.smw.path"
    const val GAME_TITLE = "Super Mario World"

    private val TITLE = Regex("super\\W*mario\\W*world(?!\\W*2)", RegexOption.IGNORE_CASE)
    fun isSuperMarioWorldTitle(title: String): Boolean = TITLE.containsMatchIn(title)

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, SmwActivity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", GAME_TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
