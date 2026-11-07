package com.noryan.nebulos.core.retroarch

import android.content.Context
import com.noryan.nebulos.api.save.SaveRoot
import java.io.File
import java.util.zip.CRC32

/**
 * 60 fps for Super Mario 64 (US, big-endian .z64) on the N64 core. The bundled patch (assets/sm64_60fps_v2_mupen.ips, 2 KB, no game
 * data) is Kaze Emanuar's "60 FPS Patch" v2 (frame interpolation; game logic stays at 30 Hz) plus a Nebulos fix for Mupen64Plus: the
 * patch collects the controller only every second frame, but the stock game starts an SI controller read every frame, and that
 * leaves the core with no buttons at all. The fix starts the read on the same frames that collect it (see core-retroarch/README).
 * It also pads the ROM back to 8 MiB and recomputes the header CRC. It is applied to the user's own ROM at launch; the ROM on disk
 * is never touched: the patched copy has the same file name, in filesDir/sm64-60fps/, so battery saves (.srm) are shared with the
 * stock ROM (the first patched launch keeps a one-time backup of the stock save). Save states are RDRAM snapshots of one specific
 * ROM, so the patched variant keeps its own state folder (RaSession.stateDir). Anything but the exact US ROM is left alone.
 */
internal object Sm64SixtyFps {
    private const val ASSET = "sm64_60fps_v2_mupen.ips"
    private const val PREF = "sm64_60fps"
    private const val ROM_SIZE = 8_388_608L
    private const val SOURCE_CRC = 0x3ce60709L // US SM64 .z64
    private const val TARGET_CRC = 0x80145e58L // after the patch

    fun isOn(ctx: Context): Boolean = ctx.getSharedPreferences("nebulos_retro", Context.MODE_PRIVATE).getBoolean(PREF, true)
    fun setOn(ctx: Context, on: Boolean) { ctx.getSharedPreferences("nebulos_retro", Context.MODE_PRIVATE).edit().putBoolean(PREF, on).commit() }

    /** True for the exact ROM the patch was made for (size and CRC32 of the .z64). */
    fun eligible(rom: File): Boolean = runCatching {
        rom.length() == ROM_SIZE && rom.extension.equals("z64", true) && crc32(rom.readBytes()) == SOURCE_CRC
    }.getOrDefault(false)

    /** Patched copy of [rom] (same file name), or null if it could not be produced; the caller then runs the stock ROM. */
    fun patchedCopy(ctx: Context, rom: File): File? = runCatching {
        val out = File(File(ctx.filesDir, "sm64-60fps").apply { mkdirs() }, rom.name)
        val bytes = applyIps(rom.readBytes(), ctx.assets.open(ASSET).use { it.readBytes() })
        check(crc32(bytes) == TARGET_CRC) { "patched ROM checksum" }
        val tmp = File(out.parentFile, rom.name + ".tmp")
        tmp.writeBytes(bytes)
        check(tmp.renameTo(out)) { "rename" }
        backupStockSave(ctx, rom)
        out
    }.onFailure { android.util.Log.w("Sm64SixtyFps", "patch failed", it) }.getOrNull()

    private fun backupStockSave(ctx: Context, rom: File) {
        val saves = SaveRoot.coreSubDir(ctx, RetroArchCore.id, "saves")
        val srm = File(saves, rom.nameWithoutExtension + ".srm")
        val bak = File(saves, rom.nameWithoutExtension + ".srm.pre60fps")
        if (srm.isFile && !bak.exists()) srm.copyTo(bak)
    }

    private fun crc32(b: ByteArray) = CRC32().also { it.update(b) }.value

    /** Plain IPS ("PATCH" ... "EOF"), no RLE records; the caller checks the result's CRC32. */
    internal fun applyIps(src: ByteArray, p: ByteArray): ByteArray {
        require(p.size > 8 && String(p, 0, 5, Charsets.ISO_8859_1) == "PATCH") { "not IPS" }
        val out = src.copyOf()
        var i = 5
        while (true) {
            require(i + 3 <= p.size) { "truncated IPS" }
            if (p[i] == 'E'.code.toByte() && p[i + 1] == 'O'.code.toByte() && p[i + 2] == 'F'.code.toByte()) break
            val off = ((p[i].toInt() and 0xff) shl 16) or ((p[i + 1].toInt() and 0xff) shl 8) or (p[i + 2].toInt() and 0xff)
            val len = ((p[i + 3].toInt() and 0xff) shl 8) or (p[i + 4].toInt() and 0xff)
            i += 5
            require(len > 0 && off + len <= out.size && i + len <= p.size) { "bad IPS record" }
            System.arraycopy(p, i, out, off, len); i += len
        }
        return out
    }
}
