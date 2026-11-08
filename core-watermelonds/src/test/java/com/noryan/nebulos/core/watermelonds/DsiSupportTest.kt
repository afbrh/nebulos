package com.noryan.nebulos.core.watermelonds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DsiSupportTest {
    /** A minimal decrypted CIA: 0x2020 header, 0xA00 cert, 0x350 ticket, RSA-2048 TMD with one plain content (a fake .nds). */
    private fun cia(tidHigh: Long, content: ByteArray, encrypted: Boolean = false): ByteArray {
        fun al(x: Int) = (x + 63) / 64 * 64
        val cert = 0xA00; val tik = 0x350; val tmd = 4 + 0x100 + 0x3C + 0x9C4 + 0x30
        val tmdStart = al(al(0x2020) + cert).let { al(it + tik) }
        val contentStart = al(tmdStart + tmd)
        val b = ByteArray(contentStart + content.size)
        fun le32(o: Int, v: Int) { for (i in 0..3) b[o + i] = (v ushr (8 * i)).toByte() }
        fun be32(o: Int, v: Long) { for (i in 0..3) b[o + i] = (v ushr (24 - 8 * i)).toByte() }
        le32(0, 0x2020); le32(8, cert); le32(12, tik); le32(16, tmd); le32(20, 0)
        be32(tmdStart, 0x10004)
        val body = tmdStart + 4 + 0x100 + 0x3C
        be32(body + 0x4C, tidHigh); be32(body + 0x50, 0x4B513945)
        b[body + 0x9F] = 1                                   // one content
        val rec = body + 0x9C4
        b[rec + 7] = if (encrypted) 1 else 0                 // content type: bit 0 = encrypted
        be32(rec + 8, 0); be32(rec + 12, content.size.toLong())
        content.copyInto(b, contentStart)
        return b
    }

    @Test fun recognisesDsiWareByTitleId() {
        assertTrue(DsiWareCia.isDsiWare(cia(0x00048004, ByteArray(0x300))))
        assertFalse(DsiWareCia.isDsiWare(cia(0x00040000, ByteArray(0x300))))          // a 3DS game
        assertFalse(DsiWareCia.isDsiWare(ByteArray(100)))
    }

    @Test fun extractsThePlainContent() {
        val game = ByteArray(0x400) { (it % 251).toByte() }
        val f = File.createTempFile("fsx", ".cia").apply { writeBytes(cia(0x00048004, game)); deleteOnExit() }
        val out = File.createTempFile("fsx", ".nds").apply { delete(); deleteOnExit() }
        assertNull(DsiWareCia.extract(f, out))
        assertEquals(game.toList(), out.readBytes().toList())
    }

    @Test fun buildsTheDsiTitleMetadata() {
        val game = ByteArray(0x400).also {
            "KQ9E".toByteArray().copyInto(it, 0x0C); it[0x1E] = 2
            byteArrayOf(0x4B, 0x51, 0x39, 0x45, 0x04, 0x00, 0x03, 0x00).copyInto(it, 0x230)   // title ID as the DSi stores it
        }
        val f = File.createTempFile("fsx", ".cia").apply { writeBytes(cia(0x00048004, game)); deleteOnExit() }
        val srl = File.createTempFile("fsx", ".nds").apply { writeBytes(game); deleteOnExit() }
        val dir = java.nio.file.Files.createTempDirectory("tmd").toFile().apply { deleteOnExit() }
        assertNull(DsiWareCia.writeDsiTmd(f, srl, dir))
        val tmd = File(dir, "000300044539514b.tmd").readBytes()
        assertEquals(520, tmd.size)
        assertEquals(listOf(0, 3, 0, 4, 0x45, 0x39, 0x51, 0x4B), tmd.copyOfRange(0x18C, 0x194).map { it.toInt() and 0xFF })
        assertEquals(2, tmd[0x1E4 + 3].toInt())                                          // content id = ROM version
        assertEquals(1, tmd[0x1E4 + 7].toInt())                                          // DSi .app
        assertEquals(0x400, ((tmd[0x1E4 + 14].toInt() and 0xFF) shl 8) or (tmd[0x1E4 + 15].toInt() and 0xFF))
    }

    @Test fun refusesEncryptedAndNonDsiWare() {
        val enc = File.createTempFile("enc", ".cia").apply { writeBytes(cia(0x00048004, ByteArray(0x400), encrypted = true)); deleteOnExit() }
        assertTrue(DsiWareCia.extract(enc, File.createTempFile("out", ".nds").apply { deleteOnExit() })!!.contains("encrypted"))
        val tds = File.createTempFile("3ds", ".cia").apply { writeBytes(cia(0x00040000, ByteArray(0x400))); deleteOnExit() }
        assertEquals("Not a DSiWare CIA.", DsiWareCia.extract(tds, File.createTempFile("out", ".nds").apply { deleteOnExit() }))
    }

    @Test fun classifiesTheBiosFolderFiles() {
        val c = DsiBios::classify
        assertEquals("dsi" to "bios7.bin", c("biosdsi7.bin", 65536))
        assertEquals("dsi" to "bios9.bin", c("biosdsi9.bin", 65536))
        assertEquals("ds" to "bios7.bin", c("biosnds7.bin", 16384))
        assertEquals("ds" to "bios9.bin", c("biosnds9.bin", 4096))
        assertEquals("ds" to "firmware.bin", c("dsfirmware.bin", 262144))
        assertEquals("dsi" to "firmware.bin", c("dsifirmware.bin", 131072))
        assertEquals("dsi" to "nand.bin", c("dsinand.bin", 251658304))
        assertNull(c("BIOSGBA.ROM", 16384))
        assertNull(c("scph70012.bin", 4194304))
    }
}
