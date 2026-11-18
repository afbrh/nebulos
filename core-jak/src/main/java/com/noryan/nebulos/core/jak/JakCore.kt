package com.noryan.nebulos.core.jak

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.Ps2DiscId
import com.noryan.nebulos.api.System
import java.io.File

/**
 * Jak and Daxter: The Precursor Legacy, Jak II and Jak 3 (PS2) via OpenGOAL (ISC license; a native port of the games' decompiled
 * GOAL code, NOT an emulator). A three-game core: [sniff] is not used (the shell reads the disc serial from `SYSTEM.CNF` with
 * [Ps2DiscId]); the shell routes the discs listed in [SERIALS] here by default and ARMSX2 stays selectable per game.
 *
 * Needs the user's own disc image (never bundled). On first launch of each game the disc is turned into OpenGOAL's game data
 * ON THE DEVICE by the bundled extractor ([JakData], one time, several minutes); later launches start straight away.
 *
 * Runs in its own `:jak` process ([JakActivity]); native library `libjak_main.so` (SDL3 static, Java package
 * `com.noryan.nebulos.core.jak.sdl`, no clash with ARMSX2's SDL3). Nebulos owns all UI (see core-jak/README-jak.md).
 */
object JakCore : CoreDescriptor {
    override val id = "jak"
    override val displayName = "OpenGOAL (Jak and Daxter)"
    override val systems = setOf(System.PS2)
    override val version: String get() = BuildConfig.VERSION_NAME
    override val extensions = setOf("iso")

    /** PS2 disc serial -> OpenGOAL game name (from the extractor's own disc database). */
    val SERIALS: Map<String, String> = mapOf(
        "SCUS-97124" to "jak1", "SCES-50361" to "jak1", "SCPS-15021" to "jak1", "SCPS-56003" to "jak1",
        "SCUS-97265" to "jak2", "SCES-51608" to "jak2", "SCPS-15057" to "jak2", "SCKA-20010" to "jak2",
        "SCUS-97330" to "jak3", "SCUS-97516" to "jak3", "SCKA-20040" to "jak3", "SCES-52460" to "jak3",
    )
    val TITLES = mapOf(
        "jak1" to "Jak and Daxter: The Precursor Legacy", "jak2" to "Jak II", "jak3" to "Jak 3",
    )

    const val EXTRA_ROM_PATH = "nebulos.jak.path"
    const val EXTRA_GAME = "nebulos.jak.game"
    /** Ids of the HD texture packs switched on (see [JakTexturePacks]); absent/empty = original textures. */
    const val EXTRA_TEXPACKS = "nebulos.jak.texpacks"
    const val STATE_UNAVAILABLE_REASON = "Jak and Daxter (OpenGOAL) uses the game's own saves."

    fun isJakSerial(serial: String?): Boolean = serial != null && serial in SERIALS

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent {
        val serial = Ps2DiscId.readSerial(File(request.romPath))
        val game = SERIALS[serial] ?: "jak1"
        return Intent(ctx, JakActivity::class.java).apply {
            putExtra(EXTRA_ROM_PATH, request.romPath)
            putExtra(EXTRA_GAME, game)
            putExtra("nebulos.title", TITLES[game] ?: "Jak and Daxter")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun register() { CoreRegistry.register(this) }
}
