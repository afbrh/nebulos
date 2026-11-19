package com.noryan.nebulos.core.totk

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System

/**
 * The Legend of Zelda: Tears of the Kingdom (Nintendo Switch) via TOTKDroid (MIT, (c) Peiikai): a static recompilation / native
 * ARM64 runtime of the game, NOT an emulator. A ONE-GAME core: [isTotkTitle] claims only Tears of the Kingdom, the shell routes it here by
 * default (Eden stays selectable per game in the core picker).
 *
 * Needs the user's own XCI/NSP (never bundled). On the first launch Eden (already holding the keys and the update) dumps the game's
 * ExeFS + RomFS once to `<_Nebulos>/native-dumps/0100F2C0115B6000`; the runtime then reads them in place ([TotkActivity], native-code
 * mode: the game's own ARM64 code runs on the CPU, nothing is compiled on the device). Supported game versions: 1.4.3.
 *
 * Runs in its own `:totk` process; native library `libtotk_main.so` (SDL3 static, Java package com.noryan.recompsdl).
 */
object TotkCore : CoreDescriptor {
    override val id = "totk"
    override val displayName = "TOTKDroid (Tears of the Kingdom)"
    override val systems = setOf(System.SWITCH)
    override val version: String get() = "TOTKDroid (nebulos patches)"
    override val extensions = setOf("xci", "nsp")

    const val EXTRA_ROM_PATH = "nebulos.totk.path"
    const val TITLE_ID = "0100F2C0115B6000"
    const val GAME_TITLE = "The Legend of Zelda: Tears of the Kingdom"
    const val STATE_UNAVAILABLE_REASON = "Tears of the Kingdom (recomp) uses the game's own saves."

    private val TITLE = Regex("tears\\s*of\\s*the\\s*kingdom", RegexOption.IGNORE_CASE)
    fun isTotkTitle(title: String): Boolean = TITLE.containsMatchIn(title)

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent =
        Intent(ctx, TotkActivity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra("nebulos.title", GAME_TITLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun register() { CoreRegistry.register(this) }
}
