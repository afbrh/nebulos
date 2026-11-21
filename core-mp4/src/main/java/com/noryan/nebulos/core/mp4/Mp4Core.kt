package com.noryan.nebulos.core.mp4

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * Metroid Prime 4: Beyond (Nintendo Switch, 1.1.0) on the Odyssey/TOTKDroid native runtime: the game's own ARM64 code runs on the CPU, NVN and the system services are
 * implemented by the runtime. NOT an emulator. A ONE-GAME core: [isMetroidPrime4Title] claims only Metroid Prime 4; Eden stays selectable per game in the core picker.
 *
 * Own everything: its own process (`:mp4`), its own game files (`<external files>/native-dumps/010019A01E2F2000` for the ExeFS, and the 26 GB RomFS next to the cartridge's
 * volume under `Nebulos-gamedata/010019A01E2F2000`), its own data root (`<files>/mp4`) and its own saves (`<_Nebulos>/mp4-core/save`). Nothing is shared with Odyssey or any other game.
 *
 * Needs the user's own XCI that carries the 1.1.0 update (the cartridge image normally does) and the user's own prod.keys.
 */
object Mp4Core : CoreDescriptor {
    override val id = "mp4"
    override val displayName = "Prime 4 runtime (Metroid Prime 4: Beyond)"
    override val systems = setOf(System.SWITCH)
    override val version: String get() = "Prime 4 1.1.0 on the Odyssey runtime (nebulos patches)"
    override val extensions = setOf("xci")

    const val EXTRA_ROM_PATH = "nebulos.mp4.path"
    const val TITLE_ID = "010019A01E2F2000"
    const val GAME_TITLE = "Metroid Prime 4: Beyond"
    const val STATE_UNAVAILABLE_REASON = "Metroid Prime 4 (native port) uses the game's own saves."

    private val TITLE = Regex("metroid\\W*prime\\W*4", RegexOption.IGNORE_CASE)
    fun isMetroidPrime4Title(title: String): Boolean = TITLE.containsMatchIn(title)

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, Mp4Activity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", GAME_TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
