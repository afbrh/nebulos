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

    /** [coreId] "totk" / "botw"; [linkName] the name of the link in the data root (`save` for the recomp runtimes, `saves` for Nebula); [sub] the folder inside `<_Nebulos>/<coreId>-core` (`save`, or `save-v160` for a game version of its own). */
    fun link(ctx: Context, coreId: String, dataRoot: File, sub: String = "save", linkName: String = "save"): File? = try {
        val target = SaveRoot.coreSubDir(ctx, coreId, sub)
        val link = File(dataRoot, linkName)
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

    /**
     * One game's own copy of a folder that the game's port keeps at a fixed name (Metroid Prime's / Echoes' `savestates`, with the quick-resume snapshot): `<parent>/<name>` always holds the
     * folder of the game being launched ([key] = its portable game key); the previous game's is put away as `<name>.<its key>` (renames only: [parent] may be on the SD card, which has no symlinks).
     * A folder from before this existed (no owner recorded) goes to the first game launched afterwards.
     */
    fun activatePerGameFolder(parent: File, name: String, key: String) {
        runCatching {
            val safe = key.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val cur = File(parent, name)
            val ownerFile = File(cur, ".owner")
            val owner = ownerFile.takeIf { it.isFile }?.readText()?.trim()
            if (owner == safe) return@runCatching
            if (cur.isDirectory && owner != null) {
                var away = File(parent, "$name.$owner")
                if (away.exists()) away = File(parent, "$name.$owner.${java.lang.System.currentTimeMillis()}")   // (never overwrite a folder that is already put away)
                if (!cur.renameTo(away)) { Log.e(TAG, "could not put $cur away"); return@runCatching }
            }
            val mine = File(parent, "$name.$safe")
            if (!cur.exists() && mine.isDirectory) mine.renameTo(cur)
            cur.mkdirs()
            ownerFile.writeText(safe)
            Log.i(TAG, "$name now belongs to $safe")
        }.onFailure { Log.e(TAG, "per-game $name failed: $it") }
    }

    /** Copies every file of [from] that [to] does not have (or has older) into [to], checks the sizes, then removes [from]. */
    fun moveInto(from: File, to: File): Boolean {
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

    /**
     * One-time: the performance overlay (Video > Show FPS) was on by default; a settings file saved while it was still the default keeps it on.
     * Turns `show_fps = 1` off in any runtime settings file under [dataRoot] (this game's own folder only: never another game's files) once (marker file), so the new default applies.
     */
    @JvmStatic
    fun hideOverlayOnce(ctx: android.content.Context, dataRoot: java.io.File) {
        try {
            val marker = java.io.File(dataRoot, ".overlay-reset")
            if (marker.exists()) return
            for (root in listOf(dataRoot)) {
                root.walkTopDown().maxDepth(3).filter { it.isFile && it.extension == "ini" && it.length() < 65536 }.forEach { f ->
                    val t = f.readText()
                    if (t.contains("show_fps = 1")) f.writeText(t.replace("show_fps = 1", "show_fps = 0"))
                }
            }
            marker.writeText("1")
        } catch (_: Throwable) {}
    }
}
