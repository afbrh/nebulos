package com.noryan.nebulos.core.sm3dw

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * Super Mario 3D World + Bowser's Fury (Nintendo Switch, the 1.1.0 update = v65536; the cartridge's base 1.0.0 also runs) on the Odyssey/TOTKDroid native runtime: the game's own ARM64 code runs on the CPU, NVN and the system services are
 * implemented by the runtime. NOT an emulator. A ONE-GAME core: [isSuperMario3dWorldTitle] claims only Super Mario 3D World; Eden stays selectable per game in the core picker.
 *
 * Own everything: its own process (`:sm3dw`), its own game files (`<external files>/native-dumps/010028600EBDA000`: the ExeFS and the 2.9 GB RomFS), its own data root (`<files>/sm3dw`) and its own saves (`<_Nebulos>/sm3dw-core/save`). Nothing is shared with Odyssey or any other game.
 *
 * Needs the user's own XCI that carries the update (the cartridge image normally carries it) and the user's own prod.keys.
 */
object Sm3dwCore : CoreDescriptor {
    override val id = "sm3dw"
    override val displayName = "3D World runtime (Super Mario 3D World + Bowser's Fury)"
    override val systems = setOf(System.SWITCH)
    override val version: String get() = "3D World v65536 on the Odyssey runtime (nebulos patches)"
    override val extensions = setOf("xci")

    const val EXTRA_ROM_PATH = "nebulos.mp4.path"
    const val TITLE_ID = "010028600EBDA000"
    const val GAME_TITLE = "Super Mario 3D World + Bowser's Fury"
    const val STATE_UNAVAILABLE_REASON = "Super Mario 3D World (native port) uses the game's own saves."

    private val TITLE = Regex("3d\\W*world|bowser", RegexOption.IGNORE_CASE)
    fun isSuperMario3dWorldTitle(title: String): Boolean = TITLE.containsMatchIn(title)

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, Sm3dwActivity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", GAME_TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
