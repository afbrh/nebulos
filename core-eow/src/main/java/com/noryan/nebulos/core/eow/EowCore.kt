package com.noryan.nebulos.core.eow

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * The Legend of Zelda: Echoes of Wisdom (Nintendo Switch, version 1.0.0) on the Odyssey/TOTKDroid native runtime: the game's own ARM64 code runs on the CPU, NVN and the system services are
 * implemented by the runtime. NOT an emulator. A ONE-GAME core: [isEchoesOfWisdomTitle] claims only Echoes of Wisdom; Eden stays selectable per game in the core picker. (Not the Metroid
 * Prime 2 "Echoes" core: that is `core-echoes`.)
 *
 * Own everything: its own process (`:eow`), its own game files (`<external files>/native-dumps/01008CF01BAAC000`: the ExeFS and the 8 GB RomFS), its own data root (`<files>/eow`) and its own
 * saves (`<_Nebulos>/eow-core/save`). Nothing is shared with Odyssey or any other game.
 *
 * Needs the user's own XCI (the base game 1.0.0; a cartridge's update is ignored) and the user's own prod.keys.
 */
object EowCore : CoreDescriptor {
    override val id = "eow"
    override val displayName = "Echoes of Wisdom runtime (The Legend of Zelda: Echoes of Wisdom)"
    override val systems = setOf(System.SWITCH)
    override val version: String get() = "Echoes of Wisdom 1.0.0 on the Odyssey runtime (nebulos patches)"
    override val extensions = setOf("xci")

    const val EXTRA_ROM_PATH = "nebulos.eow.path"
    const val TITLE_ID = "01008CF01BAAC000"
    const val GAME_TITLE = "The Legend of Zelda: Echoes of Wisdom"
    const val STATE_UNAVAILABLE_REASON = "Echoes of Wisdom (native port) uses the game's own saves."

    private val TITLE = Regex("echoes\\W*of\\W*wisdom", RegexOption.IGNORE_CASE)
    fun isEchoesOfWisdomTitle(title: String): Boolean = TITLE.containsMatchIn(title)

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, EowActivity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", GAME_TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
