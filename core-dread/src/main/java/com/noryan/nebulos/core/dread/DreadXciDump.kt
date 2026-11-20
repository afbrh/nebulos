package com.noryan.nebulos.core.dread

import android.content.Context
import android.util.Log
import com.noryan.nebulos.recomp.RecompGameFiles
import org.json.JSONObject
import java.io.File

/**
 * First launch: takes Metroid Dread 2.1.0's `exefs/` and `romfs/` out of the user's own XCI with hactool (libhactool.so from :core-smo, run from the native library folder)
 * and the user's own `prod.keys`. The cartridge carries the base game (1.0.0, the largest program NCA) and the update (the next largest); the update is applied over the
 * base (`--basenca`), which gives the merged 2.1.0 RomFS and the 2.1.0 ExeFS. `main`'s build id must be 2.1.0's; anything else is refused with a clear message.
 */
object DreadXciDump : RecompGameFiles.Dumper {
    private const val TAG = "DreadXciDump"
    private val MAIN_BUILD_ID_210 = byteArrayOf(0x64, 0x67, 0x61, 0xf6.toByte(), 0x43, 0xaf.toByte(), 0xeb.toByte(), 0xb3.toByte())

    override fun dump(ctx: Context, gamePath: String, dir: File, gameName: String, progress: (Float, String) -> Unit): JSONObject {
        val ext = gamePath.substringAfterLast('.', "").lowercase()
        if (ext != "xci") throw RecompGameFiles.Failure(gameName, "$gameName must be an XCI (cartridge image) for this core.\nA .$ext file is not supported yet.")
        val hactool = File(ctx.applicationInfo.nativeLibraryDir, "libhactool.so")
        if (!hactool.isFile) throw RecompGameFiles.Failure(gameName, "The game-file extractor is missing from this build.")
        val keys = File(ctx.getExternalFilesDir(null), "keys/prod.keys")
        if (!keys.isFile) throw RecompGameFiles.Failure(gameName, "Your Switch keys were not found.\nPut your own prod.keys in the Nebulos keys folder:\n${keys.parent}")

        val tmp = File(dir.parentFile, ".dread-xci-tmp").also { it.deleteRecursively(); it.mkdirs() }
        try {
            progress(0.02f, "Reading $gameName from the XCI (first launch only)")
            var nca = 0
            run(hactool, listOf("-k", keys.path, "--disablekeywarns", "-t", "xci", "--securedir=${tmp.path}", gamePath)) { line ->
                if (line.startsWith("Saving ") && line.contains(".nca")) { nca++; progress(0.02f + 0.25f * (nca / 10f).coerceAtMost(1f), "Reading $gameName from the XCI (first launch only)") }
            }
            val programs = tmp.listFiles { f -> f.isFile && f.name.endsWith(".nca") && !f.name.endsWith(".cnmt.nca") && f.length() > 20L * 1024 * 1024 }
                ?.sortedByDescending { it.length() }.orEmpty()
            if (programs.isEmpty()) throw RecompGameFiles.Failure(gameName, "No game data was found in the XCI.\n(Is it a complete, unmodified dump of the cartridge?)")
            if (programs.size < 2) throw RecompGameFiles.Failure(gameName, "This XCI has no update.\nThis core runs Metroid Dread 2.1.0, which needs the update the cartridge image normally carries.")
            val base = programs[0]; val patch = programs[1]

            dir.deleteRecursively(); dir.mkdirs()
            var files = 0
            progress(0.27f, "Extracting $gameName game files (first launch only)")
            run(hactool, listOf("-k", keys.path, "--disablekeywarns", "-t", "nca", "--basenca=${base.path}", "--exefsdir=${File(dir, "exefs").path}", "--romfsdir=${File(dir, "romfs").path}", patch.path)) { line ->
                if (line.startsWith("Saving ")) { files++; progress(0.27f + 0.71f * (files / 9800f).coerceAtMost(1f), "Extracting $gameName game files (first launch only): $files") }
            }

            val main = File(dir, "exefs/main")
            val id = if (main.isFile) main.inputStream().use { s -> ByteArray(0x48).also { s.read(it) }.copyOfRange(0x40, 0x48) } else null
            if (id == null || !id.contentEquals(MAIN_BUILD_ID_210)) {
                dir.deleteRecursively()
                throw RecompGameFiles.Failure(gameName, "This XCI's update is not Metroid Dread 2.1.0.\nThis core runs version 2.1.0 only.")
            }
            progress(1f, "$gameName game files ready")
            return JSONObject().put("ok", true).put("displayVersion", "2.1.0").put("version", "2.1.0").put("source", "XCI base + update (hactool)")
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
            throw RecompGameFiles.Failure("Metroid Dread", "Reading the game file failed (hactool exit $code).\n${tail.lastOrNull().orEmpty()}")
        }
    }
}
