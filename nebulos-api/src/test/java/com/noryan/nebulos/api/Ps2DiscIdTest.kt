package com.noryan.nebulos.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class Ps2DiscIdTest {
    @Test fun parsesBoot2Lines() {
        assertEquals("SCUS-97124", Ps2DiscId.parseBoot2("BOOT2 = cdrom0:\\SCUS_971.24;1\r\nVER = 1.00\r\nVMODE = NTSC"))
        assertEquals("SCES-50361", Ps2DiscId.parseBoot2("BOOT2 = cdrom0:\\SCES_503.61;1"))
        assertEquals("SCPS-15021", Ps2DiscId.parseBoot2("BOOT2=cdrom0:\\SCPS_150.21;1"))
        assertNull(Ps2DiscId.parseBoot2("hello"))
    }

    /** A minimal ISO9660 image: PVD at sector 16, root dir at sector 20 holding SYSTEM.CNF (sector 21). */
    private fun iso(cnf: String): File {
        val img = ByteArray(2048 * 22)
        fun le32(o: Int, v: Int) { for (i in 0..3) img[o + i] = (v ushr (8 * i)).toByte() }
        val pvd = 16 * 2048
        img[pvd] = 1; "CD001".toByteArray().copyInto(img, pvd + 1)
        le32(pvd + 156 + 2, 20); le32(pvd + 156 + 10, 2048)
        val dir = 20 * 2048
        val name = "SYSTEM.CNF;1".toByteArray()
        img[dir] = (33 + name.size).toByte(); le32(dir + 2, 21); le32(dir + 10, cnf.length)
        img[dir + 32] = name.size.toByte(); name.copyInto(img, dir + 33)
        cnf.toByteArray().copyInto(img, 21 * 2048)
        return File.createTempFile("ps2", ".iso").apply { deleteOnExit(); writeBytes(img) }
    }

    @Test fun readsSerialFromIsoImage() {
        assertEquals("SCUS-97265", Ps2DiscId.readSerial(iso("BOOT2 = cdrom0:\\SCUS_972.65;1\nVER = 1.00")))
    }

    @Test fun notAnIsoIsNull() {
        val f = File.createTempFile("notiso", ".iso").apply { deleteOnExit(); writeBytes(ByteArray(100000)) }
        assertNull(Ps2DiscId.readSerial(f))
    }
}
