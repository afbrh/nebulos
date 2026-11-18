package com.noryan.nebulos.core.jak

import android.content.Context
import com.noryan.nebulos.api.save.SaveRoot
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Community HD texture packs for the OpenGOAL games. A pack is a folder or a .zip in
 * `<_Nebulos>/jak-core/<jak1|jak2|jak3>/texture_packs/`, laid out like OpenGOAL's `texture_replacements`
 * (`<tpage name>/<texture name>.png`, optionally `_all/<texture name>.png`). The engine applies replacements while the
 * disc is converted, so choosing (or changing) a pack re-runs that game's one-time import ([JakData]).
 */
object JakTexturePacks {
    private const val DIR = "texture_packs"

    /** Where the user puts packs for [game]; created on demand so it can be found in a file manager. */
    fun packsDir(ctx: Context, game: String): File = File(SaveRoot.coreDir(ctx, JakSaveData.CORE_ID), "$game/$DIR").apply { mkdirs() }

    /** Names of the packs available for [game] (sub-folders and .zip files, sorted). */
    fun list(ctx: Context, game: String): List<String> =
        packsDir(ctx, game).listFiles().orEmpty()
            .filter { (it.isDirectory || it.name.endsWith(".zip", true)) && !it.name.startsWith(".") }
            .map { it.name }.sortedBy { it.lowercase() }

    /** Changes when the chosen pack is replaced or edited, so the import is redone. Empty when no pack is chosen. */
    internal fun signature(ctx: Context, game: String, name: String?): String {
        if (name.isNullOrEmpty()) return ""
        val f = File(packsDir(ctx, game), name)
        if (!f.exists()) return ""
        return "$name:${if (f.isFile) f.length() else 0}:${f.lastModified()}"
    }

    /** Puts the pack's textures where the extractor looks (`<proj>/custom_assets/<game>/texture_replacements`); an empty/missing pack clears it. */
    internal fun stage(ctx: Context, proj: File, game: String, name: String?) {
        val target = File(proj, "custom_assets/$game/texture_replacements")
        target.deleteRecursively()
        if (name.isNullOrEmpty()) return
        val src = File(packsDir(ctx, game), name)
        if (!src.exists()) return
        target.parentFile?.mkdirs()
        if (src.isDirectory) {
            replacementsRoot(src).copyRecursively(target, overwrite = true)
        } else {
            val tmp = File(proj, "custom_assets/$game/.unzip")
            tmp.deleteRecursively(); tmp.mkdirs()
            unzip(src, tmp)
            replacementsRoot(tmp).copyRecursively(target, overwrite = true)
            tmp.deleteRecursively()
        }
    }

    /** Zips usually wrap the textures in a folder (or ship them inside `texture_replacements`): find the real root. */
    internal fun replacementsRoot(dir: File): File {
        var cur = dir
        repeat(4) {
            val named = File(cur, "texture_replacements")
            if (named.isDirectory) return named
            val kids = cur.listFiles().orEmpty().filter { !it.name.startsWith(".") && !it.name.equals("__MACOSX", true) }
            if (kids.size == 1 && kids[0].isDirectory && kids[0].name != "_all" && kids[0].listFiles().orEmpty().any { it.isDirectory || it.name.endsWith(".png", true) } &&
                !looksLikeTpage(kids[0])) cur = kids[0] else return cur
        }
        return cur
    }

    /** A folder directly holding PNGs is a texture page, not a wrapper. */
    private fun looksLikeTpage(d: File) = d.listFiles().orEmpty().any { it.isFile && it.name.endsWith(".png", true) }

    private fun unzip(zip: File, into: File) {
        val root = into.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val out = File(into, e.name)
                if (!out.canonicalPath.startsWith(root)) continue   // zip-slip guard
                if (e.isDirectory) out.mkdirs() else { out.parentFile?.mkdirs(); out.outputStream().use { z.copyTo(it) } }
            }
        }
    }
}
