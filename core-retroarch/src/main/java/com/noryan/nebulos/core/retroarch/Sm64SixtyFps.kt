package com.noryan.nebulos.core.retroarch

import android.content.Context
import com.noryan.nebulos.api.save.SaveRoot
import java.io.File
import java.util.zip.CRC32

/**
 * 60 fps for Super Mario 64 (US, big-endian .z64) on the N64 core: Kaze Emanuar's "60 FPS Patch" v2 (BPS, 1181 bytes, bundled as an
 * asset; it carries no game data) is applied to the user's own ROM at launch. The ROM on disk is never touched: a patched copy with
 * the same file name goes to filesDir/sm64-60fps/, so battery saves (.srm, named after the content file) are shared with the stock
 * ROM. The first patched launch keeps a one-time backup of the stock save. Save states are RDRAM snapshots of one specific ROM, so the
 * patched variant keeps its own state folder (see RaSession.stateDir). Anything that is not the exact US ROM is left alone.
 */
internal object Sm64SixtyFps {
    private const val ASSET = "sm64_60fps_v2.bps"
    private const val PREF = "sm64_60fps"
    private const val ROM_SIZE = 8_388_608L

    fun isOn(ctx: Context): Boolean = ctx.getSharedPreferences("nebulos_retro", Context.MODE_PRIVATE).getBoolean(PREF, false)
    fun setOn(ctx: Context, on: Boolean) { ctx.getSharedPreferences("nebulos_retro", Context.MODE_PRIVATE).edit().putBoolean(PREF, on).commit() }

    /** True for the exact ROM the patch was made for (size and CRC32 of the .z64 match the patch's source checksum). */
    fun eligible(ctx: Context, rom: File): Boolean = runCatching {
        if (rom.length() != ROM_SIZE || !rom.extension.equals("z64", true)) return false
        val patch = ctx.assets.open(ASSET).use { it.readBytes() }
        crc32(rom.readBytes()) == le32(patch, patch.size - 12)
    }.getOrDefault(false)

    /** Patched copy of [rom] (same file name), or null if it could not be produced; the caller then runs the stock ROM. */
    fun patchedCopy(ctx: Context, rom: File): File? = runCatching {
        val out = File(File(ctx.filesDir, "sm64-60fps").apply { mkdirs() }, rom.name)
        val bytes = applyBps(rom.readBytes(), ctx.assets.open(ASSET).use { it.readBytes() })
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

    private fun le32(b: ByteArray, o: Int) = (b[o].toLong() and 0xff) or ((b[o + 1].toLong() and 0xff) shl 8) or
        ((b[o + 2].toLong() and 0xff) shl 16) or ((b[o + 3].toLong() and 0xff) shl 24)
    private fun crc32(b: ByteArray) = CRC32().also { it.update(b) }.value

    /** Standard BPS1 decoder; verifies the source and target CRC32s. */
    internal fun applyBps(src: ByteArray, p: ByteArray): ByteArray {
        require(p.size > 19 && p[0] == 'B'.code.toByte() && p[1] == 'P'.code.toByte() && p[2] == 'S'.code.toByte() && p[3] == '1'.code.toByte()) { "not BPS" }
        var i = 4
        fun num(): Long {
            var v = 0L; var s = 1L
            while (true) {
                val x = p[i++].toInt() and 0xff
                v += (x and 0x7f) * s
                if (x and 0x80 != 0) break
                s = s shl 7; v += s
            }
            return v
        }
        val srcSize = num(); val tgtSize = num(); val metaSize = num()
        i += metaSize.toInt()
        require(srcSize == src.size.toLong()) { "source size" }
        require(crc32(src) == le32(p, p.size - 12)) { "source crc" }
        val out = ByteArray(tgtSize.toInt())
        var o = 0; var so = 0; var to = 0
        val end = p.size - 12
        while (i < end) {
            val v = num(); val n = (v shr 2).toInt() + 1
            when ((v and 3).toInt()) {
                0 -> { System.arraycopy(src, o, out, o, n); o += n }
                1 -> { System.arraycopy(p, i, out, o, n); i += n; o += n }
                2 -> { val d = num(); so += (if (d and 1L != 0L) -(d shr 1) else d shr 1).toInt(); repeat(n) { out[o++] = src[so++] } }
                else -> { val d = num(); to += (if (d and 1L != 0L) -(d shr 1) else d shr 1).toInt(); repeat(n) { out[o++] = out[to++] } }
            }
        }
        require(crc32(out) == le32(p, p.size - 8)) { "target crc" }
        return out
    }
}
