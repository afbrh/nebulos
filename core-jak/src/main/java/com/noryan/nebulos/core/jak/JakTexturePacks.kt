package com.noryan.nebulos.core.jak

import android.content.Context
import com.noryan.nebulos.api.save.SaveRoot
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * HD texture packs for the OpenGOAL games. A pack is OpenGOAL `texture_replacements` content (`<tpage>/<texture>.png`,
 * optionally `_all/<texture>.png`). Two sources:
 *  - built-in packs shipped inside the app ([BUILTIN], assets `jakpacks/<game>/<file>.zip`), and
 *  - the user's own: a folder or .zip in `<_Nebulos>/jak-core/<game>/texture_packs/`.
 * Several can be on at once (later ones win where they replace the same texture). The engine applies replacements while
 * the disc is converted, so changing the set re-runs that game's one-time import ([JakData]).
 */
object JakTexturePacks {
    private const val DIR = "texture_packs"
    const val BUILTIN_PREFIX = "@"

    data class Builtin(val id: String, val game: String, val title: String, val asset: String, val version: Int)

    /** Shipped packs, applied in this order. Entries whose asset is missing from the APK are not offered. */
    val BUILTIN = listOf(
        Builtin("${BUILTIN_PREFIX}refreshed-hud", "jak1", "Refreshed HUD", "jakpacks/jak1/refreshed-hud.zip", 1),
        Builtin("${BUILTIN_PREFIX}esrgan", "jak1", "ESRGAN HD", "jakpacks/jak1/esrgan.zip", 1),
    )

    private fun builtin(ctx: Context, id: String): Builtin? = BUILTIN.firstOrNull { it.id == id && assetExists(ctx, it.asset) }

    private fun assetExists(ctx: Context, path: String) = runCatching { ctx.assets.open(path).close(); true }.getOrDefault(false)

    /** Where the user puts packs for [game]; created on demand so it can be found in a file manager. */
    fun packsDir(ctx: Context, game: String): File = File(SaveRoot.coreDir(ctx, JakSaveData.CORE_ID), "$game/$DIR").apply { mkdirs() }

    /** Ids of every pack offered for [game]: built-in (`@id`) first, then the user's folders/.zips (by file name). */
    fun list(ctx: Context, game: String): List<String> {
        val own = packsDir(ctx, game).listFiles().orEmpty()
            .filter { (it.isDirectory || it.name.endsWith(".zip", true)) && !it.name.startsWith(".") }
            .map { it.name }.sortedBy { it.lowercase() }
        return BUILTIN.filter { it.game == game && assetExists(ctx, it.asset) }.map { it.id } + own
    }

    /** Human label for a pack id. */
    fun title(id: String): String = BUILTIN.firstOrNull { it.id == id }?.title ?: id.removeSuffix(".zip")

    /** Applied order: built-in in [BUILTIN] order, then the user's by name. */
    private fun ordered(ids: Collection<String>): List<String> =
        BUILTIN.map { it.id }.filter { it in ids } + ids.filter { !it.startsWith(BUILTIN_PREFIX) }.sortedBy { it.lowercase() }

    /** Changes when a chosen pack is replaced, edited, or the app ships a new version, so the import is redone. Empty = no packs. */
    internal fun signature(ctx: Context, game: String, ids: Collection<String>?): String {
        if (ids.isNullOrEmpty()) return ""
        return ordered(ids).mapNotNull { id ->
            if (id.startsWith(BUILTIN_PREFIX)) builtin(ctx, id)?.let { "$id:v${it.version}" }
            else File(packsDir(ctx, game), id).takeIf { it.exists() }?.let { "$id:${if (it.isFile) it.length() else 0}:${it.lastModified()}" }
        }.joinToString("|")
    }

    /** Puts the chosen packs where the extractor looks (`<proj>/custom_assets/<game>/texture_replacements`); none = cleared. */
    internal fun stage(ctx: Context, proj: File, game: String, ids: Collection<String>?) {
        val target = File(proj, "custom_assets/$game/texture_replacements")
        target.deleteRecursively()
        if (ids.isNullOrEmpty()) return
        target.mkdirs()
        val tmp = File(proj, "custom_assets/$game/.unpack")
        for (id in ordered(ids)) {
            tmp.deleteRecursively(); tmp.mkdirs()
            val src: File
            if (id.startsWith(BUILTIN_PREFIX)) {
                val b = builtin(ctx, id) ?: continue
                ctx.assets.open(b.asset).use { unzip(it, tmp) }
                src = tmp
            } else {
                val f = File(packsDir(ctx, game), id)
                if (!f.exists()) continue
                if (f.isDirectory) src = f else { f.inputStream().buffered().use { unzip(it, tmp) }; src = tmp }
            }
            replacementsRoot(src).copyRecursively(target, overwrite = true)
        }
        tmp.deleteRecursively()
    }

    /** Packs often wrap the textures (`custom_assets/<game>/texture_replacements`, or a folder): find the real root. */
    internal fun replacementsRoot(dir: File): File {
        findNamed(dir, "texture_replacements", 4)?.let { return it }
        var cur = dir
        repeat(3) {
            val kids = cur.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") && !it.name.equals("__MACOSX", true) }
            val files = cur.listFiles().orEmpty().any { it.isFile && it.name.endsWith(".png", true) }
            if (kids.size == 1 && !files && kids[0].name != "_all") cur = kids[0] else return cur
        }
        return cur
    }

    private fun findNamed(dir: File, name: String, depth: Int): File? {
        if (depth < 0) return null
        for (k in dir.listFiles().orEmpty()) if (k.isDirectory) {
            if (k.name == name) return k
            findNamed(k, name, depth - 1)?.let { return it }
        }
        return null
    }

    private fun unzip(input: InputStream, into: File) {
        val root = into.canonicalPath + File.separator
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val out = File(into, e.name)
                if (!out.canonicalPath.startsWith(root)) continue   // zip-slip guard
                if (e.isDirectory) out.mkdirs() else { out.parentFile?.mkdirs(); out.outputStream().use { z.copyTo(it) } }
            }
        }
    }
}
