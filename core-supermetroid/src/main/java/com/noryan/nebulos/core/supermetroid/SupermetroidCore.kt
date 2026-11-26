package com.noryan.nebulos.core.supermetroid

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * Super Metroid (Super Nintendo) via the snesrev/sm decompilation (MIT; with the fixes of nickdesaulniers/sm): the game's logic in C, not an emulator
 * running its code. It reads graphics, sound and rooms from the user's own ROM; nothing from the game is bundled. A ONE-GAME core:
 * [isSuperMetroidTitle] claims the game by title (the SNES list has no disc ID); the ROM's SHA-1 is checked when the game starts. The SNES libretro
 * cores stay selectable per game.
 *
 * Runs in its own `:supermetroid` process. Own data folder (`files/supermetroid`), saves in `_Nebulos/supermetroid-core`.
 */
object SupermetroidCore : CoreDescriptor {
    override val id = "supermetroid"
    override val displayName = "sm (Super Metroid)"
    override val systems = setOf(System.SNES)
    override val version: String get() = "sm c4b8c21 (nebulos patches)"
    override val extensions = setOf("sfc", "smc")

    const val EXTRA_ROM_PATH = "nebulos.supermetroid.path"
    const val GAME_TITLE = "Super Metroid"

    private val TITLE = Regex("super\\W*metroid", RegexOption.IGNORE_CASE)
    fun isSuperMetroidTitle(title: String): Boolean = TITLE.containsMatchIn(title)

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, SupermetroidActivity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", GAME_TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
