package com.noryan.nebulos.core.botw

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * The Legend of Zelda: Breath of the Wild (Nintendo Switch) via BOTWRecompAndroid (MIT, (c) Peiikai): a static recompilation / native
 * ARM64 runtime of the game, NOT an emulator. A ONE-GAME core: [isBotwTitle] claims only Breath of the Wild, the shell routes it here by
 * default (Eden stays selectable per game in the core picker).
 *
 * Needs the user's own XCI/NSP (never bundled). On the first launch Eden (already holding the keys and the update) dumps the game's
 * ExeFS + RomFS once to `<_Nebulos>/native-dumps/01007EF00011E000`; the runtime then reads them in place ([BotwActivity], native-code
 * mode: the game's own ARM64 code runs on the CPU, nothing is compiled on the device). Supported game versions: 1.0.0, 1.6.0, 1.8.2, 1.9.0.
 *
 * Runs in its own `:botw` process; native library `libbotw_main.so` (SDL3 static, Java package com.noryan.recompsdl).
 */
object BotwCore : CoreDescriptor {
    override val id = "botw"
    override val displayName = "BOTW Recomp (Breath of the Wild)"
    override val systems = setOf(System.SWITCH)
    override val version: String get() = "BOTWRecompAndroid 0.3.4-beta (nebulos patches)"
    override val extensions = setOf("xci", "nsp")

    const val EXTRA_ROM_PATH = "nebulos.botw.path"
    const val TITLE_ID = "01007EF00011E000"
    const val GAME_TITLE = "The Legend of Zelda: Breath of the Wild"
    const val STATE_UNAVAILABLE_REASON = "Breath of the Wild (recomp) uses the game's own saves."

    private val TITLE = Regex("breath\\s*of\\s*the\\s*wild", RegexOption.IGNORE_CASE)
    fun isBotwTitle(title: String): Boolean = TITLE.containsMatchIn(title)

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, BotwActivity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", GAME_TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
