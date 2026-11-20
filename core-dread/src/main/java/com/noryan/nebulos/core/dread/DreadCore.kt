package com.noryan.nebulos.core.dread

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * Metroid Dread (Nintendo Switch) 2.1.0 on the Odyssey/TOTKDroid native runtime: the game's own ARM64 code runs on the CPU, NVN and the system services are
 * implemented by the runtime. NOT an emulator. A ONE-GAME core: [isDreadTitle] claims only Metroid Dread; Eden stays selectable per game in the core picker.
 *
 * Needs the user's own XCI that carries the 2.1.0 update (a cartridge image normally does); the first launch extracts the base game and applies the update with
 * hactool and the user's own prod.keys, once, into `<external files>/native-dumps/010093801237C000`. Runs in its own `:dread` process.
 */
object DreadCore : CoreDescriptor {
    override val id = "dread"
    override val displayName = "Dread runtime (Metroid Dread)"
    override val systems = setOf(System.SWITCH)
    override val version: String get() = "Dread 2.1.0 on the Odyssey runtime (nebulos patches)"
    override val extensions = setOf("xci")

    const val EXTRA_ROM_PATH = "nebulos.dread.path"
    const val TITLE_ID = "010093801237C000"
    const val GAME_TITLE = "Metroid Dread"
    const val STATE_UNAVAILABLE_REASON = "Metroid Dread (native port) uses the game's own saves."

    private val TITLE = Regex("metroid\\W*dread", RegexOption.IGNORE_CASE)
    fun isDreadTitle(title: String): Boolean = TITLE.containsMatchIn(title)

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, DreadActivity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", GAME_TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
