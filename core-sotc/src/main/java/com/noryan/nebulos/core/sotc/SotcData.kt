package com.noryan.nebulos.core.sotc

import android.content.Context
import android.os.StatFs
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * The game's data: Celosia reads the disc's files from a folder (`<files>/sotc/disc/SCUS_974.72`, `NICO.DAT`, the XFF modules,
 * the IOP modules, ...). The first launch copies them out of the user's ISO (a plain 2048-byte-sector ISO9660 image) into the app's
 * storage; a marker file records that it is complete, so later launches start at once.
 */
internal object SotcData {
    private const val TAG = "SotcData"
    private const val SECTOR = 2048
    private const val MARKER = ".nebulos_disc_version"
    const val DATA_VERSION = "1"
    const val BOOT_FILE = "SCUS_974.72"

    fun root(ctx: Context): File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "sotc").apply { mkdirs() }
    fun discDir(ctx: Context): File = File(root(ctx), "disc")

    fun ready(ctx: Context): Boolean {
        val d = discDir(ctx)
        return File(d, MARKER).let { it.isFile && it.readText().trim() == DATA_VERSION } && File(d, BOOT_FILE).isFile
    }

    @Volatile private var cancelled = false
    fun cancel() { cancelled = true }

    /** Null when the game can start, otherwise a user-facing error. Blocks (call off the UI thread). */
    fun ensureReady(ctx: Context, iso: String?, progress: (String) -> Unit): String? {
        if (ready(ctx)) return null
        if (iso == null || !File(iso).isFile) return "The disc image is not available: ${iso ?: "(no path)"}"
        val dir = discDir(ctx)
        return try {
            RandomAccessFile(iso, "r").use { raf -> extract(raf, dir, progress) }
        } catch (e: IOException) {
            Log.e(TAG, "disc copy failed", e)
            "Could not read the disc image: ${e.message}"
        } catch (e: IllegalStateException) {
            Log.e(TAG, "disc copy failed", e)
            e.message ?: "The disc image is not a readable PS2 disc."
        }
    }

    private class Entry(val path: String, val lba: Long, val size: Long, val dir: Boolean)

    private fun le32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24))

    private fun readFully(raf: RandomAccessFile, sector: Long, bytes: Int): ByteArray {
        val b = ByteArray(bytes)
        raf.seek(sector * SECTOR)
        raf.readFully(b)
        return b
    }

    /** Every file of the image (path relative to the root, the ";1" version suffix dropped). */
    private fun listFiles(raf: RandomAccessFile): List<Entry> {
        val pvd = readFully(raf, 16, SECTOR)
        if (String(pvd, 1, 5, Charsets.ISO_8859_1) != "CD001") error("The disc image is not an ISO9660 PS2 disc.")
        val out = ArrayList<Entry>()
        fun walk(prefix: String, lba: Long, size: Long) {
            val dir = readFully(raf, lba, ((size + SECTOR - 1) / SECTOR * SECTOR).toInt())
            var p = 0
            while (p < dir.size) {
                val len = dir[p].toInt() and 0xFF
                if (len == 0) { p = (p / SECTOR + 1) * SECTOR; continue }
                val nameLen = dir[p + 32].toInt() and 0xFF
                val isDir = (dir[p + 25].toInt() and 2) != 0
                val first = dir[p + 33].toInt() and 0xFF
                if (!(nameLen == 1 && (first == 0 || first == 1))) {   // not "." / ".."
                    var name = String(dir, p + 33, nameLen, Charsets.ISO_8859_1)
                    name = name.substringBefore(';')
                    val rel = if (prefix.isEmpty()) name else "$prefix/$name"
                    val eLba = le32(dir, p + 2); val eSize = le32(dir, p + 10)
                    if (isDir) walk(rel, eLba, eSize) else out.add(Entry(rel, eLba, eSize, false))
                }
                p += len
            }
        }
        walk("", le32(pvd, 156 + 2), le32(pvd, 156 + 10))
        return out
    }

    private fun extract(raf: RandomAccessFile, dir: File, progress: (String) -> Unit): String? {
        progress("Reading the disc")
        val files = listFiles(raf)
        if (files.none { it.path == BOOT_FILE }) return "This disc has no $BOOT_FILE: it is not the USA release of Shadow of the Colossus."
        val total = files.sumOf { it.size }
        val free = runCatching { StatFs(dir.parentFile!!.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (free < total + (256L shl 20)) return "Not enough free storage to prepare this game: ${(total shr 20) + 256} MB needed, ${free shr 20} MB free."
        dir.deleteRecursively(); dir.mkdirs()
        cancelled = false
        var done = 0L
        var lastPct = -1
        val buf = ByteArray(1 shl 20)
        for (e in files) {
            val out = File(dir, e.path)
            out.parentFile?.mkdirs()
            raf.seek(e.lba * SECTOR)
            out.outputStream().buffered(1 shl 20).use { os ->
                var left = e.size
                while (left > 0) {
                    if (cancelled) return "Preparing the game was cancelled."
                    val n = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n <= 0) throw IOException("The disc image ended early (${e.path}).")
                    os.write(buf, 0, n)
                    left -= n; done += n
                    val pct = if (total > 0) (done * 100 / total).toInt() else 100
                    if (pct != lastPct) { lastPct = pct; progress("Preparing Shadow of the Colossus: $pct%") }
                }
            }
        }
        File(dir, MARKER).writeText(DATA_VERSION)
        return null
    }
}
