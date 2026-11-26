package com.noryan.nebulos.core.smw

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** The game's own folder (`files/smw`): the settings file and the game data built from the user's ROM (`smw_assets.dat`). */
object SmwFiles {
    private const val ASSETS_FILE = "smw_assets.dat"
    /** Bump when [SmwAssets] changes what it produces: the data is then built again. */
    private const val BUILDER_VERSION = "1"
    const val INI = "smw.ini"

    /** The US ROM by SHA-1, bare or behind a 512-byte copier header (the hash is of the 512 KiB after it). */
    private const val US_ROM_SHA1 = "6b47bb75d16514b6a476aa0c73a683a2a4c18765"
    private const val ROM_SIZE = 1 shl 19
    private const val HEADER = 512

    fun dataDir(ctx: Context): File = File(ctx.filesDir, "smw").also { it.mkdirs() }
    private fun stamp(ctx: Context) = File(dataDir(ctx), ".assets-version")

    fun hasAssets(ctx: Context) = File(dataDir(ctx), ASSETS_FILE).length() > 0 && stamp(ctx).takeIf { it.isFile }?.readText() == BUILDER_VERSION

    /** Puts the default settings in place; an existing file (the user's changes) is kept. */
    fun ensureSettings(ctx: Context) {
        val f = File(dataDir(ctx), INI)
        if (f.length() > 0) return
        ctx.assets.open("smw/$INI").use { i -> f.outputStream().use { o -> i.copyTo(o) } }
    }

    private fun sha1(b: ByteArray, from: Int): String =
        MessageDigest.getInstance("SHA-1").apply { update(b, from, b.size - from) }.digest().joinToString("") { "%02x".format(it) }

    /** Builds `smw_assets.dat` from [rom]. Returns an error message for the user, or null. */
    fun buildAssets(ctx: Context, rom: File, progress: (Float) -> Unit): String? {
        if (!rom.isFile) return "The ROM was not found: ${rom.path}"
        val length = rom.length()
        if (length != ROM_SIZE.toLong() && length != (ROM_SIZE + HEADER).toLong()) return wrongRom("$length bytes")
        val bytes = rom.readBytes()
        val sha = sha1(bytes, bytes.size - ROM_SIZE)
        if (sha != US_ROM_SHA1) return wrongRom("SHA-1 $sha")
        val data = SmwAssets.build(bytes, progress)
        val dir = dataDir(ctx)
        val tmp = File(dir, "$ASSETS_FILE.tmp")
        tmp.writeBytes(data)
        if (!tmp.renameTo(File(dir, ASSETS_FILE))) return "Could not write the game data."
        stamp(ctx).writeText(BUILDER_VERSION)
        return null
    }

    private fun wrongRom(detail: String) =
        "This is not the supported ROM.\n\nThe port needs the US release of Super Mario World (512 KB .sfc). Choose another engine for this game to play other versions.\n\nThis file: $detail"
}
