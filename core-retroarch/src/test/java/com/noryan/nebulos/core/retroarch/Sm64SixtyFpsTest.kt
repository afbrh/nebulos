package com.noryan.nebulos.core.retroarch

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Sm64SixtyFpsTest {
    private fun ips(vararg rec: Pair<Int, ByteArray>): ByteArray {
        var b = "PATCH".toByteArray(Charsets.ISO_8859_1)
        for ((off, data) in rec) {
            b += byteArrayOf((off shr 16).toByte(), (off shr 8).toByte(), off.toByte(), (data.size shr 8).toByte(), data.size.toByte()) + data
        }
        return b + "EOF".toByteArray(Charsets.ISO_8859_1)
    }

    @Test fun appliesRecordsAndLeavesTheRestAlone() {
        val src = ByteArray(32) { it.toByte() }
        val out = Sm64SixtyFps.applyIps(src, ips(2 to byteArrayOf(9, 9), 30 to byteArrayOf(7, 7)))
        val want = src.copyOf().also { it[2] = 9; it[3] = 9; it[30] = 7; it[31] = 7 }
        assertArrayEquals(want, out)
    }

    @Test fun rejectsRecordsPastTheEnd() {
        assertThrows(IllegalArgumentException::class.java) { Sm64SixtyFps.applyIps(ByteArray(8), ips(7 to byteArrayOf(1, 2))) }
    }

    @Test fun rejectsNonIps() {
        assertThrows(IllegalArgumentException::class.java) { Sm64SixtyFps.applyIps(ByteArray(8), "BPS1xxxxxxxxx".toByteArray()) }
    }
}
