package com.noryan.nebulos.core.smo

import android.content.Context
import android.util.Log
import com.noryan.nebulos.recomp.RecompGameFiles
import org.json.JSONObject
import java.io.File

/**
 * First launch: takes the base game's `exefs/` and `romfs/` out of the user's own XCI with hactool (ISC, (c) SciresM; built as libhactool.so and run
 * from the app's native library folder) and the user's own `prod.keys` (the `keys` folder Eden already uses).
 *
 * Why not Eden's dump ([RecompGameFiles]'s default): a Super Mario Odyssey cartridge also carries the update, and Eden applies it, giving a newer
 * version than the 1.0.0 this runtime is built for. Here the cartridge's BASE program NCA (the largest one) is extracted and its `main` checked
 * against 1.0.0's build id; any other version is refused with a clear message.
 */
object SmoXciDump : RecompGameFiles.Dumper {
    private const val TAG = "SmoXciDump"
    private val MAIN_BUILD_ID_100 = byteArrayOf(0x3c, 0xa1.toByte(), 0x2d, 0xfa.toByte(), 0xaf.toByte(), 0x9c.toByte(), 0x82.toByte(), 0xda.toByte())

    override fun dump(ctx: Context, gamePath: String, dir: File, gameName: String, progress: (Float, String) -> Unit): JSONObject {
        val ext = gamePath.substringAfterLast('.', "").lowercase()
        if (ext != "xci") throw RecompGameFiles.Failure(gameName, "$gameName must be an XCI (cartridge image) for this core.\\nA .$ext file is not supported yet.")
        val hactool = File(ctx.applicationInfo.nativeLibraryDir, "libhactool.so")
        if (!hactool.isFile) throw RecompGameFiles.Failure(gameName, "The game-file extractor is missing from this build.")
        val keys = File(ctx.getExternalFilesDir(null), "keys/prod.keys")
        if (!keys.isFile) throw RecompGameFiles.Failure(gameName, "Your Switch keys were not found.\\nPut your own prod.keys in the Nebulos keys folder:\\n${keys.parent}")

        val tmp = File(dir.parentFile, ".smo-xci-tmp").also { it.deleteRecursively(); it.mkdirs() }
        try {
            // 1. the cartridge's secure partition: its NCA files
            progress(0.02f, "Reading $gameName from the XCI (first launch only)")
            var nca = 0
            run(hactool, listOf("-k", keys.path, "--disablekeywarns", "-t", "xci", "--securedir=${tmp.path}", gamePath)) { line ->
                if (line.startsWith("Saving ") && line.contains(".nca")) { nca++; progress((0.02f + 0.30f * (nca / 12f).coerceAtMost(1f)), "Reading $gameName from the XCI (first launch only)") }
            }
            val base = tmp.listFiles { f -> f.isFile && f.name.endsWith(".nca") && !f.name.endsWith(".cnmt.nca") }?.maxByOrNull { it.length() }
                ?: throw RecompGameFiles.Failure(gameName, "No game data was found in the XCI.\\n(Is it a complete, unmodified dump of the cartridge?)")

            // 2. the base program: exefs + romfs as loose files
            dir.deleteRecursively(); dir.mkdirs()
            var files = 0
            run(hactool, listOf("-k", keys.path, "--disablekeywarns", "-t", "nca", "--exefsdir=${File(dir, "exefs").path}", "--romfsdir=${File(dir, "romfs").path}", base.path)) { line ->
                if (line.startsWith("Saving ")) { files++; progress(0.32f + 0.66f * (files / 4700f).coerceAtMost(1f), "Extracting $gameName game files (first launch only): $files") }
            }

            // 3. it must be 1.0.0 (main's build id)
            val main = File(dir, "exefs/main")
            val id = if (main.isFile) main.inputStream().use { s -> ByteArray(0x48).also { s.read(it) }.copyOfRange(0x40, 0x48) } else null
            if (id == null || !id.contentEquals(MAIN_BUILD_ID_100)) {
                dir.deleteRecursively()
                throw RecompGameFiles.Failure(gameName, "This cartridge's base game is not Super Mario Odyssey 1.0.0.\\nThis core runs version 1.0.0 only.")
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
            throw RecompGameFiles.Failure("Super Mario Odyssey", "Reading the game file failed (hactool exit $code).\\n${tail.lastOrNull().orEmpty()}")
        }
    }
}
