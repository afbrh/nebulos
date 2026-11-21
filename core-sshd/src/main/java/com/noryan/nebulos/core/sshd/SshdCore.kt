package com.noryan.nebulos.core.sshd

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * The Legend of Zelda: Skyward Sword HD (Nintendo Switch, 1.0.1) on the Odyssey/TOTKDroid native runtime: the game's own ARM64 code runs on the CPU, NVN and the system services are
 * implemented by the runtime. NOT an emulator. A ONE-GAME core: [isSkywardSwordHdTitle] claims only Skyward Sword HD; Eden stays selectable per game in the core picker.
 *
 * Own everything: its own process (`:sshd`), its own game files (`<external files>/native-dumps/01002DA013484000` for the ExeFS, and the 7 GB RomFS next to the cartridge's
 * volume under `Nebulos-gamedata/01002DA013484000`), its own data root (`<files>/sshd`) and its own saves (`<_Nebulos>/sshd-core/save`). Nothing is shared with Odyssey or any other game.
 *
 * Needs the user's own XCI that carries the 1.0.1 update (the cartridge image normally does) and the user's own prod.keys.
 */
object SshdCore : CoreDescriptor {
    override val id = "sshd"
    override val displayName = "Skyward Sword HD runtime (The Legend of Zelda: Skyward Sword HD)"
    override val systems = setOf(System.SWITCH)
    override val version: String get() = "Skyward Sword HD 1.0.1 on the Odyssey runtime (nebulos patches)"
    override val extensions = setOf("xci")

    const val EXTRA_ROM_PATH = "nebulos.sshd.path"
    const val TITLE_ID = "01002DA013484000"
    const val GAME_TITLE = "The Legend of Zelda: Skyward Sword HD"
    const val STATE_UNAVAILABLE_REASON = "Skyward Sword HD (native port) uses the game's own saves."

    private val TITLE = Regex("skyward\\W*sword", RegexOption.IGNORE_CASE)
    fun isSkywardSwordHdTitle(title: String): Boolean = TITLE.containsMatchIn(title)

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, SshdActivity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", GAME_TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
