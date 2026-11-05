package com.noryan.nebulos.data

import com.noryan.nebulos.api.System
import org.junit.Assert.*
import org.junit.Test

class SystemDetectorDiscTest {
    /** A tiny ISO9660 image: PVD at sector 16, root directory at sector 18 holding SYSTEM.CNF (sector 19) with [cnf] as its text. */
    private fun iso(cnf: String?): ByteArray {
        val img = ByteArray(2048 * 22)
        fun put32(o: Int, v: Int) { for (i in 0..3) img[o + i] = (v shr (8 * i)).toByte() }
        "CD001".toByteArray().copyInto(img, 0x8000 + 1)
        val rec = 0x8000 + 156
        put32(rec + 2, 18); put32(rec + 10, 2048)
        if (cnf != null) {
            var o = 18 * 2048
            val name = "SYSTEM.CNF;1".toByteArray()
            img[o] = (33 + name.size).toByte(); put32(o + 2, 19); put32(o + 10, cnf.length); img[o + 32] = name.size.toByte()
            name.copyInto(img, o + 33)
            cnf.toByteArray().copyInto(img, 19 * 2048)
        }
        return img
    }
    private fun kind(img: ByteArray) = SystemDetector.discKindFromSystemCnf { off, len -> if (off + len > img.size) null else img.copyOfRange(off.toInt(), off.toInt() + len) }

    @Test fun boot2IsPs2() = assertEquals(System.PS2, kind(iso("BOOT2 = cdrom0:\\SLUS_203.14;1\r\nVER = 1.00\r\nVMODE = NTSC\r\n")))
    @Test fun bootIsPs1() = assertEquals(System.PS1, kind(iso("BOOT = cdrom:\\SLUS_007.94;1\r\nTCB = 4\r\nEVENT = 10\r\nSTACK = 801FFF00\r\n")))
    @Test fun noSystemCnfOrNotAnIsoGivesNull() {
        assertNull(kind(iso(null)))
        assertNull(SystemDetector.discKindFromSystemCnf { _, _ -> ByteArray(2048) })
        assertNull(SystemDetector.discKindFromSystemCnf { _, _ -> null })
    }
}
