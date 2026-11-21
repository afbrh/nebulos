package com.noryan.nebulos.core.sshd

import android.content.Context
import android.os.StatFs
import android.util.Log
import com.noryan.nebulos.recomp.RecompGameFiles
import org.json.JSONObject
import java.io.File

/**
 * First launch: takes The Legend of Zelda: Skyward Sword HD 1.0.1's `exefs/` and `romfs/` out of the user's own XCI with hactool (libhactool.so from :core-smo, run from the native library
 * folder) and the user's own `prod.keys`. The cartridge carries the base game (1.0.0, the largest program NCA) and the 1.0.1 update (the next largest); the update is applied
 * over the base (`--basenca`), which gives the merged RomFS (876 files, about 7 GB) and the 1.0.1 ExeFS. `main`'s build id must be 1.0.1's; anything else is refused.
 *
 * Where things go (nothing is shared with any other game):
 *  - `<external files>/native-dumps/01002DA013484000/exefs` (a few MB) and the marker: the data folder RecompGameFiles keeps for every game.
 *  - the RomFS does not fit the internal volume: it is written to `<cartridge's volume>/Nebulos-gamedata/01002DA013484000/romfs` (the volume the XCI is on, e.g. the SD card), and
 *    `romfs.override` in the data folder points to it (the runtime reads it in place, no copy). It is built as `romfs.part` and renamed when complete, so an interrupted
 *    first launch never leaves a half RomFS behind. A RomFS that is already complete there is reused.
 *  - the two NCAs are staged next to it (`.xci-tmp`, about 8 GB) and removed afterwards: the volume needs about 20 GB free for the first launch (about 7 GB afterwards).
 */
object SshdXciDump : RecompGameFiles.Dumper {
    private const val TAG = "SshdXciDump"
    private const val GB = 1024L * 1024 * 1024
    private const val ROMFS_FILES = 876f
    /** First 8 bytes of `main`'s build id (5441AF3C89F97E1D...) of the update 1.0.1. */
    private val MAIN_BUILD_ID_101 = byteArrayOf(0x54, 0x41, 0xaf.toByte(), 0x3c, 0x89.toByte(), 0xf9.toByte(), 0x7e, 0x1d)

    /** Where this game's RomFS lives for a cartridge at [gamePath]: its own folder on the cartridge's volume, else next to the ExeFS. */
    fun romfsHome(gamePath: String, dataDir: File): File {
        val vol = Regex("^(/storage/[^/]+)/").find(gamePath)?.groupValues?.get(1)?.let { File(it) }
        return if (vol != null && vol.isDirectory && vol.canWrite()) File(vol, "Nebulos-gamedata/${SshdCore.TITLE_ID}/romfs") else File(dataDir, "romfs")
    }

    override fun dump(ctx: Context, gamePath: String, dir: File, gameName: String, progress: (Float, String) -> Unit): JSONObject {
        val ext = gamePath.substringAfterLast('.', "").lowercase()
        if (ext != "xci") throw RecompGameFiles.Failure(gameName, "$gameName must be an XCI (cartridge image) for this core.\nA .$ext file is not supported yet.")
        val hactool = File(ctx.applicationInfo.nativeLibraryDir, "libhactool.so")
        if (!hactool.isFile) throw RecompGameFiles.Failure(gameName, "The game-file extractor is missing from this build.")
        val keys = File(ctx.getExternalFilesDir(null), "keys/prod.keys")
        if (!keys.isFile) throw RecompGameFiles.Failure(gameName, "Your Switch keys were not found.\nPut your own prod.keys in the Nebulos keys folder:\n${keys.parent}")

        val romfs = romfsHome(gamePath, dir)
        val part = File(romfs.parentFile, "romfs.part")
        val doneMark = File(romfs.parentFile, "romfs.complete")
        val romfsDone = doneMark.isFile && romfs.isDirectory
        val tmp = File(romfs.parentFile, ".xci-tmp")
        romfs.parentFile?.mkdirs()
        if (!romfsDone) {
            val free = runCatching { StatFs(romfs.parentFile!!.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
            if (free < 20 * GB) throw RecompGameFiles.Failure(gameName, "Not enough free space for the first launch.\n$gameName needs about 20 GB free on the volume holding the game file (about 27 GB are kept afterwards); ${free / GB} GB are free.")
        }
        tmp.deleteRecursively(); tmp.mkdirs()
        try {
            progress(0.01f, "Reading $gameName from the XCI (first launch only)")
            var nca = 0
            run(hactool, listOf("-k", keys.path, "--disablekeywarns", "-t", "xci", "--securedir=${tmp.path}", gamePath)) { line ->
                if (line.startsWith("Saving ") && line.contains(".nca")) { nca++; progress(0.01f + 0.30f * (nca / 12f).coerceAtMost(1f), "Reading $gameName from the XCI (first launch only)") }
            }
            val programs = tmp.listFiles { f -> f.isFile && f.name.endsWith(".nca") && !f.name.endsWith(".cnmt.nca") && f.length() > 20L * 1024 * 1024 }
                ?.sortedByDescending { it.length() }.orEmpty()
            if (programs.isEmpty()) throw RecompGameFiles.Failure(gameName, "No game data was found in the XCI.\n(Is it a complete, unmodified dump of the cartridge?)")
            if (programs.size < 2) throw RecompGameFiles.Failure(gameName, "This XCI has no update.\nThis core runs The Legend of Zelda: Skyward Sword HD 1.0.1, which needs the update the cartridge image normally carries.")
            val base = programs[0]; val patch = programs[1]

            // the ExeFS (and, unless it is already there, the RomFS): the update applied over the base
            dir.listFiles()?.forEach { if (it.name != "romfs") it.deleteRecursively() }
            File(dir, "romfs").mkdirs()
            val args = mutableListOf("-k", keys.path, "--disablekeywarns", "-t", "nca", "--basenca=${base.path}", "--exefsdir=${File(dir, "exefs").path}")
            if (!romfsDone) { part.deleteRecursively(); args += "--romfsdir=${part.path}" }
            args += patch.path
            var files = 0
            progress(0.31f, "Extracting $gameName game files (first launch only)")
            run(hactool, args) { line ->
                if (line.startsWith("Saving ")) { files++; progress(0.31f + 0.67f * (files / ROMFS_FILES).coerceAtMost(1f), "Extracting $gameName game files (first launch only): $files") }
            }
            if (!romfsDone) {
                romfs.deleteRecursively()
                if (!part.renameTo(romfs)) throw RecompGameFiles.Failure(gameName, "Could not finish the game files (${part.path}).")
                doneMark.writeText("1.0.1")
            }

            val main = File(dir, "exefs/main")
            val id = if (main.isFile) main.inputStream().use { s -> ByteArray(0x48).also { s.read(it) }.copyOfRange(0x40, 0x48) } else null
            if (id == null || !id.contentEquals(MAIN_BUILD_ID_101)) {
                dir.listFiles()?.forEach { it.deleteRecursively() }
                throw RecompGameFiles.Failure(gameName, "This XCI's update is not The Legend of Zelda: Skyward Sword HD 1.0.1.\nThis core runs version 1.0.1 only.")
            }
            // the runtime reads the RomFS where it is; `romfs.override` (RecompGameFiles) tells it
            if (romfs.absolutePath != File(dir, "romfs").absolutePath) File(dir, "romfs.override").writeText(romfs.absolutePath)
            progress(1f, "$gameName game files ready")
            return JSONObject().put("ok", true).put("displayVersion", "1.0.1").put("version", "1.0.1").put("source", "XCI base + update (hactool)")
        } finally {
            tmp.deleteRecursively()
            part.takeIf { it.exists() && !doneMark.isFile }?.deleteRecursively()
        }
    }

    private fun run(exe: File, args: List<String>, onLine: (String) -> Unit) {
        val p = ProcessBuilder(listOf(exe.path) + args).redirectErrorStream(true).start()
        val tail = ArrayDeque<String>()
        p.inputStream.bufferedReader().useLines { lines -> lines.forEach { l -> onLine(l); tail.addLast(l); if (tail.size > 6) tail.removeFirst() } }
        val code = p.waitFor()
        if (code != 0) {
            Log.e(TAG, "hactool exit $code: ${tail.joinToString(" | ")}")
            throw RecompGameFiles.Failure("Skyward Sword HD", "Reading the game file failed (hactool exit $code).\n${tail.lastOrNull().orEmpty()}")
        }
    }
}
