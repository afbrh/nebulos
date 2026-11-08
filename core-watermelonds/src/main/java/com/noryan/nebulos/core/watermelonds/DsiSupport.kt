package com.noryan.nebulos.core.watermelonds

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

/**
 * Nintendo DSi support: the user's own DSi BIOS / firmware / NAND dumps (copied from the BIOS folder, never bundled) and DSiWare `.cia`
 * files (the DS game inside is extracted to a plain `.nds`). WatermelonDS boots a DSiWare `.nds` in DSi mode when `bios_dir` and
 * `dsi_bios_dir` point at directories holding `bios7.bin`, `bios9.bin`, `firmware.bin` (+ `nand.bin` for DSi).
 */
internal object DsiBios {
    private const val DSI_BIOS = 0x10000L
    private const val DS_BIOS7 = 0x4000L
    private const val DS_BIOS9 = 0x1000L
    /** A NAND dump is 0xF000000 bytes, plus a 0x40-byte no$gba footer on most dumps. */
    private val NAND_SIZES = setOf(0xF000000L, 0xF000040L)

    fun dir(ctx: Context, dsi: Boolean): File =
        File(File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "watermelonds/bios"), if (dsi) "dsi" else "ds")

    /** Where a BIOS-folder file belongs ("ds"/"dsi" + the file name WatermelonDS reads), judged by size and name. Null = not a DS/DSi file. */
    fun classify(name: String, size: Long): Pair<String, String>? {
        val n = name.lowercase()
        val dsi = "dsi" in n
        return when {
            "nand" in n && size in NAND_SIZES -> "dsi" to "nand.bin"
            size == DSI_BIOS && "7" in n -> "dsi" to "bios7.bin"
            size == DSI_BIOS && "9" in n -> "dsi" to "bios9.bin"
            size == DS_BIOS7 && "7" in n && !n.contains("gba") -> "ds" to "bios7.bin"
            size == DS_BIOS9 && "9" in n -> "ds" to "bios9.bin"
            ("firmware" in n || n.startsWith("dsfw") || n.startsWith("dsifw")) && size in setOf(0x20000L, 0x40000L, 0x80000L) ->
                (if (dsi) "dsi" else "ds") to "firmware.bin"
            else -> null
        }
    }

    /** Copies the DS/DSi files found in [files] into the WatermelonDS directories. Returns quiet status lines for Settings. */
    fun import(ctx: Context, files: List<File>): List<String> {
        val chosen = LinkedHashMap<Pair<String, String>, File>()
        for (f in files) classify(f.name, f.length())?.let { chosen.putIfAbsent(it, f) }
        if (chosen.isEmpty()) return emptyList()
        var copied = 0
        for ((key, src) in chosen) {
            val dest = File(dir(ctx, key.first == "dsi"), key.second)
            if (dest.isFile && dest.length() == src.length() && dest.lastModified() >= src.lastModified()) continue
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, dest.name + ".part")
            val ok = runCatching {
                src.inputStream().use { i -> tmp.outputStream().use { o -> i.copyTo(o, 1 shl 20) } }
                tmp.setLastModified(src.lastModified())
                dest.delete(); tmp.renameTo(dest)
            }.getOrDefault(false)
            if (ok) copied++ else tmp.delete()
        }
        return listOf("DSI: " + if (copied > 0) "IMPORTED $copied OF ${chosen.size}" else "${chosen.size} OK")
    }

    /** True when the DSi directory holds everything DSi mode needs. */
    fun dsiReady(ctx: Context): Boolean = listOf("bios7.bin", "bios9.bin", "firmware.bin", "nand.bin")
        .all { File(dir(ctx, true), it).let { f -> f.isFile && f.length() > 0 } } &&
        listOf("bios7.bin", "bios9.bin", "firmware.bin").all { File(dir(ctx, false), it).let { f -> f.isFile && f.length() > 0 } }
}

/** A DSiWare `.cia`: a 3DS install package whose title ID is 00048004-<game code> and whose one content is the DSi game (`.nds`). */
internal object DsiWareCia {
    private const val DSIWARE_TID_HIGH = 0x00048004L

    private fun align64(x: Long) = (x + 63) / 64 * 64
    private fun le32(b: ByteArray, o: Int) = (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or
        ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)
    private fun be32(b: ByteArray, o: Int) = ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
        ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    /** Where the title's TMD body starts inside the CIA, plus the offset of the content (start of the first content chunk). */
    private class Layout(val tmdBody: Int, val contentStart: Long)

    private fun layout(h: ByteArray): Layout? {
        if (h.size < 0x20 || le32(h, 0) != 0x2020L) return null
        val cert = le32(h, 8); val tik = le32(h, 12); val tmd = le32(h, 16); val meta = le32(h, 20)
        val tmdStart = align64(align64(align64(0x2020L) + cert) + tik)
        if (tmdStart + 8 > h.size) return null
        val sigSize = when (be32(h, tmdStart.toInt()).toInt()) {   // RSA-4096 / 2048 / ECDSA, with SHA-1 or SHA-256
            0x10000, 0x10003 -> 0x200; 0x10001, 0x10004 -> 0x100; 0x10002, 0x10005 -> 0x3C; else -> return null
        }
        val body = tmdStart + 4 + sigSize + 0x3C
        return Layout(body.toInt(), align64(align64(tmdStart + tmd) + meta))
    }

    /** True for a DSiWare CIA ([head] = the first 64 KB of the file). */
    fun isDsiWare(head: ByteArray): Boolean {
        val l = layout(head) ?: return false
        if (l.tmdBody + 0x58 > head.size) return false
        return be32(head, l.tmdBody + 0x4C) == DSIWARE_TID_HIGH
    }

    /**
     * Writes the DSi game inside [cia] to [out] (tmp + rename). Null on success, else a message. The content must be a plain `.nds`
     * (an unencrypted/decrypted CIA, which is what dumping tools produce); an encrypted CIA is reported, not guessed at.
     */
    fun extract(cia: File, out: File): String? = runCatching {
        RandomAccessFile(cia, "r").use { f ->
            val head = ByteArray(0x10000).also { f.readFully(it, 0, minOf(it.size.toLong(), f.length()).toInt()) }
            val l = layout(head) ?: return@use "Not a CIA file."
            if (be32(head, l.tmdBody + 0x4C) != DSIWARE_TID_HIGH) return@use "Not a DSiWare CIA."
            val tmd = l.tmdBody
            if ((head[tmd + 0x9E].toInt() and 0xFF shl 8 or (head[tmd + 0x9F].toInt() and 0xFF)) < 1) return@use "The CIA has no content."
            val rec = tmd + 0x9C4
            val type = (head[rec + 6].toInt() and 0xFF shl 8) or (head[rec + 7].toInt() and 0xFF)
            if (type and 1 != 0) return@use "This CIA is encrypted. Use a decrypted DSiWare CIA."
            val size = (be32(head, rec + 8) shl 32) or be32(head, rec + 12)
            if (size < 0x200 || l.contentStart + size > f.length()) return@use "The CIA content is cut short."
            if (out.isFile && out.length() == size) return@use null
            out.parentFile?.mkdirs()
            val tmp = File(out.parentFile, out.name + ".part")
            f.seek(l.contentStart)
            tmp.outputStream().use { o ->
                val buf = ByteArray(1 shl 20); var left = size
                while (left > 0) { val n = f.read(buf, 0, minOf(buf.size.toLong(), left).toInt()); if (n <= 0) break; o.write(buf, 0, n); left -= n }
                if (left > 0) { tmp.delete(); return@use "The CIA content is cut short." }
            }
            out.delete()
            if (!tmp.renameTo(out)) return@use "Couldn't write the extracted game."
            null
        }
    }.getOrElse { "Couldn't read the CIA: ${it.message}" }

    /**
     * The DSi title metadata WatermelonDS installs a DSiWare game with (the 520-byte DSi TMD, normally downloaded from Nintendo): the CIA's
     * own TMD re-expressed in DSi form. The title ID comes from the extracted game's header (category 00030004 + the game code as the DSi
     * stores it), the single content record is rebuilt (id = ROM version, DSi .app type, size, SHA-1). Written to [dir] as
     * `<category><title>.tmd`, the name the WatermelonDS repository reads. Null on success, else a message.
     */
    fun writeDsiTmd(cia: File, srl: File, dir: File): String? = runCatching {
        val head = ByteArray(0x10000).also { b -> cia.inputStream().use { s -> var o = 0; while (o < b.size) { val n = s.read(b, o, b.size - o); if (n <= 0) break; o += n } } }
        val l = layout(head) ?: return@runCatching "Not a CIA file."
        val srlHead = ByteArray(0x240).also { b -> srl.inputStream().use { s -> var o = 0; while (o < b.size) { val n = s.read(b, o, b.size - o); if (n <= 0) break; o += n } } }
        val titleLow = le32(srlHead, 0x230); val category = le32(srlHead, 0x234)   // as the DSi stores the title ID
        val tmdStart = l.tmdBody - (4 + 0x100 + 0x3C)
        val out = ByteArray(520)
        System.arraycopy(head, tmdStart, out, 0, 0x1E4)                         // signature block + header up to the content record
        fun be(o: Int, v: Long) { for (i in 0..3) out[o + i] = (v ushr (24 - 8 * i)).toByte() }
        be(0, 0x00010001)                                                         // RSA-2048 / SHA-1, as DSi TMDs are
        be(0x18C, category); be(0x190, titleLow)
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        srl.inputStream().use { s -> val buf = ByteArray(1 shl 20); while (true) { val n = s.read(buf); if (n <= 0) break; digest.update(buf, 0, n) } }
        out[0x1E4 + 3] = srlHead[0x1E]                                           // content id 000000<ROM version>
        out[0x1E4 + 7] = 1                                                        // content type: DSi .app
        val size = srl.length()
        for (i in 0..7) out[0x1E4 + 8 + i] = (size ushr (56 - 8 * i)).toByte()
        System.arraycopy(digest.digest(), 0, out, 0x1E4 + 16, 20)
        dir.mkdirs()
        File(dir, "%08x%08x.tmd".format(category, titleLow)).writeBytes(out)
        null
    }.getOrElse { "Couldn't build the title metadata: ${it.message}" }

    fun tmdDir(ctx: Context): File = File(File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "watermelonds/dsiware"), "tmd")

    fun extractedFile(ctx: Context, cia: File): File =
        File(File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "watermelonds/dsiware"), cia.nameWithoutExtension + ".nds")
}
