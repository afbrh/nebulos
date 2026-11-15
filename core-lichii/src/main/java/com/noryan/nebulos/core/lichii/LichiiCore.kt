package com.noryan.nebulos.core.lichii

import android.content.Context
import android.content.Intent
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System
import com.noryan.nebulos.api.save.SaveRoot
import java.io.File
import com.noryan.lichii.activities.EmulationActivity
import com.noryan.lichii.model.Game

/**
 * Nintendo Switch 2 via Lichii (an Eden fork with NX2 support, GPLv3; built from pavelgajdo/Lichii-Android, package relocated to
 * com.noryan.lichii). Runs in its own `:lichii` process; see AndroidManifest.xml.
 *
 * Requires user-supplied prod.keys + firmware (never bundled). Lichii keeps its own data dir `<externalFilesDir>/lichii`; the keys and
 * firmware Eden already has are copied there on first launch ([LichiiSetup.shareFromEden]).
 *
 * No save states (same as Eden). Switch 2 games are heavy: expect a device with 16 GB of RAM (see ENGINE-SOURCES.md).
 */
object LichiiCore : CoreDescriptor {
    override val id = "lichii"
    override val displayName = "Lichii (Nintendo Switch 2)"
    override val systems = setOf(System.SWITCH2)
    override val version = "Lichii (Eden fork, NX2), local lichii-android-src build"
    override val extensions = setOf("nup")

    const val STATE_UNAVAILABLE_REASON =
        "Save states are not available for Switch 2. Use the game's own save."

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent {
        // Eden resolves content:// URIs natively (its own FileUtil/openContentUri) and also accepts plain paths.
        LichiiSetup.shareFromEden(ctx)
        val path = request.romUri ?: request.romPath
        val title = request.romPath.substringAfterLast('/').substringBeforeLast('.')
        // EmulationFragment reads the game from the nav-graph argument "game" (populated from intent.extras), NOT from
        // EmulationActivity.EXTRA_SELECTED_GAME. Do not set intent.data: that routes through Eden's own library-scan metadata.
        return Intent(ctx, EmulationActivity::class.java).apply {
            // The full constructor, not Kotlin default arguments: R8 reshapes Game's synthetic default-argument constructor, and a call
            // compiled against the unshrunk class then fails with NoSuchMethodError at launch (title, path, programId, developer, version, isHomebrew).
            putExtra("game", Game(title, path, "", "", "", false))
            putExtra("nebulos.title", title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Portable save data (SAVEDATA.md): `<save root>/lichii/save`. */
    fun saveDir(ctx: Context): File = SaveRoot.coreSubDir(ctx, id, "save")

    fun register() {
        CoreRegistry.register(this)
    }
}
