package com.noryan.nebulos.api

import java.io.File
import java.io.RandomAccessFile

/**
 * PlayStation 2 disc identification: the serial ("SCUS-97124") from `SYSTEM.CNF` (`BOOT2 = cdrom0:\SCUS_971.24;1`) of a plain
 * 2048-byte-sector ISO9660 image. Used by the shell scanner so game-specific PS2 cores (OpenGOAL / Jak and Daxter) can claim
 * their discs by serial. Raw 2352-byte CD images (Mode 1 / Mode 2 Form 1) are read too; CHD and compressed formats return null.
 */
object Ps2DiscId {
    private const val SECTOR = 2048

    /** "SCUS-97124"-style serial, or null when this is not a readable PS2 ISO9660 image. */
    fun readSerial(f: File): String? = runCatching {
        RandomAccessFile(f, "r").use { raf ->
            // plain 2048-byte sectors, or a raw CD image (2352-byte sectors: 12 sync + 4 header [+ 8 sub-header in Mode 2] before the 2048 data bytes)
            fun cd001At(off: Long): Boolean {
                if (off + 6 > raf.length()) return false
                val b = ByteArray(6); raf.seek(off); raf.readFully(b)
                return b[0].toInt() == 1 && String(b, 1, 5, Charsets.ISO_8859_1) == "CD001"
            }
            val stride: Long; val skip: Long
            when {
                cd001At(16L * SECTOR) -> { stride = SECTOR.toLong(); skip = 0 }
                cd001At(16L * 2352 + 24) -> { stride = 2352; skip = 24 }   // Mode 2 Form 1
                cd001At(16L * 2352 + 16) -> { stride = 2352; skip = 16 }   // Mode 1
                else -> return@use null
            }
            fun read(sector: Long, bytes: Int): ByteArray? {
                val out = ByteArray(bytes); var n = 0; var sec = sector
                while (n < bytes) {
                    val pos = sec * stride + skip
                    if (pos >= raf.length()) break
                    raf.seek(pos)
                    val want = minOf(bytes - n, SECTOR)
                    var got = 0
                    while (got < want) { val r = raf.read(out, n + got, want - got); if (r <= 0) break; got += r }
                    n += got; sec++
                    if (got < want) break
                }
                return if (n == 0) null else out.copyOf(n)
            }
            fun le32(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
                ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
            val pvd = read(16, SECTOR) ?: return@use null
            if (pvd.size < 190 || pvd[0].toInt() != 1 || String(pvd, 1, 5, Charsets.ISO_8859_1) != "CD001") return@use null
            val rootLba = le32(pvd, 156 + 2).toLong()
            val rootLen = le32(pvd, 156 + 10)
            if (rootLen <= 0 || rootLen > 1 shl 20) return@use null
            val dir = read(rootLba, rootLen) ?: return@use null
            var p = 0
            while (p < dir.size) {
                val len = dir[p].toInt() and 0xFF
                if (len == 0) { p = (p / SECTOR + 1) * SECTOR; continue }   // records never straddle sectors
                if (p + len > dir.size) break
                val nameLen = dir[p + 32].toInt() and 0xFF
                val name = String(dir, p + 33, minOf(nameLen, len - 33), Charsets.ISO_8859_1)
                if (name.startsWith("SYSTEM.CNF")) {
                    val lba = le32(dir, p + 2).toLong(); val size = le32(dir, p + 10)
                    val txt = read(lba, size.coerceIn(1, SECTOR)) ?: return@use null
                    return@use parseBoot2(String(txt, Charsets.ISO_8859_1))
                }
                p += len
            }
            null
        }
    }.getOrNull()

    /** `BOOT2 = cdrom0:\SCUS_971.24;1` -> `SCUS-97124` (also `BOOT = cdrom:\...` for PS1-style files). */
    fun parseBoot2(cnf: String): String? {
        val m = Regex("""BOOT2?\s*=\s*cdrom0?:\\?([A-Z]{4})[_-]?(\d{3})[.]?(\d{2})""", RegexOption.IGNORE_CASE).find(cnf) ?: return null
        return "${m.groupValues[1].uppercase()}-${m.groupValues[2]}${m.groupValues[3]}"
    }
}
