package com.noryan.nebulos.core.lichii

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Helpers for the files Eden needs but Nebulos must never ship: prod.keys (+ optional title.keys) and firmware.
 * Pure file operations (no native code loaded in the app process). Eden's user dir is
 * `<externalFilesDir>/` (keys under `keys/`, NAND under `nand/`) — identical to what Eden's DirectoryInitialization
 * computes inside the `:lichii` process, since both use the same package's external files dir.
 */
object LichiiSetup {
    private fun edenDir(ctx: Context): File = ctx.getExternalFilesDir(null) ?: ctx.filesDir
    private fun userDir(ctx: Context): File = File(edenDir(ctx), "lichii").also { it.mkdirs() }

    fun keysFile(ctx: Context) = File(userDir(ctx), "keys/prod.keys")
    fun firmwareDir(ctx: Context) = File(userDir(ctx), "nand/system/Contents/registered")

    fun isKeysImported(ctx: Context) = keysFile(ctx).let { it.isFile && it.length() > 0 }
    fun isFirmwareImported(ctx: Context) = firmwareDir(ctx).list()?.any { it.endsWith(".nca") } == true
    fun isReady(ctx: Context) = isKeysImported(ctx) && isFirmwareImported(ctx)

    /** Copy a user-picked prod.keys / title.keys into Eden's keys dir. */
    fun importKeys(ctx: Context, source: Uri, fileName: String = "prod.keys"): Boolean = runCatching {
        val dest = File(userDir(ctx), "keys/$fileName").also { it.parentFile?.mkdirs() }
        ctx.contentResolver.openInputStream(source)!!.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
        dest.length() > 0
    }.getOrDefault(false)

    /** Extract a user-picked firmware .zip (flat list of .nca) into Eden's registered-content dir. */
    fun importFirmware(ctx: Context, zip: Uri): Boolean = runCatching<Boolean> {
        val tmp = File(ctx.cacheDir, "lichii_fw").also { it.deleteRecursively(); it.mkdirs() }
        var count = 0
        ctx.contentResolver.openInputStream(zip)!!.use { raw ->
            ZipInputStream(raw).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = e.name.substringAfterLast('/')
                    if (!name.endsWith(".nca")) { tmp.deleteRecursively(); return false }
                    File(tmp, name).outputStream().use { z.copyTo(it) }
                    count++
                }
            }
        }
        if (count == 0) return false
        val dest = firmwareDir(ctx)
        dest.deleteRecursively(); dest.mkdirs()
        tmp.copyRecursively(dest, overwrite = true)
        tmp.deleteRecursively()
        true
    }.getOrDefault(false)

    /**
     * Lichii keeps its own data dir, so the keys and firmware the user already gave Eden are copied across the first time
     * Lichii launches (and again if Lichii has none). Runs in the Nebulos app process before the game process starts.
     */
    fun shareFromEden(ctx: Context) {
        runCatching {
            val eden = edenDir(ctx)
            val ek = File(eden, "keys")
            if (!isKeysImported(ctx) && ek.isDirectory) {
                File(userDir(ctx), "keys").mkdirs()
                ek.listFiles()?.filter { it.isFile }?.forEach { it.copyTo(File(userDir(ctx), "keys/${it.name}"), overwrite = true) }
            }
            val ef = File(eden, "nand/system/Contents/registered")
            if (!isFirmwareImported(ctx) && ef.isDirectory) {
                val dest = firmwareDir(ctx).also { it.mkdirs() }
                ef.listFiles()?.filter { it.isFile }?.forEach { it.copyTo(File(dest, it.name), overwrite = true) }
            }
        }
    }
}
