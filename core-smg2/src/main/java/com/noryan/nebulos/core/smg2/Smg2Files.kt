package com.noryan.nebulos.core.smg2

import android.content.Context
import com.noryan.nebulos.api.save.SaveRoot
import com.noryan.nebulos.recomp.RecompSaveData
import java.io.File

/** Folders of the Super Mario Galaxy 2 core: everything is its own, nothing is shared with any other game. */
object Smg2Files {
    class Folders(val root: File, val support: File, val cache: File)

    /** The extracted disc (`files/` + `sys/`): on the internal volume (an SD card writes the ~3,900 small files slowly), a cache of the user's own game, rebuilt from the disc image if lost. */
    fun gameDir(ctx: Context): File =
        File(File(ctx.getExternalFilesDir(null)?.takeIf { it.isDirectory || it.mkdirs() } ?: ctx.filesDir, "native-dumps"), "SB4E01")

    private fun marker(game: File) = File(game, "nebulos-dump.json")
    fun isExtracted(game: File): Boolean = marker(game).isFile && File(game, "sys/main.dol").isFile && File(game, "files").isDirectory
    fun markExtracted(game: File) { runCatching { marker(game).writeText("{\"ok\":true,\"gameId\":\"SB4E01\",\"source\":\"disc image (nod)\"}") } }

    /** `files/smg2` (internal): `cache` (Dawn + pipeline caches) and `support` (a link to `_Nebulos/smg2-core`: saves, settings, controls, logs). */
    fun prepareFolders(ctx: Context): Folders {
        val root = File(ctx.filesDir, "smg2").also { it.mkdirs() }
        val cache = File(root, "cache").also { it.mkdirs() }
        // the port's user folder (the NAND with the save file, display and control settings, logs) lives in the _Nebulos folder
        RecompSaveData.link(ctx, "smg2", root, sub = "support", linkName = "support")
        val support = File(root, "support").also { it.mkdirs() }
        return Folders(root, support, cache)
    }
}
