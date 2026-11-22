package com.noryan.nebulos.core.las

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * The Legend of Zelda: Link's Awakening (Nintendo Switch remake; the cartridge's base 1.0.0 and its update, 1.0.1/1.1.0, both run) on the Odyssey/TOTKDroid native runtime: the game's own ARM64 code runs on the CPU, NVN and the system services are
 * implemented by the runtime. NOT an emulator. A ONE-GAME core: [isLinksAwakeningTitle] claims only Link's Awakening; Eden stays selectable per game in the core picker.
 *
 * Own everything: its own process (`:las`), its own game files (`<external files>/native-dumps/01006BB00C6F0000`: the ExeFS and the 6 GB RomFS), its own data root (`<files>/las`) and its own saves (`<_Nebulos>/las-core/save`). Nothing is shared with Odyssey or any other game.
 *
 * Needs the user's own XCI that carries the update (the cartridge image normally carries it) and the user's own prod.keys.
 */
object LasCore : CoreDescriptor {
    override val id = "las"
    override val displayName = "Link's Awakening runtime (The Legend of Zelda: Link's Awakening)"
    override val systems = setOf(System.SWITCH)
    override val version: String get() = "Link's Awakening 1.0.0/1.0.1 on the Odyssey runtime (nebulos patches)"
    override val extensions = setOf("xci")

    const val EXTRA_ROM_PATH = "nebulos.las.path"
    const val TITLE_ID = "01006BB00C6F0000"
    const val GAME_TITLE = "The Legend of Zelda: Link's Awakening"
    const val STATE_UNAVAILABLE_REASON = "Link's Awakening (native port) uses the game's own saves."

    private val TITLE = Regex("link.?s\\W*awakening", RegexOption.IGNORE_CASE)
    fun isLinksAwakeningTitle(title: String): Boolean = TITLE.containsMatchIn(title)

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, LasActivity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", GAME_TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
