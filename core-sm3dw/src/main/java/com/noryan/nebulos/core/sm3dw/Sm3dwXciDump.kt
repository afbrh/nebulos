package com.noryan.nebulos.core.sm3dw

import android.content.Context
import android.util.Log
import com.noryan.nebulos.recomp.RecompGameFiles
import org.json.JSONObject
import java.io.File

/**
 * First launch: takes Super Mario 3D World + Bowser's Fury's `exefs/` and `romfs/` out of the user's own XCI with hactool (libhactool.so from :core-smo, run from the native
 * library folder) and the user's own `prod.keys`. The cartridge carries the base game (the largest program NCA, about 3 GB) and, normally, an update (v65536, the next one,
 * about 90 MB); the update is applied over the base (`--basenca`), which gives the merged RomFS (about 3,780 files, 2.9 GB) and the updated ExeFS. `main`'s build id must be
 * the update's or the base's; anything else is refused.
 *
 * Everything goes into this game's own data folder `<external files>/native-dumps/010028600EBDA000` (`exefs/`, `romfs/`, the marker RecompGameFiles keeps): nothing is shared with
 * any other game. The cartridge's two NCAs are staged beside it (`.sm3dw-xci-tmp`, about 3.2 GB) and removed afterwards.
 */
object Sm3dwXciDump : RecompGameFiles.Dumper {
    private const val TAG = "Sm3dwXciDump"
    private const val ROMFS_FILES = 3780f
    /** First 8 bytes of `main`'s build id: the update (891687f016a18f17...) and the base 1.0.0 (9f7efc2fb9653e5c...). */
    private val MAIN_BUILD_ID_UPDATE = byteArrayOf(0x89.toByte(), 0x16, 0x87.toByte(), 0xf0.toByte(), 0x16, 0xa1.toByte(), 0x8f.toByte(), 0x17)
    private val MAIN_BUILD_ID_BASE = byteArrayOf(0x9f.toByte(), 0x7e, 0xfc.toByte(), 0x2f, 0xb9.toByte(), 0x65, 0x3e, 0x5c)

    override fun dump(ctx: Context, gamePath: String, dir: File, gameName: String, progress: (Float, String) -> Unit): JSONObject {
        val ext = gamePath.substringAfterLast('.', "").lowercase()
        if (ext != "xci") throw RecompGameFiles.Failure(gameName, "$gameName must be an XCI (cartridge image) for this core.\nA .$ext file is not supported yet.")
        val hactool = File(ctx.applicationInfo.nativeLibraryDir, "libhactool.so")
        if (!hactool.isFile) throw RecompGameFiles.Failure(gameName, "The game-file extractor is missing from this build.")
        val keys = File(ctx.getExternalFilesDir(null), "keys/prod.keys")
        if (!keys.isFile) throw RecompGameFiles.Failure(gameName, "Your Switch keys were not found.\nPut your own prod.keys in the Nebulos keys folder:\n${keys.parent}")

        val tmp = File(dir.parentFile, ".sm3dw-xci-tmp").also { it.deleteRecursively(); it.mkdirs() }
        try {
            progress(0.02f, "Reading $gameName from the XCI (first launch only)")
            var nca = 0
            run(hactool, listOf("-k", keys.path, "--disablekeywarns", "-t", "xci", "--securedir=${tmp.path}", gamePath)) { line ->
                if (line.startsWith("Saving ") && line.contains(".nca")) { nca++; progress(0.02f + 0.20f * (nca / 8f).coerceAtMost(1f), "Reading $gameName from the XCI (first launch only)") }
            }
            val programs = tmp.listFiles { f -> f.isFile && f.name.endsWith(".nca") && !f.name.endsWith(".cnmt.nca") && f.length() > 20L * 1024 * 1024 }
                ?.sortedByDescending { it.length() }.orEmpty()
            if (programs.isEmpty()) throw RecompGameFiles.Failure(gameName, "No game data was found in the XCI.\n(Is it a complete, unmodified dump of the cartridge?)")
            val base = programs[0]; val patch = programs.getOrNull(1)

            dir.deleteRecursively(); dir.mkdirs()
            val args = mutableListOf("-k", keys.path, "--disablekeywarns", "-t", "nca")
            if (patch != null) args += "--basenca=${base.path}"
            args += listOf("--exefsdir=${File(dir, "exefs").path}", "--romfsdir=${File(dir, "romfs").path}", (patch ?: base).path)
            var files = 0
            progress(0.22f, "Extracting $gameName game files (first launch only)")
            run(hactool, args) { line ->
                if (line.startsWith("Saving ")) { files++; progress(0.22f + 0.76f * (files / ROMFS_FILES).coerceAtMost(1f), "Extracting $gameName game files (first launch only): $files") }
            }

            val main = File(dir, "exefs/main")
            val id = if (main.isFile) main.inputStream().use { s -> ByteArray(0x48).also { s.read(it) }.copyOfRange(0x40, 0x48) } else null
            val isUpdate = id != null && id.contentEquals(MAIN_BUILD_ID_UPDATE)
            if (id == null || !(isUpdate || id.contentEquals(MAIN_BUILD_ID_BASE))) {
                dir.deleteRecursively()
                throw RecompGameFiles.Failure(gameName, "This cartridge's game is not a Super Mario 3D World + Bowser's Fury version this core knows.\nIt runs the v65536 update or the base 1.0.0.")
            }
            progress(1f, "$gameName game files ready")
            return JSONObject().put("ok", true).put("displayVersion", "1.1.0").put("version", "1.1.0").put("source", if (isUpdate) "XCI base + update (hactool)" else "XCI base program (hactool)")
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun run(exe: File, args: List<String>, onLine: (String) -> Unit) {
        val p = ProcessBuilder(listOf(exe.path) + args).redirectErrorStream(true).start()
        val tail = ArrayDeque<String>()
        p.inputStream.bufferedReader().useLines { lines -> lines.forEach { l -> onLine(l); tail.addLast(l); if (tail.size > 6) tail.removeFirst() } }
        val code = p.waitFor()
        if (code != 0) {
            Log.e(TAG, "hactool exit $code: ${tail.joinToString(" | ")}")
            throw RecompGameFiles.Failure("Super Mario 3D World", "Reading the game file failed (hactool exit $code).\n${tail.lastOrNull().orEmpty()}")
        }
    }
}
