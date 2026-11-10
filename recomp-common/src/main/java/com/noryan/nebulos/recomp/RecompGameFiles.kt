package com.noryan.nebulos.recomp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.noryan.nebulos.api.save.SaveRoot
import org.json.JSONObject
import java.io.File

/**
 * The recomp runtimes read the game's `exefs/` (main, sdk, subsdk*, rtld, main.npdm) and `romfs/` as loose files. Nebulos gets them from
 * the user's own XCI/NSP: Eden (which already holds the keys and the update) dumps them once ([EdenDumpService] in the :eden process, the
 * JNI `dumpGameFiles` of core-eden patch 0009) into `<_Nebulos root>/native-dumps/<TITLEID>/{exefs,romfs}`; this class starts that dump on
 * the first launch, waits for it (progress -> the loading screen) and builds the runtime's data root from it:
 *
 *   <filesDir>/<coreId>/[v160|v182|v190]/extracted/exefs/...          (copied, a few MB)
 *   <filesDir>/<coreId>/[...]/extracted/romfs.link                    (text: the dump's romfs/ folder, used in place, no copy)
 *
 * The folder name decides the game version for BotW (`runtime/paths.c`: v160 / v182 / v190, none = 1.0.0).
 */
object RecompGameFiles {
    private const val TAG = "RecompGameFiles"
    const val EDEN_DUMP_SERVICE = "com.noryan.nebulos.core.eden.EdenDumpService"

    class Prepared(val dataRoot: File, val displayVersion: String)
    class Failure(val title: String, message: String) : Exception(message)

    fun dumpDir(ctx: Context, titleId: String): File = File(File(SaveRoot.current(ctx), "native-dumps"), titleId)
    private fun marker(dir: File) = File(dir, "nebulos-dump.json")

    /** Folder suffix the BotW runtime expects for a game version ("" = 1.0.0), or null for a version it cannot run. */
    fun botwFolder(displayVersion: String): String? = when (displayVersion.trim()) {
        "1.0.0" -> ""; "1.6.0" -> "v160"; "1.8.2" -> "v182"; "1.9.0" -> "v190"; else -> null
    }

    /** TotK: only 1.4.3 (the port's main version, no folder suffix). */
    fun totkFolder(displayVersion: String): String? = if (displayVersion.trim() == "1.4.3") "" else null

    /** Blocks (call off the main thread). [progress]: fraction 0..1 (NaN = unknown) and a status line. */
    fun ensure(
        ctx: Context, coreId: String, titleId: String, gamePath: String, folderFor: (String) -> String?,
        gameName: String, progress: (Float, String) -> Unit,
    ): Prepared {
        val dir = dumpDir(ctx, titleId)
        var info = readMarker(dir)
        if (info == null) {
            if (gamePath.isBlank() || !File(gamePath).exists()) throw Failure(gameName, "The game file is missing:\n$gamePath")
            progress(Float.NaN, "Preparing $gameName game files (first launch only)")
            info = dump(ctx, gamePath, dir, gameName, progress)
        }
        val display = info.optString("displayVersion").ifBlank { info.optString("version") }
        val folder = folderFor(display)
            ?: throw Failure(gameName, "$gameName $display is not supported by this core.\n(See the core's README for the supported versions.)")
        val root = File(File(ctx.filesDir, coreId), folder).also { it.mkdirs() }
        val extracted = File(root, "extracted").also { it.mkdirs() }
        val exefsSrc = File(dir, "exefs")
        val exefsDst = File(extracted, "exefs")
        if (!exefsDst.isDirectory || exefsDst.listFiles().isNullOrEmpty() || exefsMtime(exefsSrc) != File(extracted, ".exefs-stamp").takeIf { it.exists() }?.readText()?.trim()) {
            exefsDst.deleteRecursively(); exefsDst.mkdirs()
            exefsSrc.listFiles()?.forEach { it.copyTo(File(exefsDst, it.name), overwrite = true) }
            File(extracted, ".exefs-stamp").writeText(exefsMtime(exefsSrc) ?: "")
        }
        File(extracted, "romfs.link").writeText(File(dir, "romfs").absolutePath)
        return Prepared(root, display)
    }

    private fun exefsMtime(d: File): String? = d.listFiles()?.maxOfOrNull { it.lastModified() }?.toString()

    private fun readMarker(dir: File): JSONObject? = try {
        val m = marker(dir)
        if (m.exists() && File(dir, "exefs/main").exists() && File(dir, "romfs").isDirectory) JSONObject(m.readText()) else null
    } catch (_: Exception) { null }

    private fun dump(ctx: Context, gamePath: String, dir: File, gameName: String, progress: (Float, String) -> Unit): JSONObject {
        val status = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "eden_dump_status.json")
        status.delete()
        dir.mkdirs()
        val i = Intent().setComponent(ComponentName(ctx.packageName, EDEN_DUMP_SERVICE)).putExtra("game", gamePath).putExtra("out", dir.absolutePath)
        ctx.startForegroundService(i)
        var lastLog = 0L
        while (true) {
            Thread.sleep(500)
            val j = try { if (status.exists()) JSONObject(status.readText()) else null } catch (_: Exception) { null } ?: continue
            when (j.optString("state")) {
                "running" -> {
                    val done = j.optLong("done", -1); val total = j.optLong("total", -1)
                    if (total > 0) progress((done.toDouble() / total).toFloat(), "Preparing $gameName game files (first launch only): ${done * 100 / total}%")
                    if (System.currentTimeMillis() - lastLog > 10_000) { lastLog = System.currentTimeMillis(); Log.i(TAG, "dumping: $done / $total") }
                }
                "finished" -> {
                    val r = j.optJSONObject("result") ?: JSONObject()
                    if (!r.optBoolean("ok", false)) throw Failure(gameName, "Could not read the game files:\n${r.optString("error", "unknown error")}")
                    marker(dir).writeText(r.toString())
                    return r
                }
            }
        }
    }
}
