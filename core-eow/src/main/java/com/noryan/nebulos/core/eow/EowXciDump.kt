package com.noryan.nebulos.core.eow

import android.content.Context
import android.util.Log
import com.noryan.nebulos.recomp.RecompGameFiles
import org.json.JSONObject
import java.io.File

/**
 * First launch: takes The Legend of Zelda: Echoes of Wisdom's `exefs/` and `romfs/` out of the user's own XCI with hactool (libhactool.so from :core-smo, run from the native library folder) and
 * the user's own `prod.keys`. The game's RomFS (10,963 files, 8 GB) is stored through the NCA compression layer (LZ4 blocks), which this build of hactool reads (nca_comp.c). Only the BASE program
 * NCA (the largest one, about 5.8 GB) is extracted: its `main` is version 1.0.0 (build id a581406bc9858651...), the version this core runs. A cartridge's update (1.0.1) is ignored.
 *
 * Everything goes into this game's own data folder `<external files>/native-dumps/01008CF01BAAC000` (`exefs/`, `romfs/`, the marker RecompGameFiles keeps): nothing is shared with any other
 * game. The cartridge's NCAs are staged beside it (`.eow-xci-tmp`, about 6 GB) and removed afterwards. Needs about 15 GB free during the first launch (8 GB stays).
 */
object EowXciDump : RecompGameFiles.Dumper {
    private const val TAG = "EowXciDump"
    private const val ROMFS_FILES = 10963f
    /** First 8 bytes of `main`'s build id: 1.0.0 (a581406bc9858651...). */
    private val MAIN_BUILD_ID_100 = byteArrayOf(0xa5.toByte(), 0x81.toByte(), 0x40, 0x6b, 0xc9.toByte(), 0x85.toByte(), 0x86.toByte(), 0x51)

    override fun dump(ctx: Context, gamePath: String, dir: File, gameName: String, progress: (Float, String) -> Unit): JSONObject {
        val ext = gamePath.substringAfterLast('.', "").lowercase()
        if (ext != "xci") throw RecompGameFiles.Failure(gameName, "$gameName must be an XCI (cartridge image) for this core.\nA .$ext file is not supported yet.")
        val hactool = File(ctx.applicationInfo.nativeLibraryDir, "libhactool.so")
        if (!hactool.isFile) throw RecompGameFiles.Failure(gameName, "The game-file extractor is missing from this build.")
        val keys = File(ctx.getExternalFilesDir(null), "keys/prod.keys")
        if (!keys.isFile) throw RecompGameFiles.Failure(gameName, "Your Switch keys were not found.\nPut your own prod.keys in the Nebulos keys folder:\n${keys.parent}")

        val tmp = File(dir.parentFile, ".eow-xci-tmp").also { it.deleteRecursively(); it.mkdirs() }
        try {
            if (tmp.usableSpace < 15L * 1024 * 1024 * 1024) throw RecompGameFiles.Failure(gameName, "Not enough free space for the first-launch extraction.\n$gameName needs about 15 GB free (8 GB stay used).")
            progress(0.02f, "Reading $gameName from the XCI (first launch only)")
            var nca = 0
            run(hactool, listOf("-k", keys.path, "--disablekeywarns", "-t", "xci", "--securedir=${tmp.path}", gamePath)) { line ->
                if (line.startsWith("Saving ") && line.contains(".nca")) { nca++; progress(0.02f + 0.10f * (nca / 8f).coerceAtMost(1f), "Reading $gameName from the XCI (first launch only)") }
            }
            val base = tmp.listFiles { f -> f.isFile && f.name.endsWith(".nca") && !f.name.endsWith(".cnmt.nca") }?.maxByOrNull { it.length() }
                ?: throw RecompGameFiles.Failure(gameName, "No game data was found in the XCI.\n(Is it a complete, unmodified dump of the cartridge?)")

            dir.deleteRecursively(); dir.mkdirs()
            var files = 0
            progress(0.12f, "Extracting $gameName game files (first launch only)")
            run(hactool, listOf("-k", keys.path, "--disablekeywarns", "-t", "nca", "--exefsdir=${File(dir, "exefs").path}", "--romfsdir=${File(dir, "romfs").path}", base.path)) { line ->
                if (line.startsWith("Saving ")) { files++; progress(0.12f + 0.86f * (files / ROMFS_FILES).coerceAtMost(1f), "Extracting $gameName game files (first launch only): $files") }
            }

            val main = File(dir, "exefs/main")
            val id = if (main.isFile) main.inputStream().use { s -> ByteArray(0x48).also { s.read(it) }.copyOfRange(0x40, 0x48) } else null
            if (id == null || !id.contentEquals(MAIN_BUILD_ID_100)) {
                dir.deleteRecursively()
                throw RecompGameFiles.Failure(gameName, "This cartridge's base game is not The Legend of Zelda: Echoes of Wisdom 1.0.0.\nThis core runs version 1.0.0 only.")
            }
            progress(1f, "$gameName game files ready")
            return JSONObject().put("ok", true).put("displayVersion", "1.0.0").put("version", "1.0.0").put("source", "XCI base program (hactool)")
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
            throw RecompGameFiles.Failure("Echoes of Wisdom", "Reading the game file failed (hactool exit $code).\n${tail.lastOrNull().orEmpty()}")
        }
    }
}
