package com.noryan.nebulos.recomp

import android.content.Context
import android.system.Os
import android.util.Log
import com.noryan.nebulos.api.save.SaveRoot
import java.io.File

/**
 * Portable save data (nebulos-api/SAVEDATA.md): the recomp runtimes keep the game's own saves in `<data root>/save`, a relative path with no setting
 * for it. The data root is INTERNAL (`filesDir/<core>/...`), and a symlink from an internal folder to a /sdcard path works (SAVEDATA.md rule 2), so
 * `<data root>/save` becomes a symlink to `<_Nebulos>/<coreId>-core/<sub>`. The first time, a real save folder there (the saves made before this existed)
 * is moved into the `_Nebulos` folder (copied, checked, then removed). Call before the runtime starts (it reads the saves while booting).
 */
object RecompSaveData {
    private const val TAG = "RecompSaveData"

    /** [coreId] "totk" / "botw"; [sub] the folder inside `<_Nebulos>/<coreId>-core` (`save`, or `save-v160` for a game version of its own). */
    fun link(ctx: Context, coreId: String, dataRoot: File, sub: String = "save"): File? = try {
        val target = SaveRoot.coreSubDir(ctx, coreId, sub)
        val link = File(dataRoot, "save")
        val current = runCatching { Os.readlink(link.absolutePath) }.getOrNull()
        if (current == target.absolutePath) {
            target
        } else {
            if (current != null) {
                link.delete()   // a link to another root (the user moved _Nebulos): saves there are not touched, the new root's are used
            } else if (link.isDirectory) {
                val moved = moveInto(link, target)
                if (!moved) { Log.e(TAG, "could not move ${link.path} into ${target.path}: leaving the saves where they are"); return null }
            }
            dataRoot.mkdirs()
            Os.symlink(target.absolutePath, link.absolutePath)
            Log.i(TAG, "${link.path} -> ${target.path}")
            target
        }
    } catch (t: Throwable) {
        Log.e(TAG, "save link failed: $t")
        null
    }

    /** Copies every file of [from] that [to] does not have (or has older) into [to], checks the sizes, then removes [from]. */
    private fun moveInto(from: File, to: File): Boolean {
        to.mkdirs()
        from.walkTopDown().filter { it.isFile }.forEach { f ->
            val rel = f.relativeTo(from)
            val dst = File(to, rel.path)
            if (!dst.exists() || dst.lastModified() < f.lastModified()) {
                dst.parentFile?.mkdirs()
                f.copyTo(dst, overwrite = true)
                dst.setLastModified(f.lastModified())
            }
        }
        val ok = from.walkTopDown().filter { it.isFile }.all { f -> File(to, f.relativeTo(from).path).let { it.exists() && it.length() == f.length() || it.lastModified() > f.lastModified() } }
        if (ok) from.deleteRecursively()
        return ok && !from.exists()
    }
}
