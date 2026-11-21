package com.noryan.nebulos.core.wonder

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * Super Mario Bros. Wonder (Nintendo Switch, 1.0.0) on the Odyssey/TOTKDroid native runtime: the game's own ARM64 code runs on the CPU, NVN and the system services are
 * implemented by the runtime. NOT an emulator. A ONE-GAME core: [isWonderTitle] claims only Super Mario Bros. Wonder; Eden stays selectable per game in the core picker.
 *
 * Own everything: its own process (`:wonder`), its own game files (`<external files>/native-dumps/010015100B514000`, ExeFS + RomFS of the cartridge's base game), its own data
 * root (`<files>/wonder`) and its own saves (`<_Nebulos>/wonder-core/save`). Nothing is shared with Odyssey or any other game.
 *
 * Needs the user's own XCI (the base game, version 1.0.0) and the user's own prod.keys.
 */
object WonderCore : CoreDescriptor {
    override val id = "wonder"
    override val displayName = "Wonder runtime (Super Mario Bros. Wonder)"
    override val systems = setOf(System.SWITCH)
    override val version: String get() = "Wonder 1.0.0 on the Odyssey runtime (nebulos patches)"
    override val extensions = setOf("xci")

    const val EXTRA_ROM_PATH = "nebulos.wonder.path"
    const val TITLE_ID = "010015100B514000"
    const val GAME_TITLE = "Super Mario Bros. Wonder"
    const val STATE_UNAVAILABLE_REASON = "Super Mario Bros. Wonder (native port) uses the game's own saves."

    private val TITLE = Regex("mario\\W*bros\\W*wonder", RegexOption.IGNORE_CASE)
    fun isWonderTitle(title: String): Boolean = TITLE.containsMatchIn(title)

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, WonderActivity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", GAME_TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
