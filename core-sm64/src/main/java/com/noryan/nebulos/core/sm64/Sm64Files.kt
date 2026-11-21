package com.noryan.nebulos.core.sm64

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** The game's own folder (`files/sm64`) and how it gets its contents: bundled recipes + engine archive (unpacked from assets), the ROM check, the extracted `sm64.o2r`. */
object Sm64Files {
    private const val TAG = "Sm64Files"
    const val DATA_ZIP = "sm64/sm64data.zip"
    const val DATA_VERSION = "sm64/sm64data.version"
    private const val STAMP = ".sm64data.version"
    const val GAME_ARCHIVE = "sm64.o2r"
    const val ROM_NAME = "baserom.us.z64"   // the name the engine's own mobile fallback looks for; Torch hashes the contents, so a JP ROM under this name still extracts

    /** The ROMs Torch can extract, by SHA-1. */
    val SUPPORTED_ROMS = mapOf(
        "9bef1128717f958171a4afac3ed78ee2bb4e86ce" to "Super Mario 64 (US)",
        "8a20a5c83d6ceb0f0506cfc9fa20d8f438cafe51" to "Super Mario 64 (JP)",
    )

    fun dataDir(ctx: Context): File = File(ctx.filesDir, "sm64").also { it.mkdirs() }
    fun archive(ctx: Context) = File(dataDir(ctx), GAME_ARCHIVE)
    private fun stamp(ctx: Context) = File(dataDir(ctx), ".sm64-extracted")
    /** The archive counts only with the stamp written after Torch exited cleanly (a killed conversion leaves a truncated archive behind). */
    fun isExtracted(ctx: Context) = archive(ctx).length() > 0 && stamp(ctx).isFile
    fun markExtracted(ctx: Context) { stamp(ctx).writeText("1") }

    fun sha1(f: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        f.inputStream().use { i ->
            val buf = ByteArray(1 shl 17)
            while (true) { val n = i.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Unpacks the bundled Torch recipes + `ghostship.o2r` into [dataDir] (again when the bundle changed: the old extraction is then stale). Returns an error message or null. */
    fun stageBundled(ctx: Context): String? = try {
        val dir = dataDir(ctx)
        val stamp = File(dir, STAMP)
        val expected = ctx.assets.open(DATA_VERSION).use { it.readBytes().decodeToString() }
        if (stamp.takeIf { it.isFile }?.readText() == expected && File(dir, "ghostship.o2r").isFile) null
        else {
            ctx.assets.open(DATA_ZIP).use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        val dest = File(dir, e.name).canonicalFile
                        if (!dest.path.startsWith(dir.canonicalPath + File.separator)) { zip.closeEntry(); continue }
                        if (e.isDirectory) dest.mkdirs() else { dest.parentFile?.mkdirs(); FileOutputStream(dest).use { zip.copyTo(it, 1 shl 17) } }
                        zip.closeEntry()
                    }
                }
            }
            // new recipes: the previous archive (and Torch's "inputs unchanged" record) is stale
            archive(ctx).delete(); stamp(ctx).delete(); File(dir, "torch.hash.yml").delete()
            stamp.writeText(expected)
            null
        }
    } catch (t: Throwable) {
        Log.e(TAG, "staging failed", t)
        "Could not unpack the bundled game files: ${t.message}"
    }
}
