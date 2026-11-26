package com.noryan.nebulos.core.supermetroid

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** The game's own folder (`files/supermetroid`): the settings file and a checked copy of the user's ROM (`sm.smc`, which the game reads). */
object SupermetroidFiles {
    private const val ROM_FILE = "sm.smc"
    const val INI = "sm.ini"

    /** The Japan/USA ROM by SHA-1, bare or behind a 512-byte copier header (the hash is of the 3 MiB after it). */
    private const val ROM_SHA1 = "da957f0d63d14cb441d215462904c4fa8519c613"
    private const val ROM_SIZE = 3 shl 20
    private const val HEADER = 512

    fun dataDir(ctx: Context): File = File(ctx.filesDir, "supermetroid").also { it.mkdirs() }

    fun hasRom(ctx: Context) = File(dataDir(ctx), ROM_FILE).length() == ROM_SIZE.toLong()

    /** Puts the default settings in place; an existing file (the user's changes) is kept. */
    fun ensureSettings(ctx: Context) {
        val f = File(dataDir(ctx), INI)
        if (f.length() > 0) return
        ctx.assets.open("supermetroid/$INI").use { i -> f.outputStream().use { o -> i.copyTo(o) } }
    }

    /** Copies [rom] into the game folder after checking it. Returns an error message for the user, or null. */
    fun copyRom(ctx: Context, rom: File): String? {
        if (!rom.isFile) return "The ROM was not found: ${rom.path}"
        val length = rom.length()
        if (length != ROM_SIZE.toLong() && length != (ROM_SIZE + HEADER).toLong()) return wrongRom("$length bytes")
        val bytes = rom.readBytes()
        val from = bytes.size - ROM_SIZE
        val sha = MessageDigest.getInstance("SHA-1").apply { update(bytes, from, ROM_SIZE) }.digest().joinToString("") { "%02x".format(it) }
        if (sha != ROM_SHA1) return wrongRom("SHA-1 $sha")
        val dir = dataDir(ctx)
        val tmp = File(dir, "$ROM_FILE.tmp")
        tmp.outputStream().use { it.write(bytes, from, ROM_SIZE) }
        if (!tmp.renameTo(File(dir, ROM_FILE))) return "Could not copy the ROM into the game folder."
        return null
    }

    private fun wrongRom(detail: String) =
        "This is not the supported ROM.\n\nThe port needs the Japan/USA release of Super Metroid (3 MB .sfc). Choose another engine for this game to play other versions.\n\nThis file: $detail"
}
